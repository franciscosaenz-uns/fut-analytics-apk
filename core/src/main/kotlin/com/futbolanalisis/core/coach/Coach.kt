package com.futbolanalisis.core.coach

import com.futbolanalisis.core.MatchReport
import com.futbolanalisis.core.physical.AlertLevel
import com.futbolanalisis.core.physio.Readiness
import com.futbolanalisis.core.tactical.Direction
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
enum class TipArea(val label: String) {
    PACING("Ritmo"), ENERGY("Energía"), POSITION("Posición"), TACTICS("Táctica"),
    PRODUCTION("Producción"), RECOVERY("Recuperación"),
}

@Serializable
data class CoachTip(
    val area: TipArea,
    val title: String,
    val detail: String,
    /** Minuto del partido al que se refiere, si aplica. */
    val minute: Int? = null,
    /** 1 = más importante. */
    val priority: Int = 2,
)

@Serializable
enum class DrillType(val label: String) { PHYSICAL("Físico"), TECHNICAL("Técnico"), TACTICAL("Táctico") }

@Serializable
data class Drill(
    val type: DrillType,
    val title: String,
    /** Por qué te lo recomendamos (ligado a tus datos o a tu rol). */
    val why: String,
    /** Cómo hacerlo: series, repeticiones, espacio. */
    val how: String,
    val dose: String,
)

@Serializable
data class CoachReport(
    val roleGroup: RoleGroup,
    val inMatchTips: List<CoachTip>,
    val drills: List<Drill>,
    val proMatches: List<ProMatch>,
)

@Serializable
enum class RoleGroup(val label: String) {
    GOALKEEPER("Portero"), CENTER_BACK("Central"), FULL_BACK("Lateral"), HOLDING_MID("Pivote"),
    CENTRAL_MID("Mediocentro"), ATTACKING_MID("Interior / mediapunta"), WINGER("Extremo"), STRIKER("Delantero");

    companion object {
        fun of(archetype: String): RoleGroup = when {
            archetype.startsWith("Portero") -> GOALKEEPER
            archetype.startsWith("Defensa") || archetype.startsWith("Central") -> CENTER_BACK
            archetype.startsWith("Lateral") || archetype.startsWith("Volante") -> FULL_BACK
            archetype.startsWith("Pivote") -> HOLDING_MID
            archetype.startsWith("Mediocentro") -> CENTRAL_MID
            archetype.startsWith("Interior") || archetype.startsWith("Mediapunta") -> ATTACKING_MID
            archetype.startsWith("Extremo") -> WINGER
            else -> STRIKER
        }
    }
}

/**
 * Entrenador virtual: consejos para el próximo partido y entrenamientos recomendados,
 * a partir del análisis, del registro manual y del rol detectado. Reglas explicables.
 */
object Coach {

    fun advise(r: MatchReport, log: MatchLog = MatchLog()): CoachReport {
        val group = RoleGroup.of(r.tactical.description.archetype)
        return CoachReport(
            roleGroup = group,
            inMatchTips = tips(r, log, group).sortedBy { it.priority }.take(8),
            drills = drills(r, group),
            proMatches = ProComparison.compare(r),
        )
    }

    private fun tips(r: MatchReport, log: MatchLog, group: RoleGroup): List<CoachTip> {
        val out = ArrayList<CoachTip>()
        val p = r.physical
        val t = r.tactical
        val e = r.energy
        val a = r.advanced
        val minutes = p.playingTimeS / 60
        val attacking = group in setOf(RoleGroup.WINGER, RoleGroup.STRIKER, RoleGroup.ATTACKING_MID)

        // --- Energía y ritmo ---
        if (e != null) {
            val critical = r.alerts.firstOrNull { it.level == AlertLevel.CRITICAL && it.title.startsWith("Reserva") }
            if (critical != null) {
                out += CoachTip(TipArea.ENERGY, "Llegaste sin reserva en el minuto ${critical.minute}",
                    "Antes de ese momento encadenaste esfuerzos sin recuperar. Tras cada sprint, recupera 20-30 s a trote suave " +
                        "si el juego lo permite, y evita presionar en solitario cuando el equipo no acompaña.", critical.minute, 1)
            } else if (e.minEnergy < 50) {
                out += CoachTip(TipArea.ENERGY, "Tu punto más bajo fue el minuto ${e.minEnergyMinute}",
                    "La energía bajó a ${e.minEnergy.roundToInt()}%. Hidrátate en cada pausa y elige mejor cuándo presionar en los 10 minutos previos.",
                    e.minEnergyMinute, 2)
            }
        }
        val fatigue = p.fatigueIndexPct
        if (fatigue != null && fatigue < -10) {
            out += CoachTip(TipArea.PACING, "Bajaste ${(-fatigue).roundToInt()}% de intensidad al final",
                "Arrancaste fuerte y lo pagaste al final. Reparte el esfuerzo: en los primeros 15' elige 2-3 acciones de máxima intensidad, no todas.",
                null, 1)
        }
        val lateHsrDrop = p.splits.size >= 3 && p.splits.last().highSpeedDistanceM < 0.5 * p.splits.map { it.highSpeedDistanceM }.average()
        if (lateHsrDrop) {
            out += CoachTip(TipArea.PACING, "Guarda piernas para el cierre",
                "En el último bloque tu distancia a alta intensidad fue menos de la mitad de tu media. Los goles tardíos se definen ahí: dosifica en la mitad del 2º tiempo.",
                r.alerts.lastOrNull { it.title.startsWith("Caída") }?.minute, 2)
        }
        a?.repeatedEfforts?.firstOrNull()?.let {
            out += CoachTip(TipArea.PACING, "Recupera tras las secuencias de esfuerzos",
                "En el minuto ${it.startMinute} hiciste ${it.efforts} esfuerzos casi seguidos. Después de una secuencia así, ocupa una posición de apoyo y respira 30 s antes de volver a presionar.",
                it.startMinute, 3)
        }
        r.alerts.firstOrNull { it.title == "Baja participación" }?.let {
            out += CoachTip(TipArea.TACTICS, "Te desconectaste del juego en el minuto ${it.minute}",
                "Pasaste varios minutos casi sin moverte. Aunque el balón esté lejos, ajusta tu posición con cada pase: es la forma de seguir en el partido.",
                it.minute, 2)
        }
        r.alerts.firstOrNull { it.title == "FC al límite" }?.let {
            out += CoachTip(TipArea.ENERGY, "Tu pulso estuvo al límite en el minuto ${it.minute}",
                "Más de 2 minutos sobre el 92% de tu FC máxima. En la siguiente pausa, exhala largo y lento para bajar pulsaciones antes de reengancharte.",
                it.minute, 2)
        }

        // --- Posición y táctica según el rol ---
        val forward = t.sprintVectors.count { it.direction == Direction.FORWARD }
        val sprints = t.sprintVectors.size
        if (attacking && sprints >= 3 && forward < sprints * 0.5) {
            out += CoachTip(TipArea.TACTICS, "Más desmarques en profundidad",
                "Solo $forward de $sprints sprints fueron hacia el arco rival. En tu rol, los sprints valen más cuando atacan la espalda de la defensa: arranca cuando el compañero levanta la cabeza.", null, 2)
        }
        if (group == RoleGroup.STRIKER && t.penaltyBoxPct < 10) {
            out += CoachTip(TipArea.POSITION, "Pisa más el área",
                "Estuviste solo el ${t.penaltyBoxPct.roundToInt()}% del tiempo en el área rival. Si bajas a asociarte, vuelve a ocupar el área antes de que llegue el centro.", null, 2)
        }
        if (group == RoleGroup.WINGER && t.widthRangeM.let { it[1] - it[0] } < 15 && t.lanesPct[1] > 40) {
            out += CoachTip(TipArea.POSITION, "Abre el campo",
                "Pasaste el ${t.lanesPct[1].roundToInt()}% en el carril central. Como extremo, mantener la amplitud estira la defensa rival y abre espacios para tus compañeros.", null, 2)
        }
        if (attacking && t.thirdsPct[0] > 30) {
            out += CoachTip(TipArea.POSITION, "Recibe más arriba",
                "Pasaste el ${t.thirdsPct[0].roundToInt()}% en tu tercio defensivo. Si bajas tanto a recibir, llegas tarde al área: espera el balón entre líneas.", null, 3)
        }
        if (group in setOf(RoleGroup.CENTER_BACK, RoleGroup.HOLDING_MID) && t.thirdsPct[2] > 25) {
            out += CoachTip(TipArea.POSITION, "Cuida el equilibrio defensivo",
                "Te fuiste al ataque el ${t.thirdsPct[2].roundToInt()}% del tiempo. Está bien sumarse, pero asegúrate de que alguien cubra tu zona antes de subir.", null, 3)
        }
        if (group == RoleGroup.FULL_BACK && t.thirdsPct[2] < 12 && p.sprints.size >= 2) {
            out += CoachTip(TipArea.TACTICS, "Acompaña más los ataques por tu banda",
                "Solo el ${t.thirdsPct[2].roundToInt()}% en el tercio ofensivo. Cuando el extremo fija por dentro, el carril queda libre para tu llegada.", null, 3)
        }
        val p1 = t.modePositionByPeriod[1]; val p2 = t.modePositionByPeriod[2]
        if (p1 != null && p2 != null && p2.x < p1.x - 10) {
            out += CoachTip(TipArea.POSITION, "En el 2º tiempo te hundiste",
                "Tu zona habitual retrocedió ${(p1.x - p2.x).roundToInt()} m. Si fue por cansancio, el equipo pierde metros: comunícalo o pide relevo antes de que se note.",
                r.periods.getOrNull(1)?.let { r.periods[0].durationMs.div(60000).toInt() + 1 }, 2)
        }

        // --- Registro manual ---
        if (log.shots >= 3 && log.shotsOnTarget < log.shots * 0.4) {
            out += CoachTip(TipArea.PRODUCTION, "Mejora la selección de tiro",
                "${log.shotsOnTarget} de ${log.shots} tiros fueron al arco. Antes de tirar, mira si hay un compañero mejor posicionado o si puedes acercarte un control más.", null, 2)
        }
        if (log.duelsLost > log.duelsWon && log.duelsLost >= 3) {
            out += CoachTip(TipArea.TACTICS, "Más duelos ganados",
                "Perdiste ${log.duelsLost} duelos y ganaste ${log.duelsWon}. Perfílate antes de recibir el choque y usa el cuerpo para proteger el balón.", null, 2)
        }
        val hrPct = r.heartRate?.let { it.avgBpm / it.maxHrUsed }
        val rpe = log.rpe ?: r.options.rpe
        if (rpe != null && rpe >= 8 && hrPct != null && hrPct < 0.75) {
            out += CoachTip(TipArea.RECOVERY, "Te costó más de lo que marcó tu corazón",
                "Sentiste un esfuerzo de $rpe/10 con una FC media de solo ${(hrPct * 100).roundToInt()}%. Suele indicar fatiga acumulada, mal descanso o hidratación: revisa el sueño de los días previos.", null, 1)
        }
        if (log.feeling != null && log.feeling <= 2) {
            out += CoachTip(TipArea.RECOVERY, "Sensaciones bajas",
                "Marcaste malas sensaciones. Prioriza 48 h de recuperación activa (trote suave, movilidad) antes del próximo esfuerzo intenso.", null, 2)
        }
        if (r.wellness?.readiness == Readiness.LOW) {
            out += CoachTip(TipArea.RECOVERY, "Llegaste al partido con fatiga",
                "Tu HRV de ese día estaba más de un 10% por debajo de tu media. En días así, calienta más largo y reserva los esfuerzos máximos para momentos decisivos.", null, 2)
        }
        if (attacking && log.goals + log.assists == 0 && minutes > 45 && t.sprintsIntoFinalThird <= 1) {
            out += CoachTip(TipArea.PRODUCTION, "Llega más a zona de definición",
                "Sin goles ni asistencias y con ${t.sprintsIntoFinalThird} sprint(s) que terminaron en el último tercio. Las ocasiones nacen de llegar: busca 3-4 carreras más por tiempo hacia el área.", null, 2)
        }
        if (out.isEmpty()) {
            out += CoachTip(TipArea.PACING, "Partido equilibrado",
                "No hay señales de alarma: mantuviste la intensidad y tu rol. Para dar el salto, intenta subir un 5-10% tu pico de 5' en el próximo partido.", null, 3)
        }
        return out
    }

    private fun drills(r: MatchReport, group: RoleGroup): List<Drill> {
        val out = ArrayList<Drill>()
        val p = r.physical
        val per90 = { n: Int -> n / (p.playingTimeS / 60).coerceAtLeast(1.0) * 90 }

        // --- Físico, según los datos ---
        val fatigue = p.fatigueIndexPct ?: 0.0
        if (fatigue < -8 || (r.advanced?.repeatedEfforts?.isNotEmpty() == true)) {
            out += Drill(DrillType.PHYSICAL, "Capacidad de sprint repetido (RSA)",
                "Tu intensidad cayó ${(-fatigue).roundToInt()}% hacia el final" + (if (r.advanced?.repeatedEfforts?.isNotEmpty() == true) " y tuviste secuencias de esfuerzos repetidos" else "") + ".",
                "6 sprints de 30 m con 20 s de pausa activa. Descansa 4 min y repite. Sube a 3 series en 3 semanas.",
                "2 series · 2 veces por semana")
        }
        val energyMin = r.energy?.minEnergy ?: 100.0
        val decoupling = r.advanced?.decouplingPct ?: 0.0
        if (energyMin < 50 || decoupling < -8) {
            out += Drill(DrillType.PHYSICAL, "Potencia aeróbica (4×4 noruego)",
                "Tu energía bajó a ${energyMin.roundToInt()}%" + (if (decoupling < -8) " y cada metro te costó más latidos al final (${decoupling.roundToInt()}%)" else "") + ".",
                "4 bloques de 4 min al 90-95% de tu FC máxima con 3 min de trote suave entre bloques. Con balón: rondo 4c4 en 30×25 m.",
                "1-2 veces por semana")
        }
        if (per90(p.sprints.size) < 6 || p.maxSpeedKmh < 26) {
            out += Drill(DrillType.PHYSICAL, "Velocidad máxima y aceleración",
                "${p.sprints.size} sprints y ${p.maxSpeedKmh.roundToInt()} km/h de máxima: hay margen para ganar velocidad.",
                "Aceleraciones de 10-20 m desde distintas posiciones (de espaldas, sentado, tras giro) y 4-6 lanzados de 30 m al 100%. Descanso completo (2 min).",
                "6-8 repeticiones · 2 veces por semana, lejos del partido")
        }
        if (per90(p.accelerations + p.decelerations) < 30) {
            out += Drill(DrillType.PHYSICAL, "Cambios de dirección y frenada",
                "Hiciste ${p.accelerations} aceleraciones y ${p.decelerations} frenadas: tu juego es poco explosivo en espacios cortos.",
                "Circuitos de conos en 5-10 m (T-test, 5-10-5) y frenadas en 3 pasos tras sprint de 10 m.",
                "3×6 repeticiones · 2 veces por semana")
        }
        out += Drill(DrillType.PHYSICAL, "Prevención: isquios y aductores",
            "Recorriste ${p.highSpeedDistanceM.roundToInt()} m a alta intensidad: los isquiotibiales son la lesión más común en fútbol.",
            "Nórdicos (3×5), Copenhague para aductores (3×6 por lado) y puente de glúteo a una pierna (3×10).",
            "2 veces por semana")

        // --- Técnico y táctico, según el rol ---
        out += roleLibrary[group].orEmpty()
        return out
    }

    private val roleLibrary: Map<RoleGroup, List<Drill>> = mapOf(
        RoleGroup.GOALKEEPER to listOf(
            Drill(DrillType.TECHNICAL, "Blocaje y caída lateral", "Base técnica del portero.", "Series de 10 remates a media altura alternando lados; trabaja la caída sin rebote.", "3 series"),
            Drill(DrillType.TECHNICAL, "Juego de pies bajo presión", "El portero moderno inicia el juego.", "Rondo 6c2 con el portero como apoyo, máximo 2 toques.", "10 min"),
            Drill(DrillType.TACTICAL, "Dominio del área en centros", "Decide cuándo salir.", "Centros desde ambas bandas con 2 atacantes; grita «¡mía!» o «¡fuera!» en cada uno.", "15 centros"),
        ),
        RoleGroup.CENTER_BACK to listOf(
            Drill(DrillType.TECHNICAL, "Pase largo y cambio de orientación", "El central que saca bien el balón rompe líneas.", "Pases de 30-40 m al costado contrario a un compañero en movimiento, ambos perfiles.", "20 pases por pie"),
            Drill(DrillType.TECHNICAL, "Juego aéreo defensivo", "Ganar el primer balón aéreo.", "Despejes de cabeza con oposición pasiva, buscando altura y distancia hacia la banda.", "3×8"),
            Drill(DrillType.TACTICAL, "Línea defensiva y coberturas", "Coordinar la línea y la cobertura del compañero.", "4 defensores contra 3 atacantes + 2 comodines: la línea sube, baja y se cubre a la voz de un líder.", "4×4 min"),
            Drill(DrillType.TACTICAL, "1c1 defensivo en carrera", "Perfilarse y temporizar.", "Atacante parte con 5 m de ventaja; el defensor debe orientarlo a banda sin tirarse.", "10 repeticiones"),
        ),
        RoleGroup.FULL_BACK to listOf(
            Drill(DrillType.TECHNICAL, "Centro tras conducción", "Tu llegada por banda tiene que terminar en un buen centro.", "Conducción de 20 m por banda y centro a 2 rematadores; alterna centro raso atrás y al segundo palo.", "12 centros por lado"),
            Drill(DrillType.TECHNICAL, "Control orientado hacia adelante", "Ganar tiempo al recibir en banda.", "Recibe de espaldas y controla orientado hacia el carril libre, a 1-2 toques.", "3×10"),
            Drill(DrillType.TACTICAL, "Doblaje y desdoblaje con el extremo", "Superioridad por banda.", "2c2 en el carril con portería: el extremo fija y el lateral dobla o se mete por dentro según el defensor.", "6×2 min"),
            Drill(DrillType.TACTICAL, "Repliegue tras pérdida", "El lateral que sube debe volver.", "Tras cada ataque por banda, sprint de vuelta a tu zona en menos de 6 s.", "Integrado en juego reducido"),
        ),
        RoleGroup.HOLDING_MID to listOf(
            Drill(DrillType.TECHNICAL, "Recepción perfilada y pase vertical", "El pivote debe girar y filtrar.", "Recibe del central, perfílate abierto y filtra al delantero entre conos a 2 toques.", "4×10"),
            Drill(DrillType.TECHNICAL, "Escaneo antes de recibir", "Saber dónde está el rival antes del balón.", "Rondo 5c2: un entrenador muestra un color justo antes del pase y debes decirlo antes de tocar.", "10 min"),
            Drill(DrillType.TACTICAL, "Basculación y cobertura de línea de pase", "Proteger el carril central.", "Juego 6c6 en campo de 3 carriles: el pivote no puede salir del central y debe tapar el pase interior.", "3×6 min"),
        ),
        RoleGroup.CENTRAL_MID to listOf(
            Drill(DrillType.TECHNICAL, "Pase a uno y dos toques en movimiento", "El mediocentro conecta todo el equipo.", "Rondos 4c2 y circuito de pared y tercer hombre a máxima velocidad.", "15 min"),
            Drill(DrillType.TECHNICAL, "Disparo de media distancia", "Llegas al borde del área: aprovéchalo.", "Recibe de lado, controla orientado y dispara desde 18-22 m.", "3×8"),
            Drill(DrillType.TACTICAL, "Llegada desde segunda línea", "Aparecer en el área sin ser detectado.", "Ataque 5c4: el mediocentro arranca tarde para atacar el rechace o el pase atrás.", "8 ataques"),
            Drill(DrillType.TACTICAL, "Presión tras pérdida (5 s)", "Recuperar rápido evita contraataques.", "Juego 5c5: al perder el balón, el equipo tiene 5 s para recuperar o se repliega.", "4×4 min"),
        ),
        RoleGroup.ATTACKING_MID to listOf(
            Drill(DrillType.TECHNICAL, "Giro entre líneas", "Recibir de espaldas y encarar.", "Pase del pivote, control con el pie lejano y giro hacia la portería con defensor pasivo.", "3×8 por lado"),
            Drill(DrillType.TECHNICAL, "Último pase", "Convertir posesión en ocasión.", "Pases filtrados al desmarque de ruptura entre dos defensores, con conos como límite de fuera de juego.", "20 pases"),
            Drill(DrillType.TACTICAL, "Ocupar el espacio entre líneas", "Ser opción de pase detrás del medio rival.", "6c6 con zona libre entre líneas: solo el interior/mediapunta puede recibir allí.", "3×5 min"),
        ),
        RoleGroup.WINGER to listOf(
            Drill(DrillType.TECHNICAL, "1c1 en banda", "El extremo gana partidos desbordando.", "Encara al defensor en 15×10 m: finaliza con centro o diagonal hacia el área.", "10 duelos por lado"),
            Drill(DrillType.TECHNICAL, "Diagonal y remate al palo largo", "Atacar el área desde fuera.", "Conducción hacia dentro desde la banda y remate cruzado tras recorte.", "3×6"),
            Drill(DrillType.TACTICAL, "Amplitud y ataque al espacio", "Estirar la defensa y aparecer a la espalda.", "Juego 7c7: puntos dobles si el gol nace de una carrera del extremo a la espalda.", "3×6 min"),
            Drill(DrillType.TACTICAL, "Repliegue en carril", "El extremo también defiende su banda.", "Tras perder, sprint para cerrar el carril del lateral rival en menos de 8 s.", "Integrado en juego"),
        ),
        RoleGroup.STRIKER to listOf(
            Drill(DrillType.TECHNICAL, "Definición a 1-2 toques", "El delantero vive del gol.", "Pases desde distintos ángulos y remate en el área a 1 o 2 toques, alternando pierna.", "30 remates"),
            Drill(DrillType.TECHNICAL, "Juego de espaldas y descarga", "Fijar centrales y conectar con la segunda línea.", "Recibe de espaldas con defensor encima, protege y descarga al mediocampista que llega.", "3×8"),
            Drill(DrillType.TACTICAL, "Desmarques de ruptura y contra-movimiento", "Ganar la espalda al central.", "Delantero contra 2 centrales: amaga hacia el balón y ataca la espalda al pase del mediapunta.", "12 repeticiones"),
            Drill(DrillType.TACTICAL, "Ataque al primer y segundo palo", "Ocupar el área en los centros.", "Centros desde banda: un delantero ataca el primer palo y otro el segundo, rotando roles.", "15 centros"),
        ),
    )
}
