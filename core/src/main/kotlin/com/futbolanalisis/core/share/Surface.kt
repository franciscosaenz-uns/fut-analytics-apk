package com.futbolanalisis.core.share

enum class Weight { REGULAR, MEDIUM, BOLD, BLACK }
enum class Align { LEFT, CENTER, RIGHT }

/** [tracking] es el espaciado entre letras en "em" (0.1 = 10% del tamaño). */
data class Font(val size: Float, val weight: Weight = Weight.REGULAR, val condensed: Boolean = false, val tracking: Float = 0f)

/**
 * Lienzo mínimo para dibujar la imagen de compartir. La app lo implementa con android.graphics
 * y las pruebas con Java2D, así el diseño se puede revisar sin un teléfono.
 * Colores en ARGB (0xAARRGGBB). Ángulos en grados, en sentido horario desde las 3 en punto.
 */
interface Surface {
    val width: Float
    val height: Float
    fun gradientRect(l: Float, t: Float, r: Float, b: Float, top: Int, bottom: Int)
    /** [stroke] = 0 rellena; > 0 dibuja solo el borde con ese grosor. */
    fun rect(l: Float, t: Float, r: Float, b: Float, color: Int, radius: Float = 0f, stroke: Float = 0f)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float)
    fun circle(cx: Float, cy: Float, radius: Float, color: Int, stroke: Float = 0f)
    fun arc(cx: Float, cy: Float, radius: Float, startDeg: Float, sweepDeg: Float, color: Int, stroke: Float)
    /** Polígono cerrado; [points] = x0, y0, x1, y1... */
    fun polygon(points: FloatArray, color: Int, stroke: Float = 0f)
    fun text(text: String, x: Float, baseline: Float, font: Font, color: Int, align: Align = Align.LEFT)
    fun textWidth(text: String, font: Font): Float
    fun clip(l: Float, t: Float, r: Float, b: Float, radius: Float)
    fun unclip()
    /** Dibuja la imagen de fondo (vista satelital) si existe. Devuelve false si no hay. */
    fun backdrop(l: Float, t: Float, r: Float, b: Float): Boolean
    fun pixels(argb: IntArray, w: Int, h: Int, l: Float, t: Float, r: Float, b: Float)
}
