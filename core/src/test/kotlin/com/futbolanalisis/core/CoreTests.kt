package com.futbolanalisis.core

import com.futbolanalisis.core.geo.Geo
import com.futbolanalisis.core.geo.LocalProjection
import com.futbolanalisis.core.geo.Vec2
import com.futbolanalisis.core.io.GpxParser
import com.futbolanalisis.core.io.GpxWriter
import com.futbolanalisis.core.io.TcxParser
import com.futbolanalisis.core.io.TrackImporter
import com.futbolanalisis.core.io.TrackParseException
import com.futbolanalisis.core.model.PlayerProfile
import com.futbolanalisis.core.model.Track
import com.futbolanalisis.core.model.TrackPoint
import com.futbolanalisis.core.physical.Period
import com.futbolanalisis.core.physical.PeriodDetector
import com.futbolanalisis.core.physical.PhysicalAnalyzer
import com.futbolanalisis.core.pitch.PitchCalibration
import com.futbolanalisis.core.physio.Readiness
import com.futbolanalisis.core.physio.WellnessSnapshot
import com.futbolanalisis.core.tactical.TacticalAnalyzer
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeoTest {
    @Test
    fun haversineOneDegreeLatitude() {
        val d = Geo.haversine(0.0, 0.0, 1.0, 0.0)
        assertTrue(abs(d - 111_195) < 50, "1° de latitud ≈ 111.2 km, fue $d")
    }

    @Test
    fun projectionRoundTrip() {
        val p = LocalProjection(40.45, -3.69)
        val (lat, lon) = p.toLatLon(Vec2(80.0, -35.0))
        val back = p.toLocal(lat, lon)
        assertTrue(abs(back.x - 80) < 1e-6 && abs(back.y + 35) < 1e-6)
    }
}

class ParserTest {
    private val gpx = """
        <?xml version="1.0"?>
        <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1"
             xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1">
          <trk><name>Liga domingo</name><trkseg>
            <trkpt lat="40.0" lon="-3.0"><ele>600</ele><time>2026-11-08T10:00:00Z</time>
              <extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>120</gpxtpx:hr></gpxtpx:TrackPointExtension></extensions></trkpt>
            <trkpt lat="40.0001" lon="-3.0"><time>2026-11-08T10:00:01.000+00:00</time></trkpt>
            <trkpt lat="40.0002" lon="-3.0"><time>2026-11-08T10:00:02</time></trkpt>
          </trkseg></trk>
        </gpx>
    """.trimIndent()

    @Test
    fun parsesGpxWithHeartRateAndMixedTimeFormats() {
        val t = GpxParser.parse(gpx.byteInputStream())
        assertEquals("Liga domingo", t.name)
        assertEquals(3, t.points.size)
        assertEquals(120, t.points[0].heartRate)
        assertNull(t.points[1].heartRate)
        assertEquals(2000, t.points[2].timeMs - t.points[0].timeMs)
    }

    @Test
    fun parsesTcxIncludingHeartRateWithoutPosition() {
        val tcx = """
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
             <Activities><Activity Sport="Other"><Lap><Track>
              <Trackpoint><Time>2026-11-08T10:00:00Z</Time><Position><LatitudeDegrees>40</LatitudeDegrees><LongitudeDegrees>-3</LongitudeDegrees></Position><HeartRateBpm><Value>130</Value></HeartRateBpm></Trackpoint>
              <Trackpoint><Time>2026-11-08T10:00:01Z</Time><HeartRateBpm><Value>131</Value></HeartRateBpm></Trackpoint>
              <Trackpoint><Time>2026-11-08T10:00:02Z</Time><Position><LatitudeDegrees>40.0001</LatitudeDegrees><LongitudeDegrees>-3</LongitudeDegrees></Position></Trackpoint>
             </Track></Lap></Activity></Activities>
            </TrainingCenterDatabase>
        """.trimIndent()
        val t = TrackImporter.parse(tcx.byteInputStream())
        assertEquals(2, t.points.size)
        assertEquals(listOf(130, 131), t.heartRateSamples.map { it.bpm })
        assertEquals("Other", TcxParser.parse(tcx.byteInputStream()).name)
    }

    @Test
    fun rejectsUnknownFormatsAndFit() {
        assertFailsWith<TrackParseException> { TrackImporter.parse("hola".byteInputStream()) }
        assertFailsWith<TrackParseException> { TrackImporter.parse(byteArrayOf(14, 16, 0).inputStream(), "partido.fit") }
    }

    @Test
    fun writerOutputParsesBack() {
        val original = SyntheticMatch.generate()
        val again = GpxParser.parse(GpxWriter.write(original).byteInputStream())
        assertEquals(original.points.size, again.points.size)
        assertEquals(original.points[100].heartRate, again.points[100].heartRate)
        assertTrue(abs(original.points[100].lat - again.points[100].lat) < 1e-6)
    }
}

class PitchTest {
    private val proj = LocalProjection(SyntheticMatch.CENTER_LAT, SyntheticMatch.CENTER_LON)
    private val a = Math.toRadians(SyntheticMatch.PITCH_ANGLE_DEG)
    private fun geo(px: Double, py: Double): Pair<Double, Double> {
        val x = px - 52.5; val y = py - 34.0
        return proj.toLatLon(Vec2(x * kotlin.math.cos(a) - y * kotlin.math.sin(a), x * kotlin.math.sin(a) + y * kotlin.math.cos(a)))
    }

    @Test
    fun cornersCalibrationIsExact() {
        val cal = PitchCalibration.fromCorners(geo(0.0, 0.0), geo(0.0, 68.0), geo(105.0, 0.0))
        val (lat, lon) = geo(30.0, 50.0)
        val p = cal.toPitch(lat, lon)
        assertTrue(abs(p.x - 30) < 0.05 && abs(p.y - 50) < 0.05, "fue $p")
        val flipped = cal.withAttackFlipped(true).toPitch(lat, lon)
        assertTrue(abs(flipped.x - 75) < 0.05 && abs(flipped.y - 18) < 0.05, "fue $flipped")
    }

    @Test
    fun autoCalibrationRecoversRotatedPitchUpToDirection() {
        // Puntos uniformes en toda la cancha.
        val rnd = java.util.Random(1)
        val pts = (0 until 4000).map {
            val (lat, lon) = geo(rnd.nextDouble() * 105, rnd.nextDouble() * 68)
            TrackPoint(it * 1000L, lat, lon)
        }
        val cal = PitchCalibration.auto(pts, lowPct = 0.0)
        val (lat, lon) = geo(20.0, 10.0)
        val p = cal.toPitch(lat, lon)
        val direct = abs(p.x - 20) < 2 && abs(p.y - 10) < 2
        val rotated = abs(p.x - 85) < 2 && abs(p.y - 58) < 2
        assertTrue(direct || rotated, "La calibración automática debe conservar la cancha salvo rotación de 180°: $p")
    }
}

class PeriodTest {
    private fun line(startMs: Long, seconds: Int, speed: Double, lat0: Double = 40.0): List<TrackPoint> =
        (0 until seconds).map { TrackPoint(startMs + it * 1000L, lat0 + it * speed / 111_195.0 * if (it / 30 % 2 == 0) 1 else -1, -3.0) }

    @Test
    fun detectsGapAsHalfTime() {
        val first = line(0, 45 * 60, 2.0)
        val second = line(60 * 60_000L, 45 * 60, 2.0)
        val periods = PeriodDetector.detect(first + second)
        assertEquals(2, periods.size)
        assertEquals(first.last().timeMs, periods[0].endMs)
        assertEquals(second.first().timeMs, periods[1].startMs)
    }

    @Test
    fun detectsStillBreakWithGpsNoise() {
        val g = SyntheticMatch.generate()
        val periods = PeriodDetector.detect(g.points)
        assertEquals(2, periods.size)
        val breakMin = (periods[1].startMs - periods[0].endMs) / 60_000.0
        assertTrue(breakMin in 12.0..16.0, "descanso de $breakMin min")
        assertTrue(abs((periods[0].endMs - g.startMs) / 60_000.0 - 45) <= 1.5)
    }

    @Test
    fun shortSessionIsSinglePeriod() {
        assertEquals(1, PeriodDetector.detect(line(0, 20 * 60, 2.0)).size)
    }
}

class PhysicalTest {
    private val std = com.futbolanalisis.core.physical.PhysicalConfig(zoneMode = com.futbolanalisis.core.physical.ZoneMode.STANDARD)
    private fun straight(speedMs: Double, seconds: Int, t0: Long = 0): List<TrackPoint> =
        (0..seconds).map { TrackPoint(t0 + it * 1000L, 40.0 + it * speedMs / 111_195.0, -3.0) }

    @Test
    fun constantRunIsMeasuredAndZoned() {
        val pts = straight(5.0, 100) // 18 km/h -> zona "Carrera"
        val periods = listOf(Period(1, pts.first().timeMs, pts.last().timeMs))
        val r = PhysicalAnalyzer.analyze(PhysicalAnalyzer.clean(pts, periods), periods, std)
        assertTrue(abs(r.totalDistanceM - 500) < 2, "distancia ${r.totalDistanceM}")
        assertTrue(r.distanceByZoneM[2] > 495)
        assertTrue(abs(r.maxSpeedKmh - 18) < 0.2)
        assertEquals(0, r.sprints.size)
    }

    @Test
    fun detectsSprintAndAcceleration() {
        val walk = straight(1.5, 30)
        val last = walk.last()
        val sprint = (1..6).map { TrackPoint(last.timeMs + it * 1000L, last.lat + it * 8.0 / 111_195.0, -3.0) }
        val after = sprint.last().let { s -> (1..30).map { TrackPoint(s.timeMs + it * 1000L, s.lat + it * 1.5 / 111_195.0, -3.0) } }
        val pts = walk + sprint + after
        val periods = listOf(Period(1, pts.first().timeMs, pts.last().timeMs))
        val r = PhysicalAnalyzer.analyze(PhysicalAnalyzer.clean(pts, periods), periods, std)
        assertEquals(1, r.sprints.size)
        assertTrue(r.sprints[0].maxSpeedKmh > 25.2)
        assertTrue(r.accelerations >= 1)
        assertTrue(r.decelerations >= 1)
    }

    @Test
    fun gpsJumpsAreDiscarded() {
        val pts = straight(3.0, 60).toMutableList()
        pts[30] = pts[30].copy(lat = pts[30].lat + 0.001) // salto de ~111 m
        val periods = listOf(Period(1, pts.first().timeMs, pts.last().timeMs))
        val r = PhysicalAnalyzer.analyze(PhysicalAnalyzer.clean(pts, periods), periods, std)
        assertTrue(abs(r.totalDistanceM - 180) < 3, "distancia ${r.totalDistanceM}")
        assertTrue(r.maxSpeedKmh < 12)
    }
}

class TacticalTest {
    @Test
    fun gaussianBlurPreservesMass() {
        val src = DoubleArray(21 * 14).also { it[7 * 21 + 10] = 1.0 }
        val out = TacticalAnalyzer.gaussianBlur(src, 21, 14, 1.0)
        assertTrue(abs(out.sum() - 1.0) < 1e-9)
    }

    @Test
    fun roleHeuristic() {
        val dims = com.futbolanalisis.core.pitch.PitchDimensions()
        val e = com.futbolanalisis.core.tactical.PositionEllipse(com.futbolanalisis.core.pitch.PitchPoint(0.0, 0.0), 30.0, 20.0, 0.0, 2000.0)
        assertEquals("Defensa central", TacticalAnalyzer.estimateRole(com.futbolanalisis.core.pitch.PitchPoint(25.0, 34.0), e, dims))
        assertEquals("Extremo izquierdo", TacticalAnalyzer.estimateRole(com.futbolanalisis.core.pitch.PitchPoint(80.0, 60.0), e, dims))
    }
}

class MatchAnalyzerTest {
    private val report by lazy { MatchAnalyzer.analyze(SyntheticMatch.generate(), PlayerProfile(age = 30)) }

    @Test
    fun syntheticMidfielderLooksRealistic() {
        val r = report
        assertEquals(2, r.periods.size)
        assertTrue(r.physical.totalDistanceM in 8_000.0..13_500.0, "distancia ${r.physical.totalDistanceM}")
        assertTrue(r.physical.sprints.isNotEmpty())
        assertNotNull(r.heartRate)
        assertEquals("Reloj (archivo)", r.heartRateSource)
        assertTrue(r.tactical.estimatedRole.startsWith("Mediocampista") || r.tactical.estimatedRole.startsWith("Volante"), r.tactical.estimatedRole)
        assertTrue(abs(r.tactical.thirdsPct.sum() - 100) < 1e-6)
        // El descanso en el banco no debe contar como juego.
        assertTrue(r.physical.playingTimeS / 60 in 85.0..95.0, "minutos ${r.physical.playingTimeS / 60}")
    }

    @Test
    fun secondHalfIsMirroredSoBothHalvesAttackTheSameWay() {
        val avg = report.tactical.averagePositionByPeriod
        // El mediocampista sintético juega en x≈50 en ambos tiempos (coordenadas de ataque).
        assertTrue(abs(avg.getValue(1).x - avg.getValue(2).x) < 12, "medias $avg")
    }

    @Test
    fun usesExternalHeartRateWhenFileHasNone() {
        val g = SyntheticMatch.generate()
        val noHr = Track(points = g.points.map { it.copy(heartRate = null) })
        val r = MatchAnalyzer.analyze(noHr, externalHeartRate = g.heartRateSamples)
        assertEquals("Health Connect", r.heartRateSource)
        assertNotNull(r.heartRate)
    }

    @Test
    fun reportJsonRoundTrip() {
        val text = MatchAnalyzer.json.encodeToString(MatchReport.serializer(), report)
        val back = MatchAnalyzer.json.decodeFromString(MatchReport.serializer(), text)
        assertEquals(report.physical.totalDistanceM, back.physical.totalDistanceM)
        assertEquals(report.tactical.heatmap.values.size, back.tactical.heatmap.values.size)
    }

    @Test
    fun attackFlipOverrideMovesAveragePosition() {
        val track = SyntheticMatch.generate(homeX = 75.0) // delantero
        val auto = MatchAnalyzer.analyze(track)
        val forced = MatchAnalyzer.analyze(track, options = AnalysisOptions(attackFlipped = !auto.tactical.attackFlipped))
        assertTrue(abs(auto.tactical.averagePosition.x + forced.tactical.averagePosition.x - 105) < 1.0)
    }
}

class WellnessTest {
    @Test
    fun readinessFromHrv() {
        assertEquals(Readiness.GOOD, WellnessSnapshot(hrvRmssdMs = 60.0, hrvBaselineMs = 62.0).readiness)
        assertEquals(Readiness.MODERATE, WellnessSnapshot(hrvRmssdMs = 56.0, hrvBaselineMs = 62.0).readiness)
        assertEquals(Readiness.LOW, WellnessSnapshot(hrvRmssdMs = 45.0, hrvBaselineMs = 62.0).readiness)
        assertNull(WellnessSnapshot(hrvRmssdMs = 45.0).readiness)
    }

    @Test
    fun tanakaMaxHr() {
        assertEquals(187, PlayerProfile(age = 30).effectiveMaxHr)
        assertEquals(195, PlayerProfile(age = 30, maxHeartRate = 195).effectiveMaxHr)
    }
}
