package com.futbolanalisis.core.physical

import com.futbolanalisis.core.geo.Geo
import com.futbolanalisis.core.model.TrackPoint
import kotlinx.serialization.Serializable

/** Un periodo de juego (primer tiempo, segundo tiempo, o el partido completo). */
@Serializable
data class Period(val index: Int, val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = endMs - startMs
    operator fun contains(t: Long) = t in startMs..endMs
}

object PeriodDetector {
    /**
     * Detecta el descanso entre tiempos. Busca, en la franja central del registro (30%-70%),
     * o un hueco sin datos de al menos [minGapMs], o la racha más larga de minutos casi quietos
     * (menos de [stillMetersPerMinute] por minuto o dentro de un radio de [stillRadiusM]) de al menos [minBreakMinutes].
     * Si no encuentra nada, devuelve un único periodo.
     */
    fun detect(
        points: List<TrackPoint>,
        minGapMs: Long = 3 * 60_000L,
        minBreakMinutes: Int = 5,
        stillMetersPerMinute: Double = 45.0,
        stillRadiusM: Double = 10.0,
    ): List<Period> {
        if (points.size < 2) return emptyList()
        val start = points.first().timeMs
        val end = points.last().timeMs
        val total = end - start
        val whole = listOf(Period(1, start, end))
        if (total < 40 * 60_000L) return whole
        val winLo = start + (total * 0.3).toLong()
        val winHi = start + (total * 0.7).toLong()

        // 1) Hueco de registro (el reloj se pausó en el descanso).
        var bestGap: Pair<Long, Long>? = null
        for (i in 1 until points.size) {
            val a = points[i - 1].timeMs; val b = points[i].timeMs
            if (b - a >= minGapMs && a < winHi && b > winLo) {
                if (bestGap == null || (b - a) > (bestGap.second - bestGap.first)) bestGap = a to b
            }
        }
        if (bestGap != null) {
            return listOf(Period(1, start, bestGap.first), Period(2, bestGap.second, end))
        }

        // 2) Racha de minutos con poco movimiento. Un minuto es "quieto" si el recorrido es
        //    corto o si todos los puntos caen en un radio pequeño (el ruido del GPS parado
        //    suma metros, pero no se aleja del sitio).
        val minutes = ((total / 60_000L) + 1).toInt()
        val perMinute = DoubleArray(minutes)
        val buckets = Array(minutes) { ArrayList<TrackPoint>() }
        for (i in points.indices) {
            val m = ((points[i].timeMs - start) / 60_000L).toInt().coerceIn(0, minutes - 1)
            buckets[m] += points[i]
            if (i > 0) perMinute[m] += Geo.haversine(points[i - 1].lat, points[i - 1].lon, points[i].lat, points[i].lon)
        }
        val still = BooleanArray(minutes) { m ->
            val b = buckets[m]
            if (b.isEmpty()) return@BooleanArray true
            val cLat = b.sumOf { it.lat } / b.size
            val cLon = b.sumOf { it.lon } / b.size
            val radius = b.maxOf { Geo.haversine(cLat, cLon, it.lat, it.lon) }
            perMinute[m] < stillMetersPerMinute || radius < stillRadiusM
        }
        val mLo = ((winLo - start) / 60_000L).toInt()
        val mHi = ((winHi - start) / 60_000L).toInt()
        var bestStart = -1; var bestLen = 0
        var runStart = -1
        for (m in mLo..mHi + minBreakMinutes) {
            val isStill = m < minutes && still[m]
            if (isStill && runStart < 0) runStart = m
            if ((!isStill || m == mHi + minBreakMinutes) && runStart >= 0) {
                val len = (if (isStill) m + 1 else m) - runStart
                if (len > bestLen) { bestLen = len; bestStart = runStart }
                runStart = -1
            }
        }
        if (bestLen >= minBreakMinutes) {
            val breakStart = start + bestStart * 60_000L
            val breakEnd = start + (bestStart + bestLen) * 60_000L
            return listOf(Period(1, start, breakStart), Period(2, breakEnd, end))
        }
        return whole
    }
}
