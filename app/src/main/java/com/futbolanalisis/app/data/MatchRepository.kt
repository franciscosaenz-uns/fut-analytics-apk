package com.futbolanalisis.app.data

import android.content.Context
import com.futbolanalisis.core.AnalysisOptions
import com.futbolanalisis.core.MatchAnalyzer
import com.futbolanalisis.core.MatchReport
import com.futbolanalisis.core.coach.MatchLog
import com.futbolanalisis.core.model.HeartRateSample
import com.futbolanalisis.core.model.PlayerProfile
import com.futbolanalisis.core.model.Track
import com.futbolanalisis.core.physio.WellnessSnapshot
import com.futbolanalisis.core.physical.ZoneMode
import com.futbolanalisis.core.pitch.PitchCalibration
import com.futbolanalisis.core.tactical.Heatmap
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

/** Todo lo necesario para re-analizar un partido: datos crudos + opciones + último reporte. */
@Serializable
data class MatchRecord(
    val id: String,
    val title: String,
    val createdAtMs: Long,
    val track: Track,
    val externalHeartRate: List<HeartRateSample> = emptyList(),
    val wellness: WellnessSnapshot? = null,
    val options: AnalysisOptions = AnalysisOptions(),
    val report: MatchReport,
    val log: MatchLog = MatchLog(),
)

@Serializable
data class MatchSummary(
    val id: String,
    val title: String,
    val startMs: Long,
    val distanceM: Double,
    val playingTimeS: Double,
    val role: String,
    val archetype: String = "",
    val sprints: Int = 0,
    val maxSpeedKmh: Double = 0.0,
    val heatmap: Heatmap? = null,
    val goals: Int = 0,
    val assists: Int = 0,
    val result: String? = null,
    val opponent: String = "",
)

/**
 * Almacenamiento local en archivos JSON (un archivo por partido + un índice).
 * Decisión v0.1: sin base de datos; los datos se quedan en el teléfono.
 */
class MatchRepository(context: Context) {
    private val dir = File(context.filesDir, "matches").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")
    private val json = MatchAnalyzer.json
    private val profiles = ProfileStore(context)

    suspend fun list(): List<MatchSummary> = withContext(Dispatchers.IO) {
        if (!indexFile.exists()) return@withContext emptyList()
        runCatching {
            json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(MatchSummary.serializer()), indexFile.readText())
        }.getOrDefault(emptyList()).sortedByDescending { it.startMs }
    }

    suspend fun get(id: String): MatchRecord? {
        val text = withContext(Dispatchers.IO) { File(dir, "$id.json").takeIf { it.exists() }?.readText() } ?: return null
        runCatching { json.decodeFromString(MatchRecord.serializer(), text) }.getOrNull()?.let { return it }
        // Partido guardado por una versión anterior: se conservan los datos crudos y se re-analiza.
        val raw = runCatching { json.decodeFromString(RawRecord.serializer(), text) }.getOrNull() ?: return null
        val report = withContext(Dispatchers.Default) {
            MatchAnalyzer.analyze(raw.track, profiles.load(), raw.options, raw.externalHeartRate, raw.wellness, raw.title)
        }
        val migrated = MatchRecord(raw.id, raw.title, raw.createdAtMs, raw.track, raw.externalHeartRate, raw.wellness, raw.options, report, raw.log)
        save(migrated)
        return migrated
    }

    @Serializable
    private data class RawRecord(
        val id: String,
        val title: String,
        val createdAtMs: Long,
        val track: Track,
        val externalHeartRate: List<HeartRateSample> = emptyList(),
        val wellness: WellnessSnapshot? = null,
        val options: AnalysisOptions = AnalysisOptions(),
        val log: MatchLog = MatchLog(),
    )

    suspend fun create(
        track: Track,
        profile: PlayerProfile,
        options: AnalysisOptions,
        externalHr: List<HeartRateSample>,
        wellness: WellnessSnapshot?,
        title: String?,
    ): MatchRecord = withContext(Dispatchers.Default) {
        val report = MatchAnalyzer.analyze(track, profile, options, externalHr, wellness, title)
        val record = MatchRecord(
            id = UUID.randomUUID().toString(),
            title = report.title,
            createdAtMs = System.currentTimeMillis(),
            track = track,
            externalHeartRate = externalHr,
            wellness = wellness,
            options = options,
            report = report,
        )
        save(record)
        record
    }

    /** Re-analiza con otras opciones (dirección de ataque, umbrales, periodos) o con otro perfil. */
    suspend fun reanalyze(id: String, profile: PlayerProfile, options: AnalysisOptions): MatchRecord? {
        val old = get(id) ?: return null
        val report = withContext(Dispatchers.Default) {
            MatchAnalyzer.analyze(old.track, profile, options, old.externalHeartRate, old.wellness, old.title)
        }
        val updated = old.copy(options = options, report = report)
        save(updated)
        return updated
    }

    /** Guarda el registro manual; el RPE y la posición también alimentan el análisis. */
    suspend fun saveLog(id: String, profile: PlayerProfile, log: MatchLog): MatchRecord? {
        val old = get(id) ?: return null
        val options = old.options.copy(rpe = log.rpe, positionHint = log.positionPlayed.ifBlank { null })
        val report = if (options != old.options) withContext(Dispatchers.Default) {
            MatchAnalyzer.analyze(old.track, profile, options, old.externalHeartRate, old.wellness, old.title)
        } else old.report
        val updated = old.copy(options = options, report = report, log = log)
        save(updated)
        return updated
    }

    suspend fun rename(id: String, title: String) {
        val old = get(id) ?: return
        save(old.copy(title = title, report = old.report.copy(title = title)))
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        File(dir, "$id.json").delete()
        writeIndex(list().filterNot { it.id == id })
    }

    private suspend fun save(record: MatchRecord) = withContext(Dispatchers.IO) {
        File(dir, "${record.id}.json").writeText(json.encodeToString(MatchRecord.serializer(), record))
        val summary = MatchSummary(
            id = record.id,
            title = record.title,
            startMs = record.report.startMs,
            distanceM = record.report.physical.totalDistanceM,
            playingTimeS = record.report.physical.playingTimeS,
            role = record.report.tactical.estimatedRole,
            archetype = record.report.tactical.description.archetype,
            sprints = record.report.physical.sprints.size,
            maxSpeedKmh = record.report.physical.maxSpeedKmh,
            heatmap = record.report.tactical.heatmap,
            goals = record.log.goals,
            assists = record.log.assists,
            result = record.log.result,
            opponent = record.log.opponent,
        )
        writeIndex(list().filterNot { it.id == record.id } + summary)
    }

    private fun writeIndex(items: List<MatchSummary>) {
        indexFile.writeText(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(MatchSummary.serializer()), items))
    }
}

/** Cancha calibrada a mano, para reutilizarla en próximos partidos en el mismo lugar. */
@Serializable
data class SavedPitch(val name: String, val calibration: PitchCalibration) {
    val centerLat get() = calibration.toLatLon(com.futbolanalisis.core.pitch.PitchPoint(calibration.dims.length / 2, calibration.dims.width / 2)).first
    val centerLon get() = calibration.toLatLon(com.futbolanalisis.core.pitch.PitchPoint(calibration.dims.length / 2, calibration.dims.width / 2)).second
}

/** Perfil del jugador y preferencias, en SharedPreferences como JSON. */
class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
    private val json = MatchAnalyzer.json

    fun load(): PlayerProfile = prefs.getString("profile", null)
        ?.let { runCatching { json.decodeFromString(PlayerProfile.serializer(), it) }.getOrNull() }
        ?: PlayerProfile()

    fun save(p: PlayerProfile) = prefs.edit().putString("profile", json.encodeToString(PlayerProfile.serializer(), p)).apply()

    var zoneMode: ZoneMode
        get() = prefs.getString("zoneMode", null)?.let { runCatching { ZoneMode.valueOf(it) }.getOrNull() }
            ?: if (prefs.getBoolean("amateurZones", false)) ZoneMode.AMATEUR else ZoneMode.ADAPTIVE
        set(v) = prefs.edit().putString("zoneMode", v.name).apply()

    var pitches: List<SavedPitch>
        get() = prefs.getString("pitches", null)
            ?.let { runCatching { json.decodeFromString(ListSerializer(SavedPitch.serializer()), it) }.getOrNull() }
            ?: emptyList()
        set(v) = prefs.edit().putString("pitches", json.encodeToString(ListSerializer(SavedPitch.serializer()), v)).apply()
}
