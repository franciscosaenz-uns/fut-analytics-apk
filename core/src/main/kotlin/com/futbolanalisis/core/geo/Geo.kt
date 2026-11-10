package com.futbolanalisis.core.geo

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    const val EARTH_RADIUS_M = 6_371_008.8

    /** Distancia en metros entre dos coordenadas (fórmula del haversine). */
    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}

/**
 * Proyección local equirectangular alrededor de un origen. En la escala de una cancha
 * (~100 m) el error es despreciable. x = metros al este, y = metros al norte.
 */
class LocalProjection(val originLat: Double, val originLon: Double) {
    private val cosLat = cos(Math.toRadians(originLat))

    fun toLocal(lat: Double, lon: Double): Vec2 {
        val x = Math.toRadians(lon - originLon) * cosLat * Geo.EARTH_RADIUS_M
        val y = Math.toRadians(lat - originLat) * Geo.EARTH_RADIUS_M
        return Vec2(x, y)
    }

    fun toLatLon(p: Vec2): Pair<Double, Double> {
        val lat = originLat + Math.toDegrees(p.y / Geo.EARTH_RADIUS_M)
        val lon = originLon + Math.toDegrees(p.x / (Geo.EARTH_RADIUS_M * cosLat))
        return lat to lon
    }
}

data class Vec2(val x: Double, val y: Double) {
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun times(k: Double) = Vec2(x * k, y * k)
    fun dot(o: Vec2) = x * o.x + y * o.y
    fun length() = sqrt(x * x + y * y)
}
