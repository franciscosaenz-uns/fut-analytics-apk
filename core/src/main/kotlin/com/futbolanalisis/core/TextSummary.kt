package com.futbolanalisis.core

import com.futbolanalisis.core.tactical.TacticalReport
import java.util.Locale

/** Resumen en texto plano del reporte (para compartir o depurar). */
object TextSummary {
    fun of(r: MatchReport): String = buildString {
        val p = r.physical
        fun f(v: Double, d: Int = 1) = String.format(Locale.US, "%.${d}f", v)
        appendLine("== ${r.title} ==")
        appendLine("Periodos: ${r.periods.joinToString { "${it.index}T ${it.durationMs / 60000}'" }}")
        appendLine("Distancia: ${f(p.totalDistanceM / 1000, 2)} km en ${f(p.playingTimeS / 60, 0)} min (${f(p.metersPerMinute, 0)} m/min)")
        appendLine("Velocidad máx: ${f(p.maxSpeedKmh)} km/h · media ${f(p.avgSpeedKmh)} km/h")
        p.zoneLabels.forEachIndexed { i, l -> appendLine("  $l: ${f(p.distanceByZoneM[i], 0)} m") }
        appendLine("Sprints: ${p.sprints.size} (${f(p.sprintDistanceM, 0)} m) · Alta intensidad: ${p.highIntensityRuns.size} carreras")
        appendLine("Aceleraciones: ${p.accelerations} · Desaceleraciones: ${p.decelerations}")
        p.fatigueIndexPct?.let { appendLine("Índice de fatiga (último vs primer bloque): ${f(it)}%") }
        p.splits.forEach { appendLine("  ${it.label}: ${f(it.distanceM, 0)} m, HSR ${f(it.highSpeedDistanceM, 0)} m, sprints ${it.sprints}" + (it.avgHeartRate?.let { h -> ", FC ${f(h, 0)}" } ?: "")) }
        r.heartRate?.let { h ->
            appendLine("FC media ${f(h.avgBpm, 0)} · máx ${h.maxBpm} (FCmáx usada ${h.maxHrUsed}) · TRIMP ${f(h.edwardsTrimp, 0)} · fuente: ${r.heartRateSource}")
            h.zoneLabels.forEachIndexed { i, l -> appendLine("  $l: ${f(h.timeInZoneS[i] / 60, 1)} min") }
        }
        val t = r.tactical
        appendLine("Zonas (${p.zoneMode.label}, ref ${f(p.referenceMaxSpeedKmh)} km/h): sprint > ${p.zonesUsed.sprintKmh}, alta int. > ${p.zonesUsed.highSpeedKmh}")
        r.energy?.let { e -> appendLine("Energía: mínima ${f(e.minEnergy, 0)}% (min ${e.minEnergyMinute}), final ${f(e.finalEnergy, 0)}% · potencia metabólica ${f(e.avgMetabolicPowerWkg)} W/kg · HMLD ${f(e.hmldM, 0)} m · ${f(e.energyKjPerKg)} kJ/kg") }
        r.advanced?.let { a ->
            appendLine("Picos: " + a.peakDemands.joinToString { "${it.windowMinutes}' ${f(it.metersPerMinute, 0)} m/min (min ${it.startMinute})" })
            appendLine("Esfuerzos repetidos: ${a.repeatedEfforts.size} · trabajo:pausa 1:${f(if (a.workRestRatio > 0) 1 / a.workRestRatio else 0.0)} · desacople ${a.decouplingPct?.let { f(it) } ?: "—"}%")
        }
        r.alerts.forEach { appendLine("  [${it.level}] min ${it.minute}: ${it.title} — ${it.detail}") }
        appendLine("Posición estimada: ${t.estimatedRole} · ${t.description.archetype}")
        appendLine(t.description.summary)
        t.description.observations.forEach { appendLine("  - $it") }
        appendLine("Posición media: x=${f(t.averagePosition.x)} m, y=${f(t.averagePosition.y)} m · área de influencia ${f(t.ellipse.areaM2, 0)} m²")
        appendLine("Tercios: " + TacticalReport.THIRD_LABELS.indices.joinToString { "${TacticalReport.THIRD_LABELS[it]} ${f(t.thirdsPct[it], 0)}%" })
        appendLine("Carriles: " + TacticalReport.LANE_LABELS.indices.joinToString { "${TacticalReport.LANE_LABELS[it]} ${f(t.lanesPct[it], 0)}%" })
        appendLine("Área rival: ${f(t.penaltyBoxPct)}% · Área propia: ${f(t.ownBoxPct)}%")
        val dirs = t.sprintVectors.groupingBy { it.direction.label }.eachCount()
        appendLine("Dirección de sprints: $dirs")
    }
}
