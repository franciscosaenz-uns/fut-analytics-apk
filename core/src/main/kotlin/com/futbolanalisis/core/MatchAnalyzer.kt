package com.futbolanalisis.core

import com.futbolanalisis.core.model.HeartRateSample
import com.futbolanalisis.core.model.PlayerProfile
import com.futbolanalisis.core.model.Track
import com.futbolanalisis.core.physical.AdvancedMetrics
import com.futbolanalisis.core.physical.AdvancedMetricsAnalyzer
import com.futbolanalisis.core.physical.AlertEngine
import com.futbolanalisis.core.physical.EnergyModel
import com.futbolanalisis.core.physical.EnergyReport
import com.futbolanalisis.core.physical.Period
import com.futbolanalisis.core.physical.PhysicalAlert
import com.futbolanalisis.core.physical.PeriodDetector
import com.futbolanalisis.core.physical.PhysicalAnalyzer
import com.futbolanalisis.core.physical.PhysicalConfig
import com.futbolanalisis.core.physical.PhysicalReport
import com.futbolanalisis.core.physio.HeartRateAnalyzer
import com.futbolanalisis.core.physio.HeartRateReport
import com.futbolanalisis.core.physio.WellnessSnapshot
import com.futbolanalisis.core.pitch.PitchCalibration
import com.futbolanalisis.core.pitch.PitchDimensions
import com.futbolanalisis.core.tactical.TacticalAnalyzer
import com.futbolanalisis.core.tactical.TacticalReport
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Opciones que el usuario puede ajustar desde la app y que se guardan con el partido. */
@Serializable
data class AnalysisOptions(
    val physical: PhysicalConfig = PhysicalConfig(),
    val pitch: PitchDimensions = PitchDimensions(),
    /** Calibración manual (esquinas); si es null se usa la automática. */
    val calibration: PitchCalibration? = null,
    /** null = heurística automática. */
    val attackFlipped: Boolean? = null,
    /** true = tratar el registro como un único periodo (sin descanso). */
    val singlePeriod: Boolean = false,
    /** Periodos definidos a mano; tienen prioridad sobre la detección. */
    val manualPeriods: List<Period>? = null,
    /** Recorte del registro (ms epoch): se ignora lo anterior/posterior (calentamiento, vuelta a casa...). */
    val trimStartMs: Long? = null,
    val trimEndMs: Long? = null,
    /** Esfuerzo percibido 0-10 (escala CR-10 de Borg) para la carga sRPE. */
    val rpe: Int? = null,
    /** Posición declarada en el registro del partido; ayuda a orientar la dirección de ataque. */
    val positionHint: String? = null,
)

@Serializable
data class MatchReport(
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val periods: List<Period>,
    val physical: PhysicalReport,
    val heartRate: HeartRateReport?,
    /** De dónde salió la FC: "reloj (archivo)", "Health Connect" o null. */
    val heartRateSource: String?,
    val tactical: TacticalReport,
    val energy: EnergyReport? = null,
    val advanced: AdvancedMetrics? = null,
    val alerts: List<PhysicalAlert> = emptyList(),
    /** Inicio y fin del registro completo, antes del recorte. */
    val recordStartMs: Long = 0,
    val recordEndMs: Long = 0,
    val calibration: PitchCalibration,
    val wellness: WellnessSnapshot? = null,
    val options: AnalysisOptions,
)

object MatchAnalyzer {
    fun analyze(
        track: Track,
        profile: PlayerProfile = PlayerProfile(),
        options: AnalysisOptions = AnalysisOptions(),
        externalHeartRate: List<HeartRateSample> = emptyList(),
        wellness: WellnessSnapshot? = null,
        title: String? = null,
    ): MatchReport {
        val trimmedPoints = track.points.filter {
            (options.trimStartMs == null || it.timeMs >= options.trimStartMs) &&
                (options.trimEndMs == null || it.timeMs <= options.trimEndMs)
        }
        require(trimmedPoints.size >= 30) { "El recorrido (recortado) es demasiado corto para analizarlo" }
        val startMs = trimmedPoints.first().timeMs
        val endMs = trimmedPoints.last().timeMs
        val periods = options.manualPeriods
            ?: if (options.singlePeriod) listOf(Period(1, startMs, endMs))
            else PeriodDetector.detect(trimmedPoints)

        val samples = PhysicalAnalyzer.clean(trimmedPoints, periods, options.physical)
        val physicalRaw = PhysicalAnalyzer.analyze(samples, periods, options.physical)

        val inPlayPoints = samples.map { it.point }
        val calibration = options.calibration ?: PitchCalibration.auto(inPlayPoints, options.pitch)
        require(samples.size >= 30) { "No quedan suficientes puntos de juego tras limpiar el recorrido" }

        // FC: primero la del archivo; si no trae, la de Health Connect.
        val fileHr = track.heartRateSamples
        val (hrSamples, hrSource) = when {
            fileHr.size >= 10 -> fileHr to "Reloj (archivo)"
            externalHeartRate.size >= 10 -> externalHeartRate to "Health Connect"
            else -> emptyList<HeartRateSample>() to null
        }
        val hr = HeartRateAnalyzer.analyze(hrSamples, periods, profile)
        val physical = physicalRaw.copy(
            splits = physicalRaw.splits.map { it.copy(avgHeartRate = HeartRateAnalyzer.averageBetween(hrSamples, it.startMs, it.endMs)) },
        )

        val hint = (options.positionHint ?: profile.usualPosition).orEmpty().lowercase()
        val attackingHint = listOf("delantero", "extremo", "mediapunta", "punta", "nueve", "9").any { hint.contains(it) }
        val tactical = TacticalAnalyzer.analyze(samples, calibration, physical.sprints, options.attackFlipped, attackingHint)

        val energy = EnergyModel.analyze(
            samples, periods, physical.referenceMaxSpeedKmh, hrSamples, profile.effectiveMaxHr, profile.weightKg,
            options.physical.maxSampleGapS,
        )
        val advanced = AdvancedMetricsAnalyzer.analyze(samples, periods, physical, hrSamples, options.rpe).copy(
            distanceByThirdM = tactical.distanceByThirdM,
            sprintsEndingInFinalThird = tactical.sprintsIntoFinalThird,
        )
        val alerts = AlertEngine.build(samples, periods, physical, energy, advanced, hrSamples, profile.effectiveMaxHr)

        return MatchReport(
            title = title ?: track.name ?: "Partido",
            startMs = startMs,
            endMs = endMs,
            energy = energy,
            advanced = advanced,
            alerts = alerts,
            recordStartMs = track.startMs,
            recordEndMs = track.endMs,
            periods = periods,
            physical = physical,
            heartRate = hr,
            heartRateSource = if (hr != null) hrSource else null,
            tactical = tactical,
            calibration = calibration,
            wellness = wellness,
            options = options,
        )
    }

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        allowSpecialFloatingPointValues = true
    }
}

/** Utilidades sobre el registro crudo (para la pantalla de recorte). */
object TrackStats {
    /** Metros por minuto del registro completo, desde su inicio, sin periodos ni limpieza fina. */
    fun distancePerMinute(track: Track): List<Double> {
        if (track.points.size < 2) return emptyList()
        val start = track.startMs
        val n = ((track.endMs - start) / 60_000L + 1).toInt()
        val out = DoubleArray(n)
        for (i in 1 until track.points.size) {
            val a = track.points[i - 1]; val b = track.points[i]
            val dt = (b.timeMs - a.timeMs) / 1000.0
            if (dt <= 0) continue
            val d = com.futbolanalisis.core.geo.Geo.haversine(a.lat, a.lon, b.lat, b.lon)
            if (d / dt > 11.5) continue
            out[((b.timeMs - start) / 60_000L).toInt().coerceIn(0, n - 1)] += d
        }
        return out.toList()
    }
}
