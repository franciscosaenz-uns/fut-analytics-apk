package com.futbolanalisis.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.futbolanalisis.app.data.MatchRecord
import com.futbolanalisis.core.TrackStats
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val hm = DateTimeFormatter.ofPattern("HH:mm")
private fun clock(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(hm)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimScreen(record: MatchRecord?, onApply: (Long?, Long?) -> Unit, onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Recortar registro") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
        )
    }) { padding ->
        if (record == null) return@Scaffold
        val start = record.track.startMs
        val end = record.track.endMs
        val totalMin = ((end - start) / 60_000f).coerceAtLeast(1f)
        val perMinute = remember(record.id) { TrackStats.distancePerMinute(record.track) }
        var range by remember {
            mutableStateOf(
                ((record.options.trimStartMs ?: start) - start) / 60_000f..((record.options.trimEndMs ?: end) - start) / 60_000f,
            )
        }
        val primary = MaterialTheme.colorScheme.primary
        val muted = MaterialTheme.colorScheme.outlineVariant
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Quita el calentamiento, la espera antes de empezar o el tiempo después del pitazo final. " +
                    "Las barras muestran los metros por minuto de todo el registro; las de color quedan dentro del análisis.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Canvas(Modifier.fillMaxWidth().height(160.dp)) {
                if (perMinute.isEmpty()) return@Canvas
                val maxV = perMinute.max().coerceAtLeast(1.0)
                val bw = size.width / perMinute.size
                perMinute.forEachIndexed { i, v ->
                    val inside = i + 0.5f in range
                    val h = (v / maxV * size.height).toFloat()
                    drawRect(if (inside) primary else muted, Offset(i * bw, size.height - h), Size(bw * 0.85f, h))
                }
                drawLine(Color.Red, Offset(range.start / totalMin * size.width, 0f), Offset(range.start / totalMin * size.width, size.height), 3f)
                drawLine(Color.Red, Offset(range.endInclusive / totalMin * size.width, 0f), Offset(range.endInclusive / totalMin * size.width, size.height), 3f)
            }
            RangeSlider(value = range, onValueChange = { range = it }, valueRange = 0f..totalMin)
            val s = start + (range.start * 60_000).toLong()
            val e = start + (range.endInclusive * 60_000).toLong()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Inicio: ${clock(s)} (+${range.start.toInt()}')")
                Text("Fin: ${clock(e)} (${(range.endInclusive - range.start).toInt()}' en total)")
            }
            Text("Registro completo: ${clock(start)} – ${clock(end)}", style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = {
                    onApply(if (range.start > 0.05f) s else null, if (range.endInclusive < totalMin - 0.05f) e else null)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = range.endInclusive - range.start >= 5,
            ) { Text("Aplicar recorte") }
            OutlinedButton(onClick = { onApply(null, null); onBack() }, modifier = Modifier.fillMaxWidth()) { Text("Usar el registro completo") }
        }
    }
}
