package com.futbolanalisis.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.futbolanalisis.app.ui.theme.Brand
import com.futbolanalisis.app.ui.theme.PitchGreenDark
import com.futbolanalisis.core.pitch.PitchDimensions
import com.futbolanalisis.core.pitch.PitchPoint
import com.futbolanalisis.core.render.HeatmapRenderer
import com.futbolanalisis.core.tactical.Heatmap

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

/** Margen (en metros) que la foto satelital muestra alrededor de la cancha. */
const val SATELLITE_MARGIN_M = 6.0

/**
 * Cancha con mapa de calor opcional. Si se pasa [satellite] (foto aérea ya alineada con la
 * cancha, con [SATELLITE_MARGIN_M] de margen), se dibuja debajo del mapa de calor.
 * Dentro de [overlay] el tamaño del lienzo es el de la cancha, sin el margen.
 */
@Composable
fun PitchCanvas(
    dims: PitchDimensions,
    modifier: Modifier = Modifier,
    heatmap: Heatmap? = null,
    satellite: ImageBitmap? = null,
    showLines: Boolean = true,
    corner: Dp = 14.dp,
    overlay: DrawScope.(PitchMapper) -> Unit = {},
) {
    val strong = satellite != null
    val bitmap = remember(heatmap, strong) { heatmap?.let { heatmapBitmap(it, strong) } }
    val margin = if (satellite != null) SATELLITE_MARGIN_M else 0.0
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(((dims.length + 2 * margin) / (dims.width + 2 * margin)).toFloat())
            .clip(RoundedCornerShape(corner)),
    ) {
        val pad = (margin / (dims.length + 2 * margin) * size.width).toFloat()
        if (satellite != null) {
            drawImage(satellite, dstSize = IntSize(size.width.toInt(), size.height.toInt()), dstOffset = IntOffset.Zero, filterQuality = FilterQuality.High)
            drawRect(Color.Black.copy(alpha = 0.26f))
        } else {
            drawRect(PitchGreenDark)
        }
        inset(pad, pad) {
            val m = PitchMapper(dims, size)
            if (satellite == null) {
                // Franjas de césped.
                val stripes = 12
                for (i in 0 until stripes) {
                    drawRect(if (i % 2 == 0) Brand.Turf else Brand.TurfAlt, topLeft = Offset(size.width * i / stripes, 0f), size = Size(size.width / stripes + 1f, size.height))
                }
            }
            bitmap?.let {
                drawImage(it, dstSize = IntSize(size.width.toInt(), size.height.toInt()), dstOffset = IntOffset.Zero, filterQuality = FilterQuality.High)
            }
            if (showLines) drawPitchLines(m, if (satellite != null) 0.55f else 0.8f)
            overlay(m)
        }
    }
}

private fun DrawScope.drawPitchLines(m: PitchMapper, alpha: Float) {
    val line = Color.White.copy(alpha = alpha)
    val w = (size.width / 300f).coerceIn(1.5f, 4f)
    val stroke = Stroke(width = w)
    val d = m.dims
    drawRect(line, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = stroke)
    drawLine(line, Offset(m.x(d.length / 2), 0f), Offset(m.x(d.length / 2), size.height), strokeWidth = w)
    drawCircle(line, radius = m.scale(9.15), center = Offset(m.x(d.length / 2), m.y(d.width / 2)), style = stroke)
    drawCircle(line, radius = w * 1.5f, center = Offset(m.x(d.length / 2), m.y(d.width / 2)))
    for (own in listOf(true, false)) {
        val boxX = if (own) 0.0 else d.length - 16.5
        val smallX = if (own) 0.0 else d.length - 5.5
        drawRect(line, Offset(m.x(boxX), m.y(d.width / 2 + 20.16)), Size(m.scale(16.5), m.scale(40.32)), style = stroke)
        drawRect(line, Offset(m.x(smallX), m.y(d.width / 2 + 9.16)), Size(m.scale(5.5), m.scale(18.32)), style = stroke)
        val spot = Offset(m.x(if (own) 11.0 else d.length - 11.0), m.y(d.width / 2))
        drawCircle(line, radius = w * 1.2f, center = spot)
        val r = m.scale(9.15)
        drawArc(line, if (own) -53f else 127f, 106f, false, topLeft = Offset(spot.x - r, spot.y - r), size = Size(2 * r, 2 * r), style = stroke)
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

/** Mapa de calor suavizado (lo calcula el motor, igual que en la imagen para compartir). */
fun heatmapBitmap(h: Heatmap, strong: Boolean = false, width: Int = 315): ImageBitmap {
    val height = (width * h.dims.width / h.dims.length).toInt().coerceAtLeast(1)
    val pixels = HeatmapRenderer.argb(h, width, height, strong)
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
}
