package com.futbolanalisis.core.render

import com.futbolanalisis.core.tactical.Heatmap
import kotlin.math.floor

/**
 * Convierte el mapa de calor (cuadrícula gruesa) en una imagen ARGB suave. Es Kotlin puro
 * para que la app y la imagen para compartir usen exactamente el mismo dibujo.
 */
object HeatmapRenderer {
    private val stops = listOf(
        0.00 to 0x1FA2FF, 0.30 to 0x21D4A7, 0.50 to 0x8BE04E, 0.68 to 0xFFE53B, 0.84 to 0xFF8A1F, 1.00 to 0xFF2D2D,
    )

    /**
     * Píxeles ARGB (no premultiplicados) de [width] × [height]. La fila 0 es la banda izquierda
     * (y = ancho de la cancha), igual que en pantalla. [strong] sube la opacidad para fondos
     * con mucha textura, como la vista satelital.
     */
    fun argb(h: Heatmap, width: Int, height: Int, strong: Boolean = false): IntArray {
        val values = FloatArray(width * height)
        var max = 0f
        for (py in 0 until height) {
            val fy = (height - 1 - py + 0.5) / height * h.rows - 0.5
            for (px in 0 until width) {
                val fx = (px + 0.5) / width * h.cols - 0.5
                val v = bspline(h, fx, fy).toFloat()
                values[py * width + px] = v
                if (v > max) max = v
            }
        }
        val out = IntArray(width * height)
        if (max <= 0f) return out
        val maxAlpha = if (strong) 0.92 else 0.86
        for (i in values.indices) {
            val v = (values[i] / max).toDouble()
            val a = smoothstep(0.035, if (strong) 0.36 else 0.46, v) * maxAlpha
            if (a <= 0.004) continue
            out[i] = ((a * 255).toInt() shl 24) or color(v)
        }
        return out
    }

    private fun smoothstep(e0: Double, e1: Double, x: Double): Double {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0.0, 1.0)
        return t * t * (3 - 2 * t)
    }

    fun color(v: Double): Int {
        for (i in 1 until stops.size) {
            val (p1, c1) = stops[i - 1]; val (p2, c2) = stops[i]
            if (v <= p2) {
                val t = ((v - p1) / (p2 - p1)).coerceIn(0.0, 1.0)
                fun mix(shift: Int): Int {
                    val a = (c1 shr shift) and 0xFF; val b = (c2 shr shift) and 0xFF
                    return (a + (b - a) * t).toInt()
                }
                return (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
            }
        }
        return stops.last().second
    }

    /** Interpolación B-spline cúbica: más suave que la bilineal y sin sobrepasar los valores. */
    private fun bspline(h: Heatmap, fx: Double, fy: Double): Double {
        val x0 = floor(fx).toInt(); val y0 = floor(fy).toInt()
        val wx = weights(fx - x0); val wy = weights(fy - y0)
        var sum = 0.0
        for (j in 0..3) {
            val r = (y0 - 1 + j).coerceIn(0, h.rows - 1)
            var row = 0.0
            for (i in 0..3) {
                val c = (x0 - 1 + i).coerceIn(0, h.cols - 1)
                row += wx[i] * h[c, r]
            }
            sum += wy[j] * row
        }
        return sum
    }

    private fun weights(t: Double): DoubleArray {
        val t2 = t * t; val t3 = t2 * t
        return doubleArrayOf(
            (1 - 3 * t + 3 * t2 - t3) / 6,
            (3 * t3 - 6 * t2 + 4) / 6,
            (-3 * t3 + 3 * t2 + 3 * t + 1) / 6,
            t3 / 6,
        )
    }
}
