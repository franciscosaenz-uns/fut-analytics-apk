package com.futbolanalisis.core.geo

import com.futbolanalisis.core.pitch.PitchCalibration
import com.futbolanalisis.core.pitch.PitchPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/** Proyección de los mapas web (teselas de 256 px). */
object WebMercator {
    const val TILE = 256

    fun pixelX(lon: Double, zoom: Int): Double = (lon + 180.0) / 360.0 * TILE * (1 shl zoom)

    fun pixelY(lat: Double, zoom: Int): Double {
        val rad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
        return (1 - ln(tan(rad) + 1 / cos(rad)) / PI) / 2 * TILE * (1 shl zoom)
    }

    /** Metros por píxel a esa latitud y zoom. */
    fun metersPerPixel(lat: Double, zoom: Int): Double =
        2 * PI * 6_378_137.0 * cos(Math.toRadians(lat)) / (TILE * (1 shl zoom))
}

data class TileRef(val x: Int, val y: Int, val zoom: Int)

/**
 * Qué teselas hay que descargar para ver la cancha desde el satélite y dónde caen, dentro del
 * mosaico de teselas, las cuatro esquinas del recuadro que se muestra en pantalla.
 */
data class SatellitePlan(
    val zoom: Int,
    val tileX: Int,
    val tileY: Int,
    val tilesX: Int,
    val tilesY: Int,
    /**
     * Píxeles (x, y) en el mosaico de las esquinas del recuadro, en este orden: arriba-izquierda,
     * arriba-derecha, abajo-derecha, abajo-izquierda (tal como se ve la cancha en la app,
     * atacando hacia la derecha).
     */
    val corners: List<Pair<Double, Double>>,
    val marginM: Double,
) {
    val tiles: List<TileRef>
        get() = (0 until tilesY).flatMap { j -> (0 until tilesX).map { i -> TileRef(tileX + i, tileY + j, zoom) } }
    val mosaicWidth: Int get() = tilesX * WebMercator.TILE
    val mosaicHeight: Int get() = tilesY * WebMercator.TILE

    /** Identificador estable para guardar la imagen en caché. */
    val cacheKey: String
        get() = "z${zoom}_${tileX}_${tileY}_" + corners.joinToString("_") { "${it.first.toInt()}x${it.second.toInt()}" }
}

object SatellitePlanner {
    /**
     * [attackFlipped] es el valor del análisis táctico (primer tiempo). El recuadro incluye
     * [marginM] metros alrededor de la cancha para dar contexto.
     */
    fun plan(calibration: PitchCalibration, attackFlipped: Boolean, marginM: Double = 6.0, zoom: Int = 19, maxTiles: Int = 20): SatellitePlan {
        val base = calibration.withAttackFlipped(false)
        val d = base.dims
        // Punto que se ve en pantalla -> punto real de la cancha calibrada.
        fun geo(x: Double, y: Double): Pair<Double, Double> {
            val p = if (attackFlipped) PitchPoint(d.length - x, d.width - y) else PitchPoint(x, y)
            return base.toLatLon(p)
        }
        val display = listOf(
            geo(-marginM, d.width + marginM), // arriba-izquierda (banda izquierda, fondo propio)
            geo(d.length + marginM, d.width + marginM),
            geo(d.length + marginM, -marginM),
            geo(-marginM, -marginM),
        )
        var z = zoom
        while (true) {
            val px = display.map { (lat, lon) -> WebMercator.pixelX(lon, z) to WebMercator.pixelY(lat, z) }
            val tx0 = floor(px.minOf { it.first } / WebMercator.TILE).toInt()
            val ty0 = floor(px.minOf { it.second } / WebMercator.TILE).toInt()
            val tx1 = floor(px.maxOf { it.first } / WebMercator.TILE).toInt()
            val ty1 = floor(px.maxOf { it.second } / WebMercator.TILE).toInt()
            val nx = tx1 - tx0 + 1; val ny = ty1 - ty0 + 1
            if (nx * ny <= maxTiles || z <= 15) {
                return SatellitePlan(
                    zoom = z, tileX = tx0, tileY = ty0, tilesX = nx, tilesY = ny,
                    corners = px.map { (it.first - tx0 * WebMercator.TILE) to (it.second - ty0 * WebMercator.TILE) },
                    marginM = marginM,
                )
            }
            z--
        }
    }
}
