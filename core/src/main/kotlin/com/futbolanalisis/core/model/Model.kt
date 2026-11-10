package com.futbolanalisis.core.model

import kotlinx.serialization.Serializable

/** Un punto GPS del reloj. [heartRate] viene de la extensión del GPX/TCX si existe. */
@Serializable
data class TrackPoint(
    val timeMs: Long,
    val lat: Double,
    val lon: Double,
    val elevation: Double? = null,
    val heartRate: Int? = null,
)

@Serializable
data class HeartRateSample(val timeMs: Long, val bpm: Int)

/** Recorrido importado (GPX, TCX o Health Connect). */
@Serializable
data class Track(
    val name: String? = null,
    val source: TrackSource = TrackSource.GPX,
    val points: List<TrackPoint>,
    /** Muestras de FC sin posición (p. ej. puntos TCX sin GPS). */
    val extraHeartRate: List<HeartRateSample> = emptyList(),
) {
    val startMs: Long get() = points.firstOrNull()?.timeMs ?: 0L
    val endMs: Long get() = points.lastOrNull()?.timeMs ?: 0L
    val heartRateSamples: List<HeartRateSample>
        get() = (points.mapNotNull { p -> p.heartRate?.let { HeartRateSample(p.timeMs, it) } } + extraHeartRate)
            .sortedBy { it.timeMs }
}

@Serializable
enum class TrackSource { GPX, TCX, HEALTH_CONNECT, SYNTHETIC }

/** Perfil del jugador, necesario para zonas de frecuencia cardiaca. */
@Serializable
data class PlayerProfile(
    val name: String = "Jugador",
    val age: Int = 30,
    /** Si es null se estima con la fórmula de Tanaka: 208 - 0.7 * edad. */
    val maxHeartRate: Int? = null,
    val restingHeartRate: Int? = null,
    val weightKg: Double? = null,
) {
    val effectiveMaxHr: Int get() = maxHeartRate ?: (208.0 - 0.7 * age).toInt()
}
