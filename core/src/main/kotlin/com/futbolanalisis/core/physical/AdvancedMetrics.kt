package com.futbolanalisis.core.physical

import com.futbolanalisis.core.model.HeartRateSample
import kotlinx.serialization.Serializable

/** Máxima demanda en una ventana móvil ("worst-case scenario" / peak demands). */
@Serializable
data class PeakDemand(
    val windowMinutes: Int,
    val metersPerMinute: Double,
    val hsrMetersPerMinute: Double,
    /** Minuto de juego en que empieza la ventana más exigente. */
    val startMinute: Int,
)

@Serializable
data class RepeatedEffortBout(val startMinute: Int, val efforts: Int, val durationS: Double)

@Serializable
data class PeriodEfficiency(val period: Int, val metersPerMinute: Double, val avgHr: Double?, val metersPerBeat: Double?)

@Serializable
data class AdvancedMetrics(
    val peakDemands: List<PeakDemand>,
    /** Bloques de esfuerzos repetidos de alta intensidad (≥3 esfuerzos con < 21 s entre ellos). */
    val repeatedEfforts: List<RepeatedEffortBout>,
    /** Relación tiempo de trabajo (≥ zona carrera) : tiempo de recuperación. */
    val workRestRatio: Double,
    val efficiencyByPeriod: List<PeriodEfficiency>,
    /** Variación % de metros por latido entre el último y el primer periodo. Negativo = desacople/fatiga. */
    val decouplingPct: Double?,
    /** Carga de sesión (Foster): RPE × minutos jugados. */
    val sessionRpeLoad: Double?,
    /** Distancia recorrida en cada tercio (defensivo, medio, ofensivo). Se rellena con el análisis táctico. */
    val distanceByThirdM: List<Double> = emptyList(),
    val sprintsEndingInFinalThird: Int = 0,
    /**
     * Distancias con umbrales absolutos (no dependen del modo de zonas), para poder comparar
     * partidos entre sí: carrera ≥ 14,4 km/h y alta velocidad ≥ 19,8 km/h.
     */
    val absRunDistanceM: Double? = null,
    val absHighSpeedDistanceM: Double? = null,
)

object AdvancedMetricsAnalyzer {
    private const val RHIE_MAX_GAP_S = 21.0
    const val ABS_RUN_MS = 14.4 / 3.6
    const val ABS_HSR_MS = 19.8 / 3.6

    fun analyze(
        samples: List<Sample>,
        periods: List<Period>,
        physical: PhysicalReport,
        heartRate: List<HeartRateSample>,
        rpe: Int?,
    ): AdvancedMetrics {
        val clock = MatchClock(periods)
        val hsr = physical.zonesUsed.highSpeedKmh / 3.6
        val run = physical.zonesUsed.runningKmh / 3.6

        // Series por segundo de juego (distancia y distancia de alta intensidad).
        val total = clock.totalMinutes * 60 + 1
        val dist = DoubleArray(total)
        val hsrDist = DoubleArray(total)
        var work = 0.0; var rest = 0.0
        var absRun = 0.0; var absHsr = 0.0
        for (s in samples) {
            if (s.dtS <= 0) continue
            val sec = clock.playSeconds(s.point.timeMs)?.toInt()?.coerceIn(0, total - 1) ?: continue
            dist[sec] += s.distanceM
            if (s.speedMs >= hsr) hsrDist[sec] += s.distanceM
            if (s.speedMs >= run) work += s.dtS else rest += s.dtS
            if (s.speedMs >= ABS_RUN_MS) absRun += s.distanceM
            if (s.speedMs >= ABS_HSR_MS) absHsr += s.distanceM
        }
        val peaks = listOf(1, 3, 5, 10).mapNotNull { w -> peak(dist, hsrDist, w) }

        // Esfuerzos repetidos con las carreras de alta intensidad (incluyen sprints).
        val bouts = ArrayList<RepeatedEffortBout>()
        val efforts = physical.highIntensityRuns.sortedBy { it.startMs }
        var i = 0
        while (i < efforts.size) {
            var j = i
            while (j + 1 < efforts.size && efforts[j + 1].period == efforts[j].period &&
                (efforts[j + 1].startMs - efforts[j].endMs) / 1000.0 < RHIE_MAX_GAP_S
            ) j++
            val n = j - i + 1
            if (n >= 3) {
                bouts += RepeatedEffortBout(
                    startMinute = clock.minute(efforts[i].startMs) ?: 0,
                    efforts = n,
                    durationS = (efforts[j].endMs - efforts[i].startMs) / 1000.0,
                )
            }
            i = j + 1
        }

        val eff = periods.map { p ->
            val ps = samples.filter { it.period == p.index && it.dtS > 0 }
            val t = ps.sumOf { it.dtS }
            val mpm = if (t > 0) ps.sumOf { it.distanceM } / (t / 60) else 0.0
            val hr = heartRate.filter { it.timeMs in p }.takeIf { it.size >= 10 }?.map { it.bpm }?.average()
            PeriodEfficiency(p.index, mpm, hr, hr?.let { mpm / it })
        }
        val decoupling = if (eff.size >= 2) {
            val a = eff.first().metersPerBeat; val b = eff.last().metersPerBeat
            if (a != null && b != null && a > 0) (b - a) / a * 100 else null
        } else null

        return AdvancedMetrics(
            peakDemands = peaks,
            repeatedEfforts = bouts,
            workRestRatio = if (rest > 0) work / rest else 0.0,
            efficiencyByPeriod = eff,
            decouplingPct = decoupling,
            sessionRpeLoad = rpe?.let { it * physical.playingTimeS / 60 },
            absRunDistanceM = absRun,
            absHighSpeedDistanceM = absHsr,
        )
    }

    private fun peak(dist: DoubleArray, hsr: DoubleArray, minutes: Int): PeakDemand? {
        val w = minutes * 60
        if (dist.size < w) return null
        var sum = dist.take(w).sum()
        var hsum = hsr.take(w).sum()
        var best = sum; var bestH = hsum; var bestStart = 0
        for (start in 1..dist.size - w) {
            sum += dist[start + w - 1] - dist[start - 1]
            hsum += hsr[start + w - 1] - hsr[start - 1]
            if (sum > best) { best = sum; bestH = hsum; bestStart = start }
        }
        return PeakDemand(minutes, best / minutes, bestH / minutes, bestStart / 60 + 1)
    }
}
