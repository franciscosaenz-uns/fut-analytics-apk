package com.futbolanalisis.core.share

import com.futbolanalisis.core.insights.Attribute
import com.futbolanalisis.core.insights.PerformanceScore
import com.futbolanalisis.core.pitch.PitchDimensions
import com.futbolanalisis.core.render.HeatmapRenderer
import com.futbolanalisis.core.tactical.Heatmap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class ShareStat(val label: String, val value: String, val unit: String = "")

enum class ShareFormat(val label: String, val width: Int, val height: Int) {
    POST("Publicación 4:5", 1080, 1350),
    STORY("Historia 9:16", 1080, 1920),
    SQUARE("Cuadrada 1:1", 1080, 1080),
}

/** Todo lo que aparece en la imagen para compartir. */
data class ShareCardData(
    val title: String,
    val ownTeam: String = "",
    val rival: String = "",
    val goalsFor: Int? = null,
    val goalsAgainst: Int? = null,
    val dateText: String,
    val playerName: String,
    val role: String,
    val score: PerformanceScore?,
    /** Hasta 4 datos destacados. */
    val stats: List<ShareStat>,
    val heatmap: Heatmap,
    /** Margen en metros alrededor de la cancha que cubre la imagen satelital. */
    val satelliteMarginM: Double = 6.0,
    val attribution: String? = null,
    val brand: String = "FÚTBOL ANÁLISIS",
    val showPitchLines: Boolean = true,
)

object ShareCard {
    const val BG_TOP = 0xFF0A0E13.toInt()
    const val BG_BOTTOM = 0xFF111A24.toInt()
    const val PANEL = 0xFF18222E.toInt()
    const val OUTLINE = 0xFF263445.toInt()
    const val ACCENT = 0xFFC5F82A.toInt()
    const val TEXT = 0xFFF3F6F8.toInt()
    const val MUTED = 0xFF8D9BAB.toInt()
    const val TURF = 0xFF15402B.toInt()
    const val TURF_ALT = 0xFF184A31.toInt()

    private fun alpha(color: Int, a: Float) = (color and 0x00FFFFFF) or ((a * 255).toInt().coerceIn(0, 255) shl 24)

    fun draw(s: Surface, d: ShareCardData) {
        val w = s.width; val h = s.height
        val ratio = h / w
        val story = ratio > 1.5f
        val square = ratio < 1.1f
        val pad = 64f
        s.gradientRect(0f, 0f, w, h, BG_TOP, BG_BOTTOM)
        // Detalle de marca: arco de círculo central muy tenue.
        s.circle(w * 0.92f, h * 0.04f, w * 0.42f, alpha(ACCENT, 0.05f), stroke = 3f)
        s.circle(w * 0.92f, h * 0.04f, w * 0.30f, alpha(ACCENT, 0.04f), stroke = 3f)

        var y = if (story) 250f else if (square) 44f else 60f
        val gap = if (story) 56f else if (square) 22f else 34f

        // Marca y fecha.
        s.rect(pad, y, pad + 10f, y + 30f, ACCENT, radius = 3f)
        s.text(d.brand, pad + 24f, y + 25f, Font(24f, Weight.BOLD, tracking = 0.18f), TEXT)
        s.text(d.dateText.uppercase(), w - pad, y + 25f, Font(22f, Weight.MEDIUM, tracking = 0.08f), MUTED, Align.RIGHT)
        y += 30f + gap

        // Marcador o título.
        y = header(s, d, pad, y, w, compact = square)
        y += gap * 0.8f

        // Cancha con mapa de calor.
        val m = d.satelliteMarginM.toFloat()
        val dims = d.heatmap.dims
        val blockW = w - 2 * pad
        val aspect = ((dims.length + 2 * m) / (dims.width + 2 * m)).toFloat()
        var blockH = blockW / aspect
        val reserved = if (story) 640f else if (square) 300f else 346f
        val maxH = h - y - reserved
        var blockL = pad
        var bw = blockW
        if (blockH > maxH) { blockH = maxH; bw = blockH * aspect; blockL = (w - bw) / 2 }
        pitchBlock(s, d, blockL, y, blockL + bw, y + blockH, dims, m)
        y += blockH + gap

        // Índice + datos.
        val ringR = if (square) 62f else 78f
        val rowH = ringR * 2
        var statsLeft = pad
        d.score?.let { sc ->
            ring(s, pad + ringR, y + ringR, ringR, sc.overall)
            statsLeft = pad + ringR * 2 + 44f
        }
        val stats = d.stats.take(4)
        if (story) {
            // 2 × 2 a la derecha del anillo.
            val colW = (w - pad - statsLeft) / 2
            stats.forEachIndexed { i, st ->
                stat(s, st, statsLeft + (i % 2) * colW, y + (i / 2) * (rowH / 2 + 26f) + 4f, big = true)
            }
            y += rowH + 96f
        } else {
            val colW = (w - pad - statsLeft) / stats.size.coerceAtLeast(1)
            stats.forEachIndexed { i, st -> stat(s, st, statsLeft + i * colW, y + (rowH - 112f) / 2, big = false) }
            y += rowH + gap
        }

        // Atributos.
        d.score?.let { sc ->
            val n = Attribute.entries.size
            val colGap = 18f
            val colW = (w - 2 * pad - colGap * (n - 1)) / n
            Attribute.entries.forEachIndexed { i, a ->
                val x = pad + i * (colW + colGap)
                val v = sc[a]
                s.text(a.short, x, y + 26f, Font(22f, Weight.BOLD, tracking = 0.12f), MUTED)
                s.text("$v", x + colW, y + 28f, Font(34f, Weight.BLACK, condensed = true), TEXT, Align.RIGHT)
                s.rect(x, y + 42f, x + colW, y + 52f, OUTLINE, radius = 5f)
                s.rect(x, y + 42f, x + colW * (v / 99f).coerceIn(0.04f, 1f), y + 52f, ACCENT, radius = 5f)
            }
        }

        // Pie.
        val footY = if (story) h - 210f else h - 30f
        s.text("Analizado con Fútbol Análisis · datos GPS del reloj", w / 2, footY, Font(20f, Weight.MEDIUM, tracking = 0.04f), alpha(MUTED, 0.85f), Align.CENTER)
    }

    private fun fit(s: Surface, text: String, maxWidth: Float, start: Float, min: Float, weight: Weight, condensed: Boolean = true): Pair<String, Font> {
        var size = start
        while (size > min && s.textWidth(text, Font(size, weight, condensed)) > maxWidth) size -= 2f
        val font = Font(size, weight, condensed)
        var t = text
        while (t.length > 3 && s.textWidth(t, font) > maxWidth) t = t.dropLast(2).trimEnd() + "…"
        return t to font
    }

    private fun header(s: Surface, d: ShareCardData, pad: Float, top: Float, w: Float, compact: Boolean): Float {
        var y = top
        val hasTeams = d.ownTeam.isNotBlank() && d.rival.isNotBlank()
        val hasScore = d.goalsFor != null && d.goalsAgainst != null
        if (hasTeams) {
            val kicker = if (hasScore) when {
                d.goalsFor!! > d.goalsAgainst!! -> "VICTORIA"
                d.goalsFor < d.goalsAgainst -> "DERROTA"
                else -> "EMPATE"
            } else "PARTIDO"
            s.text(kicker, w / 2, y + 22f, Font(22f, Weight.BOLD, tracking = 0.3f), ACCENT, Align.CENTER)
            y += 40f
            val pillW = if (hasScore) 220f else 120f
            val pillH = if (compact) 84f else 100f
            val cx = w / 2
            s.rect(cx - pillW / 2, y, cx + pillW / 2, y + pillH, PANEL, radius = 24f)
            s.rect(cx - pillW / 2, y, cx + pillW / 2, y + pillH, OUTLINE, radius = 24f, stroke = 2f)
            val mid = y + pillH / 2
            if (hasScore) {
                s.text("${d.goalsFor} - ${d.goalsAgainst}", cx, mid + 25f, Font(72f, Weight.BLACK, condensed = true), TEXT, Align.CENTER)
            } else {
                s.text("VS", cx, mid + 16f, Font(44f, Weight.BLACK, condensed = true), MUTED, Align.CENTER)
            }
            val maxName = w / 2 - pad - pillW / 2 - 28f
            val (home, hf) = fit(s, d.ownTeam.trim().uppercase(), maxName, 58f, 34f, Weight.BLACK)
            val (away, af) = fit(s, d.rival.trim().uppercase(), maxName, 58f, 34f, Weight.BLACK)
            val font = if (hf.size < af.size) hf else af
            val (home2, _) = fit(s, home, maxName, font.size, font.size, Weight.BLACK)
            val (away2, _) = fit(s, away, maxName, font.size, font.size, Weight.BLACK)
            s.text(home2, cx - pillW / 2 - 28f, mid + font.size * 0.35f, font, TEXT, Align.RIGHT)
            s.text(away2, cx + pillW / 2 + 28f, mid + font.size * 0.35f, font, alpha(TEXT, 0.82f), Align.LEFT)
            y += pillH + 30f
        } else {
            val (title, font) = fit(s, d.title.uppercase(), w - 2 * pad, 76f, 40f, Weight.BLACK)
            s.text(title, pad, y + font.size * 0.82f, font, TEXT)
            y += font.size + 18f
        }
        val line = listOf(d.playerName, d.role).filter { it.isNotBlank() }.joinToString("  ·  ")
        val (player, pf) = fit(s, line, w - 2 * pad, 28f, 22f, Weight.MEDIUM, condensed = false)
        if (hasTeams) s.text(player, w / 2, y + 22f, pf, MUTED, Align.CENTER) else s.text(player, pad, y + 22f, pf, MUTED)
        return y + 30f
    }

    private fun pitchBlock(s: Surface, d: ShareCardData, l: Float, t: Float, r: Float, b: Float, dims: PitchDimensions, m: Float) {
        val radius = 28f
        s.clip(l, t, r, b, radius)
        val scale = (r - l) / (dims.length.toFloat() + 2 * m)
        val pl = l + m * scale; val pt = t + m * scale
        val pr = r - m * scale; val pb = b - m * scale
        val satellite = s.backdrop(l, t, r, b)
        if (!satellite) {
            s.rect(l, t, r, b, 0xFF0F2E1F.toInt())
            val stripes = 12
            val sw = (pr - pl) / stripes
            for (i in 0 until stripes) s.rect(pl + i * sw, pt, pl + (i + 1) * sw, pb, if (i % 2 == 0) TURF else TURF_ALT)
        } else {
            // Oscurece un poco la foto para que el calor y los textos resalten.
            s.rect(l, t, r, b, 0x47000000)
        }
        val hw = 420; val hh = (hw * dims.width / dims.length).toInt()
        s.pixels(HeatmapRenderer.argb(d.heatmap, hw, hh, strong = satellite), hw, hh, pl, pt, pr, pb)
        if (d.showPitchLines) {
            PitchArt.lines(s, pl, pt, pr, pb, dims, alpha(0xFFFFFFFF.toInt(), if (satellite) 0.55f else 0.8f), 3f)
        }
        // Etiquetas.
        val tagFont = Font(20f, Weight.BOLD, tracking = 0.14f)
        val tag = "ATAQUE →"
        val tw = s.textWidth(tag, tagFont)
        s.rect(r - tw - 44f, t + 18f, r - 18f, t + 58f, 0xB30A0E13.toInt(), radius = 20f)
        s.text(tag, r - 31f, t + 45f, tagFont, TEXT, Align.RIGHT)
        val hm = "MAPA DE CALOR"
        val hmw = s.textWidth(hm, tagFont)
        s.rect(l + 18f, t + 18f, l + 44f + hmw, t + 58f, 0xB30A0E13.toInt(), radius = 20f)
        s.text(hm, l + 31f, t + 45f, tagFont, ACCENT)
        d.attribution?.takeIf { satellite }?.let {
            s.text(it, r - 16f, b - 14f, Font(15f, Weight.REGULAR), alpha(0xFFFFFFFF.toInt(), 0.7f), Align.RIGHT)
        }
        s.unclip()
        s.rect(l, t, r, b, alpha(0xFFFFFFFF.toInt(), 0.10f), radius = radius, stroke = 2f)
    }

    private fun ring(s: Surface, cx: Float, cy: Float, r: Float, value: Int) {
        val stroke = r * 0.16f
        s.circle(cx, cy, r - stroke / 2, OUTLINE, stroke = stroke)
        s.arc(cx, cy, r - stroke / 2, -90f, 360f * (value / 99f).coerceIn(0f, 1f), ACCENT, stroke)
        s.text("$value", cx, cy + r * 0.20f, Font(r * 0.78f, Weight.BLACK, condensed = true), TEXT, Align.CENTER)
        s.text("ÍNDICE", cx, cy + r * 0.52f, Font(r * 0.19f, Weight.BOLD, tracking = 0.16f), MUTED, Align.CENTER)
    }

    private fun stat(s: Surface, st: ShareStat, x: Float, top: Float, big: Boolean) {
        val vf = Font(if (big) 64f else 58f, Weight.BLACK, condensed = true)
        val lf = Font(20f, Weight.BOLD, tracking = 0.12f)
        s.text(st.label.uppercase(), x, top + 22f, lf, MUTED)
        s.text(st.value, x, top + 22f + vf.size * 0.98f, vf, TEXT)
        if (st.unit.isNotBlank()) {
            if (big) {
                s.text(st.unit, x + s.textWidth(st.value, vf) + 8f, top + 22f + vf.size * 0.98f, Font(24f, Weight.MEDIUM), MUTED)
            } else {
                // En fila de 4 no hay ancho para la unidad junto al valor: va debajo.
                s.text(st.unit, x, top + 22f + vf.size * 0.98f + 28f, Font(20f, Weight.MEDIUM), MUTED)
            }
        }
    }

    /** Polígono del radar de atributos (útil también para pruebas). */
    fun radarPoints(cx: Float, cy: Float, radius: Float, values: List<Int>): FloatArray {
        val n = values.size
        val out = FloatArray(n * 2)
        for (i in 0 until n) {
            val ang = -PI / 2 + 2 * PI * i / n
            val rr = radius * (values[i] / 99f).coerceIn(0f, 1f)
            out[i * 2] = cx + (rr * cos(ang)).toFloat()
            out[i * 2 + 1] = cy + (rr * sin(ang)).toFloat()
        }
        return out
    }
}

/** Líneas reglamentarias de la cancha sobre un rectángulo. */
object PitchArt {
    fun lines(s: Surface, l: Float, t: Float, r: Float, b: Float, dims: PitchDimensions, color: Int, stroke: Float) {
        val k = (r - l) / dims.length.toFloat()
        fun x(m: Double) = l + m.toFloat() * k
        fun y(m: Double) = t + (dims.width - m).toFloat() * k
        val cy = dims.width / 2
        s.rect(l, t, r, b, color, stroke = stroke)
        s.line(x(dims.length / 2), t, x(dims.length / 2), b, color, stroke)
        s.circle(x(dims.length / 2), y(cy), 9.15f * k, color, stroke = stroke)
        s.circle(x(dims.length / 2), y(cy), stroke * 1.4f, color)
        for (own in listOf(true, false)) {
            val boxL = if (own) 0.0 else dims.length - 16.5
            val smallL = if (own) 0.0 else dims.length - 5.5
            s.rect(x(boxL), y(cy + 20.16), x(boxL + 16.5), y(cy - 20.16), color, stroke = stroke)
            s.rect(x(smallL), y(cy + 9.16), x(smallL + 5.5), y(cy - 9.16), color, stroke = stroke)
            s.circle(x(if (own) 11.0 else dims.length - 11.0), y(cy), stroke * 1.2f, color)
            // Medialuna del área.
            val px = x(if (own) 11.0 else dims.length - 11.0)
            s.arc(px, y(cy), 9.15f * k, if (own) -53f else 127f, 106f, color, stroke)
        }
    }
}
