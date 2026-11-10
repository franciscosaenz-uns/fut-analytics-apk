package com.futbolanalisis.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.futbolanalisis.app.UiState
import com.futbolanalisis.app.data.MatchSummary
import com.futbolanalisis.app.ui.components.BigStat
import com.futbolanalisis.app.ui.components.InsightRow
import com.futbolanalisis.app.ui.components.OutcomeBadge
import com.futbolanalisis.app.ui.components.Overline
import com.futbolanalisis.app.ui.components.Panel
import com.futbolanalisis.app.ui.components.Pill
import com.futbolanalisis.app.ui.components.PitchCanvas
import com.futbolanalisis.app.ui.components.RadarChart
import com.futbolanalisis.app.ui.components.ScoreRing
import com.futbolanalisis.app.ui.components.SectionHeader
import com.futbolanalisis.app.ui.components.TrendChart
import com.futbolanalisis.app.ui.components.color
import com.futbolanalisis.app.ui.components.deltaText
import com.futbolanalisis.app.ui.dateOnly
import com.futbolanalisis.app.ui.fmt
import com.futbolanalisis.app.ui.shortDate
import com.futbolanalisis.app.ui.theme.Brand
import com.futbolanalisis.app.ui.theme.numberStyle
import com.futbolanalisis.core.insights.MatchComparator
import com.futbolanalisis.core.insights.Metric
import com.futbolanalisis.core.insights.PerformanceScore
import com.futbolanalisis.core.insights.Verdict
import com.futbolanalisis.core.pitch.PitchDimensions

@Composable
fun HomeTab(
    state: UiState,
    onAdd: () -> Unit,
    onSample: () -> Unit,
    onOpen: (String) -> Unit,
    onShare: (String) -> Unit,
    onProgress: () -> Unit,
    onMatches: () -> Unit,
) {
    val matches = state.matches
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Greeting(state.profile.name, matches.size)
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 12.dp))
        when {
            !state.loaded -> Unit
            matches.isEmpty() -> EmptyHome(onAdd, onSample)
            else -> {
                OverallPanel(matches)
                SectionHeader("Último partido", action = if (matches.size > 1) "Ver todos" else null, onAction = onMatches)
                LastMatchPanel(matches.first(), matches.drop(1), onOpen, onShare)
                if (matches.count { it.score != null } >= 2) {
                    SectionHeader("Evolución del índice", action = "Comparar", onAction = onProgress)
                    EvolutionPanel(matches)
                }
                RecordsRow(matches, onOpen)
                Spacer(Modifier.height(104.dp))
            }
        }
    }
}

@Composable
private fun Greeting(name: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Overline("Fútbol Análisis", color = Brand.Accent)
            Text(if (name.isBlank() || name == "Jugador") "Tu rendimiento" else "Hola, $name", style = MaterialTheme.typography.headlineMedium)
        }
        Box(Modifier.size(44.dp).background(Brand.SurfaceHigh, CircleShape), contentAlignment = Alignment.Center) {
            Text((name.trim().firstOrNull() ?: 'J').uppercase(), style = numberStyle(20), color = Brand.Accent)
        }
    }
}

@Composable
private fun EmptyHome(onAdd: () -> Unit, onSample: () -> Unit) {
    Panel(color = Brand.Surface, padding = 0.dp) {
        Column(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Brand.Turf, Brand.Surface))).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Overline("Empieza aquí", color = Brand.Accent)
            Text("Tu partido, analizado como el de un profesional", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Mapa de calor sobre la cancha real, índice de rendimiento, sprints, energía y comparación entre partidos con los datos de tu reloj.",
                style = MaterialTheme.typography.bodyMedium, color = Brand.Text.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(6.dp))
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("Agregar partido", fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onSample, modifier = Modifier.fillMaxWidth()) { Text("Probar con un partido de ejemplo") }
        }
    }
    SectionHeader("Cómo traer tu partido desde Zepp")
    Panel {
        HowToSteps()
    }
    Spacer(Modifier.height(32.dp))
}

@Composable
fun HowToSteps() {
    listOf(
        "Abre Zepp y entra al entrenamiento de tu partido.",
        "Toca Compartir (o ⋯ → Exportar) y elige el archivo GPX.",
        "En la lista de apps elige Fútbol Análisis. Completa equipo, rival y fecha, y listo.",
    ).forEachIndexed { i, text ->
        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(26.dp).background(Brand.Accent, CircleShape), contentAlignment = Alignment.Center) {
                Text("${i + 1}", color = Brand.OnAccent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

/** Resumen de rendimiento general: índice medio reciente, atributos y totales. */
@Composable
private fun OverallPanel(matches: List<MatchSummary>) {
    val scored = remember(matches) { matches.filter { it.score != null } }
    val recent = scored.take(5)
    val avg = remember(matches) { PerformanceScore.average(recent.mapNotNull { it.score }) }
    val form = remember(matches) { MatchComparator.form(scored.reversed().map { it.score!!.overall.toDouble() }) }
    Panel {
        Overline("Rendimiento general")
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(avg?.overall, size = 104.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(avg?.level ?: "Sin datos", style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (recent.size == 1) "Índice de tu primer partido" else "Promedio de tus últimos ${recent.size} partidos",
                    style = MaterialTheme.typography.bodySmall, color = Brand.Muted,
                )
                val formDelta = form?.delta
                if (form != null && formDelta != null) {
                    Spacer(Modifier.height(8.dp))
                    val text = when (form.verdict) {
                        Verdict.BETTER -> "▲ ${formDelta.fmt(1)} pts · en alza"
                        Verdict.WORSE -> "▼ ${(-formDelta).fmt(1)} pts · a la baja"
                        else -> "Forma estable"
                    }
                    Pill(text, form.verdict.color())
                }
            }
        }
        if (avg != null) {
            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = Brand.Outline)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadarChart(avg, size = 176.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigStat("Partidos", "${matches.size}")
                    BigStat("Minutos", (matches.sumOf { it.playingTimeS } / 60).fmt(0))
                    BigStat("Distancia", (matches.sumOf { it.distanceM } / 1000).fmt(1), unit = "km")
                }
            }
        }
        val goals = matches.sumOf { it.goals }; val assists = matches.sumOf { it.assists }
        val w = matches.count { it.outcome == 'V' }; val d = matches.count { it.outcome == 'E' }; val l = matches.count { it.outcome == 'D' }
        if (goals + assists + w + d + l > 0) {
            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = Brand.Outline)
            Row(Modifier.fillMaxWidth()) {
                BigStat("Goles", "$goals", Modifier.weight(1f))
                BigStat("Asistencias", "$assists", Modifier.weight(1f))
                BigStat("V · E · D", "$w · $d · $l", Modifier.weight(1.3f))
            }
        }
    }
}

@Composable
private fun LastMatchPanel(m: MatchSummary, others: List<MatchSummary>, onOpen: (String) -> Unit, onShare: (String) -> Unit) {
    val k = m.kpis
    val history = remember(others) { others.mapNotNull { it.kpis } }
    val cmp = remember(m, others) {
        if (k == null) emptyMap() else MatchComparator.vsAverage(k, history, per90 = false, metrics = listOf(Metric.PACE, Metric.MAX_SPEED)).associateBy { it.metric }
    }
    val insights = remember(m, others) { if (k == null) emptyList() else MatchComparator.insights(k, history, max = 2) }
    Panel(onClick = { onOpen(m.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Overline(m.startMs.dateOnly())
                Text(m.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    m.outcome?.let { OutcomeBadge(it); Spacer(Modifier.width(8.dp)) }
                    Text(
                        listOfNotNull(m.result, m.archetype.ifBlank { m.role }.takeIf { it.isNotBlank() }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = Brand.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            ScoreRing(m.score?.overall, size = 72.dp)
        }
        Spacer(Modifier.height(14.dp))
        PitchCanvas(m.heatmap?.dims ?: PitchDimensions(), heatmap = m.heatmap)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            BigStat("Distancia", (m.distanceM / 1000).fmt(2), Modifier.weight(1f), unit = "km", hint = "${(m.playingTimeS / 60).fmt(0)} min")
            BigStat(
                "Ritmo", k?.metersPerMinute?.fmt(0) ?: "—", Modifier.weight(1f), unit = "m/min",
                hint = cmp[Metric.PACE]?.deltaText(), hintColor = cmp[Metric.PACE]?.verdict?.color() ?: Brand.Muted,
            )
            BigStat(
                "Vel. máx", m.maxSpeedKmh.fmt(1), Modifier.weight(1f), unit = "km/h",
                hint = cmp[Metric.MAX_SPEED]?.deltaText(), hintColor = cmp[Metric.MAX_SPEED]?.verdict?.color() ?: Brand.Muted,
            )
        }
        if (insights.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Brand.Outline)
            Overline("Frente a tus otros partidos")
            insights.forEach { InsightRow(it) }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { onOpen(m.id) }, modifier = Modifier.weight(1f)) { Text("Ver análisis", fontWeight = FontWeight.Bold) }
            OutlinedButton(onClick = { onShare(m.id) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Compartir")
            }
        }
    }
}

@Composable
private fun EvolutionPanel(matches: List<MatchSummary>) {
    val chrono = remember(matches) { matches.filter { it.score != null }.sortedBy { it.startMs }.takeLast(12) }
    Panel {
        TrendChart(
            values = chrono.map { it.score?.overall?.toDouble() },
            labels = chrono.map { it.startMs.shortDate() },
            highlight = chrono.lastIndex,
        )
        Text("La línea discontinua es tu promedio.", style = MaterialTheme.typography.labelSmall, color = Brand.Muted, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun RecordsRow(matches: List<MatchSummary>, onOpen: (String) -> Unit) {
    val records = remember(matches) { MatchComparator.records(matches) { it.kpis } }
    if (records.isEmpty() || matches.size < 2) return
    SectionHeader("Récords personales")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        records.forEach { r ->
            Panel(Modifier.width(150.dp), onClick = { onOpen(r.holder.id) }, padding = 14.dp) {
                Overline("★ ${r.metric.short}", color = Brand.Accent)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(MatchComparator.format(r.value, r.metric.decimals), style = numberStyle(26))
                    if (r.metric.unit.isNotEmpty()) Text(" ${r.metric.unit}", style = MaterialTheme.typography.labelMedium, color = Brand.Muted, modifier = Modifier.padding(bottom = 3.dp))
                }
                Text(r.holder.startMs.shortDate() + " · " + r.holder.title, style = MaterialTheme.typography.labelSmall, color = Brand.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Lista de todos los partidos. */
@Composable
fun MatchesTab(state: UiState, onAdd: () -> Unit, onOpen: (String) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Overline("Historial", color = Brand.Accent)
            Text("Partidos", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 8.dp))
        }
        if (state.matches.isEmpty() && state.loaded) {
            item {
                Panel {
                    Text("Todavía no hay partidos", style = MaterialTheme.typography.titleMedium)
                    Text("Agrega el primero para ver tu mapa de calor y tu índice de rendimiento.", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onAdd) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("Agregar partido") }
                }
            }
        }
        items(state.matches, key = { it.id }) { m -> MatchRow(m) { onOpen(m.id) } }
    }
}

@Composable
fun MatchRow(m: MatchSummary, onClick: () -> Unit) {
    Panel(onClick = onClick, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(104.dp)) { PitchCanvas(m.heatmap?.dims ?: PitchDimensions(), heatmap = m.heatmap, corner = 10.dp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    m.outcome?.let { OutcomeBadge(it); Spacer(Modifier.width(6.dp)) }
                    Text(
                        listOfNotNull(m.startMs.shortDate(), m.result?.substringAfter(' ')).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = Brand.Muted, maxLines = 1,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${(m.distanceM / 1000).fmt(2)} km · ${(m.playingTimeS / 60).fmt(0)} min · ${m.maxSpeedKmh.fmt(1)} km/h",
                    style = MaterialTheme.typography.labelMedium, color = Brand.Text.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (m.goals + m.assists > 0) Text("${m.goals} goles · ${m.assists} asistencias", style = MaterialTheme.typography.labelSmall, color = Brand.Accent)
            }
            Spacer(Modifier.width(8.dp))
            ScoreRing(m.score?.overall, size = 50.dp)
        }
    }
}
