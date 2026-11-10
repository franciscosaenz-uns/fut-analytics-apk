package com.futbolanalisis.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.futbolanalisis.app.ui.theme.Brand
import com.futbolanalisis.app.ui.theme.OverlineStyle
import com.futbolanalisis.app.ui.theme.numberStyle
import com.futbolanalisis.core.insights.Attribute
import com.futbolanalisis.core.insights.Insight
import com.futbolanalisis.core.insights.MatchComparator
import com.futbolanalisis.core.insights.MetricComparison
import com.futbolanalisis.core.insights.PerformanceScore
import com.futbolanalisis.core.insights.Verdict
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Iconos propios (trazos de Material) para no cargar la librería completa de iconos. */
object AppIcons {
    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
            .build()

    val Chart: ImageVector by lazy { icon("Chart", "M16,6l2.29,2.29 -4.88,4.88 -4,-4L2,16.59 3.41,18l6,-6 4,4 6.3,-6.29L22,12V6z") }
    val Bars: ImageVector by lazy { icon("Bars", "M5,9.2h3V19H5zM10.6,5h2.8v14h-2.8zM16.2,13H19v6h-2.8z") }
    val Compare: ImageVector by lazy { icon("Compare", "M6.99,11L3,15l3.99,4v-3H14v-2H6.99v-3zM21,9l-3.99,-4v3H10v2h7.01v3L21,9z") }
    val Layers: ImageVector by lazy {
        icon("Layers", "M11.99,18.54l-7.37,-5.73L3,14.07l9,7 9,-7 -1.63,-1.27 -7.38,5.74zM12,16l7.36,-5.73L21,9l-9,-7 -9,7 1.63,1.27L12,16z")
    }
    val Download: ImageVector by lazy { icon("Download", "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z") }
    val Pitch: ImageVector by lazy {
        icon("Pitch", "M3,5h18v14H3zM5,7v10h6v-2.2a3,3 0 0,1 0,-5.6V7zM13,7v2.2a3,3 0 0,1 0,5.6V17h6V7zM12,11a1,1 0 1,0 0.01,0z")
    }
}

fun Verdict.color(): Color = when (this) {
    Verdict.BETTER -> Brand.Good
    Verdict.WORSE -> Brand.Bad
    Verdict.SIMILAR, Verdict.NEUTRAL -> Brand.Muted
}

/** Tarjeta base de la app: superficie oscura con borde fino. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = Brand.Surface,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        modifier = modifier.fillMaxWidth().clip(shape).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = color,
        border = BorderStroke(1.dp, Brand.Outline),
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = Brand.Muted) {
    Text(text.uppercase(), style = OverlineStyle, color = color, modifier = modifier, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Encabezado de sección con acción opcional a la derecha. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(action, style = MaterialTheme.typography.labelLarge, color = Brand.Accent, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(6.dp))
        }
    }
}

/** Cifra grande con etiqueta arriba y unidad pequeña al lado. */
@Composable
fun BigStat(label: String, value: String, modifier: Modifier = Modifier, unit: String = "", size: Int = 26, hint: String? = null, hintColor: Color = Brand.Muted) {
    Column(modifier) {
        Overline(label)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = numberStyle(size), color = Brand.Text, maxLines = 1)
            if (unit.isNotEmpty()) Text(" $unit", style = MaterialTheme.typography.labelMedium, color = Brand.Muted, modifier = Modifier.padding(bottom = 3.dp), maxLines = 1)
        }
        hint?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = hintColor, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false) {
    Box(
        modifier.background(if (filled) color else color.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (filled) Brand.OnAccent else color, maxLines = 1)
    }
}

/** Insignia V / E / D del resultado. */
@Composable
fun OutcomeBadge(outcome: Char?, modifier: Modifier = Modifier) {
    if (outcome == null) return
    val color = when (outcome) { 'V' -> Brand.Good; 'D' -> Brand.Bad; else -> Brand.Warn }
    Box(modifier.size(22.dp).background(color, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
        Text("$outcome", color = Brand.OnAccent, fontWeight = FontWeight.Black, fontSize = 12.sp)
    }
}

/** Anillo con el índice de rendimiento (0-99). */
@Composable
fun ScoreRing(score: Int?, modifier: Modifier = Modifier, size: Dp = 96.dp, label: String = "ÍNDICE", color: Color = Brand.Accent) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = this.size.minDimension * 0.09f
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(Brand.Outline, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
            if (score != null) {
                drawArc(color, -90f, 360f * (score / 99f).coerceIn(0f, 1f), false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(score?.toString() ?: "—", style = numberStyle((size.value * 0.36f).toInt()), color = Brand.Text)
            if (size >= 72.dp) Text(label, style = OverlineStyle.copy(fontSize = (size.value * 0.095f).coerceIn(8f, 11f).sp), color = Brand.Muted)
        }
    }
}

/** Radar de los cinco atributos; [compare] se dibuja detrás en gris (p. ej. tu promedio). */
@Composable
fun RadarChart(score: PerformanceScore, modifier: Modifier = Modifier, compare: PerformanceScore? = null, size: Dp = 190.dp) {
    val attrs = Attribute.entries
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val c = Offset(this.size.width / 2, this.size.height / 2)
            val radius = this.size.minDimension / 2 * 0.70f
            fun point(i: Int, f: Float): Offset {
                val ang = -PI / 2 + 2 * PI * i / attrs.size
                return Offset(c.x + (radius * f * cos(ang)).toFloat(), c.y + (radius * f * sin(ang)).toFloat())
            }
            fun polygon(f: (Int) -> Float) = Path().apply {
                attrs.indices.forEach { i -> val p = point(i, f(i)); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
                close()
            }
            for (ring in 1..4) drawPath(polygon { ring / 4f }, Brand.Outline, style = Stroke(1.5f))
            attrs.indices.forEach { i -> drawLine(Brand.Outline, c, point(i, 1f), 1.5f) }
            compare?.let { cmp ->
                val p = polygon { cmp[attrs[it]] / 99f }
                drawPath(p, Brand.Muted.copy(alpha = 0.18f))
                drawPath(p, Brand.Muted, style = Stroke(2.5f))
            }
            val p = polygon { score[attrs[it]] / 99f }
            drawPath(p, Brand.Accent.copy(alpha = 0.22f))
            drawPath(p, Brand.Accent, style = Stroke(4f))
            attrs.indices.forEach { i -> drawCircle(Brand.Accent, 5f, point(i, score[attrs[i]] / 99f)) }
        }
        // Etiquetas en los vértices.
        attrs.forEachIndexed { i, a ->
            val ang = -PI / 2 + 2 * PI * i / attrs.size
            val r = size.value / 2 * 0.90f
            Column(
                Modifier.align(Alignment.Center).offset(x = (r * cos(ang)).dp, y = (r * sin(ang)).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(a.short, style = OverlineStyle.copy(fontSize = 9.sp), color = Brand.Muted)
                Text("${score[a]}", style = numberStyle(14), color = Brand.Text)
            }
        }
    }
}

/** Barras horizontales de atributos, con marca opcional de la referencia. */
@Composable
fun AttributeBars(score: PerformanceScore, modifier: Modifier = Modifier, compare: PerformanceScore? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Attribute.entries.forEach { a ->
            val v = score[a]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.label, style = MaterialTheme.typography.bodySmall, color = Brand.Muted, modifier = Modifier.width(92.dp), maxLines = 1)
                Box(Modifier.weight(1f).height(8.dp)) {
                    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                        val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                        drawRoundRect(Brand.Outline, cornerRadius = r)
                        drawRoundRect(Brand.Accent, size = Size(size.width * (v / 99f).coerceIn(0.03f, 1f), size.height), cornerRadius = r)
                        compare?.let { c ->
                            val x = size.width * (c[a] / 99f).coerceIn(0f, 1f)
                            drawLine(Brand.Text, Offset(x, -4f), Offset(x, size.height + 4f), 3f)
                        }
                    }
                }
                Text("$v", style = numberStyle(16), color = Brand.Text, textAlign = TextAlign.End, modifier = Modifier.width(34.dp))
                if (compare != null) {
                    val d = v - compare[a]
                    Text(
                        if (d > 0) "+$d" else "$d", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End, modifier = Modifier.width(30.dp),
                        color = if (d >= 3) Brand.Good else if (d <= -3) Brand.Bad else Brand.Muted,
                    )
                }
            }
        }
    }
}

/** Texto corto con la variación: "▲ 12 %" en verde o "▼ 8 %" en rojo. */
fun MetricComparison.deltaText(): String? {
    val d = delta ?: return null
    val arrow = if (d > 0) "▲" else if (d < 0) "▼" else "="
    return if (metric.isDelta || deltaPct == null) "$arrow ${MatchComparator.format(abs(d), metric.decimals)}"
    else "$arrow ${MatchComparator.format(abs(deltaPct!!), 0)} %"
}

@Composable
fun DeltaBadge(c: MetricComparison, modifier: Modifier = Modifier) {
    if (c.isRecord) { Pill("★ Récord", Brand.Accent, modifier); return }
    val text = c.deltaText() ?: return
    Pill(text, c.verdict.color(), modifier)
}

@Composable
fun InsightRow(insight: Insight, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 6.dp).size(8.dp).background(insight.tone.color(), CircleShape))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(insight.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(insight.detail, style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
        }
    }
}

/**
 * Fila de comparación tipo "cara a cara": valor de la izquierda, barras enfrentadas y valor de
 * la derecha. La barra del lado ganador se pinta con el color de acento.
 */
@Composable
fun VersusRow(c: MetricComparison, modifier: Modifier = Modifier, rightIsReference: Boolean = true) {
    val a = c.value; val b = c.reference
    val max = maxOf(abs(a ?: 0.0), abs(b ?: 0.0)).coerceAtLeast(1e-9)
    val leftWins = c.verdict == Verdict.BETTER
    val rightWins = c.verdict == Verdict.WORSE
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(MatchComparator.format(c.metric, a, withUnit = false), style = numberStyle(18), color = if (leftWins) Brand.Accent else Brand.Text, modifier = Modifier.width(70.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(c.metric.short + if (c.metric.unit.isNotEmpty()) " · ${c.metric.unit}" else "", style = MaterialTheme.typography.labelMedium, color = Brand.Muted, maxLines = 1)
            }
            Text(
                MatchComparator.format(c.metric, b, withUnit = false), style = numberStyle(18), textAlign = TextAlign.End, modifier = Modifier.width(70.dp),
                color = if (rightWins && !rightIsReference) Brand.Accent else if (rightIsReference) Brand.Muted else Brand.Text,
            )
        }
        Spacer(Modifier.height(4.dp))
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            val gap = 6f
            val half = (size.width - gap) / 2
            val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
            drawRoundRect(Brand.Outline, size = Size(half, size.height), cornerRadius = r)
            drawRoundRect(Brand.Outline, topLeft = Offset(half + gap, 0f), size = Size(half, size.height), cornerRadius = r)
            if (a != null) {
                val w = (half * (abs(a) / max)).toFloat()
                drawRoundRect(if (leftWins) Brand.Accent else Brand.Text.copy(alpha = 0.75f), topLeft = Offset(half - w, 0f), size = Size(w, size.height), cornerRadius = r)
            }
            if (b != null) {
                val w = (half * (abs(b) / max)).toFloat()
                drawRoundRect(
                    if (rightWins && !rightIsReference) Brand.Accent else Brand.Muted,
                    topLeft = Offset(half + gap, 0f), size = Size(w, size.height), cornerRadius = r,
                )
            }
        }
    }
}

/**
 * Evolución de una métrica partido a partido. [values] va del más antiguo al más reciente;
 * los nulos se saltan. Dibuja la media como línea discontinua y resalta [highlight].
 */
@Composable
fun TrendChart(values: List<Double?>, modifier: Modifier = Modifier, labels: List<String> = emptyList(), highlight: Int? = null, height: Dp = 150.dp) {
    val present = values.filterNotNull()
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            if (present.isEmpty()) return@Canvas
            val lo = present.min(); val hi = present.max()
            val span = (hi - lo).takeIf { it > 1e-9 } ?: (abs(hi) * 0.2).coerceAtLeast(1.0)
            val minY = lo - span * 0.18; val maxY = hi + span * 0.18
            val padX = 14f
            fun x(i: Int) = if (values.size <= 1) size.width / 2 else padX + i * (size.width - 2 * padX) / (values.size - 1)
            fun y(v: Double) = (size.height * (1 - (v - minY) / (maxY - minY))).toFloat()
            for (g in 0..3) {
                val gy = size.height * g / 3f
                drawLine(Brand.Outline.copy(alpha = 0.6f), Offset(0f, gy), Offset(size.width, gy), 1f)
            }
            val avg = present.average()
            var dx = 0f
            while (dx < size.width) { drawLine(Brand.Muted, Offset(dx, y(avg)), Offset(dx + 10f, y(avg)), 2f); dx += 20f }
            val pts = values.mapIndexedNotNull { i, v -> v?.let { Offset(x(i), y(it)) } }
            if (pts.size >= 2) {
                val area = Path().apply {
                    moveTo(pts.first().x, size.height)
                    pts.forEach { lineTo(it.x, it.y) }
                    lineTo(pts.last().x, size.height); close()
                }
                drawPath(area, Brand.Accent.copy(alpha = 0.10f))
                val line = Path().apply { pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
                drawPath(line, Brand.Accent, style = Stroke(4f, cap = StrokeCap.Round))
            }
            values.forEachIndexed { i, v ->
                if (v == null) return@forEachIndexed
                val p = Offset(x(i), y(v))
                val on = i == highlight
                drawCircle(Brand.Bg, if (on) 11f else 7f, p)
                drawCircle(if (on) Brand.Text else Brand.Accent, if (on) 8f else 5f, p)
            }
        }
        if (labels.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                val step = ((labels.size - 1) / 4).coerceAtLeast(1)
                val shown = labels.indices.filter { it % step == 0 || it == labels.lastIndex }.distinct()
                shown.forEach { Text(labels[it], style = MaterialTheme.typography.labelSmall, color = Brand.Muted) }
            }
        }
    }
}

/** Línea mínima sin ejes, para tarjetas pequeñas. */
@Composable
fun Sparkline(values: List<Double>, modifier: Modifier = Modifier, color: Color = Brand.Accent) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val lo = values.min(); val hi = values.max()
        val span = (hi - lo).takeIf { it > 1e-9 } ?: 1.0
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * size.width / (values.size - 1)
            val y = (size.height * (0.1 + 0.8 * (1 - (v - lo) / span))).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(4f, cap = StrokeCap.Round))
        val last = values.last()
        drawCircle(color, 6f, Offset(size.width, (size.height * (0.1 + 0.8 * (1 - (last - lo) / span))).toFloat()))
    }
}
