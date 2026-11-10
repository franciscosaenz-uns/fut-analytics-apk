package com.futbolanalisis.core

import com.futbolanalisis.core.geo.LocalProjection
import com.futbolanalisis.core.geo.Vec2
import com.futbolanalisis.core.model.PlayerProfile
import com.futbolanalisis.core.model.Track
import com.futbolanalisis.core.model.TrackPoint
import com.futbolanalisis.core.physical.EnergyModel
import com.futbolanalisis.core.physical.MatchClock
import com.futbolanalisis.core.physical.Period
import com.futbolanalisis.core.physical.PhysicalAnalyzer
import com.futbolanalisis.core.physical.PhysicalConfig
import com.futbolanalisis.core.physical.SpeedZones
import com.futbolanalisis.core.physical.ZoneMode
import com.futbolanalisis.core.pitch.PitchCalibration
import com.futbolanalisis.core.pitch.PitchDimensions
import com.futbolanalisis.core.tactical.Heatmap
import com.futbolanalisis.core.tactical.TacticalAnalyzer
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ModeAndRoleTest {
    @Test
    fun modeFindsPeakNotMean() {
        // Dos zonas: mucho tiempo en x≈20 y algo menos en x≈85. La media cae en ~45, donde no estuvo.
        val dims = PitchDimensions()
        val values = MutableList(21 * 14) { 0.0 }
        values[7 * 21 + 4] = 1.0
        values[7 * 21 + 17] = 0.7
        val mode = TacticalAnalyzer.modeOf(Heatmap(21, 14, values, dims))
        assertTrue(abs(mode.x - 22.5) < 3, "moda en $mode")
    }

    @Test
    fun descriptionIsGenerated() {
        val r = MatchAnalyzer.analyze(SyntheticMatch.generate(), PlayerProfile(age = 30))
        val d = r.tactical.description
        assertTrue(d.archetype.isNotBlank())
        assertTrue(d.observations.size >= 3)
        println(d)
    }
}

class TrimAndZonesTest {
    @Test
    fun trimRemovesWarmupAndExtraTime() {
        val g = SyntheticMatch.generate()
        val full = MatchAnalyzer.analyze(g)
        val trimmed = MatchAnalyzer.analyze(g, options = AnalysisOptions(trimStartMs = g.startMs + 10 * 60_000, trimEndMs = g.endMs - 10 * 60_000))
        assertTrue(trimmed.physical.totalDistanceM < full.physical.totalDistanceM - 1500)
        assertEquals(g.startMs, trimmed.recordStartMs)
        assertEquals(g.startMs + 10 * 60_000, trimmed.startMs)
    }

    @Test
    fun adaptiveZonesScaleWithPlayer() {
        val z = SpeedZones.adaptive(24.0)
        assertEquals(20.4, z.sprintKmh)
        assertEquals(16.8, z.highSpeedKmh)
        val r = MatchAnalyzer.analyze(SyntheticMatch.generate())
        assertEquals(ZoneMode.ADAPTIVE, r.physical.zoneMode)
        assertTrue(r.physical.referenceMaxSpeedKmh in 22.0..33.0, "ref ${r.physical.referenceMaxSpeedKmh}")
        assertTrue(r.physical.sprints.isNotEmpty())
    }

    @Test
    fun slowerPlayerStillGetsSprints() {
        // Mismo partido a 75% de velocidad: con umbrales fijos casi no hay sprints, con adaptativos sí.
        val g = SyntheticMatch.generate()
        val t0 = g.points.first()
        val proj = LocalProjection(t0.lat, t0.lon)
        val slow = Track(points = g.points.map { p ->
            val l = proj.toLocal(p.lat, p.lon)
            val (lat, lon) = proj.toLatLon(Vec2(l.x, l.y))
            TrackPoint(t0.timeMs + ((p.timeMs - t0.timeMs) / 0.75).toLong(), lat, lon, heartRate = p.heartRate)
        })
        val fixed = MatchAnalyzer.analyze(slow, options = AnalysisOptions(physical = PhysicalConfig(zoneMode = ZoneMode.STANDARD)))
        val adaptive = MatchAnalyzer.analyze(slow)
        assertTrue(adaptive.physical.sprints.size > fixed.physical.sprints.size, "fijos ${fixed.physical.sprints.size}, adaptativos ${adaptive.physical.sprints.size}")
    }
}

class EnergyAndAlertsTest {
    @Test
    fun energyCostRisesWithAcceleration() {
        assertTrue(EnergyModel.energyCost(5.0, 2.0) > EnergyModel.energyCost(5.0, 0.0))
        assertTrue(abs(EnergyModel.energyCost(4.0, 0.0) - 3.6 * 1.29) < 1e-9)
    }

    @Test
    fun energyDropsDuringRepeatedSprintsAndRecovers() {
        val pts = ArrayList<TrackPoint>()
        var lat = 40.0; var t = 0L
        fun run(speed: Double, secs: Int) = repeat(secs) { lat += speed / 111_195.0; t += 1000; pts += TrackPoint(t, lat, -3.0) }
        run(1.5, 120)
        repeat(6) { run(8.0, 5); run(2.0, 8) }
        val sprintEnd = pts.size
        run(1.0, 240)
        val periods = listOf(Period(1, pts.first().timeMs, pts.last().timeMs))
        val samples = PhysicalAnalyzer.clean(pts, periods)
        val e = EnergyModel.analyze(samples, periods, 29.0, emptyList(), 190, 70.0)
        val minuteAfterSprints = sprintEnd / 60
        assertTrue(e.energyPerMinute[minuteAfterSprints] < e.energyPerMinute[0] - 15, "energía ${e.energyPerMinute}")
        assertTrue(e.finalEnergy > e.minEnergy + 10, "debe recuperarse: ${e.energyPerMinute}")
        assertNotNull(e.kcal)
    }

    @Test
    fun fullMatchHasAlertsAdvancedMetricsAndEnergy() {
        val r = MatchAnalyzer.analyze(SyntheticMatch.generate(), PlayerProfile(age = 30, weightKg = 72.0), AnalysisOptions(rpe = 7))
        val e = assertNotNull(r.energy)
        assertTrue(e.energyPerMinute.size in 88..95, "minutos ${e.energyPerMinute.size}")
        assertTrue(e.finalEnergy < 90)
        val a = assertNotNull(r.advanced)
        assertEquals(listOf(1, 3, 5, 10), a.peakDemands.map { it.windowMinutes })
        assertTrue(a.peakDemands[0].metersPerMinute >= a.peakDemands[3].metersPerMinute)
        assertTrue((a.sessionRpeLoad ?: 0.0) > 500)
        assertTrue(r.alerts.isNotEmpty())
        assertTrue(r.alerts.all { it.minute in 1..95 })
    }

    @Test
    fun matchClockContinuesIntoSecondHalf() {
        val clock = MatchClock(listOf(Period(1, 0, 45 * 60_000L), Period(2, 60 * 60_000L, 105 * 60_000L)))
        assertEquals(1, clock.minute(30_000))
        assertEquals(46, clock.minute(60 * 60_000L + 1000))
        assertEquals("min 50 (2T 5')", clock.label(64 * 60_000L + 1000))
        assertEquals(null, clock.minute(50 * 60_000L))
    }
}

class ThreeCornersTest {
    private val proj = LocalProjection(40.0, -3.0)
    private fun geo(x: Double, y: Double) = proj.toLatLon(Vec2(x, y)) // pitch alineada con este/norte

    @Test
    fun anyOrderGivesSamePitchAndMeasuresSize() {
        // Cancha de 90x55: x=este (largo), y=norte (ancho, "izquierda" mirando al este).
        val a = geo(0.0, 0.0); val b = geo(0.0, 55.0); val c = geo(90.0, 0.0)
        val cal1 = PitchCalibration.fromThreeCorners(a, b, c)
        assertEquals(90.0, cal1.dims.length)
        assertEquals(55.0, cal1.dims.width)
        // Mismas esquinas, pero tocando primero la de la izquierda (espejo): debe corregirse sola.
        val cal2 = PitchCalibration.fromThreeCorners(b, a, geo(90.0, 55.0))
        val (lat, lon) = geo(30.0, 40.0)
        val p1 = cal1.toPitch(lat, lon); val p2 = cal2.toPitch(lat, lon)
        assertTrue(abs(p1.x - 30) < 0.1 && abs(p1.y - 40) < 0.1, "$p1")
        assertTrue(abs(p2.x - 30) < 0.1 && abs(p2.y - 40) < 0.1, "$p2")
        val corners = cal1.cornersLatLon()
        assertTrue(abs(corners[2].first - geo(90.0, 55.0).first) < 1e-7)
    }
}
