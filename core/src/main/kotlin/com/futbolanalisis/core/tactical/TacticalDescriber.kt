package com.futbolanalisis.core.tactical

import com.futbolanalisis.core.pitch.PitchDimensions
import com.futbolanalisis.core.pitch.PitchPoint
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

@Serializable
data class TacticalDescription(
    /** Rol táctico (arquetipo), p. ej. "Mediocentro box-to-box". */
    val archetype: String,
    /** Párrafo corto que resume cómo jugó. */
    val summary: String,
    /** Observaciones concretas, una por línea. */
    val observations: List<String>,
)

/**
 * Genera una descripción del rol táctico a partir del mapa de calor y la ocupación.
 * Son reglas explicables (no un modelo opaco): cada frase sale de un dato que se ve en la app.
 */
object TacticalDescriber {
    fun describe(
        role: String,
        mode: PitchPoint,
        average: PitchPoint,
        modeByPeriod: Map<Int, PitchPoint>,
        hotspots: List<Hotspot>,
        thirdsPct: List<Double>,
        lanesPct: List<Double>,
        boxPct: Double,
        depth: List<Double>,
        width: List<Double>,
        sprints: List<SprintVector>,
        ellipse: PositionEllipse,
        dims: PitchDimensions,
    ): TacticalDescription {
        val xr = mode.x / dims.length
        val yr = mode.y / dims.width
        val wide = yr < 0.3 || yr > 0.7
        val side = if (yr < 0.5) "derecha" else "izquierda"
        val vertical = depth[1] - depth[0]
        val lateral = width[1] - width[0]
        val att = thirdsPct[2]; val def = thirdsPct[0]
        val forward = sprints.count { it.direction == Direction.FORWARD }
        val backward = sprints.count { it.direction == Direction.BACKWARD }
        val twoZones = hotspots.size >= 2 && hypot(hotspots[0].point.x - hotspots[1].point.x, hotspots[0].point.y - hotspots[1].point.y) > 25

        val archetype = when {
            role == "Portero" -> "Portero"
            xr < 0.36 && !wide -> if (vertical > 40 || att > 15) "Central que sale con el balón" else "Defensa central posicional"
            xr < 0.45 && wide -> if (vertical > 50 || att > 22) "Lateral ofensivo (carrilero)" else "Lateral defensivo"
            xr < 0.65 && !wide -> when {
                vertical > 48 && att > 22 && def > 18 -> "Mediocentro box-to-box"
                att < 18 -> "Pivote / mediocentro defensivo"
                xr > 0.55 -> "Interior / mediapunta de llegada"
                else -> "Mediocentro organizador"
            }
            xr < 0.65 -> if (lateral > 32) "Interior que alterna banda y centro ($side)" else "Volante por banda ($side)"
            !wide -> when {
                boxPct > 12 -> "Delantero de área"
                vertical > 45 || lanesPct[1] < 45 -> "Delantero móvil (sale del área a asociarse)"
                else -> "Mediapunta"
            }
            else -> if (lanesPct[1] > 33) "Extremo a pierna cambiada (pisa el centro, $side)" else "Extremo abierto ($side)"
        }

        val obs = ArrayList<String>()
        val thirdName = listOf("defensivo", "medio", "ofensivo")[(xr * 3).toInt().coerceIn(0, 2)]
        val laneName = when { yr < 1.0 / 3 -> "derecho"; yr > 2.0 / 3 -> "izquierdo"; else -> "central" }
        obs += "Tu zona más frecuente fue el carril $laneName del tercio $thirdName (a ${mode.x.roundToInt()} m de tu línea de fondo)."
        obs += "Ocupación por tercios: ${pct(def)} defensivo, ${pct(thirdsPct[1])} medio, ${pct(att)} ofensivo."
        obs += "Te moviste en ${vertical.roundToInt()} m de profundidad y ${lateral.roundToInt()} m de ancho (80% central de tu tiempo)" +
            when {
                vertical > 50 -> ": mucho recorrido vertical, conectaste defensa y ataque."
                lateral > 35 -> ": cubriste mucho ancho, con basculaciones de lado a lado."
                vertical < 30 && lateral < 25 -> ": jugaste muy posicional, sosteniendo tu zona."
                else -> "."
            }
        if (twoZones) {
            obs += "Tuviste dos zonas de influencia separadas; tu posición media (${average.x.roundToInt()} m) cae entre ambas y no representa dónde jugaste."
        } else if (hypot(average.x - mode.x, average.y - mode.y) > 12) {
            obs += "Tu posición media está a ${hypot(average.x - mode.x, average.y - mode.y).roundToInt()} m de tu zona más frecuente: te desplazaste a menudo fuera de tu sitio."
        }
        if (sprints.isNotEmpty()) {
            obs += when {
                forward >= sprints.size * 0.65 -> "$forward de ${sprints.size} sprints fueron hacia adelante: perfil de ruptura y llegada."
                backward >= sprints.size * 0.5 -> "$backward de ${sprints.size} sprints fueron hacia atrás: mucho trabajo de repliegue."
                else -> "Sprints repartidos entre ataque ($forward), repliegue ($backward) y laterales (${sprints.size - forward - backward})."
            }
        }
        if (boxPct >= 8) obs += "Pisaste el área rival el ${pct(boxPct)} del tiempo."
        val p1 = modeByPeriod[1]; val p2 = modeByPeriod[2]
        if (p1 != null && p2 != null) {
            val dx = p2.x - p1.x; val dy = p2.y - p1.y
            if (abs(dx) > 10) obs += "En el 2º tiempo jugaste ${abs(dx).roundToInt()} m más ${if (dx > 0) "adelantado" else "retrasado"}."
            if (abs(dy) > 12) obs += "En el 2º tiempo te cargaste hacia la ${if (dy > 0) "izquierda" else "derecha"}."
        }

        val summary = buildString {
            append("Rol: $archetype. ")
            append(
                when {
                    archetype.startsWith("Mediocentro box") -> "Participaste en las dos áreas, con recorrido de ida y vuelta."
                    archetype.startsWith("Pivote") -> "Te mantuviste por delante de la defensa, priorizando el equilibrio."
                    archetype.startsWith("Mediocentro organizador") -> "Ocupaste el centro del campo como punto de apoyo del equipo."
                    archetype.startsWith("Interior") -> "Llegaste a zonas de último pase sin perder la conexión con el medio."
                    archetype.startsWith("Lateral ofensivo") -> "Diste profundidad por banda y acompañaste los ataques."
                    archetype.startsWith("Lateral") -> "Priorizaste tu zona defensiva por banda."
                    archetype.startsWith("Central que sale") -> "Desde la defensa, saliste a menudo hacia el medio."
                    archetype.startsWith("Defensa") -> "Sostuviste la línea defensiva con pocos desplazamientos."
                    archetype.startsWith("Extremo a pierna") -> "Partiste desde la banda para atacar el espacio interior."
                    archetype.startsWith("Extremo") -> "Diste amplitud pegado a la banda."
                    archetype.startsWith("Delantero de área") -> "Fijaste a los centrales y viviste cerca del arco."
                    archetype.startsWith("Delantero móvil") -> "Bajaste a recibir y arrastraste marcas fuera del área."
                    archetype.startsWith("Volante") -> "Cubriste tu banda en ataque y defensa."
                    else -> ""
                },
            )
        }
        return TacticalDescription(archetype, summary.trim(), obs)
    }

    private fun pct(v: Double) = "${v.roundToInt()}%"
}
