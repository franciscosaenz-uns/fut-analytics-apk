package com.futbolanalisis.core.insights

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** Los cinco atributos físicos que componen el índice de rendimiento. */
@Serializable
enum class Attribute(val label: String, val short: String, val weight: Double, val explain: String) {
    SPEED("Velocidad", "VEL", 0.20, "Velocidad máxima y metros recorridos por encima de 19,8 km/h."),
    STAMINA("Resistencia", "RES", 0.25, "Ritmo medio (m/min) y metros en carrera por encima de 14,4 km/h."),
    INTENSITY("Intensidad", "INT", 0.25, "Alta carga metabólica, pico de 1 minuto y potencia media."),
    EXPLOSIVENESS("Explosividad", "EXP", 0.15, "Aceleraciones y frenadas por cada 90 minutos."),
    ENDURANCE("Aguante", "AGU", 0.15, "Cuánto sostuviste el ritmo: último bloque, 2º tiempo y energía final."),
}

/**
 * Índice de rendimiento físico (0-99) y sus cinco atributos. Cada atributo se obtiene
 * situando tus datos en una escala de referencia que va del fútbol recreativo (≈30-50)
 * al profesional (≈90+). Es orientativo: sirve sobre todo para compararte contigo mismo.
 */
@Serializable
data class PerformanceScore(
    val overall: Int,
    /** Un valor por [Attribute], en el orden de [Attribute.entries]. */
    val values: List<Int>,
) {
    operator fun get(a: Attribute): Int = values.getOrElse(a.ordinal) { 0 }

    val level: String get() = levelOf(overall)

    companion object {
        fun levelOf(score: Int): String = when {
            score >= 88 -> "Élite"
            score >= 78 -> "Avanzado"
            score >= 66 -> "Competitivo"
            score >= 52 -> "Amateur"
            else -> "En construcción"
        }

        /** Convierte [v] a la escala 30-95 entre [lo] y [hi], acotado a 10-99. */
        internal fun scale(v: Double, lo: Double, hi: Double): Double =
            (30 + (v - lo) / (hi - lo) * 65).coerceIn(10.0, 99.0)

        private fun mean(vararg parts: Pair<Double?, Double>): Double {
            var sum = 0.0; var w = 0.0
            for ((v, weight) in parts) if (v != null) { sum += v * weight; w += weight }
            return if (w > 0) sum / w else 50.0
        }

        fun of(k: MatchKpis): PerformanceScore {
            fun per90(v: Double?): Double? = if (v != null && k.minutes >= 1) v * 90 / k.minutes else null
            val speed = mean(
                scale(k.maxSpeedKmh, 18.0, 32.0) to 0.7,
                per90(k.highSpeedDistanceM)?.let { scale(it, 0.0, 700.0) } to 0.3,
            )
            val stamina = mean(
                scale(k.metersPerMinute, 55.0, 120.0) to 0.7,
                per90(k.runDistanceM)?.let { scale(it, 0.0, 2200.0) } to 0.3,
            )
            val intensity = mean(
                per90(k.hmldM)?.let { scale(it, 200.0, 2200.0) } to 0.5,
                k.peak1MinMpm?.let { scale(it, 90.0, 190.0) } to 0.25,
                k.metabolicPowerWkg?.let { scale(it, 5.0, 11.0) } to 0.25,
                // Sin modelo de energía, se aproxima con el ritmo.
                (if (k.hmldM == null && k.peak1MinMpm == null) scale(k.metersPerMinute, 55.0, 120.0) else null) to 1.0,
            )
            val explosive = per90((k.accelerations + k.decelerations).toDouble())?.let { scale(it, 8.0, 70.0) } ?: 50.0
            val endurance = mean(
                k.fatigueIndexPct?.let { scale(it, -40.0, 5.0) } to 0.4,
                k.secondHalfPct?.let { scale(it, -25.0, 5.0) } to 0.3,
                k.finalEnergy?.let { scale(it, 15.0, 80.0) } to 0.3,
            )
            val values = listOf(speed, stamina, intensity, explosive, endurance)
            val overall = Attribute.entries.sumOf { values[it.ordinal] * it.weight }
            return PerformanceScore(overall.roundToInt().coerceIn(1, 99), values.map { it.roundToInt() })
        }

        /** Promedio de varios índices (p. ej. los últimos partidos). */
        fun average(scores: List<PerformanceScore>): PerformanceScore? {
            if (scores.isEmpty()) return null
            return PerformanceScore(
                overall = scores.map { it.overall }.average().roundToInt(),
                values = Attribute.entries.map { a -> scores.map { it[a] }.average().roundToInt() },
            )
        }
    }
}
