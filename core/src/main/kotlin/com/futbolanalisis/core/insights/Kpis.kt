package com.futbolanalisis.core.insights

import com.futbolanalisis.core.MatchReport
import kotlinx.serialization.Serializable

/**
 * Indicadores compactos de un partido. Se guardan en el índice para comparar partidos
 * sin tener que abrir cada registro completo.
 */
@Serializable
data class MatchKpis(
    val minutes: Double,
    val distanceM: Double,
    val metersPerMinute: Double,
    val maxSpeedKmh: Double,
    val sprints: Int,
    val sprintDistanceM: Double,
    val highIntensityRuns: Int,
    val accelerations: Int,
    val decelerations: Int,
    /** Distancia ≥ 14,4 km/h (umbral absoluto, comparable entre partidos). */
    val runDistanceM: Double? = null,
    /** Distancia ≥ 19,8 km/h (umbral absoluto). */
    val highSpeedDistanceM: Double? = null,
    val hmldM: Double? = null,
    val metabolicPowerWkg: Double? = null,
    val peak1MinMpm: Double? = null,
    val peak5MinMpm: Double? = null,
    val repeatedEfforts: Int = 0,
    val fatigueIndexPct: Double? = null,
    /** Variación % de m/min del 2º tiempo frente al 1º. */
    val secondHalfPct: Double? = null,
    val finalEnergy: Double? = null,
    val minEnergy: Double? = null,
    val avgHr: Double? = null,
    val maxHr: Int? = null,
    /** FC media como % de la FC máxima usada. */
    val avgHrPct: Double? = null,
    val trimp: Double? = null,
    val kcal: Double? = null,
    val sessionRpeLoad: Double? = null,
) {
    companion object {
        fun of(r: MatchReport): MatchKpis {
            val p = r.physical
            val a = r.advanced
            val e = r.energy
            val h = r.heartRate
            val halves = a?.efficiencyByPeriod.orEmpty()
            val secondHalf = if (halves.size >= 2 && halves.first().metersPerMinute > 0) {
                (halves.last().metersPerMinute - halves.first().metersPerMinute) / halves.first().metersPerMinute * 100
            } else null
            return MatchKpis(
                minutes = p.playingTimeS / 60,
                distanceM = p.totalDistanceM,
                metersPerMinute = p.metersPerMinute,
                maxSpeedKmh = p.maxSpeedKmh,
                sprints = p.sprints.size,
                sprintDistanceM = p.sprintDistanceM,
                highIntensityRuns = p.highIntensityRuns.size,
                accelerations = p.accelerations,
                decelerations = p.decelerations,
                runDistanceM = a?.absRunDistanceM,
                highSpeedDistanceM = a?.absHighSpeedDistanceM,
                hmldM = e?.hmldM,
                metabolicPowerWkg = e?.avgMetabolicPowerWkg,
                peak1MinMpm = a?.peakDemands?.firstOrNull { it.windowMinutes == 1 }?.metersPerMinute,
                peak5MinMpm = a?.peakDemands?.firstOrNull { it.windowMinutes == 5 }?.metersPerMinute,
                repeatedEfforts = a?.repeatedEfforts?.size ?: 0,
                fatigueIndexPct = p.fatigueIndexPct,
                secondHalfPct = secondHalf,
                finalEnergy = e?.finalEnergy,
                minEnergy = e?.minEnergy,
                avgHr = h?.avgBpm,
                maxHr = h?.maxBpm,
                avgHrPct = h?.let { it.avgBpm / it.maxHrUsed * 100 },
                trimp = h?.edwardsTrimp,
                kcal = e?.kcal,
                sessionRpeLoad = a?.sessionRpeLoad,
            )
        }
    }
}

/** Sentido en que una métrica es "mejor". */
enum class Better { HIGHER, LOWER, NEUTRAL }

/**
 * Métricas comparables entre partidos. [volume] indica que depende de los minutos jugados
 * y por tanto se puede normalizar a 90 minutos.
 */
enum class Metric(
    val label: String,
    val short: String,
    val unit: String,
    val decimals: Int,
    val better: Better,
    val volume: Boolean,
    val group: String,
    /** Variación mínima (en %) para considerar que hubo un cambio real. */
    val noisePct: Double = 5.0,
    val get: (MatchKpis) -> Double?,
) {
    DISTANCE("Distancia", "Distancia", "km", 2, Better.HIGHER, true, "Volumen", get = { it.distanceM / 1000 }),
    PACE("Ritmo", "Ritmo", "m/min", 0, Better.HIGHER, false, "Volumen", 4.0, { it.metersPerMinute }),
    RUN_DISTANCE("Distancia en carrera (≥14,4 km/h)", "Carrera", "m", 0, Better.HIGHER, true, "Intensidad", 8.0, { it.runDistanceM }),
    HIGH_SPEED("Alta velocidad (≥19,8 km/h)", "Alta vel.", "m", 0, Better.HIGHER, true, "Intensidad", 10.0, { it.highSpeedDistanceM }),
    HMLD("Distancia de alta carga metabólica", "HMLD", "m", 0, Better.HIGHER, true, "Intensidad", 8.0, { it.hmldM }),
    PEAK_1("Pico de 1 minuto", "Pico 1'", "m/min", 0, Better.HIGHER, false, "Intensidad", 4.0, { it.peak1MinMpm }),
    PEAK_5("Pico de 5 minutos", "Pico 5'", "m/min", 0, Better.HIGHER, false, "Intensidad", 4.0, { it.peak5MinMpm }),
    MAX_SPEED("Velocidad máxima", "Vel. máx", "km/h", 1, Better.HIGHER, false, "Velocidad", 3.0, { it.maxSpeedKmh }),
    SPRINTS("Sprints", "Sprints", "", 0, Better.HIGHER, true, "Velocidad", 15.0, { it.sprints.toDouble() }),
    HI_RUNS("Carreras de alta intensidad", "Carreras AI", "", 0, Better.HIGHER, true, "Velocidad", 12.0, { it.highIntensityRuns.toDouble() }),
    ACCELERATIONS("Aceleraciones", "Acel.", "", 0, Better.HIGHER, true, "Explosividad", 10.0, { it.accelerations.toDouble() }),
    DECELERATIONS("Frenadas", "Frenadas", "", 0, Better.HIGHER, true, "Explosividad", 10.0, { it.decelerations.toDouble() }),
    FINAL_ENERGY("Energía al final", "Energía", "%", 0, Better.HIGHER, false, "Aguante", 6.0, { it.finalEnergy }),
    SECOND_HALF("Ritmo del 2º tiempo vs 1º", "2T vs 1T", "%", 0, Better.HIGHER, false, "Aguante", 0.0, { it.secondHalfPct }),
    AVG_HR("FC media", "FC media", "lpm", 0, Better.NEUTRAL, false, "Cardio", 3.0, { it.avgHr }),
    TRIMP("Carga cardiaca (TRIMP)", "TRIMP", "", 0, Better.NEUTRAL, true, "Cardio", 8.0, { it.trimp }),
    ;

    /** Valor de la métrica; si [per90] y es de volumen, normalizado a 90 minutos. */
    fun value(k: MatchKpis, per90: Boolean = false): Double? {
        val v = get(k) ?: return null
        return if (per90 && volume && k.minutes >= 1) v * 90 / k.minutes else v
    }

    /** true si la métrica ya es una diferencia en puntos porcentuales (no tiene sentido un % de %). */
    val isDelta: Boolean get() = this == SECOND_HALF
}
