package com.futbolanalisis.core

import com.futbolanalisis.core.coach.MatchLog
import com.futbolanalisis.core.geo.SatellitePlanner
import com.futbolanalisis.core.geo.WebMercator
import com.futbolanalisis.core.insights.Attribute
import com.futbolanalisis.core.insights.MatchComparator
import com.futbolanalisis.core.insights.MatchKpis
import com.futbolanalisis.core.insights.Metric
import com.futbolanalisis.core.insights.PerformanceScore
import com.futbolanalisis.core.insights.Verdict
import com.futbolanalisis.core.model.PlayerProfile
import com.futbolanalisis.core.render.HeatmapRenderer
import com.futbolanalisis.core.share.ShareCard
import com.futbolanalisis.core.share.ShareCardData
import com.futbolanalisis.core.share.ShareFormat
import com.futbolanalisis.core.share.ShareStat
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class V04Tests {
    private val report by lazy { MatchAnalyzer.analyze(SyntheticMatch.generate(), PlayerProfile(age = 30, weightKg = 72.0)) }
    private val kpis by lazy { MatchKpis.of(report) }

    @Test
    fun kpisCarryAbsoluteDistances() {
        assertTrue(kpis.minutes in 85.0..95.0)
        assertNotNull(kpis.runDistanceM)
        assertNotNull(kpis.highSpeedDistanceM)
        assertTrue(kpis.runDistanceM!! > kpis.highSpeedDistanceM!!)
        assertTrue(kpis.highSpeedDistanceM!! > 0)
        assertNotNull(kpis.secondHalfPct)
        assertNotNull(kpis.peak1MinMpm)
    }

    @Test
    fun scoreIsInRangeAndRewardsBetterNumbers() {
        val s = PerformanceScore.of(kpis)
        assertEquals(Attribute.entries.size, s.values.size)
        assertTrue(s.overall in 40..95, "índice ${s.overall}")
        s.values.forEach { assertTrue(it in 10..99) }
        val better = PerformanceScore.of(kpis.copy(metersPerMinute = kpis.metersPerMinute * 1.15, maxSpeedKmh = kpis.maxSpeedKmh + 3))
        assertTrue(better.overall > s.overall)
        assertTrue(better[Attribute.SPEED] > s[Attribute.SPEED])
        val worse = PerformanceScore.of(kpis.copy(fatigueIndexPct = -35.0, finalEnergy = 18.0, secondHalfPct = -22.0))
        assertTrue(worse[Attribute.ENDURANCE] < s[Attribute.ENDURANCE])
        // Jugador recreativo: debe quedar claramente por debajo.
        val casual = PerformanceScore.of(
            kpis.copy(metersPerMinute = 70.0, maxSpeedKmh = 21.0, runDistanceM = 300.0, highSpeedDistanceM = 20.0, hmldM = 350.0,
                peak1MinMpm = 115.0, metabolicPowerWkg = 6.0, accelerations = 12, decelerations = 14),
        )
        assertTrue(casual.overall < s.overall - 10, "recreativo ${casual.overall} vs ${s.overall}")
        assertEquals(s.overall, PerformanceScore.average(listOf(s, s))!!.overall)
    }

    @Test
    fun per90NormalisesOnlyVolumeMetrics() {
        val half = kpis.copy(minutes = 45.0, distanceM = 5000.0)
        assertEquals(10.0, Metric.DISTANCE.value(half, per90 = true)!!, 1e-9)
        assertEquals(5.0, Metric.DISTANCE.value(half, per90 = false)!!, 1e-9)
        assertEquals(half.metersPerMinute, Metric.PACE.value(half, per90 = true)!!, 1e-9)
    }

    @Test
    fun comparisonFindsImprovementsAndRecords() {
        val base = kpis
        val history = listOf(base, base.copy(maxSpeedKmh = base.maxSpeedKmh - 1), base.copy(distanceM = base.distanceM * 0.97))
        val target = base.copy(distanceM = base.distanceM * 1.2, metersPerMinute = base.metersPerMinute * 1.2, maxSpeedKmh = base.maxSpeedKmh + 2, finalEnergy = 10.0)
        val cmp = MatchComparator.vsAverage(target, history, per90 = false).associateBy { it.metric }
        assertEquals(Verdict.BETTER, cmp[Metric.DISTANCE]!!.verdict)
        assertTrue(cmp[Metric.DISTANCE]!!.isRecord)
        assertEquals(Verdict.BETTER, cmp[Metric.MAX_SPEED]!!.verdict)
        assertEquals(Verdict.WORSE, cmp[Metric.FINAL_ENERGY]!!.verdict)
        assertEquals(Verdict.SIMILAR, cmp[Metric.ACCELERATIONS]!!.verdict)
        assertEquals(Verdict.NEUTRAL, cmp[Metric.AVG_HR]!!.verdict)
        val insights = MatchComparator.insights(target, history)
        assertTrue(insights.isNotEmpty() && insights.size <= 3)
        assertTrue(insights.first().title.startsWith("Récord personal"))
        assertTrue(MatchComparator.insights(target, emptyList()).isEmpty())

        val h2h = MatchComparator.headToHead(target, base, per90 = true).first { it.metric == Metric.PACE }
        assertEquals(20.0, h2h.deltaPct!!, 0.01)

        val records = MatchComparator.records(history + target) { it }
        assertEquals(target, records.first { it.metric == Metric.MAX_SPEED }.holder)
        assertEquals(Verdict.BETTER, MatchComparator.form(listOf(60.0, 61.0, 62.0, 70.0, 71.0, 72.0))!!.verdict)
        assertEquals("10,98 km", MatchComparator.format(Metric.DISTANCE, 10.98))
    }

    @Test
    fun heatmapImageIsHottestWhereThePlayerWas() {
        val h = report.tactical.heatmap
        val w = 210; val ht = 136
        val px = HeatmapRenderer.argb(h, w, ht)
        assertEquals(w * ht, px.size)
        val hottest = px.indices.maxByOrNull { px[it] ushr 24 }!!
        val mode = report.tactical.modePosition
        val x = (hottest % w + 0.5) / w * h.dims.length
        val y = (1 - (hottest / w + 0.5) / ht) * h.dims.width
        assertTrue(hypot(x - mode.x, y - mode.y) < 25, "pico en ($x, $y), moda en $mode")
        assertTrue(px.count { it == 0 } > 0, "las zonas sin presencia deben ser transparentes")
        val strong = HeatmapRenderer.argb(h, w, ht, strong = true)
        assertTrue(strong.sumOf { (it ushr 24).toLong() } > px.sumOf { (it ushr 24).toLong() })
    }

    @Test
    fun satellitePlanCoversThePitch() {
        // Cancha real de 105 × 68 m calibrada por esquinas (la automática estira el recorrido).
        val proj = com.futbolanalisis.core.geo.LocalProjection(-34.6037, -58.3816)
        fun geo(x: Double, y: Double) = proj.toLatLon(com.futbolanalisis.core.geo.Vec2(x, y))
        val cal = com.futbolanalisis.core.pitch.PitchCalibration.fromThreeCorners(geo(10.0, 5.0), geo(-20.0, 66.0), geo(104.2, 51.3))
        val plan = SatellitePlanner.plan(cal, attackFlipped = false, marginM = 6.0)
        assertTrue(plan.tilesX * plan.tilesY in 1..20)
        val (tl, tr, br, bl) = plan.corners
        plan.corners.forEach { (x, y) ->
            assertTrue(x >= 0 && x <= plan.mosaicWidth && y >= 0 && y <= plan.mosaicHeight)
        }
        val mpp = WebMercator.metersPerPixel(cal.originLat, plan.zoom)
        val topM = hypot(tr.first - tl.first, tr.second - tl.second) * mpp
        val sideM = hypot(bl.first - tl.first, bl.second - tl.second) * mpp
        assertEquals(cal.dims.length + 12, topM, 2.0)
        assertEquals(cal.dims.width + 12, sideM, 2.0)
        // Invertir el ataque gira el recuadro 180°: las esquinas opuestas se intercambian.
        val flipped = SatellitePlanner.plan(cal, attackFlipped = true, marginM = 6.0)
        val ox = (plan.tileX - flipped.tileX) * WebMercator.TILE; val oy = (plan.tileY - flipped.tileY) * WebMercator.TILE
        assertTrue(abs(flipped.corners[2].first - (tl.first + ox)) < 1e-6 && abs(flipped.corners[2].second - (tl.second + oy)) < 1e-6)
        assertEquals(plan.tiles.size, plan.tilesX * plan.tilesY)
        // Sentido horario en pantalla (y hacia abajo): la cancha no queda reflejada.
        val cross = (tr.first - tl.first) * (bl.second - tl.second) - (tr.second - tl.second) * (bl.first - tl.first)
        assertTrue(cross > 0)
        assertTrue(br.first != bl.first || br.second != bl.second)
    }

    @Test
    fun mercatorMatchesKnownTile() {
        // Greenwich/ecuador cae en el centro del mapa.
        assertEquals(128.0, WebMercator.pixelX(0.0, 0), 1e-9)
        assertEquals(128.0, WebMercator.pixelY(0.0, 0), 1e-9)
        assertTrue(WebMercator.pixelY(40.0, 5) < WebMercator.pixelY(-40.0, 5))
    }

    @Test
    fun shareCardDrawsInEveryFormat() {
        val data = sampleShareData(report)
        ShareFormat.entries.forEach { f ->
            val s = AwtSurface(f.width, f.height)
            ShareCard.draw(s, data)
            ShareCard.draw(AwtSurface(f.width, f.height), data.copy(ownTeam = "", rival = "", score = null))
            // El fondo no puede quedar vacío.
            assertTrue(s.image.getRGB(f.width / 2, f.height / 2) ushr 24 == 0xFF)
        }
    }

    @Test
    fun matchLogFixture() {
        assertEquals("Los Pumas vs Atlético", MatchLog(ownTeam = "Los Pumas", opponent = "Atlético").fixture)
        assertEquals("vs Atlético", MatchLog(opponent = "Atlético").fixture)
        assertEquals(null, MatchLog().fixture)
        assertTrue(!MatchLog(ownTeam = "A", opponent = "B", matchDateMs = 5).hasDetails)
        assertTrue(MatchLog(goals = 1).hasDetails)
    }
}

fun sampleShareData(report: MatchReport): ShareCardData {
    val k = MatchKpis.of(report)
    return ShareCardData(
        title = "Partido de ejemplo",
        ownTeam = "Los Pumas", rival = "Atlético Sur", goalsFor = 3, goalsAgainst = 1,
        dateText = "sáb 8 nov 2026",
        playerName = "Francisco", role = report.tactical.description.archetype,
        score = PerformanceScore.of(k),
        stats = listOf(
            ShareStat("Distancia", MatchComparator.format(k.distanceM / 1000, 2), "km"),
            ShareStat("Vel. máx", MatchComparator.format(k.maxSpeedKmh, 1), "km/h"),
            ShareStat("Sprints", "${k.sprints}"),
            ShareStat("Ritmo", MatchComparator.format(k.metersPerMinute, 0), "m/min"),
        ),
        heatmap = report.tactical.heatmap,
        attribution = "Imágenes © Esri, Maxar",
    )
}
