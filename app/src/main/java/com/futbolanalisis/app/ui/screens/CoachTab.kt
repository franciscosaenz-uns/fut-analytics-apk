package com.futbolanalisis.app.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.futbolanalisis.app.data.MatchRecord
import com.futbolanalisis.app.ui.components.SectionTitle
import com.futbolanalisis.core.coach.Coach
import com.futbolanalisis.core.coach.CoachTip
import com.futbolanalisis.core.coach.Drill
import com.futbolanalisis.core.coach.DrillType
import com.futbolanalisis.core.coach.MatchLog
import com.futbolanalisis.core.coach.ProMatch
import com.futbolanalisis.core.coach.TipArea

private fun TipArea.color() = when (this) {
    TipArea.ENERGY -> Color(0xFFE53935)
    TipArea.PACING -> Color(0xFFFB8C00)
    TipArea.POSITION -> Color(0xFF43A047)
    TipArea.TACTICS -> Color(0xFF1E88E5)
    TipArea.PRODUCTION -> Color(0xFF8E24AA)
    TipArea.RECOVERY -> Color(0xFF00897B)
}

private fun DrillType.color() = when (this) {
    DrillType.PHYSICAL -> Color(0xFFE53935)
    DrillType.TECHNICAL -> Color(0xFF1E88E5)
    DrillType.TACTICAL -> Color(0xFF43A047)
}

@Composable
fun LogCard(log: MatchLog, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Registro del partido", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (log.isEmpty) {
                        Text("Anota resultado, goles, asistencias y cómo te sentiste", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(
                            listOfNotNull(log.result, log.opponent.takeIf { it.isNotBlank() }?.let { "vs $it" }).joinToString(" ").ifBlank { "Partido" },
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                FilledTonalButton(onClick = onEdit) { Text(if (log.isEmpty) "Registrar" else "Editar") }
            }
            if (!log.isEmpty) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LogStat("⚽", "${log.goals}", "goles")
                    LogStat("🅰", "${log.assists}", "asist.")
                    LogStat("🎯", "${log.shotsOnTarget}/${log.shots}", "tiros")
                    log.rpe?.let { LogStat("💪", "$it/10", "RPE") }
                    log.selfRating?.let { LogStat("⭐", "$it/10", "nota") }
                }
                if (log.notes.isNotBlank()) Text(log.notes, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun LogStat(icon: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$icon $value", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun CoachTab(record: MatchRecord) {
    val coach = remember(record.report, record.log) { Coach.advise(record.report, record.log) }

    SectionTitle("Consejos para el próximo partido")
    if (record.log.isEmpty) {
        Text("Completa el registro del partido (pestaña Resumen) para recibir consejos más precisos.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { coach.inMatchTips.forEach { TipCard(it) } }

    SectionTitle("Entrenamientos recomendados · ${coach.roleGroup.label}")
    var filter by rememberSaveable { mutableStateOf<DrillType?>(null) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AssistChip(onClick = { filter = null }, label = { Text(if (filter == null) "● Todos" else "Todos") })
        DrillType.entries.forEach { t ->
            AssistChip(onClick = { filter = t }, label = { Text(if (filter == t) "● ${t.label}" else t.label) })
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        coach.drills.filter { filter == null || it.type == filter }.forEach { DrillCard(it) }
    }

    SectionTitle("Te pareces a…")
    Text(
        "Comparamos cómo ocupaste el campo (zona más frecuente, tercios, carriles, recorrido y sprints) con perfiles de rol " +
            "aproximados de jugadores profesionales. Es una comparación de estilo posicional, no de nivel.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        coach.proMatches.forEachIndexed { i, m -> ProCard(i + 1, m) }
    }
}

@Composable
private fun TipCard(tip: CoachTip) {
    Card(colors = CardDefaults.cardColors(containerColor = tip.area.color().copy(alpha = 0.08f))) {
        Row(Modifier.fillMaxWidth().padding(12.dp)) {
            Box(Modifier.width(4.dp).height(56.dp).background(tip.area.color(), RoundedCornerShape(2.dp)))
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    tip.area.label.uppercase() + (tip.minute?.let { " · min $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall, color = tip.area.color(), fontWeight = FontWeight.Bold,
                )
                Text(tip.title, fontWeight = FontWeight.SemiBold)
                Text(tip.detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DrillCard(d: Drill) {
    var open by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable { open = !open }.animateContentSize()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.background(d.type.color(), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text(d.type.label, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Text(d.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(if (open) "▲" else "▼", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(d.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            if (open) {
                Text("Cómo: ${d.how}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                Text("Dosis: ${d.dose}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun ProCard(rank: Int, m: ProMatch) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                Text(m.player.name.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString(""), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text("$rank. ${m.player.name}", fontWeight = FontWeight.SemiBold)
                Text("${m.player.role} · ${m.player.team}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    LinearProgressIndicator(progress = { (m.similarity / 100).toFloat() }, modifier = Modifier.weight(1f))
                    Text(" ${m.similarity.toInt()}%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
                Text(m.player.style, style = MaterialTheme.typography.bodySmall)
                if (m.sharedTraits.isNotEmpty()) Text("Coinciden: ${m.sharedTraits.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                if (m.differences.isNotEmpty()) Text("A diferencia de él, ${m.differences.joinToString(" y ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
