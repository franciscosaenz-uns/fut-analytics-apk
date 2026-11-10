package com.futbolanalisis.app.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.futbolanalisis.core.share.Align
import com.futbolanalisis.core.share.Font
import com.futbolanalisis.core.share.ShareCard
import com.futbolanalisis.core.share.ShareCardData
import com.futbolanalisis.core.share.ShareFormat
import com.futbolanalisis.core.share.Surface
import com.futbolanalisis.core.share.Weight

/** Implementación de [Surface] sobre un Canvas de Android. */
class AndroidSurface(private val canvas: Canvas, w: Int, h: Int, private val backdropImage: Bitmap?) : Surface {
    override val width = w.toFloat()
    override val height = h.toFloat()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val typefaces = HashMap<Pair<Weight, Boolean>, Typeface>()

    private fun typeface(f: Font): Typeface = typefaces.getOrPut(f.weight to f.condensed) {
        if (f.condensed) {
            Typeface.create("sans-serif-condensed", if (f.weight >= Weight.BOLD) Typeface.BOLD else Typeface.NORMAL)
        } else when (f.weight) {
            Weight.REGULAR -> Typeface.create("sans-serif", Typeface.NORMAL)
            Weight.MEDIUM -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            Weight.BOLD -> Typeface.create("sans-serif", Typeface.BOLD)
            Weight.BLACK -> Typeface.create("sans-serif-black", Typeface.NORMAL)
        }
    }

    private fun fill(color: Int) = paint.apply { shader = null; style = Paint.Style.FILL; this.color = color }
    private fun stroke(color: Int, width: Float, round: Boolean = false) = paint.apply {
        shader = null; style = Paint.Style.STROKE; this.color = color; strokeWidth = width
        strokeCap = if (round) Paint.Cap.ROUND else Paint.Cap.BUTT
    }

    private fun text(f: Font, color: Int) = textPaint.apply {
        typeface = typeface(f); textSize = f.size; letterSpacing = f.tracking; this.color = color
    }

    override fun gradientRect(l: Float, t: Float, r: Float, b: Float, top: Int, bottom: Int) {
        paint.style = Paint.Style.FILL
        paint.color = 0xFF000000.toInt()
        paint.shader = LinearGradient(0f, t, 0f, b, top, bottom, Shader.TileMode.CLAMP)
        canvas.drawRect(l, t, r, b, paint)
        paint.shader = null
    }

    override fun rect(l: Float, t: Float, r: Float, b: Float, color: Int, radius: Float, stroke: Float) {
        val p = if (stroke > 0) stroke(color, stroke) else fill(color)
        if (radius > 0) canvas.drawRoundRect(RectF(l, t, r, b), radius, radius, p) else canvas.drawRect(l, t, r, b, p)
    }

    override fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float) {
        canvas.drawLine(x1, y1, x2, y2, stroke(color, width))
    }

    override fun circle(cx: Float, cy: Float, radius: Float, color: Int, stroke: Float) {
        canvas.drawCircle(cx, cy, radius, if (stroke > 0) stroke(color, stroke) else fill(color))
    }

    override fun arc(cx: Float, cy: Float, radius: Float, startDeg: Float, sweepDeg: Float, color: Int, stroke: Float) {
        canvas.drawArc(RectF(cx - radius, cy - radius, cx + radius, cy + radius), startDeg, sweepDeg, false, stroke(color, stroke, round = true))
    }

    override fun polygon(points: FloatArray, color: Int, stroke: Float) {
        val path = Path()
        for (i in points.indices step 2) if (i == 0) path.moveTo(points[0], points[1]) else path.lineTo(points[i], points[i + 1])
        path.close()
        canvas.drawPath(path, if (stroke > 0) stroke(color, stroke) else fill(color))
    }

    override fun text(text: String, x: Float, baseline: Float, font: Font, color: Int, align: Align) {
        val p = text(font, color)
        val w = p.measureText(text)
        val dx = when (align) { Align.LEFT -> 0f; Align.CENTER -> -w / 2; Align.RIGHT -> -w }
        canvas.drawText(text, x + dx, baseline, p)
    }

    override fun textWidth(text: String, font: Font): Float = text(font, 0).measureText(text)

    override fun clip(l: Float, t: Float, r: Float, b: Float, radius: Float) {
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(RectF(l, t, r, b), radius, radius, Path.Direction.CW) })
    }

    override fun unclip() = canvas.restore()

    override fun backdrop(l: Float, t: Float, r: Float, b: Float): Boolean {
        val img = backdropImage ?: return false
        paint.shader = null; paint.color = 0xFF000000.toInt(); paint.style = Paint.Style.FILL
        canvas.drawBitmap(img, null, RectF(l, t, r, b), paint)
        return true
    }

    override fun pixels(argb: IntArray, w: Int, h: Int, l: Float, t: Float, r: Float, b: Float) {
        val bmp = Bitmap.createBitmap(argb, w, h, Bitmap.Config.ARGB_8888)
        paint.shader = null; paint.color = 0xFF000000.toInt(); paint.style = Paint.Style.FILL
        canvas.drawBitmap(bmp, null, RectF(l, t, r, b), paint)
        bmp.recycle()
    }
}

object ShareRenderer {
    /** Dibuja la imagen para compartir en el formato pedido. */
    fun render(data: ShareCardData, format: ShareFormat, satellite: Bitmap?): Bitmap {
        val bitmap = Bitmap.createBitmap(format.width, format.height, Bitmap.Config.ARGB_8888)
        ShareCard.draw(AndroidSurface(Canvas(bitmap), format.width, format.height, satellite), data)
        return bitmap
    }
}
