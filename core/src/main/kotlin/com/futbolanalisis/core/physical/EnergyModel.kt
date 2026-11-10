package com.futbolanalisis.core.physical

import com.futbolanalisis.core.model.HeartRateSample
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.sqrt

@Serializable
data class EnergyReport(
    /** Energía disponible (0-100) por minuto de juego. */
    val energyPerMinute: List<Double>,
    /** Componentes por minuto, para explicar el indicador. */
    val anaerobicPerMinute: List<Double>,
    val endurancePerMinute: List<Double>,
    val cardioPerMinute: List<Double>?,
    val minEnergy: Double,
    val minEnergyMinute: Int,
    val finalEnergy: Double,
    val minutesBelow30: Int,
    /** Potencia metabólica media (W/kg), según di Prampero/Osgnach. */
    val avgMetabolicPowerWkg: Double,
    /** Distancia con potencia metabólica > 25.5 W/kg (High Metabolic Load Distance). */
    val hmldM: Double,
    /** Gasto energético estimado del juego en kJ/kg. */
    val energyKjPerKg: Double,
    /** kcal si se conoce el peso. */
    val kcal: Double?,
    val criticalPowerWkg: Double,
)

/**
 * Indicador integral de energía (0-100), combina tres reservas:
 * - **Anaeróbica (W' balance, Skiba)**: se vacía con esfuerzos por encima de la potencia
 *   crítica (sprints, aceleraciones) y se recupera al bajar el ritmo.
 * - **Resistencia**: se consume con el trabajo metabólico acumulado del partido.
 * - **Cardiaca** (si hay FC): se vacía al sostener > 85% de la FC máxima.
 *
 * La potencia metabólica sale del modelo de "carrera acelerada" de di Prampero (2005) y
 * Osgnach (2010): una aceleración equivale a correr cuesta arriba, así que cuesta más
 * energía que la misma velocidad constante.
 */
object EnergyModel {
    private const val G = 9.81
    private const val KT = 1.29 // coste extra de correr sobre césped
    private const val HML_THRESHOLD_WKG = 25.5
    /** Distancia "anaeróbica" que se puede correr por encima de la velocidad crítica. */
    private const val D_PRIME_M = 160.0
    /** Trabajo de referencia de un partido completo profesional, ~60 kJ/kg (Osgnach 2010). */
    private const val FULL_MATCH_WORK_JKG = 60_000.0

    fun energyCost(speedMs: Double, accel: Double): Double {
        val es = (accel / G).coerceIn(-0.45, 0.45)
        val em = sqrt(es * es + 1)
        val ec = (155.4 * es.pow5() - 30.4 * es.pow4() - 43.3 * es.pow3() + 46.3 * es * es + 19.5 * es + 3.6) * em * KT
        return max(ec, 1.0)
    }

    fun analyze(
        samples: List<Sample>,
        periods: List<Period>,
        referenceMaxSpeedKmh: Double,
        heartRate: List<HeartRateSample>,
        maxHr: Int,
        weightKg: Double?,
        maxSampleGapS: Double = 10.0,
    ): EnergyReport {
        val clock = MatchClock(periods)
        val vmax = (referenceMaxSpeedKmh / 3.6).coerceIn(5.0, 10.0)
        // Velocidad crítica ≈ 50% de la máxima; la potencia crítica es su coste metabólico constante.
        val cp = 3.6 * KT * vmax * 0.5
        val wPrime = 3.6 * KT * D_PRIME_M

        var wBal = wPrime
        var work = 0.0
        var cardio = 1.0
        var hml = 0.0
        var powerTime = 0.0
        val hasHr = heartRate.size >= 10
        var hrIdx = 0

        val minutes = clock.totalMinutes.coerceAtLeast(1)
        val accE = DoubleArray(minutes); val accA = DoubleArray(minutes); val accR = DoubleArray(minutes); val accC = DoubleArray(minutes)
        val cnt = IntArray(minutes)

        for ((i, s) in samples.withIndex()) {
            if (s.dtS <= 0) continue
            val dt = s.dtS.coerceAtMost(maxSampleGapS)
            val prev = samples.getOrNull(i - 1)?.takeIf { it.period == s.period }
            val a = if (prev != null && s.dtS <= maxSampleGapS) (s.speedMs - prev.speedMs) / s.dtS else 0.0
            val p = energyCost(s.speedMs, a) * s.speedMs
            work += p * dt
            powerTime += dt
            if (p > HML_THRESHOLD_WKG) hml += s.distanceM

            if (p > cp) {
                wBal -= (p - cp) * dt
            } else {
                // Recuperación exponencial (Skiba 2015): más rápida cuanto más bajo el ritmo.
                wBal += (wPrime - wBal) * (1 - kotlin.math.exp(-(cp - p) * dt / wPrime))
            }
            wBal = wBal.coerceIn(0.0, wPrime)

            if (hasHr) {
                while (hrIdx + 1 < heartRate.size && heartRate[hrIdx + 1].timeMs <= s.point.timeMs) hrIdx++
                val hr = heartRate[hrIdx]
                if (kotlin.math.abs(hr.timeMs - s.point.timeMs) < 30_000) {
                    val pct = hr.bpm.toDouble() / maxHr
                    cardio = if (pct > 0.85) cardio - (pct - 0.85) / 0.15 * dt / 300.0
                    else cardio + (1 - cardio) * dt / 180.0
                    cardio = cardio.coerceIn(0.0, 1.0)
                }
            }

            val anaerobic = wBal / wPrime * 100
            val endurance = (100 * (1 - 0.5 * work / FULL_MATCH_WORK_JKG)).coerceIn(0.0, 100.0)
            val energy = if (hasHr) 0.45 * anaerobic + 0.35 * endurance + 0.2 * cardio * 100
            else 0.55 * anaerobic + 0.45 * endurance
            val m = ((clock.minute(s.point.timeMs) ?: continue) - 1).coerceIn(0, minutes - 1)
            accE[m] += energy; accA[m] += anaerobic; accR[m] += endurance; accC[m] += cardio * 100; cnt[m]++
        }

        fun series(acc: DoubleArray): List<Double> {
            val out = ArrayList<Double>(minutes)
            var last = 100.0
            for (m in 0 until minutes) {
                if (cnt[m] > 0) last = acc[m] / cnt[m]
                out += last
            }
            return out
        }
        val energy = series(accE)
        val minIdx = energy.indices.minByOrNull { energy[it] } ?: 0
        return EnergyReport(
            energyPerMinute = energy,
            anaerobicPerMinute = series(accA),
            endurancePerMinute = series(accR),
            cardioPerMinute = if (hasHr) series(accC) else null,
            minEnergy = energy.getOrElse(minIdx) { 100.0 },
            minEnergyMinute = minIdx + 1,
            finalEnergy = energy.lastOrNull() ?: 100.0,
            minutesBelow30 = energy.count { it < 30 },
            avgMetabolicPowerWkg = if (powerTime > 0) work / powerTime else 0.0,
            hmldM = hml,
            energyKjPerKg = work / 1000,
            kcal = weightKg?.let { work * it / 4184.0 },
            criticalPowerWkg = cp,
        )
    }

    private fun Double.pow3() = this * this * this
    private fun Double.pow4() = this * this * this * this
    private fun Double.pow5() = this * this * this * this * this
}
