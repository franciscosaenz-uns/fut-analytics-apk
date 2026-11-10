package com.futbolanalisis.core

import com.futbolanalisis.core.io.GpxParser
import com.futbolanalisis.core.io.GpxWriter
import com.futbolanalisis.core.model.PlayerProfile
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Genera un partido de ejemplo, lo analiza y escribe GPX, JSON, PNG del mapa de calor y un resumen. */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "build/demo").apply { mkdirs() }
    val gpx = GpxWriter.write(SyntheticMatch.generate())
    File(out, "sample_match.gpx").writeText(gpx)
    val track = GpxParser.parse(gpx.byteInputStream())
    val report = MatchAnalyzer.analyze(track, PlayerProfile(age = 30))
    File(out, "report.json").writeText(MatchAnalyzer.json.encodeToString(MatchReport.serializer(), report))
    File(out, "summary.txt").writeText(TextSummary.of(report))
    ImageIO.write(renderHeatmap(report), "png", File(out, "heatmap.png"))
    println(TextSummary.of(report))
    println("Archivos en ${out.absolutePath}")
}

private fun renderHeatmap(r: MatchReport): BufferedImage {
    val hm = r.tactical.heatmap
    val scale = 10
    val w = (hm.dims.length * scale).toInt(); val h = (hm.dims.width * scale).toInt()
    val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.color = Color(0x2E7D32); g.fillRect(0, 0, w, h)
    for (py in 0 until h) for (px in 0 until w) {
        val v = bilinear(hm.values, hm.cols, hm.rows, px.toDouble() / w * hm.cols - 0.5, (1 - py.toDouble() / h) * hm.rows - 0.5)
        if (v < 0.08) continue
        val c = ramp(v)
        val base = Color(img.getRGB(px, py))
        val al = (0.25 + 0.6 * v).coerceAtMost(0.85)
        img.setRGB(px, py, Color(
            (c.red * al + base.red * (1 - al)).toInt(),
            (c.green * al + base.green * (1 - al)).toInt(),
            (c.blue * al + base.blue * (1 - al)).toInt(),
        ).rgb)
    }
    g.color = Color.WHITE; g.stroke = BasicStroke(3f)
    fun x(m: Double) = (m * scale).toInt()
    fun y(m: Double) = ((hm.dims.width - m) * scale).toInt()
    g.drawRect(2, 2, w - 4, h - 4); g.drawLine(w / 2, 0, w / 2, h)
    g.drawOval(w / 2 - x(9.15), h / 2 - x(9.15), x(18.3), x(18.3))
    g.drawRect(0, y(34 + 20.16), x(16.5), x(40.32)); g.drawRect(w - x(16.5), y(34 + 20.16), x(16.5), x(40.32))
    val avg = r.tactical.averagePosition
    g.color = Color.WHITE; g.fill(Ellipse2D.Double(x(avg.x) - 9.0, y(avg.y) - 9.0, 18.0, 18.0))
    g.dispose()
    return img
}

internal fun bilinear(v: List<Double>, cols: Int, rows: Int, fx: Double, fy: Double): Double {
    val x0 = kotlin.math.floor(fx).toInt(); val y0 = kotlin.math.floor(fy).toInt()
    val tx = fx - x0; val ty = fy - y0
    fun at(c: Int, r: Int) = v[r.coerceIn(0, rows - 1) * cols + c.coerceIn(0, cols - 1)]
    return (at(x0, y0) * (1 - tx) + at(x0 + 1, y0) * tx) * (1 - ty) + (at(x0, y0 + 1) * (1 - tx) + at(x0 + 1, y0 + 1) * tx) * ty
}

private fun ramp(v: Double): Color {
    val stops = listOf(0.0 to Color(0x2196F3), 0.35 to Color(0x4CAF50), 0.6 to Color(0xFFEB3B), 0.8 to Color(0xFF9800), 1.0 to Color(0xF44336))
    for (i in 1 until stops.size) {
        val (p1, c1) = stops[i - 1]; val (p2, c2) = stops[i]
        if (v <= p2) {
            val t = ((v - p1) / (p2 - p1)).coerceIn(0.0, 1.0)
            return Color((c1.red + (c2.red - c1.red) * t).toInt(), (c1.green + (c2.green - c1.green) * t).toInt(), (c1.blue + (c2.blue - c1.blue) * t).toInt())
        }
    }
    return stops.last().second
}
