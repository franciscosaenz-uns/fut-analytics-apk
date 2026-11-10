package com.futbolanalisis.core.pitch

import com.futbolanalisis.core.geo.LocalProjection
import com.futbolanalisis.core.geo.Vec2
import com.futbolanalisis.core.model.TrackPoint
import kotlinx.serialization.Serializable
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Serializable
data class PitchDimensions(val length: Double = 105.0, val width: Double = 68.0)

/**
 * Coordenadas en la cancha, en metros.
 * x: 0 = línea de fondo propia, length = línea de fondo rival (dirección de ataque).
 * y: 0 = banda derecha, width = banda izquierda (mirando hacia el arco rival).
 */
@Serializable
data class PitchPoint(val x: Double, val y: Double)

@Serializable
enum class CalibrationMethod {
    /** Orientación por PCA del recorrido y extensión por percentiles. Aproximada. */
    AUTO,
    /** Tres esquinas reales de la cancha. Precisa. */
    CORNERS,
}

/**
 * Transformación afín de coordenadas GPS a la cancha: pitch = M · local + offset.
 * [attackFlipped] rota 180° (equivale a cambiar la dirección de ataque sin reflejar
 * izquierda/derecha, porque el mundo real no se refleja).
 */
@Serializable
data class PitchCalibration(
    val originLat: Double,
    val originLon: Double,
    val m00: Double, val m01: Double,
    val m10: Double, val m11: Double,
    val offX: Double, val offY: Double,
    val dims: PitchDimensions = PitchDimensions(),
    val method: CalibrationMethod = CalibrationMethod.AUTO,
    val attackFlipped: Boolean = false,
) {
    @kotlinx.serialization.Transient
    private val projection = LocalProjection(originLat, originLon)

    fun toPitch(lat: Double, lon: Double, clamp: Boolean = true): PitchPoint {
        val l = projection.toLocal(lat, lon)
        var x = m00 * l.x + m01 * l.y + offX
        var y = m10 * l.x + m11 * l.y + offY
        if (attackFlipped) {
            x = dims.length - x
            y = dims.width - y
        }
        if (clamp) {
            x = x.coerceIn(0.0, dims.length)
            y = y.coerceIn(0.0, dims.width)
        }
        return PitchPoint(x, y)
    }

    fun toPitch(p: TrackPoint, clamp: Boolean = true) = toPitch(p.lat, p.lon, clamp)

    fun withAttackFlipped(flipped: Boolean) = copy(attackFlipped = flipped)

    companion object {
        /**
         * Calibración automática. El eje principal del recorrido (PCA) se toma como el largo
         * de la cancha, y el rango entre los percentiles [lowPct] y 100-[lowPct] se ajusta al
         * tamaño de la cancha. Funciona bien para jugadores que recorren casi todo el campo
         * (mediocampistas, laterales); para porteros o centrales conviene usar esquinas.
         */
        fun auto(
            points: List<TrackPoint>,
            dims: PitchDimensions = PitchDimensions(),
            lowPct: Double = 2.0,
        ): PitchCalibration {
            require(points.size >= 10) { "Se necesitan al menos 10 puntos para calibrar" }
            val originLat = points.map { it.lat }.average()
            val originLon = points.map { it.lon }.average()
            val proj = LocalProjection(originLat, originLon)
            val local = points.map { proj.toLocal(it.lat, it.lon) }

            val mx = local.sumOf { it.x } / local.size
            val my = local.sumOf { it.y } / local.size
            var sxx = 0.0; var syy = 0.0; var sxy = 0.0
            for (p in local) {
                val dx = p.x - mx; val dy = p.y - my
                sxx += dx * dx; syy += dy * dy; sxy += dx * dy
            }
            val angle = 0.5 * atan2(2 * sxy, sxx - syy)
            val c = cos(angle); val s = sin(angle)
            // u: eje largo, v: eje corto (v está 90° a la izquierda de u, sistema dextrógiro).
            val us = local.map { c * it.x + s * it.y }.sorted()
            val vs = local.map { -s * it.x + c * it.y }.sorted()
            val uMin = percentile(us, lowPct); val uMax = percentile(us, 100 - lowPct)
            val vMin = percentile(vs, lowPct); val vMax = percentile(vs, 100 - lowPct)
            val ku = dims.length / (uMax - uMin).coerceAtLeast(1.0)
            val kv = dims.width / (vMax - vMin).coerceAtLeast(1.0)
            // x = ku (u - uMin); y = kv (v - vMin).
            return PitchCalibration(
                originLat, originLon,
                m00 = ku * c, m01 = ku * s,
                m10 = -kv * s, m11 = kv * c,
                offX = -ku * uMin, offY = -kv * vMin,
                dims = dims, method = CalibrationMethod.AUTO,
            )
        }

        /**
         * Calibración por esquinas, vistas desde tu arco mirando hacia el rival:
         * [ownRight] esquina derecha de tu línea de fondo, [ownLeft] esquina izquierda de tu
         * línea de fondo y [rivalRight] esquina derecha de la línea de fondo rival.
         * Cada par es (lat, lon).
         */
        fun fromCorners(
            ownRight: Pair<Double, Double>,
            ownLeft: Pair<Double, Double>,
            rivalRight: Pair<Double, Double>,
            dims: PitchDimensions = PitchDimensions(),
        ): PitchCalibration {
            val proj = LocalProjection(ownRight.first, ownRight.second)
            val o = Vec2(0.0, 0.0)
            val wVec = proj.toLocal(ownLeft.first, ownLeft.second) - o // hacia y = width
            val lVec = proj.toLocal(rivalRight.first, rivalRight.second) - o // hacia x = length
            // Resolver [lVec wVec] · [x/L, y/W]^T = local  =>  inversa de la matriz 2x2.
            val det = lVec.x * wVec.y - wVec.x * lVec.y
            require(kotlin.math.abs(det) > 1e-6) { "Las esquinas no pueden estar alineadas" }
            val i00 = wVec.y / det; val i01 = -wVec.x / det
            val i10 = -lVec.y / det; val i11 = lVec.x / det
            return PitchCalibration(
                ownRight.first, ownRight.second,
                m00 = dims.length * i00, m01 = dims.length * i01,
                m10 = dims.width * i10, m11 = dims.width * i11,
                offX = 0.0, offY = 0.0,
                dims = dims, method = CalibrationMethod.CORNERS,
            )
        }

        internal fun percentile(sorted: List<Double>, pct: Double): Double {
            if (sorted.isEmpty()) return 0.0
            val rank = (pct / 100.0) * (sorted.size - 1)
            val lo = rank.toInt().coerceIn(0, sorted.size - 1)
            val hi = (lo + 1).coerceAtMost(sorted.size - 1)
            val f = rank - lo
            return sorted[lo] * (1 - f) + sorted[hi] * f
        }
    }
}
