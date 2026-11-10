package com.futbolanalisis.core.coach

import com.futbolanalisis.core.MatchReport
import com.futbolanalisis.core.tactical.Direction
import kotlinx.serialization.Serializable
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Huella de rol: 9 rasgos normalizados (0-1) de cómo ocupa el campo un jugador.
 * Las huellas de profesionales son aproximaciones construidas a partir de mapas de calor
 * y perfiles de rol públicos (no son datos de tracking oficiales).
 */
@Serializable
data class RoleFingerprint(
    /** Zona más frecuente: profundidad (0 = propia línea de fondo, 1 = rival). */
    val depth: Double,
    /** Zona más frecuente: lado (0 = banda derecha, 0.5 = centro, 1 = banda izquierda). */
    val side: Double,
    val defensiveThird: Double,
    val attackingThird: Double,
    val centralLane: Double,
    /** Recorrido vertical (p10-p90) / largo. */
    val verticalRange: Double,
    /** Recorrido lateral (p10-p90) / ancho. */
    val lateralRange: Double,
    /** % del tiempo en el área rival (0-1, escalado ×4 para pesar). */
    val boxPresence: Double,
    /** Proporción de sprints hacia adelante. */
    val forwardSprints: Double,
) {
    fun toVector() = doubleArrayOf(depth, side, defensiveThird, attackingThird, centralLane, verticalRange, lateralRange, boxPresence, forwardSprints)

    companion object {
        val WEIGHTS = doubleArrayOf(3.0, 2.2, 1.0, 1.2, 1.0, 0.8, 0.8, 1.0, 0.5)
        val LABELS = listOf("profundidad", "lado", "tercio defensivo", "tercio ofensivo", "carril central", "recorrido vertical", "recorrido lateral", "presencia en el área", "sprints hacia adelante")

        fun of(r: MatchReport): RoleFingerprint {
            val t = r.tactical
            val d = r.calibration.dims
            val sprints = t.sprintVectors
            return RoleFingerprint(
                depth = t.modePosition.x / d.length,
                side = t.modePosition.y / d.width,
                defensiveThird = t.thirdsPct[0] / 100,
                attackingThird = t.thirdsPct[2] / 100,
                centralLane = t.lanesPct[1] / 100,
                verticalRange = (t.depthRangeM[1] - t.depthRangeM[0]) / d.length,
                lateralRange = (t.widthRangeM[1] - t.widthRangeM[0]) / d.width,
                boxPresence = (t.penaltyBoxPct / 100 * 4).coerceAtMost(1.0),
                forwardSprints = if (sprints.isEmpty()) 0.5 else sprints.count { it.direction == Direction.FORWARD }.toDouble() / sprints.size,
            )
        }
    }
}

@Serializable
data class ProPlayer(
    val name: String,
    val team: String,
    val role: String,
    val style: String,
    val fingerprint: RoleFingerprint,
)

@Serializable
data class ProMatch(
    val player: ProPlayer,
    /** 0-100. */
    val similarity: Double,
    val sharedTraits: List<String>,
    val differences: List<String>,
)

object ProComparison {
    private fun fp(depth: Double, side: Double, def: Double, att: Double, center: Double, vert: Double, lat: Double, box: Double, fwd: Double) =
        RoleFingerprint(depth, side, def, att, center, vert, lat, box, fwd)

    /** Perfiles aproximados de rol (temporadas 2023-2025). side: 0 = derecha, 1 = izquierda. */
    val PLAYERS = listOf(
        ProPlayer("Thibaut Courtois", "Real Madrid", "Portero", "Portero de área, pocas salidas largas", fp(0.05, 0.5, 0.97, 0.0, 0.95, 0.10, 0.25, 0.0, 0.5)),
        ProPlayer("Virgil van Dijk", "Liverpool", "Defensa central", "Central dominante, posicional, organiza la línea", fp(0.26, 0.62, 0.62, 0.05, 0.75, 0.33, 0.25, 0.04, 0.45)),
        ProPlayer("Rúben Dias", "Manchester City", "Defensa central", "Central de línea alta, agresivo en la anticipación", fp(0.30, 0.38, 0.55, 0.06, 0.78, 0.35, 0.25, 0.04, 0.5)),
        ProPlayer("John Stones", "Manchester City", "Central que sale", "Central que avanza al mediocampo con balón", fp(0.36, 0.45, 0.42, 0.12, 0.80, 0.45, 0.30, 0.05, 0.6)),
        ProPlayer("Antonio Rüdiger", "Real Madrid", "Defensa central", "Central físico, mucho duelo y repliegue a velocidad", fp(0.25, 0.40, 0.65, 0.05, 0.70, 0.32, 0.30, 0.05, 0.35)),
        ProPlayer("Dani Carvajal", "Real Madrid", "Lateral derecho", "Lateral equilibrado, sube con criterio", fp(0.40, 0.10, 0.42, 0.20, 0.20, 0.55, 0.25, 0.05, 0.6)),
        ProPlayer("Achraf Hakimi", "PSG", "Lateral ofensivo (carrilero)", "Carrilero muy profundo y rápido", fp(0.55, 0.08, 0.28, 0.38, 0.12, 0.70, 0.22, 0.10, 0.75)),
        ProPlayer("Trent Alexander-Arnold", "Real Madrid", "Lateral ofensivo (carrilero)", "Lateral que se mete por dentro a organizar", fp(0.48, 0.18, 0.35, 0.28, 0.35, 0.60, 0.40, 0.06, 0.6)),
        ProPlayer("Theo Hernández", "AC Milan", "Lateral ofensivo (carrilero)", "Lateral izquierdo de conducción y llegada", fp(0.52, 0.90, 0.30, 0.35, 0.15, 0.70, 0.25, 0.12, 0.75)),
        ProPlayer("Alphonso Davies", "Bayern", "Lateral ofensivo (carrilero)", "Lateral izquierdo explosivo, gran recorrido", fp(0.50, 0.92, 0.32, 0.33, 0.12, 0.72, 0.22, 0.08, 0.7)),
        ProPlayer("Rodri", "Manchester City", "Pivote / mediocentro defensivo", "Ancla del equipo, ocupa el centro por delante de la defensa", fp(0.42, 0.50, 0.30, 0.15, 0.80, 0.38, 0.35, 0.03, 0.5)),
        ProPlayer("N'Golo Kanté", "Al-Ittihad", "Pivote / mediocentro defensivo", "Recuperador incansable, cubre mucho campo", fp(0.45, 0.45, 0.30, 0.22, 0.65, 0.55, 0.50, 0.05, 0.55)),
        ProPlayer("Sergio Busquets", "Inter Miami", "Pivote / mediocentro defensivo", "Pivote posicional, poco desplazamiento", fp(0.42, 0.52, 0.32, 0.12, 0.85, 0.30, 0.28, 0.02, 0.45)),
        ProPlayer("Toni Kroos", "Real Madrid (ret.)", "Mediocentro organizador", "Organizador que cae al carril izquierdo a iniciar", fp(0.48, 0.65, 0.25, 0.20, 0.60, 0.40, 0.40, 0.03, 0.45)),
        ProPlayer("Luka Modrić", "AC Milan", "Mediocentro organizador", "Organizador móvil, aparece por la derecha", fp(0.52, 0.35, 0.22, 0.25, 0.58, 0.48, 0.45, 0.05, 0.55)),
        ProPlayer("Federico Valverde", "Real Madrid", "Mediocentro box-to-box", "Ida y vuelta por el carril derecho, gran despliegue", fp(0.52, 0.30, 0.28, 0.30, 0.50, 0.65, 0.40, 0.08, 0.7)),
        ProPlayer("Jude Bellingham", "Real Madrid", "Interior / mediapunta de llegada", "Interior que pisa el área con frecuencia", fp(0.60, 0.55, 0.15, 0.40, 0.65, 0.55, 0.40, 0.15, 0.75)),
        ProPlayer("Declan Rice", "Arsenal", "Mediocentro box-to-box", "Box-to-box con mucho peso defensivo", fp(0.48, 0.45, 0.30, 0.26, 0.70, 0.58, 0.35, 0.06, 0.65)),
        ProPlayer("Pedri", "FC Barcelona", "Interior / mediapunta de llegada", "Interior izquierdo asociativo entre líneas", fp(0.58, 0.68, 0.18, 0.32, 0.55, 0.48, 0.42, 0.06, 0.6)),
        ProPlayer("Kevin De Bruyne", "Napoli", "Interior que alterna banda y centro", "Interior derecho que cae a banda a centrar y filtrar", fp(0.62, 0.28, 0.12, 0.42, 0.45, 0.50, 0.50, 0.08, 0.65)),
        ProPlayer("Jamal Musiala", "Bayern", "Mediapunta", "Mediapunta regateador entre líneas", fp(0.68, 0.55, 0.08, 0.50, 0.60, 0.40, 0.45, 0.14, 0.7)),
        ProPlayer("Thomas Müller", "Vancouver Whitecaps", "Delantero móvil (sale del área a asociarse)", "Interpreta espacios, aparece en todas las zonas de ataque", fp(0.70, 0.45, 0.10, 0.50, 0.55, 0.45, 0.55, 0.20, 0.65)),
        ProPlayer("Antoine Griezmann", "Atlético de Madrid", "Delantero móvil (sale del área a asociarse)", "Segundo delantero que baja a crear y presiona", fp(0.65, 0.52, 0.15, 0.45, 0.62, 0.55, 0.45, 0.14, 0.6)),
        ProPlayer("Lionel Messi", "Inter Miami", "Extremo a pierna cambiada (pisa el centro, derecha)", "Parte de la derecha hacia el centro, caminando y acelerando en momentos clave", fp(0.72, 0.30, 0.05, 0.55, 0.45, 0.35, 0.45, 0.16, 0.7)),
        ProPlayer("Mohamed Salah", "Liverpool", "Extremo a pierna cambiada (pisa el centro, derecha)", "Extremo derecho que ataca el área en diagonal", fp(0.76, 0.18, 0.06, 0.58, 0.30, 0.40, 0.30, 0.25, 0.8)),
        ProPlayer("Lamine Yamal", "FC Barcelona", "Extremo abierto (derecha)", "Extremo derecho pegado a la línea, uno contra uno", fp(0.74, 0.08, 0.08, 0.55, 0.15, 0.40, 0.20, 0.12, 0.75)),
        ProPlayer("Vinícius Júnior", "Real Madrid", "Extremo abierto (izquierda)", "Extremo izquierdo vertical, desborde y velocidad", fp(0.75, 0.90, 0.07, 0.58, 0.15, 0.42, 0.22, 0.16, 0.85)),
        ProPlayer("Jérémy Doku", "Manchester City", "Extremo abierto (izquierda)", "Regateador abierto que fija y desborda", fp(0.74, 0.92, 0.08, 0.55, 0.10, 0.38, 0.18, 0.10, 0.8)),
        ProPlayer("Kylian Mbappé", "Real Madrid", "Delantero móvil (sale del área a asociarse)", "Delantero que arranca desde la izquierda y ataca la espalda", fp(0.78, 0.68, 0.05, 0.62, 0.45, 0.42, 0.40, 0.25, 0.9)),
        ProPlayer("Erling Haaland", "Manchester City", "Delantero de área", "Nueve de área, poco recorrido, letal en el último tercio", fp(0.84, 0.50, 0.04, 0.72, 0.80, 0.30, 0.25, 0.35, 0.85)),
        ProPlayer("Robert Lewandowski", "FC Barcelona", "Delantero de área", "Nueve de referencia que también baja a descargar", fp(0.80, 0.50, 0.05, 0.66, 0.78, 0.36, 0.30, 0.30, 0.75)),
        ProPlayer("Harry Kane", "Bayern", "Delantero móvil (sale del área a asociarse)", "Nueve que baja al medio a organizar y luego ataca el área", fp(0.72, 0.50, 0.08, 0.52, 0.75, 0.50, 0.32, 0.22, 0.7)),
        ProPlayer("Lautaro Martínez", "Inter", "Delantero de área", "Delantero de presión y desmarque al primer palo", fp(0.80, 0.45, 0.06, 0.65, 0.70, 0.40, 0.35, 0.28, 0.8)),
    )

    fun compare(r: MatchReport, top: Int = 3): List<ProMatch> {
        val me = RoleFingerprint.of(r)
        val v = me.toVector()
        val w = RoleFingerprint.WEIGHTS
        return PLAYERS.map { p ->
            val pv = p.fingerprint.toVector()
            var d2 = 0.0
            val diffs = DoubleArray(v.size)
            for (i in v.indices) {
                diffs[i] = v[i] - pv[i]
                d2 += w[i] * diffs[i] * diffs[i]
            }
            val sim = 100 * exp(-d2 / 0.35)
            val shared = diffs.indices.filter { kotlin.math.abs(diffs[it]) < 0.08 }
                .sortedByDescending { w[it] }.take(3).map { RoleFingerprint.LABELS[it] }
            val different = diffs.indices.filter { kotlin.math.abs(diffs[it]) > 0.15 }
                .sortedByDescending { w[it] * kotlin.math.abs(diffs[it]) }.take(2).map { i ->
                    val more = diffs[i] > 0
                    when (i) {
                        0 -> if (more) "juegas más adelantado" else "juegas más retrasado"
                        1 -> if (more) "te cargas más a la izquierda" else "te cargas más a la derecha"
                        2 -> if (more) "pasas más tiempo en tu tercio defensivo" else "pasas menos tiempo en tu tercio defensivo"
                        3 -> if (more) "pisas más el tercio ofensivo" else "pisas menos el tercio ofensivo"
                        4 -> if (more) "eres más central" else "abres más el campo"
                        5 -> if (more) "recorres más campo en vertical" else "recorres menos campo en vertical"
                        6 -> if (more) "cubres más ancho" else "cubres menos ancho"
                        7 -> if (more) "pisas más el área" else "pisas menos el área"
                        else -> if (more) "tus sprints van más hacia adelante" else "tus sprints van menos hacia adelante"
                    }
                }
            ProMatch(p, sim, shared, different)
        }.sortedByDescending { it.similarity }.take(top)
    }

    @Suppress("unused")
    private fun norm(v: DoubleArray) = sqrt(v.sumOf { it * it })
}
