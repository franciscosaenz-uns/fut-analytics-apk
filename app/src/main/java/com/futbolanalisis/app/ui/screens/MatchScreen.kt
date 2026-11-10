package com.futbolanalisis.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.futbolanalisis.app.data.MatchRecord
import com.futbolanalisis.app.data.MatchSummary
import com.futbolanalisis.app.ui.components.AlertTimeline
import com.futbolanalisis.app.ui.components.AttributeBars
import com.futbolanalisis.app.ui.components.BarChart
import com.futbolanalisis.app.ui.components.EnergyChart
import com.futbolanalisis.app.ui.components.InsightRow
import com.futbolanalisis.app.ui.components.LabeledBar
import com.futbolanalisis.app.ui.components.LineChart
import com.futbolanalisis.app.ui.components.Overline
import com.futbolanalisis.app.ui.components.Panel
import com.futbolanalisis.app.ui.components.Pill
import com.futbolanalisis.app.ui.components.PitchCanvas
import com.futbolanalisis.app.ui.components.SatelliteState
import com.futbolanalisis.app.ui.components.ScoreRing
import com.futbolanalisis.app.ui.components.SectionTitle
import com.futbolanalisis.app.ui.components.StatRow
import com.futbolanalisis.app.ui.components.arrow
import com.futbolanalisis.app.ui.components.rememberSatellite
import com.futbolanalisis.app.ui.components.zoneColors
import com.futbolanalisis.app.data.SatelliteStore
import com.futbolanalisis.app.ui.dateTime
import com.futbolanalisis.app.ui.dateOnly
import com.futbolanalisis.app.ui.fmt
import com.futbolanalisis.app.ui.km
import com.futbolanalisis.app.ui.minutes
import com.futbolanalisis.app.ui.theme.Brand
import com.futbolanalisis.app.ui.theme.numberStyle
import com.futbolanalisis.core.AnalysisOptions
import com.futbolanalisis.core.MatchReport
import com.futbolanalisis.core.coach.MatchLog
import com.futbolanalisis.core.insights.MatchComparator
import com.futbolanalisis.core.insights.MatchKpis
import com.futbolanalisis.core.insights.PerformanceScore
import com.futbolanalisis.core.physical.AlertLevel
import com.futbolanalisis.core.physical.MatchClock
import com.futbolanalisis.core.physical.ZoneMode
import com.futbolanalisis.core.pitch.CalibrationMethod
import com.futbolanalisis.core.tactical.Direction
import com.futbolanalisis.core.tactical.TacticalReport

private val tabs = listOf("Resumen", "Comparar", "Físico", "Mapa", "Táctico", "Cardio", "Coach")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchScreen(
    record: MatchRecord?,
    matches: List<MatchSummary>,
    busy: Boolean,
    onBack: () -> Unit,
    onOptions: ((AnalysisOptions) -> AnalysisOptions) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onTrim: () -> Unit,
    onCalibrate: () -> Unit,
    onLog: () -> Unit,
    onShare: () -> Unit,
    initialTab: Int = 0,
) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    var showSettings by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(TextFieldValue("")) }
    fun commitTitle() {
        val t = draft.text.trim()
        if (t.isNotBlank() && t != record?.title) onRename(t)
        editing = false
    }
    BackHandler(enabled = editing) { editing = false }

    Scaffold(
        containerColor = Brand.Bg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Brand.Bg),
                title = {
                    if (editing) {
                        // El nombre se edita aquí mismo, al tocarlo.
                        val focus = remember { FocusRequester() }
                        BasicTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleLarge.copy(color = Brand.Text),
                            cursorBrush = SolidColor(Brand.Accent),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { commitTitle() }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                            decorationBox = { inner ->
                                Column {
                                    inner()
                                    Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(2.dp).background(Brand.Accent))
                                }
                            },
                        )
                        LaunchedEffect(Unit) { focus.requestFocus() }
                    } else {
                        Row(
                            Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = record != null) {
                                val t = record?.title.orEmpty()
                                draft = TextFieldValue(t, selection = TextRange(0, t.length))
                                editing = true
                            }.padding(vertical = 4.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(record?.title ?: "Partido", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Filled.Edit, "Cambiar nombre", tint = Brand.Muted, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = { if (editing) editing = false else onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                actions = {
                    if (editing) {
                        IconButton(onClick = { commitTitle() }) { Icon(Icons.Filled.Check, "Guardar nombre", tint = Brand.Accent) }
                    } else {
                        IconButton(onClick = onShare, enabled = record != null) { Icon(Icons.Filled.Share, "Compartir imagen") }
                        IconButton(onClick = { showSettings = true }, enabled = record != null) { Icon(Icons.Filled.Settings, "Ajustes del análisis") }
                    }
                },
            )
        },
    ) { padding ->
        if (record == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val r = record.report
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            ScrollableTabRow(
                selectedTabIndex = tab, edgePadding = 8.dp, containerColor = Brand.Bg, contentColor = Brand.Text,
                indicator = { positions ->
                    if (tab < positions.size) TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[tab]), height = 3.dp, color = Brand.Accent)
                },
                divider = { HorizontalDivider(color = Brand.Outline) },
            ) {
                tabs.forEachIndexed { i, t ->
                    Tab(
                        selected = tab == i, onClick = { tab = i },
                        selectedContentColor = Brand.Text, unselectedContentColor = Brand.Muted,
                        text = { Text(t, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Medium) },
                    )
                }
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                when (tab) {
                    0 -> SummaryTab(record, matches, onLog, onShare) { tab = 1 }
                    1 -> CompareTab(record, matches)
                    2 -> PhysicalTab(r)
                    3 -> HeatmapTab(r, onCalibrate, onShare)
                    4 -> TacticalTab(r)
                    5 -> PhysiologyTab(r)
                    else -> CoachTab(record)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (showSettings && record != null) {
        SettingsDialog(
            record, onDismiss = { showSettings = false }, onOptions = onOptions,
            onDelete = { showSettings = false; onDelete() },
            onTrim = { showSettings = false; onTrim() },
            onCalibrate = { showSettings = false; onCalibrate() },
        )
    }
}

/** Marcador con los dos equipos, si se conocen. */
@Composable
private fun Scoreboard(log: MatchLog) {
    if (log.ownTeam.isBlank() && log.opponent.isBlank()) return
    val hasScore = log.goalsFor != null && log.goalsAgainst != null
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            log.ownTeam.ifBlank { "Mi equipo" }, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.End,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Box(
            Modifier.padding(horizontal = 12.dp).background(Brand.SurfaceHigh, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (hasScore) "${log.goalsFor} - ${log.goalsAgainst}" else "VS", style = numberStyle(if (hasScore) 26 else 18), color = if (hasScore) Brand.Text else Brand.Muted)
        }
        Text(
            log.opponent.ifBlank { "Rival" }, style = MaterialTheme.typography.titleMedium, color = Brand.Text.copy(alpha = 0.8f),
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryTab(record: MatchRecord, matches: List<MatchSummary>, onLog: () -> Unit, onShare: () -> Unit, onCompare: () -> Unit) {
    val r = record.report
    val log = record.log
    val clock = MatchClock(r.periods)
    val p = r.physical
    val d = r.tactical.description
    val kpis = remember(r) { MatchKpis.of(r) }
    val score = remember(kpis) { PerformanceScore.of(kpis) }
    val others = remember(matches, record.id) { matches.filter { it.id != record.id } }
    val avg = remember(others) { PerformanceScore.average(others.mapNotNull { it.score }) }
    val insights = remember(kpis, others) { MatchComparator.insights(kpis, others.mapNotNull { it.kpis }) }

    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Overline(record.dateMs.dateOnly() + " · " + p.playingTimeS.minutes(), modifier = Modifier.weight(1f))
            log.result?.let { res ->
                Pill(res.substringBefore(' '), if (res.startsWith("V")) Brand.Good else if (res.startsWith("D")) Brand.Bad else Brand.Warn)
            }
        }
        Scoreboard(log)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            ScoreRing(score.overall, size = 100.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(score.level, style = MaterialTheme.typography.headlineSmall)
                Text(d.archetype, style = MaterialTheme.typography.bodyMedium, color = Brand.Accent)
                if (avg != null) {
                    val diff = score.overall - avg.overall
                    Text(
                        (if (diff > 0) "+$diff" else "$diff") + " frente a tu promedio (${avg.overall})",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (diff >= 2) Brand.Good else if (diff <= -2) Brand.Bad else Brand.Muted,
                    )
                } else {
                    Text("Índice de rendimiento físico (0-99)", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 14.dp), color = Brand.Outline)
        AttributeBars(score, compare = avg)
        if (avg != null) Text("La marca blanca es tu promedio en los demás partidos.", style = MaterialTheme.typography.labelSmall, color = Brand.Muted, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onShare, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Compartir imagen", fontWeight = FontWeight.Bold)
            }
            if (others.isNotEmpty()) OutlinedButton(onClick = onCompare, modifier = Modifier.weight(1f)) { Text("Comparar") }
        }
    }

    if (insights.isNotEmpty()) {
        SectionTitle("Frente a tus otros partidos")
        Panel { insights.forEach { InsightRow(it) } }
    }

    SectionTitle("Datos clave")
    StatRow(
        Triple("Distancia", p.totalDistanceM.km(), "${p.metersPerMinute.fmt(0)} m/min"),
        Triple("Sprints", "${p.sprints.size}", "máx ${p.maxSpeedKmh.fmt(1)} km/h"),
        Triple("Energía final", r.energy?.let { "${it.finalEnergy.fmt(0)} %" } ?: "—", r.energy?.let { "mín ${it.minEnergy.fmt(0)} % (${it.minEnergyMinute}')" }),
    )
    Panel(Modifier.padding(top = 8.dp)) {
        Overline("Rol táctico", color = Brand.Accent)
        Text(d.archetype, style = MaterialTheme.typography.titleLarge)
        Text(d.summary.removePrefix("Rol: ${d.archetype}. "), style = MaterialTheme.typography.bodyMedium, color = Brand.Text.copy(alpha = 0.85f))
    }
    LogCard(log, onLog)
    r.energy?.let { e ->
        SectionTitle("Energía durante el partido")
        Panel {
            val firstHalf = r.periods.takeIf { it.size > 1 }?.first()?.let { (it.durationMs / 60000).toInt() }
            EnergyChart(e.energyPerMinute, dividerAtMinute = firstHalf, markers = r.alerts.filter { it.level != AlertLevel.INFO }.map { it.minute })
        }
        Text(
            "Indicador integral (0-100) que combina tu reserva anaeróbica (sprints y aceleraciones, se recupera al bajar el ritmo), " +
                "el desgaste acumulado del partido" + (if (e.cardioPerMinute != null) " y la exigencia cardiaca" else "") +
                ". Los puntos rojos son alertas.",
            style = MaterialTheme.typography.bodySmall, color = Brand.Muted, modifier = Modifier.padding(top = 8.dp),
        )
    }
    SectionTitle("Alertas físicas (${r.alerts.size})")
    AlertTimeline(r.alerts) { clock.labelForMinute(it) }
}

/** Este partido frente a tu promedio o frente a otro partido concreto. */
@Composable
private fun CompareTab(record: MatchRecord, matches: List<MatchSummary>) {
    val self = matches.firstOrNull { it.id == record.id }
    val others = remember(matches, record.id) { matches.filter { it.id != record.id && it.kpis != null } }
    if (self == null || others.isEmpty()) {
        Panel {
            Text("Aún no hay con qué comparar", style = MaterialTheme.typography.titleMedium)
            Text(
                "Cuando agregues otro partido verás aquí, métrica por métrica, si rendiste por encima o por debajo de tu nivel habitual.",
                style = MaterialTheme.typography.bodySmall, color = Brand.Muted,
            )
        }
        return
    }
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var otherId by rememberSaveable { mutableStateOf(others.first().id) }
    var per90 by rememberSaveable { mutableStateOf(true) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(mode == 0, { mode = 0 }, label = { Text("Contra mi promedio") })
        FilterChip(mode == 1, { mode = 1 }, label = { Text("Contra otro partido") })
    }
    if (mode == 1) {
        val other = others.firstOrNull { it.id == otherId } ?: others.first()
        MatchPicker("Comparar con", other, others, Modifier.padding(top = 8.dp), accent = false) { otherId = it }
        VersusPanel(self, other, per90, onPer90 = { per90 = it }, rightIsReference = false)
    } else {
        VersusPanel(self, null, per90, onPer90 = { per90 = it }, rightIsReference = true, reference = others)
    }
}

@Composable
private fun PhysicalTab(r: MatchReport) {
    val p = r.physical
    StatRow(
        Triple("Distancia", p.totalDistanceM.km(), "${p.metersPerMinute.fmt(0)} m/min"),
        Triple("Tiempo de juego", p.playingTimeS.minutes(), r.periods.joinToString(" + ") { "${it.durationMs / 60000}'" }),
    )
    StatRow(
        Triple("Vel. máxima", "${p.maxSpeedKmh.fmt(1)} km/h", null),
        Triple("Sprints", "${p.sprints.size}", "${p.sprintDistanceM.fmt(0)} m"),
        Triple("Alta int.", "${p.highSpeedDistanceM.fmt(0)} m", "${p.highIntensityRuns.size} carreras"),
    )
    StatRow(
        Triple("Aceleraciones", "${p.accelerations}", "> 2 m/s²"),
        Triple("Frenadas", "${p.decelerations}", "< -2 m/s²"),
        Triple("Fatiga", p.fatigueIndexPct?.let { "${if (it > 0) "+" else ""}${it.fmt(0)} %" } ?: "—", "m/min último vs primer bloque"),
    )

    SectionTitle("Distancia por zona de velocidad")
    val maxZ = p.distanceByZoneM.max().coerceAtLeast(1.0)
    val zones = p.zonesUsed
    Text(
        "Umbrales: ${p.zoneMode.label}" + if (p.zoneMode == ZoneMode.ADAPTIVE) " (tu velocidad máx. de referencia: ${p.referenceMaxSpeedKmh.fmt(1)} km/h)" else "",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val bounds = listOf("< ${zones.joggingKmh}", "${zones.joggingKmh}-${zones.runningKmh}", "${zones.runningKmh}-${zones.highSpeedKmh}", "${zones.highSpeedKmh}-${zones.sprintKmh}", "> ${zones.sprintKmh}")
    p.zoneLabels.forEachIndexed { i, label ->
        LabeledBar("$label\n${bounds[i]} km/h", (p.distanceByZoneM[i] / maxZ).toFloat(), "${p.distanceByZoneM[i].fmt(0)} m", zoneColors[i])
    }

    SectionTitle("Metros por minuto")
    val firstHalfMinutes = r.periods.firstOrNull()?.takeIf { r.periods.size > 1 }?.let { (it.durationMs / 60000).toInt() }
    BarChart(p.distancePerMinute, MaterialTheme.colorScheme.primary, dividerAt = firstHalfMinutes)
    Text("La línea vertical marca el descanso.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    SectionTitle("Por bloques de ${r.options.physical.splitMinutes} minutos")
    val maxSplit = p.splits.maxOfOrNull { it.distanceM }?.coerceAtLeast(1.0) ?: 1.0
    p.splits.forEach { s ->
        LabeledBar(
            s.label,
            (s.distanceM / maxSplit).toFloat(),
            "${s.distanceM.fmt(0)} m" + (s.avgHeartRate?.let { "\n${it.fmt(0)} lpm" } ?: ""),
            MaterialTheme.colorScheme.tertiary,
        )
    }
    AdvancedSection(r)
}

@Composable
private fun AdvancedSection(r: MatchReport) {
    val a = r.advanced ?: return
    val e = r.energy
    SectionTitle("Picos de demanda (los pasajes más exigentes)")
    a.peakDemands.forEach { pd ->
        val maxPeak = a.peakDemands.maxOf { it.metersPerMinute }.coerceAtLeast(1.0)
        LabeledBar("Mejor ${pd.windowMinutes}' (desde ${pd.startMinute}')", (pd.metersPerMinute / maxPeak).toFloat(), "${pd.metersPerMinute.fmt(0)} m/min", MaterialTheme.colorScheme.secondary)
    }
    Text("Úsalos como referencia para entrenar: un buen entrenamiento debería reproducir estos picos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SectionTitle("Más métricas")
    StatRow(
        Triple("Esfuerzos repetidos", "${a.repeatedEfforts.size}", "bloques de ≥3 carreras con < 21 s de pausa"),
        Triple("Trabajo : pausa", if (a.workRestRatio > 0) "1 : ${(1 / a.workRestRatio).fmt(1)}" else "—", "tiempo ≥ carrera vs. por debajo"),
    )
    if (e != null) {
        StatRow(
            Triple("Potencia metabólica", "${e.avgMetabolicPowerWkg.fmt(1)} W/kg", "media del partido"),
            Triple("HMLD", "${e.hmldM.fmt(0)} m", "distancia con > 25,5 W/kg"),
            Triple("Gasto", e.kcal?.let { "${it.fmt(0)} kcal" } ?: "${e.energyKjPerKg.fmt(0)} kJ/kg", if (e.kcal == null) "indica tu peso para kcal" else "${e.energyKjPerKg.fmt(0)} kJ/kg"),
        )
    }
    if (a.distanceByThirdM.size == 3) {
        StatRow(
            Triple("Dist. tercio def.", "${a.distanceByThirdM[0].fmt(0)} m", null),
            Triple("Tercio medio", "${a.distanceByThirdM[1].fmt(0)} m", null),
            Triple("Tercio ofensivo", "${a.distanceByThirdM[2].fmt(0)} m", "${a.sprintsEndingInFinalThird} sprints terminan aquí"),
        )
    }
}

@Composable
private fun HeatmapTab(r: MatchReport, onCalibrate: () -> Unit, onShare: () -> Unit) {
    var period by rememberSaveable { mutableIntStateOf(0) } // 0 = todo
    val calibrated = r.calibration.method == CalibrationMethod.CORNERS
    var satelliteOn by rememberSaveable { mutableStateOf(calibrated) }
    val sat = rememberSatellite(r, satelliteOn)
    val t = r.tactical
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!satelliteOn, { satelliteOn = false }, label = { Text("Cancha") })
        FilterChip(satelliteOn, { satelliteOn = true }, label = { Text("Satélite") })
        if (r.periods.size > 1) {
            Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(32.dp).background(Brand.Outline))
            FilterChip(period == 0, { period = 0 }, label = { Text("Partido") })
            r.periods.forEach { p -> FilterChip(period == p.index, { period = p.index }, label = { Text("${p.index}º tiempo") }) }
        }
    }
    val hm = if (period == 0) t.heatmap else t.heatmapByPeriod[period] ?: t.heatmap
    val image = (sat as? SatelliteState.Ready)?.image
    Box {
        PitchCanvas(r.calibration.dims, heatmap = hm, satellite = image, showLines = image == null || calibrated) { m ->
            val mode = if (period == 0) t.modePosition else t.modePositionByPeriod[period] ?: t.modePosition
            drawCircle(Color.White, radius = 14f, center = m.offset(mode))
            drawCircle(Color.Black, radius = 14f, center = m.offset(mode), style = Stroke(3f))
            if (period == 0) t.hotspots.drop(1).forEach { h ->
                drawCircle(Color.White, radius = 9f, center = m.offset(h.point), style = Stroke(3f))
            }
        }
        if (sat is SatelliteState.Loading) {
            Row(
                Modifier.align(Alignment.Center).background(Brand.Bg.copy(alpha = 0.8f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Descargando vista satelital…", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Ataque →", style = MaterialTheme.typography.labelLarge, color = Brand.Accent, modifier = Modifier.weight(1f))
        if (image != null) Text(SatelliteStore.ATTRIBUTION, style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
    }
    if (sat is SatelliteState.Failed) {
        Text(
            "No se pudo descargar la vista satelital. Revisa tu conexión y vuelve a tocar Satélite; después queda guardada y funciona sin internet.",
            style = MaterialTheme.typography.bodySmall, color = Brand.Warn, modifier = Modifier.padding(top = 6.dp),
        )
    }
    if (satelliteOn && !calibrated) {
        Panel(Modifier.padding(top = 10.dp), padding = 12.dp) {
            Text("Para que el mapa encaje exacto sobre la foto", style = MaterialTheme.typography.titleSmall)
            Text(
                "La cancha se ubicó automáticamente a partir de tu recorrido, así que la foto muestra la zona que recorriste, no los límites reales. " +
                    "Marca tres esquinas una sola vez y quedará alineada (se guarda para los próximos partidos en ese lugar).",
                style = MaterialTheme.typography.bodySmall, color = Brand.Muted,
            )
        }
    }
    Text(
        "Rojo = donde más tiempo pasaste. El círculo blanco es tu zona más frecuente; los anillos, otras zonas calientes. " +
            "El segundo tiempo se gira para que ambos tiempos ataquen hacia la derecha.",
        style = MaterialTheme.typography.bodySmall, color = Brand.Muted,
        modifier = Modifier.padding(top = 10.dp),
    )
    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onCalibrate, modifier = Modifier.weight(1f)) {
            Text(if (calibrated) "Cancha calibrada ✓" else "Calibrar cancha")
        }
        Button(onClick = onShare, modifier = Modifier.weight(1f)) { Text("Compartir", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun AttackHint() {
    Text("Ataque →", style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), color = Brand.Accent)
}

@Composable
private fun TacticalTab(r: MatchReport) {
    val t = r.tactical
    Panel(Modifier.padding(vertical = 6.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Overline("Rol táctico", color = Brand.Accent)
            Text(t.description.archetype, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(t.description.summary.removePrefix("Rol: ${t.description.archetype}. "), style = MaterialTheme.typography.bodyMedium)
            t.description.observations.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
    StatRow(
        Triple("Posición estimada", t.estimatedRole, "según tu zona más frecuente"),
        Triple("Área de influencia", "${t.ellipse.areaM2.fmt(0)} m²", "95% del tiempo"),
    )

    SectionTitle("Ocupación por zonas")
    PitchCanvas(r.calibration.dims) { m ->
        val cw = size.width / 3; val ch = size.height / 3
        val maxPct = t.zoneTimePct.max().coerceAtLeast(1.0)
        for (third in 0..2) for (lane in 0..2) {
            val pct = t.zoneTimePct[third * 3 + lane]
            // carril 0 = derecha = abajo en pantalla.
            val top = (2 - lane) * ch
            drawRect(Brand.Accent.copy(alpha = (pct / maxPct * 0.55).toFloat()), Offset(third * cw, top), Size(cw, ch))
        }
        for (i in 1..2) {
            drawLine(Color.White.copy(alpha = 0.5f), Offset(i * cw, 0f), Offset(i * cw, size.height), strokeWidth = 2f)
            drawLine(Color.White.copy(alpha = 0.5f), Offset(0f, i * ch), Offset(size.width, i * ch), strokeWidth = 2f)
        }
        // Elipse de dispersión.
        val e = t.ellipse
        val c = m.offset(e.center)
        rotate(-e.angleDeg.toFloat(), pivot = c) {
            drawOval(
                Color.White,
                topLeft = Offset(c.x - m.scale(e.semiMajorM), c.y - m.scale(e.semiMinorM)),
                size = Size(2 * m.scale(e.semiMajorM), 2 * m.scale(e.semiMinorM)),
                style = Stroke(3f),
            )
        }
        t.modePositionByPeriod.forEach { (period, p) ->
            drawCircle(if (period == 1) Color(0xFF42A5F5) else Color(0xFFFFCA28), radius = 11f, center = m.offset(p))
        }
    }
    AttackHint()
    val pct = { v: Double -> "${v.fmt(0)} %" }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Column(Modifier.weight(1f)) {
            TacticalReport.THIRD_LABELS.forEachIndexed { i, l -> Text("Tercio ${l.lowercase()}: ${pct(t.thirdsPct[i])}", style = MaterialTheme.typography.bodySmall) }
        }
        Column(Modifier.weight(1f)) {
            TacticalReport.LANE_LABELS.forEachIndexed { i, l -> Text("Carril ${l.lowercase()}: ${pct(t.lanesPct[i])}", style = MaterialTheme.typography.bodySmall) }
        }
    }
    Text("Área rival: ${pct(t.penaltyBoxPct)} · Área propia: ${pct(t.ownBoxPct)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    if (t.averagePositionByPeriod.size > 1) {
        Text("● azul: zona más frecuente 1er tiempo · ● amarillo: 2º tiempo · óvalo: área de influencia", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    SectionTitle("Mapa de sprints (${t.sprintVectors.size})")
    PitchCanvas(r.calibration.dims) { m ->
        t.sprintVectors.forEach { v ->
            val color = when (v.direction) {
                Direction.FORWARD -> Color(0xFFFFEB3B)
                Direction.BACKWARD -> Color(0xFF64B5F6)
                Direction.LATERAL -> Color.White
            }
            arrow(m.offset(v.start), m.offset(v.end), color)
        }
    }
    AttackHint()
    val byDir = t.sprintVectors.groupingBy { it.direction }.eachCount()
    Text(
        Direction.entries.joinToString(" · ") { "${it.label}: ${byDir[it] ?: 0}" } + "\nAmarillo: hacia adelante · Azul: hacia atrás · Blanco: lateral",
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun PhysiologyTab(r: MatchReport) {
    val h = r.heartRate
    if (h == null) {
        Text(
            "Este partido no tiene frecuencia cardiaca. Exporta el GPX con FC desde Zepp/Strava o concede a la app " +
                "acceso a Health Connect antes de importar.",
            modifier = Modifier.padding(vertical = 12.dp),
        )
    } else {
        StatRow(
            Triple("FC media", "${h.avgBpm.fmt(0)} lpm", "${(h.avgBpm / h.maxHrUsed * 100).fmt(0)} % FCmáx"),
            Triple("FC máxima", "${h.maxBpm} lpm", "FCmáx usada: ${h.maxHrUsed}"),
        )
        StatRow(
            Triple("TRIMP", h.edwardsTrimp.fmt(0), "carga (Edwards)"),
            Triple("> 85% FCmáx", h.timeAbove85PctS.minutes(), "fuente: ${r.heartRateSource ?: "—"}"),
        )
        SectionTitle("Tiempo en zonas de FC")
        val maxT = h.timeInZoneS.max().coerceAtLeast(1.0)
        h.zoneLabels.forEachIndexed { i, l -> LabeledBar(l, (h.timeInZoneS[i] / maxT).toFloat(), h.timeInZoneS[i].minutes(), zoneColors[i]) }
        SectionTitle("FC por minuto")
        val maxHr = h.maxHrUsed.toDouble()
        LineChart(
            h.bpmPerMinute, Brand.Bad,
            minY = maxHr * 0.4, maxY = maxHr,
            bands = listOf(0.6, 0.7, 0.8, 0.9, 1.0).mapIndexed { i, f -> maxHr * f to zoneColors[i] },
        )
    }

    r.advanced?.let { a ->
        if (a.efficiencyByPeriod.any { it.metersPerBeat != null }) {
            SectionTitle("Eficiencia cardiaca")
            a.efficiencyByPeriod.forEach { pe ->
                Text("${pe.period}º tiempo: ${pe.metersPerMinute.fmt(0)} m/min con ${pe.avgHr?.fmt(0) ?: "—"} lpm → ${pe.metersPerBeat?.fmt(2) ?: "—"} m por latido", style = MaterialTheme.typography.bodySmall)
            }
            a.decouplingPct?.let { Text("Desacople: ${it.fmt(1)} % (negativo = te costó más cada metro al final)", style = MaterialTheme.typography.bodySmall) }
        }
        SectionTitle("Carga percibida (sRPE)")
        Text(
            a.sessionRpeLoad?.let { "Carga: ${it.fmt(0)} UA (RPE ${r.options.rpe} × ${(r.physical.playingTimeS / 60).fmt(0)} min)" }
                ?: "Indica tu esfuerzo percibido (0-10) en el registro del partido (pestaña Resumen) para calcular la carga de sesión.",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    SectionTitle("Estado el día del partido")
    val w = r.wellness
    if (w == null) {
        Text("Sin datos de Health Connect para ese día.", style = MaterialTheme.typography.bodySmall)
    } else {
        StatRow(
            Triple("HRV", w.hrvRmssdMs?.let { "${it.fmt(0)} ms" } ?: "—", w.readiness?.label),
            Triple("FC reposo", w.restingHr?.let { "$it lpm" } ?: "—", null),
        )
        StatRow(
            Triple("SpO2", w.spo2Pct?.let { "${it.fmt(0)} %" } ?: "—", null),
            Triple("VO2 máx", w.vo2Max?.fmt(1) ?: "—", "ml/kg/min"),
            Triple("Pasos del día", w.stepsToday?.toString() ?: "—", null),
        )
    }
}

@Composable
private fun SettingsDialog(
    record: MatchRecord,
    onDismiss: () -> Unit,
    onOptions: ((AnalysisOptions) -> AnalysisOptions) -> Unit,
    onDelete: () -> Unit,
    onTrim: () -> Unit,
    onCalibrate: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val opts = record.options
    val flipped = record.report.tactical.attackFlipped
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajustes del análisis") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Para cambiar el nombre del partido, toca el título en la parte superior.", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onTrim, modifier = Modifier.weight(1f)) {
                        Text(if (opts.trimStartMs != null || opts.trimEndMs != null) "Recorte ✓" else "Recortar")
                    }
                    OutlinedButton(onClick = onCalibrate, modifier = Modifier.weight(1f)) { Text("Cancha") }
                }
                Text("Umbrales de velocidad", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ZoneMode.entries.forEach { zm ->
                        FilterChip(opts.physical.zoneMode == zm, { onOptions { it.copy(physical = it.physical.copy(zoneMode = zm)) } }, label = { Text(zm.label) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Invertir dirección de ataque")
                        Text(if (opts.attackFlipped == null) "Ahora: automática" else "Ahora: manual", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = opts.attackFlipped != null, onCheckedChange = { on ->
                        onOptions { it.copy(attackFlipped = if (on) !flipped else null) }
                    })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Sin descanso (un solo periodo)")
                        Text("Para entrenamientos o partidos sin medio tiempo", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = opts.singlePeriod, onCheckedChange = { on -> onOptions { it.copy(singlePeriod = on) } })
                }
                if (confirmDelete) {
                    Text("¿Seguro? Se borrará el partido de este teléfono.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Listo") }
        },
        dismissButton = {
            TextButton(onClick = { if (confirmDelete) onDelete() else confirmDelete = true }) {
                Text(if (confirmDelete) "Sí, borrar" else "Borrar partido", color = MaterialTheme.colorScheme.error)
            }
        },
    )
}
