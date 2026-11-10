package com.futbolanalisis.core

import com.futbolanalisis.core.share.Align
import com.futbolanalisis.core.share.Font
import com.futbolanalisis.core.share.Surface
import com.futbolanalisis.core.share.Weight
import java.awt.BasicStroke
import java.awt.Color
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.font.TextAttribute
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

/** Implementación Java2D de [Surface] para revisar el diseño de la imagen sin Android. */
class AwtSurface(w: Int, h: Int, private val backdropImage: BufferedImage? = null) : Surface {
    val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    private val g: Graphics2D = image.createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    }
    private val clips = ArrayDeque<Shape?>()
    override val width = w.toFloat()
    override val height = h.toFloat()

    private fun c(argb: Int) = Color(argb, true)
    private fun shape(l: Float, t: Float, r: Float, b: Float, radius: Float): Shape =
        if (radius > 0) RoundRectangle2D.Float(l, t, r - l, b - t, radius * 2, radius * 2) else Rectangle2D.Float(l, t, r - l, b - t)

    private fun awtFont(f: Font): java.awt.Font {
        val attrs = HashMap<TextAttribute, Any>()
        attrs[TextAttribute.FAMILY] = when (f.weight) {
            Weight.MEDIUM -> "Inter Medium"
            Weight.BLACK -> "Inter Black"
            else -> "Inter"
        }
        attrs[TextAttribute.SIZE] = f.size
        if (f.weight == Weight.BOLD) attrs[TextAttribute.WEIGHT] = TextAttribute.WEIGHT_BOLD
        attrs[TextAttribute.TRACKING] = f.tracking
        val font = java.awt.Font(attrs)
        // Android usa una variante condensada real; aquí se aproxima estrechando la fuente.
        return if (f.condensed) font.deriveFont(java.awt.geom.AffineTransform.getScaleInstance(0.86, 1.0)) else font
    }

    override fun gradientRect(l: Float, t: Float, r: Float, b: Float, top: Int, bottom: Int) {
        g.paint = GradientPaint(0f, t, c(top), 0f, b, c(bottom)); g.fill(Rectangle2D.Float(l, t, r - l, b - t))
    }

    override fun rect(l: Float, t: Float, r: Float, b: Float, color: Int, radius: Float, stroke: Float) {
        g.color = c(color)
        if (stroke > 0) { g.stroke = BasicStroke(stroke); g.draw(shape(l, t, r, b, radius)) } else g.fill(shape(l, t, r, b, radius))
    }

    override fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float) {
        g.color = c(color); g.stroke = BasicStroke(width); g.draw(Line2D.Float(x1, y1, x2, y2))
    }

    override fun circle(cx: Float, cy: Float, radius: Float, color: Int, stroke: Float) {
        g.color = c(color)
        val e = Ellipse2D.Float(cx - radius, cy - radius, radius * 2, radius * 2)
        if (stroke > 0) { g.stroke = BasicStroke(stroke); g.draw(e) } else g.fill(e)
    }

    override fun arc(cx: Float, cy: Float, radius: Float, startDeg: Float, sweepDeg: Float, color: Int, stroke: Float) {
        g.color = c(color); g.stroke = BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(Arc2D.Float(cx - radius, cy - radius, radius * 2, radius * 2, -startDeg, -sweepDeg, Arc2D.OPEN))
    }

    override fun polygon(points: FloatArray, color: Int, stroke: Float) {
        val p = Path2D.Float()
        for (i in points.indices step 2) if (i == 0) p.moveTo(points[0], points[1]) else p.lineTo(points[i], points[i + 1])
        p.closePath()
        g.color = c(color)
        if (stroke > 0) { g.stroke = BasicStroke(stroke); g.draw(p) } else g.fill(p)
    }

    override fun text(text: String, x: Float, baseline: Float, font: Font, color: Int, align: Align) {
        g.font = awtFont(font); g.color = c(color)
        val w = g.fontMetrics.stringWidth(text)
        val dx = when (align) { Align.LEFT -> 0f; Align.CENTER -> -w / 2f; Align.RIGHT -> -w.toFloat() }
        g.drawString(text, x + dx, baseline)
    }

    override fun textWidth(text: String, font: Font): Float = g.getFontMetrics(awtFont(font)).stringWidth(text).toFloat()

    override fun clip(l: Float, t: Float, r: Float, b: Float, radius: Float) {
        clips.addLast(g.clip); g.clip(shape(l, t, r, b, radius))
    }

    override fun unclip() { g.clip = clips.removeLast() }

    override fun backdrop(l: Float, t: Float, r: Float, b: Float): Boolean {
        val img = backdropImage ?: return false
        g.drawImage(img, l.toInt(), t.toInt(), (r - l).toInt(), (b - t).toInt(), null)
        return true
    }

    override fun pixels(argb: IntArray, w: Int, h: Int, l: Float, t: Float, r: Float, b: Float) {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        img.setRGB(0, 0, w, h, argb, 0, w)
        g.drawImage(img, l.toInt(), t.toInt(), (r - l).toInt(), (b - t).toInt(), null)
    }
}
