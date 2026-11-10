package com.futbolanalisis.app

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.futbolanalisis.app.data.MatchRecord
import com.futbolanalisis.app.data.MatchSummary
import com.futbolanalisis.app.share.ShareRenderer
import com.futbolanalisis.app.ui.screens.MainScreen
import com.futbolanalisis.app.ui.screens.MatchScreen
import com.futbolanalisis.app.ui.screens.NewMatchScreen
import com.futbolanalisis.app.ui.screens.shareData
import com.futbolanalisis.app.ui.theme.FutbolTheme
import com.futbolanalisis.core.MatchAnalyzer
import com.futbolanalisis.core.coach.MatchLog
import com.futbolanalisis.core.insights.MatchKpis
import com.futbolanalisis.core.insights.PerformanceScore
import com.futbolanalisis.core.io.TrackImporter
import com.futbolanalisis.core.model.PlayerProfile
import com.futbolanalisis.core.share.ShareFormat
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Genera capturas de las pantallas principales con el partido de ejemplo (sin emulador). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h900dp-xhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val profile = PlayerProfile(name = "Francisco", age = 29, weightKg = 72.0, usualPosition = "Mediocentro")

    private val record: MatchRecord by lazy {
        val track = File("src/main/assets/sample_match.gpx").inputStream().use { TrackImporter.parse(it, "sample_match.gpx") }
        val log = MatchLog(ownTeam = "Los Pumas", opponent = "Atlético Sur", goalsFor = 3, goalsAgainst = 1, goals = 1, assists = 1, shots = 3, shotsOnTarget = 2, rpe = 7, selfRating = 8)
        MatchRecord("m3", "Los Pumas vs Atlético Sur", 0, track, report = MatchAnalyzer.analyze(track, profile, title = "Los Pumas vs Atlético Sur"), log = log)
    }

    private fun summary(id: String, title: String, daysAgo: Int, f: (MatchKpis) -> MatchKpis, gf: Int, ga: Int, rival: String): MatchSummary {
        val r = record.report
        val k = f(MatchKpis.of(r))
        return MatchSummary(
            id = id, title = title, startMs = r.startMs - daysAgo * 86_400_000L, distanceM = k.distanceM, playingTimeS = k.minutes * 60,
            role = r.tactical.estimatedRole, archetype = r.tactical.description.archetype, sprints = k.sprints, maxSpeedKmh = k.maxSpeedKmh,
            heatmap = r.tactical.heatmap, goals = if (gf > ga) 1 else 0, assists = 1, result = MatchLog(goalsFor = gf, goalsAgainst = ga).result,
            opponent = rival, ownTeam = "Los Pumas", goalsFor = gf, goalsAgainst = ga, kpis = k, score = PerformanceScore.of(k),
        )
    }

    private val matches: List<MatchSummary> by lazy {
        listOf(
            summary("m3", "Los Pumas vs Atlético Sur", 0, { it }, 3, 1, "Atlético Sur"),
            summary("m2", "Los Pumas vs Deportivo Norte", 7, {
                it.copy(distanceM = it.distanceM * 0.88, metersPerMinute = it.metersPerMinute * 0.88, maxSpeedKmh = 28.4, sprints = 9, highSpeedDistanceM = 480.0, runDistanceM = 1900.0, hmldM = 1200.0, finalEnergy = 48.0, fatigueIndexPct = -18.0, accelerations = 15)
            }, 1, 1, "Deportivo Norte"),
            summary("m1", "Los Pumas vs Unión", 14, {
                it.copy(distanceM = it.distanceM * 0.8, metersPerMinute = it.metersPerMinute * 0.8, maxSpeedKmh = 27.1, sprints = 7, highSpeedDistanceM = 390.0, runDistanceM = 1500.0, hmldM = 980.0, finalEnergy = 41.0, fatigueIndexPct = -24.0, secondHalfPct = -14.0, accelerations = 12, decelerations = 11)
            }, 0, 2, "Unión"),
        )
    }

    private fun state() = UiState(matches = matches, profile = profile, loaded = true, teamName = "Los Pumas")

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent { FutbolTheme(content) }
        compose.waitForIdle()
        File("build/screenshots").mkdirs()
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Composable
    private fun Main(tab: Int, s: UiState = state()) = MainScreen(s, SnackbarHostState(), {}, {}, {}, {}, {}, {}, initialTab = tab)

    @Composable
    private fun Match(tab: Int) = MatchScreen(record, matches, false, {}, {}, {}, {}, {}, {}, {}, {}, initialTab = tab)

    @Test @Config(qualifiers = "w411dp-h1900dp-xhdpi") fun home() = shot("01_inicio") { Main(0) }
    @Test fun homeEmpty() = shot("02_inicio_vacio") { Main(0, UiState(loaded = true)) }
    @Test fun matchesList() = shot("03_partidos") { Main(1) }
    @Test @Config(qualifiers = "w411dp-h2300dp-xhdpi") fun progress() = shot("04_progreso") { Main(2) }
    @Test @Config(qualifiers = "w411dp-h1100dp-xhdpi") fun profileTab() = shot("05_perfil") { Main(3) }
    @Test @Config(qualifiers = "w411dp-h1300dp-xhdpi") fun newMatch() = shot("06_nuevo_partido") {
        NewMatchScreen(PendingImport(record.track, "Partido", "Archivo GPX"), "Los Pumas", false, {}, {})
    }
    @Test @Config(qualifiers = "w411dp-h2600dp-xhdpi") fun matchSummary() = shot("07_partido_resumen") { Match(0) }
    @Test @Config(qualifiers = "w411dp-h2300dp-xhdpi") fun matchCompare() = shot("08_partido_comparar") { Match(1) }
    @Test @Config(qualifiers = "w411dp-h1000dp-xhdpi") fun matchHeatmap() = shot("09_partido_mapa") { Match(3) }
    @Test @Config(qualifiers = "w411dp-h1900dp-xhdpi") fun matchTactical() = shot("10_partido_tactico") { Match(4) }

    @Test
    fun shareImage() {
        val bmp = ShareRenderer.render(shareData(record, profile, satellite = false), ShareFormat.POST, null)
        File("build/screenshots").mkdirs()
        File("build/screenshots/11_imagen_compartir.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
