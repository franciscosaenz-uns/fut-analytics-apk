package com.futbolanalisis.app.data

import android.content.Context
import com.futbolanalisis.core.AnalysisOptions
import com.futbolanalisis.core.MatchAnalyzer
import com.futbolanalisis.core.MatchReport
import com.futbolanalisis.core.model.HeartRateSample
import com.futbolanalisis.core.model.PlayerProfile
import com.futbolanalisis.core.model.Track
import com.futbolanalisis.core.physio.WellnessSnapshot
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
)

@Serializable
data class MatchSummary(
    val id: String,
    val title: String,
    val startMs: Long,
    val distanceM: Double,
    val playingTimeS: Double,
    val role: String,
)

/**
 * Almacenamiento local en archivos JSON (un archivo por partido + un índice).
 * Decisión v0.1: sin base de datos; los datos se quedan en el teléfono.
 */
class MatchRepository(context: Context) {
    private val dir = File(context.filesDir, "matches").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")
    private val json = MatchAnalyzer.json

    suspend fun list(): List<MatchSummary> = withContext(Dispatchers.IO) {
        if (!indexFile.exists()) return@withContext emptyList()
        runCatching {
            json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(MatchSummary.serializer()), indexFile.readText())
        }.getOrDefault(emptyList()).sortedByDescending { it.startMs }
    }

    suspend fun get(id: String): MatchRecord? = withContext(Dispatchers.IO) {
        val f = File(dir, "$id.json")
        if (!f.exists()) null else runCatching { json.decodeFromString(MatchRecord.serializer(), f.readText()) }.getOrNull()
    }

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
        )
        writeIndex(list().filterNot { it.id == record.id } + summary)
    }

    private fun writeIndex(items: List<MatchSummary>) {
        indexFile.writeText(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(MatchSummary.serializer()), items))
    }
}

/** Perfil del jugador y preferencias, en SharedPreferences como JSON. */
class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
    private val json = MatchAnalyzer.json

    fun load(): PlayerProfile = prefs.getString("profile", null)
        ?.let { runCatching { json.decodeFromString(PlayerProfile.serializer(), it) }.getOrNull() }
        ?: PlayerProfile()

    fun save(p: PlayerProfile) = prefs.edit().putString("profile", json.encodeToString(PlayerProfile.serializer(), p)).apply()

    var amateurZones: Boolean
        get() = prefs.getBoolean("amateurZones", false)
        set(v) = prefs.edit().putBoolean("amateurZones", v).apply()
}
