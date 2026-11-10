package com.futbolanalisis.app.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.futbolanalisis.app.UiState
import com.futbolanalisis.app.data.MatchSummary
import com.futbolanalisis.app.ui.components.AttributeBars
import com.futbolanalisis.app.ui.components.BigStat
import com.futbolanalisis.app.ui.components.DeltaBadge
import com.futbolanalisis.app.ui.components.Overline
import com.futbolanalisis.app.ui.components.Panel
import com.futbolanalisis.app.ui.components.RadarChart
import com.futbolanalisis.app.ui.components.ScoreRing
import com.futbolanalisis.app.ui.components.SectionHeader
import com.futbolanalisis.app.ui.components.TrendChart
import com.futbolanalisis.app.ui.components.VersusRow
import com.futbolanalisis.app.ui.shortDate
import com.futbolanalisis.app.ui.theme.Brand
import com.futbolanalisis.app.ui.theme.numberStyle
import com.futbolanalisis.core.insights.MatchComparator
import com.futbolanalisis.core.insights.Metric
import com.futbolanalisis.core.insights.MetricComparison
import com.futbolanalisis.core.insights.PerformanceScore

/** Progreso: evolución de cada métrica y comparación directa entre dos partidos. */
@Composable
fun ProgressTab(state: UiState, onOpen: (String) -> Unit) {
    val matches = remember(state.matches) { state.matches.filter { it.kpis != null && it.score != null } }
    var mode by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Overline("Comparación entre partidos", color = Brand.Accent, modifier = Modifier.padding(top = 18.dp))
        Text("Progreso", style = MaterialTheme.typography.headlineMedium)
        if (matches.size < 2) {
            Spacer(Modifier.height(16.dp))
            Panel {
                Text("Necesitas al menos dos partidos", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Con el segundo partido verás aquí cómo evoluciona cada métrica, tus récords personales y la comparación cara a cara.",
                    style = MaterialTheme.typography.bodySmall, color = Brand.Muted,
                )
            }
            return@Column
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(mode == 0, { mode = 0 }, label = { Text("Evolución") })
            FilterChip(mode == 1, { mode = 1 }, label = { Text("Cara a cara") })
        }
        if (mode == 0) Evolution(matches, onOpen) else HeadToHead(matches)
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun Evolution(matches: List<MatchSummary>, onOpen: (String) -> Unit) {
    // 0 = índice de rendimiento; el resto son métricas.
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var per90 by rememberSaveable { mutableStateOf(true) }
    val metric = Metric.entries.getOrNull(selected - 1)
    val chrono = remember(matches) { matches.sortedBy { it.startMs } }
    fun valueOf(m: MatchSummary): Double? = if (metric == null) m.score?.overall?.toDouble() else m.kpis?.let { metric.value(it, per90) }

    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected == 0, { selected = 0 }, label = { Text("Índice") })
        Metric.entries.forEachIndexed { i, m ->
            if (chrono.any { it.kpis?.let(m.get) != null }) FilterChip(selected == i + 1, { selected = i + 1 }, label = { Text(m.short) })
        }
    }

    val values = chrono.map { valueOf(it) }
    val present = values.filterNotNull()
    val decimals = metric?.decimals ?: 0
    val unit = metric?.unit.orEmpty()
    Panel(Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(metric?.label ?: "Índice de rendimiento", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (metric?.volume == true && per90) "Normalizado a 90 minutos" else if (metric?.volume == true) "Total del partido" else "Por partido",
                    style = MaterialTheme.typography.bodySmall, color = Brand.Muted,
                )
            }
            if (metric?.volume == true) FilterChip(per90, { per90 = !per90 }, label = { Text("Por 90'") })
        }
        Spacer(Modifier.height(12.dp))
        TrendChart(values, labels = chrono.map { it.startMs.shortDate() }, highlight = values.indexOfLast { it != null })
        if (present.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Brand.Outline)
            Row(Modifier.fillMaxWidth()) {
                BigStat("Promedio", MatchComparator.format(present.average(), decimals), Modifier.weight(1f), unit = unit)
                BigStat("Mejor", MatchComparator.format(present.max(), decimals), Modifier.weight(1f), unit = unit)
                BigStat("Último", MatchComparator.format(present.last(), decimals), Modifier.weight(1f), unit = unit)
            }
        }
    }

    SectionHeader("Partido a partido")
    Panel(padding = 4.dp) {
        chrono.reversed().forEachIndexed { i, m ->
            val v = valueOf(m)
            val others = chrono.filter { it.id != m.id }.mapNotNull { valueOf(it) }
            val cmp: MetricComparison? = if (v != null && others.isNotEmpty()) {
                if (metric != null) MatchComparator.compare(metric, v, others.average(), isRecord = v > others.max() && !metric.isDelta)
                else MatchComparator.compare(Metric.PACE, v, others.average(), isRecord = v > others.max())
            } else null
            if (i > 0) HorizontalDivider(color = Brand.Outline)
            Row(Modifier.fillMaxWidth().clickable { onOpen(m.id) }.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(m.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(m.startMs.shortDate(), style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
                }
                cmp?.let { DeltaBadge(it) }
                Text(
                    v?.let { MatchComparator.format(it, decimals) } ?: "—",
                    style = numberStyle(20), modifier = Modifier.width(72.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        }
    }

    val avg = remember(matches) { PerformanceScore.average(matches.mapNotNull { it.score }) }
    val last = chrono.last().score
    if (avg != null && last != null) {
        SectionHeader("Atributos: último partido vs tu promedio")
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadarChart(last, compare = avg, size = 170.dp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Legend(Brand.Accent, "Último partido")
                    Legend(Brand.Muted, "Tu promedio")
                }
            }
            Spacer(Modifier.height(8.dp))
            AttributeBars(last, compare = avg)
        }
    }
}

@Composable
private fun Legend(color: androidx.compose.ui.graphics.Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        androidx.compose.foundation.Canvas(Modifier.width(14.dp).height(4.dp)) { drawRect(color) }
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = Brand.Muted)
    }
}

@Composable
private fun HeadToHead(matches: List<MatchSummary>) {
    var aId by rememberSaveable { mutableStateOf(matches[0].id) }
    var bId by rememberSaveable { mutableStateOf(matches[1].id) }
    var per90 by rememberSaveable { mutableStateOf(true) }
    val a = matches.firstOrNull { it.id == aId } ?: matches[0]
    val b = matches.firstOrNull { it.id == bId } ?: matches[1]

    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MatchPicker("Partido A", a, matches, Modifier.weight(1f), accent = true) { aId = it }
        MatchPicker("Partido B", b, matches, Modifier.weight(1f), accent = false) { bId = it }
    }
    VersusPanel(a, b, per90, onPer90 = { per90 = it }, rightIsReference = false)
}

@Composable
fun MatchPicker(label: String, current: MatchSummary, all: List<MatchSummary>, modifier: Modifier, accent: Boolean, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Panel(onClick = { open = true }, padding = 12.dp) {
            Overline(label, color = if (accent) Brand.Accent else Brand.Muted)
            Text(current.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(current.startMs.shortDate(), style = MaterialTheme.typography.labelSmall, color = Brand.Muted, modifier = Modifier.weight(1f))
                Text("Cambiar ▾", style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            all.forEach { m ->
                DropdownMenuItem(
                    text = { Text("${m.startMs.shortDate()} · ${m.title}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = { open = false; onPick(m.id) },
                )
            }
        }
    }
}

/**
 * Comparación de dos conjuntos de datos: partido contra partido, o partido contra el promedio
 * del resto ([b] = null y [reference] con los demás partidos).
 */
@Composable
fun VersusPanel(
    a: MatchSummary,
    b: MatchSummary?,
    per90: Boolean,
    onPer90: (Boolean) -> Unit,
    rightIsReference: Boolean,
    reference: List<MatchSummary> = emptyList(),
) {
    val ka = a.kpis ?: return
    val kb = b?.kpis
    val rows = remember(a, b, reference, per90) {
        if (kb != null) MatchComparator.headToHead(ka, kb, per90)
        else MatchComparator.vsAverage(ka, reference.mapNotNull { it.kpis }, per90)
    }.filter { it.value != null || it.reference != null }
    val scoreB = if (b != null) b.score else remember(reference) { PerformanceScore.average(reference.mapNotNull { it.score }) }

    Panel(Modifier.padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(a.score?.overall, size = 64.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Overline("Índice")
                val d = (a.score?.overall ?: 0) - (scoreB?.overall ?: 0)
                if (a.score != null && scoreB != null) {
                    Text(
                        if (d > 0) "+$d" else "$d", style = numberStyle(24),
                        color = if (d >= 2) Brand.Good else if (d <= -2) Brand.Bad else Brand.Muted,
                    )
                }
            }
            ScoreRing(scoreB?.overall, size = 64.dp, color = if (rightIsReference) Brand.Muted else Brand.Text)
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(a.title, style = MaterialTheme.typography.labelMedium, color = Brand.Accent, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(
                b?.title ?: "Tu promedio", style = MaterialTheme.typography.labelMedium, color = Brand.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
        val sa = a.score
        if (sa != null && scoreB != null) {
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Brand.Outline)
            AttributeBars(sa, compare = scoreB)
            Text("La marca blanca es ${if (b != null) "el partido B" else "tu promedio"}.", style = MaterialTheme.typography.labelSmall, color = Brand.Muted, modifier = Modifier.padding(top = 8.dp))
        }
    }

    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Métrica a métrica", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        FilterChip(per90, { onPer90(!per90) }, label = { Text("Por 90'") })
    }
    Panel {
        rows.groupBy { it.metric.group }.entries.forEachIndexed { gi, (group, list) ->
            if (gi > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Brand.Outline)
            Overline(group, color = Brand.Accent)
            list.forEach { VersusRow(it, rightIsReference = rightIsReference) }
        }
    }
    Text(
        "En verde lima, el valor que gana. Las diferencias pequeñas (dentro del margen normal de cada métrica) no se marcan.",
        style = MaterialTheme.typography.labelSmall, color = Brand.Muted, modifier = Modifier.padding(top = 8.dp),
    )
}
