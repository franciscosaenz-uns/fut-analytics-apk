package com.futbolanalisis.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = modifier.padding(top = 16.dp, bottom = 8.dp))
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, hint: String? = null) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** Fila de 2 o 3 tarjetas del mismo ancho. */
@Composable
fun StatRow(vararg tiles: Triple<String, String, String?>) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.forEach { (l, v, h) -> StatTile(l, v, Modifier.weight(1f), h) }
    }
}

/** Barra horizontal con etiqueta y valor (zonas de velocidad, de FC, tercios...). */
@Composable
fun LabeledBar(label: String, fraction: Float, valueText: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(110.dp))
        Box(
            Modifier
                .weight(1f)
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(7.dp))
                    .background(color),
            )
        }
        Text(valueText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(78.dp).padding(start = 8.dp))
    }
}

val zoneColors = listOf(
    Color(0xFF90CAF9), Color(0xFF66BB6A), Color(0xFFFFEE58), Color(0xFFFFA726), Color(0xFFEF5350),
)

/** Gráfico de barras simple (p. ej. metros por minuto). */
@Composable
fun BarChart(values: List<Double>, color: Color, modifier: Modifier = Modifier, dividerAt: Int? = null) {
    val outline = MaterialTheme.colorScheme.outline
    Canvas(modifier.fillMaxWidth().height(140.dp)) {
        if (values.isEmpty()) return@Canvas
        val maxV = values.max().coerceAtLeast(1.0)
        val bw = size.width / values.size
        values.forEachIndexed { i, v ->
            val h = (v / maxV * size.height).toFloat()
            drawRect(color, Offset(i * bw + bw * 0.1f, size.height - h), Size(bw * 0.8f, h))
        }
        dividerAt?.let { drawLine(outline, Offset(it * bw, 0f), Offset(it * bw, size.height), strokeWidth = 2f) }
    }
}

/** Línea simple (p. ej. FC por minuto) con bandas horizontales opcionales. */
@Composable
fun LineChart(values: List<Double>, color: Color, minY: Double, maxY: Double, modifier: Modifier = Modifier, bands: List<Pair<Double, Color>> = emptyList()) {
    Canvas(modifier.fillMaxWidth().height(160.dp)) {
        if (values.size < 2) return@Canvas
        fun y(v: Double) = (size.height - (v - minY) / (maxY - minY) * size.height).toFloat().coerceIn(0f, size.height)
        var prev = minY
        for ((upper, c) in bands) {
            drawRect(c.copy(alpha = 0.18f), Offset(0f, y(upper)), Size(size.width, y(prev) - y(upper)))
            prev = upper
        }
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * size.width / (values.size - 1)
            if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
        }
        drawPath(path, color, style = Stroke(width = 4f))
    }
}

/**
 * Curva de energía 0-100 por minuto con bandas (verde > 60, ámbar 30-60, rojo < 30),
 * marcas cada 15 minutos y línea de descanso.
 */
@Composable
fun EnergyChart(
    values: List<Double>,
    modifier: Modifier = Modifier,
    dividerAtMinute: Int? = null,
    markers: List<Int> = emptyList(),
    secondary: List<Pair<List<Double>, Color>> = emptyList(),
) {
    val lineColor = MaterialTheme.colorScheme.onSurface
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            if (values.size < 2) return@Canvas
            fun x(i: Int) = i * size.width / (values.size - 1)
            fun y(v: Double) = (size.height * (1 - v / 100)).toFloat()
            drawRect(Color(0xFF66BB6A).copy(alpha = 0.16f), Offset(0f, 0f), Size(size.width, y(60.0)))
            drawRect(Color(0xFFFFCA28).copy(alpha = 0.16f), Offset(0f, y(60.0)), Size(size.width, y(30.0) - y(60.0)))
            drawRect(Color(0xFFEF5350).copy(alpha = 0.16f), Offset(0f, y(30.0)), Size(size.width, size.height - y(30.0)))
            for (m in 15 until values.size step 15) drawLine(grid, Offset(x(m), 0f), Offset(x(m), size.height), 1f)
            dividerAtMinute?.let { drawLine(lineColor.copy(alpha = 0.6f), Offset(x(it), 0f), Offset(x(it), size.height), 3f) }
            markers.forEach { m ->
                val i = (m - 1).coerceIn(0, values.size - 1)
                drawCircle(Color(0xFFE53935), 7f, Offset(x(i), y(values[i])))
            }
            secondary.forEach { (series, c) ->
                val p = Path()
                series.forEachIndexed { i, v -> if (i == 0) p.moveTo(x(i), y(v)) else p.lineTo(x(i), y(v)) }
                drawPath(p, c, style = Stroke(width = 2.5f))
            }
            val path = Path()
            values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
            drawPath(path, lineColor, style = Stroke(width = 5f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0, 15, 30, 45, 60, 75, 90).filter { it < values.size }.forEach {
                Text("$it'", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
