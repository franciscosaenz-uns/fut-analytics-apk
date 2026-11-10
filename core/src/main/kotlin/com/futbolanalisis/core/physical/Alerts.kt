package com.futbolanalisis.core.physical

import com.futbolanalisis.core.model.HeartRateSample
import kotlinx.serialization.Serializable

@Serializable
enum class AlertLevel { INFO, WARNING, CRITICAL }

@Serializable
data class PhysicalAlert(
    val minute: Int,
    val endMinute: Int? = null,
    val level: AlertLevel,
    val title: String,
    val detail: String,
)

/** Reglas simples que marcan los momentos físicos clave del partido, con su minuto. */
object AlertEngine {
    fun build(
        samples: List<Sample>,
        periods: List<Period>,
        physical: PhysicalReport,
        energy: EnergyReport,
        advanced: AdvancedMetrics,
        heartRate: List<HeartRateSample>,
        maxHr: Int,
    ): List<PhysicalAlert> {
        val clock = MatchClock(periods)
        val out = ArrayList<PhysicalAlert>()
        fun f0(v: Double) = Math.round(v).toString()
        fun signed(v: Double) = (if (v > 0) "+" else "") + Math.round(v)

        // 1. Energía baja.
        val e = energy.energyPerMinute
        var m = 0
        while (m < e.size) {
            if (e[m] < 30) {
                var end = m
                while (end + 1 < e.size && e[end + 1] < 30) end++
                out += PhysicalAlert(m + 1, end + 1, AlertLevel.CRITICAL, "Reserva de energía baja",
                    "La energía cayó a ${f0(e.subList(m, end + 1).min())}%. Momento de riesgo de lesión o de perder duelos; valora un cambio o bajar el ritmo.")
                m = end + 1
            } else m++
        }
        if (energy.minEnergy >= 30) {
            out += PhysicalAlert(energy.minEnergyMinute, null, AlertLevel.INFO, "Punto de mayor desgaste",
                "Energía mínima del partido: ${f0(energy.minEnergy)}%.")
        }

        // 2. Esfuerzos repetidos de alta intensidad.
        advanced.repeatedEfforts.forEach {
            out += PhysicalAlert(it.startMinute, null, AlertLevel.WARNING, "Esfuerzos repetidos",
                "${it.efforts} carreras de alta intensidad en ${f0(it.durationS)} s con menos de 21 s de pausa.")
        }

        // 3. Pasaje más exigente (5 min).
        advanced.peakDemands.firstOrNull { it.windowMinutes == 5 }?.let {
            out += PhysicalAlert(it.startMinute, it.startMinute + 4, AlertLevel.INFO, "Pasaje más intenso (5')",
                "${f0(it.metersPerMinute)} m/min y ${f0(it.hsrMetersPerMinute)} m/min a alta intensidad: tu demanda máxima de referencia.")
        }

        // 4. Velocidad máxima.
        physical.sprints.maxByOrNull { it.maxSpeedKmh }?.let {
            out += PhysicalAlert(clock.minute(it.startMs) ?: 1, null, AlertLevel.INFO, "Velocidad máxima",
                "${"%.1f".format(java.util.Locale.US, it.maxSpeedKmh)} km/h en un sprint de ${f0(it.distanceM)} m.")
        }

        // 5. Caída de rendimiento por bloques respecto al primero.
        val splits = physical.splits
        if (splits.size >= 3) {
            val base = splits.first().let { it.distanceM / ((it.endMs - it.startMs) / 60000.0) }
            val baseHsr = splits.first().let { it.highSpeedDistanceM / ((it.endMs - it.startMs) / 60000.0) }
            splits.drop(1).forEach { s ->
                val mins = (s.endMs - s.startMs) / 60000.0
                val drop = (s.distanceM / mins - base) / base * 100
                val hsrDrop = if (baseHsr > 0) (s.highSpeedDistanceM / mins - baseHsr) / baseHsr * 100 else 0.0
                if (drop <= -15 || (hsrDrop <= -50 && baseHsr > 3)) {
                    out += PhysicalAlert(clock.minute(s.startMs + 1000) ?: 1, clock.minute(s.endMs), AlertLevel.WARNING, "Caída de rendimiento",
                        "Bloque ${s.label} frente al primero: metros por minuto ${signed(drop)}%, alta intensidad ${signed(hsrDrop)}%.")
                }
            }
        }

        // 6. FC muy alta sostenida (> 92% durante 2+ minutos).
        if (heartRate.size >= 10) {
            var start: HeartRateSample? = null
            var last: HeartRateSample? = null
            fun flush() {
                val s = start; val l = last
                if (s != null && l != null && l.timeMs - s.timeMs >= 120_000) {
                    out += PhysicalAlert(clock.minute(s.timeMs) ?: 1, clock.minute(l.timeMs), AlertLevel.WARNING, "FC al límite",
                        "Más de ${f0((l.timeMs - s.timeMs) / 60000.0)} min por encima del 92% de tu FC máxima.")
                }
                start = null
            }
            for (h in heartRate) {
                if (clock.minute(h.timeMs) == null) continue
                if (h.bpm >= 0.92 * maxHr) {
                    if (start == null || (last != null && h.timeMs - last!!.timeMs > 20_000)) { flush(); start = h }
                    last = h
                } else if (start != null) flush()
            }
            flush()
        }

        // 7. Desconexión: 4+ minutos seguidos por debajo de 50 m/min.
        val dpm = physical.distancePerMinute
        var k = 0
        while (k < dpm.size) {
            if (dpm[k] < 50) {
                var end = k
                while (end + 1 < dpm.size && dpm[end + 1] < 50) end++
                if (end - k + 1 >= 4) {
                    out += PhysicalAlert(k + 1, end + 1, AlertLevel.WARNING, "Baja participación",
                        "${end - k + 1} min seguidos por debajo de 50 m/min. ¿Lesión, pausa o desconexión del juego?")
                }
                k = end + 1
            } else k++
        }

        // 8. Desacople cardiaco.
        advanced.decouplingPct?.let {
            if (it <= -10) out += PhysicalAlert(periods.last().let { p -> clock.minute(p.startMs) ?: 1 }, null, AlertLevel.WARNING, "Desacople cardiaco",
                "En el último tiempo recorriste ${f0(-it)}% menos metros por latido: señal de fatiga.")
        }
        return out.sortedWith(compareBy<PhysicalAlert> { it.minute }.thenByDescending { it.level.ordinal })
    }
}
