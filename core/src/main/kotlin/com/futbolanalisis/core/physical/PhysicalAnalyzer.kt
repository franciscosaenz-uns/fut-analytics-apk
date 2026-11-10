package com.futbolanalisis.core.physical

import com.futbolanalisis.core.geo.Geo
import com.futbolanalisis.core.model.TrackPoint
import kotlinx.serialization.Serializable
import kotlin.math.max

/** Umbrales de velocidad en km/h. Los valores por defecto son los habituales en fútbol profesional. */
@Serializable
data class SpeedZones(
    val joggingKmh: Double = 7.0,
    val runningKmh: Double = 14.4,
    val highSpeedKmh: Double = 19.8,
    val sprintKmh: Double = 25.2,
) {
    val labels get() = listOf("Caminar", "Trote", "Carrera", "Alta intensidad", "Sprint")
    fun zoneOf(kmh: Double): Int = when {
        kmh < joggingKmh -> 0
        kmh < runningKmh -> 1
        kmh < highSpeedKmh -> 2
        kmh < sprintKmh -> 3
        else -> 4
    }

    companion object {
        /** Umbrales más bajos, adecuados para fútbol amateur o veteranos. */
        val AMATEUR = SpeedZones(joggingKmh = 6.0, runningKmh = 12.0, highSpeedKmh = 16.0, sprintKmh = 21.0)
    }
}

@Serializable
data class PhysicalConfig(
    val zones: SpeedZones = SpeedZones(),
    /** Velocidad por encima de la cual un salto de GPS se descarta (~41 km/h). */
    val maxPlausibleSpeedMs: Double = 11.5,
    /** Duración mínima para contar un sprint o una carrera de alta intensidad. */
    val minEffortDurationS: Double = 2.0,
    /** 2 m/s²: con GPS de muñeca a 1 Hz y suavizado, los picos reales de 3 m/s² se ven atenuados. */
    val accelThresholdMs2: Double = 2.0,
    /** Huecos mayores a esto no se usan para velocidad instantánea ni aceleraciones. */
    val maxSampleGapS: Double = 10.0,
    val splitMinutes: Int = 15,
)

/** Un punto limpio dentro de un periodo, con la velocidad del tramo que termina en él. */
data class Sample(
    val point: TrackPoint,
    val period: Int,
    /** Segundos desde el punto anterior del mismo periodo (0 en el primero). */
    val dtS: Double,
    val distanceM: Double,
    /** Velocidad suavizada en m/s. */
    val speedMs: Double,
)

@Serializable
data class Effort(
    val startMs: Long,
    val endMs: Long,
    val distanceM: Double,
    val maxSpeedKmh: Double,
    val startLat: Double, val startLon: Double,
    val endLat: Double, val endLon: Double,
    val period: Int,
)

@Serializable
data class Split(
    val label: String,
    val period: Int,
    val startMs: Long,
    val endMs: Long,
    val distanceM: Double,
    val highSpeedDistanceM: Double,
    val sprints: Int,
    val avgHeartRate: Double? = null,
)

@Serializable
data class PhysicalReport(
    val totalDistanceM: Double,
    val playingTimeS: Double,
    val metersPerMinute: Double,
    val maxSpeedKmh: Double,
    val avgSpeedKmh: Double,
    val zoneLabels: List<String>,
    val distanceByZoneM: List<Double>,
    val timeByZoneS: List<Double>,
    val highSpeedDistanceM: Double,
    val sprintDistanceM: Double,
    val sprints: List<Effort>,
    val highIntensityRuns: List<Effort>,
    val accelerations: Int,
    val decelerations: Int,
    val splits: List<Split>,
    /** Metros recorridos en cada minuto de juego (para la curva de intensidad). */
    val distancePerMinute: List<Double>,
    /** Variación % de metros/minuto entre el primer y el último bloque. Negativo = caída. */
    val fatigueIndexPct: Double?,
)

object PhysicalAnalyzer {

    /** Limpia el recorrido: descarta saltos de GPS, separa por periodos y suaviza la velocidad. */
    fun clean(points: List<TrackPoint>, periods: List<Period>, config: PhysicalConfig = PhysicalConfig()): List<Sample> {
        val out = ArrayList<Sample>(points.size)
        for (period in periods) {
            val inPeriod = points.filter { it.timeMs in period }
            val kept = ArrayList<TrackPoint>(inPeriod.size)
            for (p in inPeriod) {
                val last = kept.lastOrNull()
                if (last == null) { kept += p; continue }
                val dt = (p.timeMs - last.timeMs) / 1000.0
                if (dt <= 0) continue
                val d = Geo.haversine(last.lat, last.lon, p.lat, p.lon)
                if (d / dt > config.maxPlausibleSpeedMs) continue // salto de GPS
                kept += p
            }
            if (kept.isEmpty()) continue
            val dts = DoubleArray(kept.size)
            val ds = DoubleArray(kept.size)
            for (i in 1 until kept.size) {
                dts[i] = (kept[i].timeMs - kept[i - 1].timeMs) / 1000.0
                ds[i] = Geo.haversine(kept[i - 1].lat, kept[i - 1].lon, kept[i].lat, kept[i].lon)
            }
            for (i in kept.indices) {
                // Media móvil centrada de 3 tramos (distancia total / tiempo total), sin cruzar huecos.
                var dSum = 0.0; var tSum = 0.0
                for (j in max(1, i - 1)..minOf(kept.size - 1, i + 1)) {
                    if (dts[j] > config.maxSampleGapS && j != i) continue
                    dSum += ds[j]; tSum += dts[j]
                }
                val v = if (tSum > 0) dSum / tSum else 0.0
                out += Sample(kept[i], period.index, dts[i], ds[i], v)
            }
        }
        return out
    }

    fun analyze(samples: List<Sample>, periods: List<Period>, config: PhysicalConfig = PhysicalConfig()): PhysicalReport {
        val zones = config.zones
        val distByZone = DoubleArray(5)
        val timeByZone = DoubleArray(5)
        var total = 0.0
        var time = 0.0
        var maxSpeed = 0.0
        var accels = 0
        var decels = 0

        for ((i, s) in samples.withIndex()) {
            if (s.dtS <= 0) continue
            total += s.distanceM
            time += s.dtS
            val kmh = s.speedMs * 3.6
            val z = zones.zoneOf(kmh)
            distByZone[z] += s.distanceM
            timeByZone[z] += s.dtS
            if (s.dtS <= config.maxSampleGapS) maxSpeed = max(maxSpeed, kmh)

            // Aceleraciones: flanco de subida al cruzar el umbral.
            val prev = samples.getOrNull(i - 1)
            val prev2 = samples.getOrNull(i - 2)
            if (prev != null && prev.period == s.period && prev.dtS > 0 && s.dtS <= config.maxSampleGapS) {
                val a = (s.speedMs - prev.speedMs) / ((s.dtS + prev.dtS) / 2)
                val aPrev = if (prev2 != null && prev2.period == s.period && prev2.dtS > 0)
                    (prev.speedMs - prev2.speedMs) / ((prev.dtS + prev2.dtS) / 2) else 0.0
                if (a >= config.accelThresholdMs2 && aPrev < config.accelThresholdMs2) accels++
                if (a <= -config.accelThresholdMs2 && aPrev > -config.accelThresholdMs2) decels++
            }
        }

        val sprints = efforts(samples, zones.sprintKmh / 3.6, config)
        val hir = efforts(samples, zones.highSpeedKmh / 3.6, config)
        val splits = splits(samples, periods, sprints, config)
        val perMinute = distancePerMinute(samples)
        val fatigue = if (splits.size >= 2) {
            val first = splits.first(); val last = splits.last()
            val m1 = first.distanceM / ((first.endMs - first.startMs) / 60000.0).coerceAtLeast(1.0)
            val m2 = last.distanceM / ((last.endMs - last.startMs) / 60000.0).coerceAtLeast(1.0)
            if (m1 > 0) (m2 - m1) / m1 * 100 else null
        } else null

        return PhysicalReport(
            totalDistanceM = total,
            playingTimeS = time,
            metersPerMinute = if (time > 0) total / (time / 60) else 0.0,
            maxSpeedKmh = maxSpeed,
            avgSpeedKmh = if (time > 0) total / time * 3.6 else 0.0,
            zoneLabels = zones.labels,
            distanceByZoneM = distByZone.toList(),
            timeByZoneS = timeByZone.toList(),
            highSpeedDistanceM = distByZone[3] + distByZone[4],
            sprintDistanceM = distByZone[4],
            sprints = sprints,
            highIntensityRuns = hir,
            accelerations = accels,
            decelerations = decels,
            splits = splits,
            distancePerMinute = perMinute,
            fatigueIndexPct = fatigue,
        )
    }

    private fun efforts(samples: List<Sample>, thresholdMs: Double, config: PhysicalConfig): List<Effort> {
        val out = ArrayList<Effort>()
        var runStart = -1
        fun close(endExclusive: Int) {
            if (runStart < 0) return
            val first = samples[runStart]
            val last = samples[endExclusive - 1]
            val prev = samples.getOrNull(runStart - 1)?.takeIf { it.period == first.period } ?: first
            val duration = (last.point.timeMs - prev.point.timeMs) / 1000.0
            if (duration >= config.minEffortDurationS) {
                val slice = samples.subList(runStart, endExclusive)
                out += Effort(
                    startMs = prev.point.timeMs,
                    endMs = last.point.timeMs,
                    distanceM = slice.sumOf { it.distanceM },
                    maxSpeedKmh = slice.maxOf { it.speedMs } * 3.6,
                    startLat = prev.point.lat, startLon = prev.point.lon,
                    endLat = last.point.lat, endLon = last.point.lon,
                    period = first.period,
                )
            }
            runStart = -1
        }
        for ((i, s) in samples.withIndex()) {
            val fast = s.dtS > 0 && s.dtS <= config.maxSampleGapS && s.speedMs >= thresholdMs
            val samePeriod = runStart < 0 || samples[runStart].period == s.period
            if (!samePeriod) close(i)
            if (fast && runStart < 0) runStart = i
            if (!fast && runStart >= 0) close(i)
        }
        close(samples.size)
        return out
    }

    private fun splits(samples: List<Sample>, periods: List<Period>, sprintList: List<Effort>, config: PhysicalConfig): List<Split> {
        val out = ArrayList<Split>()
        val block = config.splitMinutes * 60_000L
        val hsr = config.zones.highSpeedKmh / 3.6
        for (period in periods) {
            val ps = samples.filter { it.period == period.index }
            if (ps.isEmpty()) continue
            val pStart = ps.first().point.timeMs
            val pEnd = ps.last().point.timeMs
            var b = 0
            while (pStart + b * block < pEnd) {
                val from = pStart + b * block
                val to = minOf(pEnd, from + block)
                val inBlock = ps.filter { it.point.timeMs > from && it.point.timeMs <= to }
                val sprints = sprintList.count { it.startMs > from && it.startMs <= to }
                val prefix = if (periods.size > 1) "${period.index}T " else ""
                val m0 = b * config.splitMinutes
                val m1 = m0 + ((to - from) / 60_000L).toInt()
                out += Split(
                    label = "$prefix$m0-$m1'",
                    period = period.index,
                    startMs = from, endMs = to,
                    distanceM = inBlock.sumOf { it.distanceM },
                    highSpeedDistanceM = inBlock.filter { it.speedMs >= hsr }.sumOf { it.distanceM },
                    sprints = sprints,
                )
                b++
            }
        }
        // Descarta bloques residuales de menos de 3 minutos.
        return out.filter { it.endMs - it.startMs >= 3 * 60_000L }
    }

    private fun distancePerMinute(samples: List<Sample>): List<Double> {
        val out = ArrayList<Double>()
        var acc = 0.0
        var t = 0.0
        for (s in samples) {
            if (s.dtS <= 0) continue
            acc += s.distanceM
            t += s.dtS
            while (t >= 60.0) {
                out += acc
                acc = 0.0
                t -= 60.0
            }
        }
        if (t >= 30) out += acc * 60 / t
        return out
    }
}
