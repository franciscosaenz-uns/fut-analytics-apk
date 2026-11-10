package com.futbolanalisis.core.physio

import com.futbolanalisis.core.model.HeartRateSample
import com.futbolanalisis.core.model.PlayerProfile
import com.futbolanalisis.core.physical.Period
import kotlinx.serialization.Serializable

@Serializable
data class HeartRateReport(
    val avgBpm: Double,
    val maxBpm: Int,
    val minBpm: Int,
    val maxHrUsed: Int,
    /** Zonas Z1..Z5 por % de FC máxima: 50-60, 60-70, 70-80, 80-90, 90-100. */
    val zoneLabels: List<String>,
    val timeInZoneS: List<Double>,
    /** TRIMP de Edwards: suma de minutos en cada zona por su número (1..5). */
    val edwardsTrimp: Double,
    /** Tiempo por encima del 85% de la FC máxima. */
    val timeAbove85PctS: Double,
    /** FC media por minuto de juego, para graficar. */
    val bpmPerMinute: List<Double>,
    val samples: Int,
)

object HeartRateAnalyzer {
    private const val MAX_GAP_S = 10.0

    fun analyze(
        samples: List<HeartRateSample>,
        periods: List<Period>,
        profile: PlayerProfile,
    ): HeartRateReport? {
        val inPlay = samples.filter { s -> periods.any { s.timeMs in it } }.sortedBy { it.timeMs }
        if (inPlay.size < 10) return null
        val maxHr = profile.effectiveMaxHr
        val zones = DoubleArray(5)
        var weighted = 0.0
        var totalT = 0.0
        var above85 = 0.0
        val perMinute = ArrayList<Double>()
        var minuteAcc = 0.0; var minuteT = 0.0

        for (i in 1 until inPlay.size) {
            val a = inPlay[i - 1]; val b = inPlay[i]
            val samePeriod = periods.any { a.timeMs in it && b.timeMs in it }
            val dt = (b.timeMs - a.timeMs) / 1000.0
            if (!samePeriod || dt <= 0) continue
            val w = dt.coerceAtMost(MAX_GAP_S)
            val bpm = (a.bpm + b.bpm) / 2.0
            val pct = bpm / maxHr
            val z = when {
                pct < 0.5 -> -1
                pct < 0.6 -> 0
                pct < 0.7 -> 1
                pct < 0.8 -> 2
                pct < 0.9 -> 3
                else -> 4
            }
            if (z >= 0) zones[z] += w
            if (pct >= 0.85) above85 += w
            weighted += bpm * w
            totalT += w
            minuteAcc += bpm * w; minuteT += w
            if (minuteT >= 60) {
                perMinute += minuteAcc / minuteT
                minuteAcc = 0.0; minuteT = 0.0
            }
        }
        if (totalT <= 0) return null
        val trimp = zones.withIndex().sumOf { (i, t) -> t / 60.0 * (i + 1) }
        return HeartRateReport(
            avgBpm = weighted / totalT,
            maxBpm = inPlay.maxOf { it.bpm },
            minBpm = inPlay.minOf { it.bpm },
            maxHrUsed = maxHr,
            zoneLabels = listOf("Z1 50-60%", "Z2 60-70%", "Z3 70-80%", "Z4 80-90%", "Z5 90-100%"),
            timeInZoneS = zones.toList(),
            edwardsTrimp = trimp,
            timeAbove85PctS = above85,
            bpmPerMinute = perMinute,
            samples = inPlay.size,
        )
    }

    fun averageBetween(samples: List<HeartRateSample>, fromMs: Long, toMs: Long): Double? =
        samples.filter { it.timeMs in fromMs..toMs }.takeIf { it.isNotEmpty() }?.map { it.bpm }?.average()
}

/** Valores fisiológicos de reposo/bienestar leídos de Health Connect (o ingresados a mano). */
@Serializable
data class WellnessSnapshot(
    val restingHr: Int? = null,
    /** HRV RMSSD en ms de la última medición (normalmente nocturna). */
    val hrvRmssdMs: Double? = null,
    /** Media de HRV de los 7 días previos, para comparar. */
    val hrvBaselineMs: Double? = null,
    val spo2Pct: Double? = null,
    val vo2Max: Double? = null,
    val stepsToday: Long? = null,
    val stepsMatchDay: Long? = null,
) {
    /**
     * Estado de recuperación según HRV frente a su media (enfoque habitual en monitoreo
     * deportivo): dentro de ±5% normal, por debajo de -10% fatiga probable.
     */
    val readiness: Readiness?
        get() {
            val h = hrvRmssdMs ?: return null
            val b = hrvBaselineMs ?: return null
            if (b <= 0) return null
            val delta = (h - b) / b * 100
            return when {
                delta >= -5 -> Readiness.GOOD
                delta >= -10 -> Readiness.MODERATE
                else -> Readiness.LOW
            }
        }
}

@Serializable
enum class Readiness(val label: String) {
    GOOD("Recuperado"),
    MODERATE("Recuperación parcial"),
    LOW("Fatiga acumulada"),
}
