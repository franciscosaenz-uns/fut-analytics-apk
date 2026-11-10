package com.futbolanalisis.core.insights

import java.util.Locale
import kotlin.math.abs

enum class Verdict { BETTER, SIMILAR, WORSE, NEUTRAL }

/** Una métrica de un partido frente a una referencia (tu promedio u otro partido). */
data class MetricComparison(
    val metric: Metric,
    val value: Double?,
    val reference: Double?,
    /** Diferencia absoluta (valor − referencia). */
    val delta: Double?,
    /** Diferencia en % respecto a la referencia; null si no se puede calcular. */
    val deltaPct: Double?,
    val verdict: Verdict,
    /** true si es el mejor valor histórico del jugador en esta métrica. */
    val isRecord: Boolean = false,
)

data class Insight(val tone: Verdict, val title: String, val detail: String)

data class PersonalRecord<T>(val metric: Metric, val value: Double, val holder: T)

/** Cómo viene el jugador: media de los últimos partidos frente a los anteriores. */
data class FormTrend(val recentAvg: Double, val previousAvg: Double?, val delta: Double?) {
    val verdict: Verdict
        get() = when {
            delta == null -> Verdict.NEUTRAL
            delta >= 2 -> Verdict.BETTER
            delta <= -2 -> Verdict.WORSE
            else -> Verdict.SIMILAR
        }
}

object MatchComparator {
    private val es = Locale("es")

    fun format(v: Double, decimals: Int): String = String.format(es, "%.${decimals}f", v)

    fun format(metric: Metric, v: Double?, withUnit: Boolean = true): String {
        if (v == null) return "—"
        val sign = if (metric.isDelta && v > 0) "+" else ""
        val unit = if (withUnit && metric.unit.isNotEmpty()) " ${metric.unit}" else ""
        return sign + format(v, metric.decimals) + unit
    }

    fun average(list: List<MatchKpis>, metric: Metric, per90: Boolean): Double? =
        list.mapNotNull { metric.value(it, per90) }.takeIf { it.isNotEmpty() }?.average()

    fun compare(metric: Metric, value: Double?, reference: Double?, isRecord: Boolean = false): MetricComparison {
        if (value == null || reference == null) return MetricComparison(metric, value, reference, null, null, Verdict.NEUTRAL, isRecord)
        val delta = value - reference
        // Para métricas que ya son una diferencia (2T vs 1T) se comparan puntos, no porcentajes.
        val pct = if (metric.isDelta) null else if (abs(reference) > 1e-9) delta / abs(reference) * 100 else null
        val significant = if (metric.isDelta) abs(delta) >= 4.0 else pct != null && abs(pct) >= metric.noisePct
        val verdict = when {
            metric.better == Better.NEUTRAL -> Verdict.NEUTRAL
            !significant -> Verdict.SIMILAR
            (delta > 0) == (metric.better == Better.HIGHER) -> Verdict.BETTER
            else -> Verdict.WORSE
        }
        return MetricComparison(metric, value, reference, delta, pct, verdict, isRecord)
    }

    /** El partido [target] frente al promedio de [others] (el resto de partidos del jugador). */
    fun vsAverage(target: MatchKpis, others: List<MatchKpis>, per90: Boolean, metrics: List<Metric> = Metric.entries): List<MetricComparison> =
        metrics.map { m ->
            val v = m.value(target, per90)
            val history = others.mapNotNull { m.value(it, per90) }
            val record = v != null && history.isNotEmpty() && m.better != Better.NEUTRAL && !m.isDelta &&
                (if (m.better == Better.HIGHER) v > history.max() else v < history.min())
            compare(m, v, history.takeIf { it.isNotEmpty() }?.average(), record)
        }

    fun headToHead(a: MatchKpis, b: MatchKpis, per90: Boolean, metrics: List<Metric> = Metric.entries): List<MetricComparison> =
        metrics.map { m -> compare(m, m.value(a, per90), m.value(b, per90)) }

    /** Frases cortas con lo más destacado del partido frente al historial. */
    fun insights(target: MatchKpis, others: List<MatchKpis>, max: Int = 3): List<Insight> {
        if (others.isEmpty()) return emptyList()
        val all = vsAverage(target, others, per90 = true)
        val records = all.filter { it.isRecord }.map { c ->
            Insight(Verdict.BETTER, "Récord personal: ${c.metric.label.lowercase(es)}", "${format(c.metric, c.value)}. Tu mejor marca hasta ahora.")
        }
        val notable = all.filter { !it.isRecord && it.verdict != Verdict.SIMILAR && it.verdict != Verdict.NEUTRAL }
            .sortedByDescending { c ->
                if (c.metric.isDelta) abs(c.delta ?: 0.0) / 4.0 else abs(c.deltaPct ?: 0.0) / c.metric.noisePct.coerceAtLeast(1.0)
            }
            .map { c ->
                val up = (c.delta ?: 0.0) > 0
                val change = if (c.metric.isDelta) "${format(abs(c.delta ?: 0.0), 0)} puntos" else "${format(abs(c.deltaPct ?: 0.0), 0)} %"
                val title = "${c.metric.short}: $change ${if (up) "por encima" else "por debajo"} de tu promedio"
                val scope = if (c.metric.volume) " por 90'" else ""
                Insight(c.verdict, title, "${format(c.metric, c.value)} frente a ${format(c.metric, c.reference)}$scope.")
            }
        return (records + notable).take(max)
    }

    /** Mejores marcas del jugador. [kpis] extrae los indicadores de cada elemento. */
    fun <T> records(
        items: List<T>,
        metrics: List<Metric> = listOf(Metric.MAX_SPEED, Metric.DISTANCE, Metric.PACE, Metric.HIGH_SPEED, Metric.SPRINTS, Metric.PEAK_1, Metric.ACCELERATIONS),
        kpis: (T) -> MatchKpis?,
    ): List<PersonalRecord<T>> = metrics.mapNotNull { m ->
        items.mapNotNull { item -> kpis(item)?.let { k -> m.value(k)?.let { item to it } } }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.let { PersonalRecord(m, it.second, it.first) }
    }

    /** [chronological]: valores del más antiguo al más reciente. Compara los últimos [recent] con los previos. */
    fun form(chronological: List<Double>, recent: Int = 3): FormTrend? {
        if (chronological.isEmpty()) return null
        val last = chronological.takeLast(recent)
        val before = chronological.dropLast(recent).takeLast(5)
        val prevAvg = before.takeIf { it.isNotEmpty() }?.average()
        return FormTrend(last.average(), prevAvg, prevAvg?.let { last.average() - it })
    }
}
