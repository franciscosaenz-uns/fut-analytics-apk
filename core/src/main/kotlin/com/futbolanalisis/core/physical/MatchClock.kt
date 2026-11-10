package com.futbolanalisis.core.physical

/**
 * Reloj de partido: convierte una marca de tiempo en minuto de juego.
 * El minuto de juego es acumulativo (el 2º tiempo empieza donde terminó el 1º), así que
 * con tiempos de 45' se lee como en la TV (min 46 = inicio del 2º tiempo).
 */
class MatchClock(private val periods: List<Period>) {
    private val offsets: List<Long> = periods.runningFold(0L) { acc, p -> acc + p.durationMs }

    /** Segundos de juego transcurridos en [timeMs], o null si cae fuera de los periodos. */
    fun playSeconds(timeMs: Long): Double? {
        periods.forEachIndexed { i, p ->
            if (timeMs in p) return (offsets[i] + (timeMs - p.startMs)) / 1000.0
        }
        return null
    }

    /** Minuto de juego estilo fútbol (1-based): 0-59 s = minuto 1. */
    fun minute(timeMs: Long): Int? = playSeconds(timeMs)?.let { (it / 60).toInt() + 1 }

    fun label(timeMs: Long): String {
        val m = minute(timeMs) ?: return "—"
        val idx = periods.indexOfFirst { timeMs in it }
        return if (periods.size > 1 && idx >= 0) {
            val inPeriod = ((timeMs - periods[idx].startMs) / 60000).toInt() + 1
            "min $m (${periods[idx].index}T $inPeriod')"
        } else "min $m"
    }

    fun labelForMinute(minute: Int): String {
        if (periods.size <= 1) return "min $minute"
        val sec = (minute - 1) * 60_000L
        periods.forEachIndexed { i, p ->
            if (sec < offsets[i + 1]) return "min $minute (${p.index}T ${((sec - offsets[i]) / 60000) + 1}')"
        }
        return "min $minute"
    }

    val totalMinutes: Int get() = ((offsets.last() + 59_999) / 60_000).toInt()
}
