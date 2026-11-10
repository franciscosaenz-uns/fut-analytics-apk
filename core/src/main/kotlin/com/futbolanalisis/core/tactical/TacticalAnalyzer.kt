package com.futbolanalisis.core.tactical

import com.futbolanalisis.core.physical.Effort
import com.futbolanalisis.core.physical.Sample
import com.futbolanalisis.core.pitch.PitchCalibration
import com.futbolanalisis.core.pitch.PitchDimensions
import com.futbolanalisis.core.pitch.PitchPoint
import kotlinx.serialization.Serializable
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/** Mapa de calor ponderado por tiempo. values[row * cols + col]; row 0 = banda derecha (y=0). */
@Serializable
data class Heatmap(
    val cols: Int,
    val rows: Int,
    val values: List<Double>,
    val dims: PitchDimensions,
) {
    operator fun get(col: Int, row: Int) = values[row * cols + col]
}

/** Elipse de dispersión (≈95% del tiempo) alrededor de la posición media. */
@Serializable
data class PositionEllipse(
    val center: PitchPoint,
    val semiMajorM: Double,
    val semiMinorM: Double,
    /** Ángulo del eje mayor respecto al eje x de la cancha, en grados. */
    val angleDeg: Double,
    val areaM2: Double,
)

@Serializable
data class SprintVector(
    val start: PitchPoint,
    val end: PitchPoint,
    val period: Int,
    val direction: Direction,
    val maxSpeedKmh: Double,
)

@Serializable
enum class Direction(val label: String) { FORWARD("Hacia adelante"), BACKWARD("Hacia atrás"), LATERAL("Lateral") }

/** Zona caliente del mapa de calor (máximo local). */
@Serializable
data class Hotspot(val point: PitchPoint, val intensity: Double)

@Serializable
data class TacticalReport(
    /** Zona más frecuente (moda del mapa de calor): donde realmente más estuviste. */
    val modePosition: PitchPoint,
    val modePositionByPeriod: Map<Int, PitchPoint>,
    val hotspots: List<Hotspot>,
    /** Percentiles 10 y 90 de x (profundidad) e y (ancho) en metros. */
    val depthRangeM: List<Double>,
    val widthRangeM: List<Double>,
    val description: TacticalDescription,
    val distanceByThirdM: List<Double>,
    val sprintsIntoFinalThird: Int,
    val heatmap: Heatmap,
    val heatmapByPeriod: Map<Int, Heatmap>,
    /** % de tiempo por zona 3x3. Índice [tercio * 3 + carril]; tercio 0 = defensivo, carril 0 = derecho. */
    val zoneTimePct: List<Double>,
    val thirdsPct: List<Double>,
    val lanesPct: List<Double>,
    val penaltyBoxPct: Double,
    val ownBoxPct: Double,
    val averagePosition: PitchPoint,
    val averagePositionByPeriod: Map<Int, PitchPoint>,
    val ellipse: PositionEllipse,
    val estimatedRole: String,
    val sprintVectors: List<SprintVector>,
    val attackFlipped: Boolean,
) {
    companion object {
        val THIRD_LABELS = listOf("Defensivo", "Medio", "Ofensivo")
        val LANE_LABELS = listOf("Derecha", "Centro", "Izquierda")
    }
}

object TacticalAnalyzer {
    private const val MAX_WEIGHT_S = 5.0

    /**
     * El segundo tiempo se rota 180° para que todo el partido quede mirando hacia el mismo
     * arco. Si [attackFlipped] es null se decide con una heurística: en el primer tiempo
     * el jugador pasa más tiempo en su propia mitad (cierto para defensas y medios; un
     * delantero puede necesitar invertirlo a mano).
     */
    fun analyze(
        samples: List<Sample>,
        calibration: PitchCalibration,
        sprints: List<Effort>,
        attackFlipped: Boolean? = null,
        cols: Int = 21,
        rows: Int = 14,
    ): TacticalReport {
        require(samples.isNotEmpty()) { "Sin datos para el análisis táctico" }
        val dims = calibration.dims
        val firstPeriod = samples.minOf { it.period }
        val flip = attackFlipped ?: run {
            val first = samples.filter { it.period == firstPeriod }
            val meanX = first.map { calibration.toPitch(it.point).x }.average()
            meanX > dims.length / 2
        }
        val calA = calibration.withAttackFlipped(flip)
        val calB = calibration.withAttackFlipped(!flip)
        fun pitch(period: Int, lat: Double, lon: Double): PitchPoint =
            (if ((period - firstPeriod) % 2 == 0) calA else calB).toPitch(lat, lon)

        val positioned = samples.map { s ->
            Weighted(pitch(s.period, s.point.lat, s.point.lon), s.period, s.dtS.coerceIn(0.0, MAX_WEIGHT_S).let { if (it == 0.0) 1.0 else it })
        }

        val heatmap = heatmap(positioned, dims, cols, rows)
        val byPeriod = positioned.groupBy { it.period }.mapValues { (_, list) -> heatmap(list, dims, cols, rows) }

        val totalW = positioned.sumOf { it.w }
        val zone = DoubleArray(9)
        var box = 0.0; var ownBox = 0.0
        val boxHalfWidth = 20.16
        for (p in positioned) {
            val third = (p.pt.x / dims.length * 3).toInt().coerceIn(0, 2)
            val lane = (p.pt.y / dims.width * 3).toInt().coerceIn(0, 2)
            zone[third * 3 + lane] += p.w
            val inBand = kotlin.math.abs(p.pt.y - dims.width / 2) <= boxHalfWidth
            if (inBand && p.pt.x >= dims.length - 16.5) box += p.w
            if (inBand && p.pt.x <= 16.5) ownBox += p.w
        }
        val zonePct = zone.map { it / totalW * 100 }
        val thirds = (0..2).map { t -> zonePct[t * 3] + zonePct[t * 3 + 1] + zonePct[t * 3 + 2] }
        val lanes = (0..2).map { l -> zonePct[l] + zonePct[3 + l] + zonePct[6 + l] }

        val avg = mean(positioned)
        val avgByPeriod = positioned.groupBy { it.period }.mapValues { (_, l) -> mean(l) }
        val ellipse = ellipse(positioned, avg)
        val mode = modeOf(heatmap)
        val modeByPeriod = byPeriod.mapValues { (_, h) -> modeOf(h) }
        val hotspots = hotspots(heatmap)
        val depth = listOf(weightedPercentile(positioned, 0.1) { it.pt.x }, weightedPercentile(positioned, 0.9) { it.pt.x })
        val width = listOf(weightedPercentile(positioned, 0.1) { it.pt.y }, weightedPercentile(positioned, 0.9) { it.pt.y })

        // Distancia por tercio (con la posición del final de cada tramo).
        val distThird = DoubleArray(3)
        samples.forEachIndexed { i, s ->
            val third = (positioned[i].pt.x / dims.length * 3).toInt().coerceIn(0, 2)
            distThird[third] += s.distanceM
        }

        val vectors = sprints.map { e ->
            val a = pitch(e.period, e.startLat, e.startLon)
            val b = pitch(e.period, e.endLat, e.endLon)
            val dx = b.x - a.x; val dy = b.y - a.y
            val dir = when {
                kotlin.math.abs(dx) >= kotlin.math.abs(dy) && dx > 0 -> Direction.FORWARD
                kotlin.math.abs(dx) >= kotlin.math.abs(dy) -> Direction.BACKWARD
                else -> Direction.LATERAL
            }
            SprintVector(a, b, e.period, dir, e.maxSpeedKmh)
        }

        val role = estimateRole(mode, ellipse, dims)
        val description = TacticalDescriber.describe(
            role = role, mode = mode, average = avg, modeByPeriod = modeByPeriod, hotspots = hotspots,
            thirdsPct = thirds, lanesPct = lanes, boxPct = box / totalW * 100, depth = depth, width = width,
            sprints = vectors, ellipse = ellipse, dims = dims,
        )
        return TacticalReport(
            modePosition = mode,
            modePositionByPeriod = modeByPeriod,
            hotspots = hotspots,
            depthRangeM = depth,
            widthRangeM = width,
            description = description,
            distanceByThirdM = distThird.toList(),
            sprintsIntoFinalThird = vectors.count { it.end.x >= dims.length * 2 / 3 },
            heatmap = heatmap,
            heatmapByPeriod = byPeriod,
            zoneTimePct = zonePct,
            thirdsPct = thirds,
            lanesPct = lanes,
            penaltyBoxPct = box / totalW * 100,
            ownBoxPct = ownBox / totalW * 100,
            averagePosition = avg,
            averagePositionByPeriod = avgByPeriod,
            ellipse = ellipse,
            estimatedRole = role,
            sprintVectors = vectors,
            attackFlipped = flip,
        )
    }

    /** Moda: celda con el valor máximo, refinada con el centroide de su vecindad 3x3. */
    internal fun modeOf(h: Heatmap): PitchPoint {
        var best = 0
        for (i in h.values.indices) if (h.values[i] > h.values[best]) best = i
        val bc = best % h.cols; val br = best / h.cols
        var sx = 0.0; var sy = 0.0; var sw = 0.0
        for (r in (br - 1)..(br + 1)) for (c in (bc - 1)..(bc + 1)) {
            if (r !in 0 until h.rows || c !in 0 until h.cols) continue
            val w = h[c, r]
            sx += (c + 0.5) * w; sy += (r + 0.5) * w; sw += w
        }
        if (sw <= 0) return PitchPoint(h.dims.length / 2, h.dims.width / 2)
        return PitchPoint(sx / sw * h.dims.length / h.cols, sy / sw * h.dims.width / h.rows)
    }

    /** Máximos locales ≥ 55% del máximo, separados al menos 15 m. */
    internal fun hotspots(h: Heatmap): List<Hotspot> {
        val cands = ArrayList<Hotspot>()
        for (r in 0 until h.rows) for (c in 0 until h.cols) {
            val v = h[c, r]
            if (v < 0.55) continue
            var isMax = true
            for (dr in -1..1) for (dc in -1..1) {
                val rr = r + dr; val cc = c + dc
                if ((dr != 0 || dc != 0) && rr in 0 until h.rows && cc in 0 until h.cols && h[cc, rr] > v) isMax = false
            }
            if (isMax) cands += Hotspot(PitchPoint((c + 0.5) * h.dims.length / h.cols, (r + 0.5) * h.dims.width / h.rows), v)
        }
        val out = ArrayList<Hotspot>()
        for (hs in cands.sortedByDescending { it.intensity }) {
            if (out.none { kotlin.math.hypot(it.point.x - hs.point.x, it.point.y - hs.point.y) < 15 }) out += hs
            if (out.size == 3) break
        }
        return out
    }

    private fun weightedPercentile(list: List<Weighted>, q: Double, f: (Weighted) -> Double): Double {
        val sorted = list.sortedBy(f)
        val total = sorted.sumOf { it.w }
        var acc = 0.0
        for (p in sorted) {
            acc += p.w
            if (acc >= q * total) return f(p)
        }
        return f(sorted.last())
    }

    private data class Weighted(val pt: PitchPoint, val period: Int, val w: Double)

    private fun mean(list: List<Weighted>): PitchPoint {
        val w = list.sumOf { it.w }
        return PitchPoint(list.sumOf { it.pt.x * it.w } / w, list.sumOf { it.pt.y * it.w } / w)
    }

    private fun ellipse(list: List<Weighted>, c: PitchPoint): PositionEllipse {
        val w = list.sumOf { it.w }
        var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        for (p in list) {
            val dx = p.pt.x - c.x; val dy = p.pt.y - c.y
            sxx += p.w * dx * dx; syy += p.w * dy * dy; sxy += p.w * dx * dy
        }
        sxx /= w; syy /= w; sxy /= w
        val tr = sxx + syy
        val det = sxx * syy - sxy * sxy
        val disc = sqrt(max(0.0, tr * tr / 4 - det))
        val l1 = tr / 2 + disc
        val l2 = max(0.0, tr / 2 - disc)
        val chi95 = 5.991 // chi-cuadrado, 2 grados de libertad, 95%
        val a = sqrt(chi95 * l1)
        val b = sqrt(chi95 * l2)
        val angle = Math.toDegrees(0.5 * atan2(2 * sxy, sxx - syy))
        return PositionEllipse(c, a, b, angle, Math.PI * a * b)
    }

    private fun heatmap(list: List<Weighted>, dims: PitchDimensions, cols: Int, rows: Int): Heatmap {
        val raw = DoubleArray(cols * rows)
        for (p in list) {
            val c = (p.pt.x / dims.length * cols).toInt().coerceIn(0, cols - 1)
            val r = (p.pt.y / dims.width * rows).toInt().coerceIn(0, rows - 1)
            raw[r * cols + c] += p.w
        }
        val blurred = gaussianBlur(raw, cols, rows, sigma = 1.0)
        val maxV = blurred.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        return Heatmap(cols, rows, blurred.map { it / maxV }, dims)
    }

    internal fun gaussianBlur(src: DoubleArray, cols: Int, rows: Int, sigma: Double): DoubleArray {
        val radius = (sigma * 3).toInt().coerceAtLeast(1)
        val kernel = DoubleArray(2 * radius + 1) { i -> val d = i - radius; exp(-(d * d) / (2 * sigma * sigma)) }
        val ks = kernel.sum()
        for (i in kernel.indices) kernel[i] /= ks
        val tmp = DoubleArray(src.size)
        for (r in 0 until rows) for (c in 0 until cols) {
            var acc = 0.0
            for (k in -radius..radius) acc += kernel[k + radius] * src[r * cols + (c + k).coerceIn(0, cols - 1)]
            tmp[r * cols + c] = acc
        }
        val out = DoubleArray(src.size)
        for (r in 0 until rows) for (c in 0 until cols) {
            var acc = 0.0
            for (k in -radius..radius) acc += kernel[k + radius] * tmp[(r + k).coerceIn(0, rows - 1) * cols + c]
            out[r * cols + c] = acc
        }
        return out
    }

    /** Heurística simple por la zona más frecuente; es una orientación, no una clasificación. */
    internal fun estimateRole(avg: PitchPoint, e: PositionEllipse, dims: PitchDimensions): String {
        val xr = avg.x / dims.length
        val yr = avg.y / dims.width
        val side = when {
            yr < 0.33 -> "derecho"
            yr > 0.67 -> "izquierdo"
            else -> null
        }
        return when {
            xr < 0.18 && e.areaM2 < 900 -> "Portero"
            xr < 0.36 -> if (side == null) "Defensa central" else "Lateral $side"
            xr < 0.6 -> if (side == null) "Mediocampista central" else "Volante por banda ($side)"
            else -> if (side == null) "Delantero centro" else "Extremo $side"
        }
    }
}
