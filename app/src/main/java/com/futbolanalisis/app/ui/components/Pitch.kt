package com.futbolanalisis.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.futbolanalisis.app.ui.theme.PitchGreen
import com.futbolanalisis.core.pitch.PitchDimensions
import com.futbolanalisis.core.pitch.PitchPoint
import com.futbolanalisis.core.tactical.Heatmap
import kotlin.math.floor

/**
 * Cancha horizontal: el equipo ataca hacia la derecha. La banda izquierda (y = ancho) queda
 * arriba, como si miraras el partido desde la tribuna de la banda derecha.
 */
class PitchMapper(val dims: PitchDimensions, val size: Size) {
    fun x(m: Double) = (m / dims.length * size.width).toFloat()
    fun y(m: Double) = ((dims.width - m) / dims.width * size.height).toFloat()
    fun offset(p: PitchPoint) = Offset(x(p.x), y(p.y))
    fun scale(m: Double) = (m / dims.length * size.width).toFloat()
}

@Composable
fun PitchCanvas(
    dims: PitchDimensions,
    modifier: Modifier = Modifier,
    heatmap: Heatmap? = null,
    overlay: DrawScope.(PitchMapper) -> Unit = {},
) {
    val bitmap = remember(heatmap) { heatmap?.let { heatmapBitmap(it) } }
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio((dims.length / dims.width).toFloat()),
    ) {
        val m = PitchMapper(dims, size)
        drawRect(PitchGreen)
        // Franjas de césped.
        for (i in 0 until 10 step 2) {
            drawRect(Color.White.copy(alpha = 0.04f), topLeft = Offset(size.width * i / 10f, 0f), size = Size(size.width / 10f, size.height))
        }
        bitmap?.let {
            drawImage(
                it,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                dstOffset = IntOffset.Zero,
                filterQuality = FilterQuality.High,
            )
        }
        drawPitchLines(m)
        overlay(m)
    }
}

private fun DrawScope.drawPitchLines(m: PitchMapper) {
    val line = Color.White.copy(alpha = 0.85f)
    val stroke = Stroke(width = 2.5f)
    val d = m.dims
    drawRect(line, Offset(0f, 0f), size, style = stroke)
    drawLine(line, Offset(m.x(d.length / 2), 0f), Offset(m.x(d.length / 2), size.height), strokeWidth = 2.5f)
    drawCircle(line, radius = m.scale(9.15), center = Offset(m.x(d.length / 2), m.y(d.width / 2)), style = stroke)
    drawCircle(line, radius = 4f, center = Offset(m.x(d.length / 2), m.y(d.width / 2)))
    for (own in listOf(true, false)) {
        val boxX = if (own) 0.0 else d.length - 16.5
        val smallX = if (own) 0.0 else d.length - 5.5
        drawRect(line, Offset(m.x(boxX), m.y(d.width / 2 + 20.16)), Size(m.scale(16.5), m.scale(40.32)), style = stroke)
        drawRect(line, Offset(m.x(smallX), m.y(d.width / 2 + 9.16)), Size(m.scale(5.5), m.scale(18.32)), style = stroke)
        drawCircle(line, radius = 3f, center = Offset(m.x(if (own) 11.0 else d.length - 11.0), m.y(d.width / 2)))
    }
}

/** Flecha simple para sprints. */
fun DrawScope.arrow(from: Offset, to: Offset, color: Color, width: Float = 4f) {
    drawLine(color, from, to, strokeWidth = width)
    val angle = Math.toDegrees(kotlin.math.atan2((to.y - from.y).toDouble(), (to.x - from.x).toDouble())).toFloat()
    rotate(angle, pivot = to) {
        drawLine(color, to, Offset(to.x - 16f, to.y - 9f), strokeWidth = width)
        drawLine(color, to, Offset(to.x - 16f, to.y + 9f), strokeWidth = width)
    }
}

/** Interpola el mapa de calor (bilineal) a un bitmap 10x más fino con una paleta azul→rojo. */
private fun heatmapBitmap(h: Heatmap, factor: Int = 10): ImageBitmap {
    val w = h.cols * factor
    val ht = h.rows * factor
    val pixels = IntArray(w * ht)
    for (py in 0 until ht) {
        // Fila 0 del bitmap = arriba = banda izquierda = última fila del mapa.
        val fy = (ht - 1 - py + 0.5) / factor - 0.5
        for (px in 0 until w) {
            val fx = (px + 0.5) / factor - 0.5
            val v = bilinear(h, fx, fy)
            pixels[py * w + px] = if (v < 0.06) 0 else heatColor(v)
        }
    }
    return Bitmap.createBitmap(pixels, w, ht, Bitmap.Config.ARGB_8888).asImageBitmap()
}

private fun bilinear(h: Heatmap, fx: Double, fy: Double): Double {
    val x0 = floor(fx).toInt(); val y0 = floor(fy).toInt()
    val tx = fx - x0; val ty = fy - y0
    fun at(c: Int, r: Int) = h[c.coerceIn(0, h.cols - 1), r.coerceIn(0, h.rows - 1)]
    return (at(x0, y0) * (1 - tx) + at(x0 + 1, y0) * tx) * (1 - ty) +
        (at(x0, y0 + 1) * (1 - tx) + at(x0 + 1, y0 + 1) * tx) * ty
}

private val stops = listOf(
    0.0 to 0x2196F3, 0.35 to 0x4CAF50, 0.6 to 0xFFEB3B, 0.8 to 0xFF9800, 1.0 to 0xF44336,
)

private fun heatColor(v: Double): Int {
    var rgb = stops.last().second
    for (i in 1 until stops.size) {
        val (p1, c1) = stops[i - 1]; val (p2, c2) = stops[i]
        if (v <= p2) {
            val t = ((v - p1) / (p2 - p1)).coerceIn(0.0, 1.0)
            fun ch(c: Int, s: Int) = (c shr s) and 0xFF
            fun mix(s: Int) = (ch(c1, s) + (ch(c2, s) - ch(c1, s)) * t).toInt()
            rgb = (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
            break
        }
    }
    val alpha = ((0.3 + 0.55 * v).coerceAtMost(0.85) * 255).toInt()
    return (alpha shl 24) or rgb
}
