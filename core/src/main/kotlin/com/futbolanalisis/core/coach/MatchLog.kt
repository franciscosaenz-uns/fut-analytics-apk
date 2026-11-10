package com.futbolanalisis.core.coach

import kotlinx.serialization.Serializable

/** Registro manual del partido: lo que el GPS no ve. */
@Serializable
data class MatchLog(
    val opponent: String = "",
    val goalsFor: Int? = null,
    val goalsAgainst: Int? = null,
    val goals: Int = 0,
    val assists: Int = 0,
    val shots: Int = 0,
    val shotsOnTarget: Int = 0,
    val keyPasses: Int = 0,
    val recoveries: Int = 0,
    val duelsWon: Int = 0,
    val duelsLost: Int = 0,
    val yellowCards: Int = 0,
    val redCard: Boolean = false,
    /** Posición en la que el entrenador te puso (texto libre o de la lista). */
    val positionPlayed: String = "",
    /** Esfuerzo percibido 0-10 (CR-10). */
    val rpe: Int? = null,
    /** Autoevaluación 1-10. */
    val selfRating: Int? = null,
    /** Sensaciones físicas 1 (muy mal) a 5 (muy bien). */
    val feeling: Int? = null,
    val surface: String = "",
    val notes: String = "",
) {
    val result: String?
        get() {
            val f = goalsFor ?: return null
            val a = goalsAgainst ?: return null
            return when {
                f > a -> "Victoria $f-$a"
                f < a -> "Derrota $f-$a"
                else -> "Empate $f-$a"
            }
        }

    val isEmpty: Boolean get() = this == MatchLog()

    companion object {
        val POSITIONS = listOf(
            "Portero", "Defensa central", "Lateral derecho", "Lateral izquierdo", "Pivote", "Mediocentro",
            "Interior derecho", "Interior izquierdo", "Mediapunta", "Extremo derecho", "Extremo izquierdo", "Delantero centro",
        )
    }
}
