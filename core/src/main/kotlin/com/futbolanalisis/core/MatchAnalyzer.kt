package com.futbolanalisis.core

import com.futbolanalisis.core.model.HeartRateSample
import com.futbolanalisis.core.model.PlayerProfile
import com.futbolanalisis.core.model.Track
import com.futbolanalisis.core.physical.Period
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
        require(track.points.size >= 30) { "El recorrido es demasiado corto para analizarlo" }
        val periods = options.manualPeriods
            ?: if (options.singlePeriod) listOf(Period(1, track.startMs, track.endMs))
            else PeriodDetector.detect(track.points)

        val samples = PhysicalAnalyzer.clean(track.points, periods, options.physical)
        val physicalRaw = PhysicalAnalyzer.analyze(samples, periods, options.physical)

        val inPlayPoints = samples.map { it.point }
        val calibration = options.calibration ?: PitchCalibration.auto(inPlayPoints, options.pitch)

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

        val tactical = TacticalAnalyzer.analyze(samples, calibration, physical.sprints, options.attackFlipped)

        return MatchReport(
            title = title ?: track.name ?: "Partido",
            startMs = track.startMs,
            endMs = track.endMs,
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
