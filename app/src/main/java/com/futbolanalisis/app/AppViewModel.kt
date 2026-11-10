package com.futbolanalisis.app

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.futbolanalisis.app.data.MatchRecord
import com.futbolanalisis.app.data.MatchRepository
import com.futbolanalisis.app.data.MatchSummary
import com.futbolanalisis.app.data.ProfileStore
import com.futbolanalisis.app.health.HcAvailability
import com.futbolanalisis.app.health.HcSession
import com.futbolanalisis.app.health.HealthConnectManager
import com.futbolanalisis.app.health.RouteResult
import com.futbolanalisis.core.AnalysisOptions
import com.futbolanalisis.core.io.TrackImporter
import com.futbolanalisis.core.model.PlayerProfile
import com.futbolanalisis.core.model.Track
import com.futbolanalisis.core.physical.PhysicalConfig
import com.futbolanalisis.core.physical.SpeedZones
import com.futbolanalisis.core.physio.WellnessSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

data class UiState(
    val matches: List<MatchSummary> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    /** Partido recién creado, para navegar a él. */
    val openMatchId: String? = null,
    val profile: PlayerProfile = PlayerProfile(),
    val amateurZones: Boolean = false,
    val hcAvailability: HcAvailability = HcAvailability.NOT_SUPPORTED,
    val hcGranted: Boolean = false,
    val wellness: WellnessSnapshot? = null,
    val sessions: List<HcSession> = emptyList(),
    /** Sesión cuya ruta requiere consentimiento del usuario. */
    val routeConsentFor: HcSession? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = MatchRepository(app)
    private val profileStore = ProfileStore(app)
    val health = HealthConnectManager(app)

    private val _state = MutableStateFlow(UiState(profile = profileStore.load(), amateurZones = profileStore.amateurZones))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _current = MutableStateFlow<MatchRecord?>(null)
    val current: StateFlow<MatchRecord?> = _current.asStateFlow()

    init {
        refreshMatches()
        refreshHealth()
    }

    fun refreshMatches() = viewModelScope.launch {
        _state.update { it.copy(matches = repo.list()) }
    }

    fun refreshHealth() = viewModelScope.launch {
        val availability = health.availability()
        val granted = availability == HcAvailability.AVAILABLE && runCatching { health.hasAllPermissions() }.getOrDefault(false)
        val anyGranted = availability == HcAvailability.AVAILABLE && runCatching { health.grantedPermissions().isNotEmpty() }.getOrDefault(false)
        _state.update { it.copy(hcAvailability = availability, hcGranted = granted) }
        if (anyGranted) {
            val wellness = runCatching { health.wellness() }.getOrNull()
            val sessions = runCatching { health.recentSessions() }.getOrDefault(emptyList())
            _state.update { it.copy(wellness = wellness, sessions = sessions) }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
    fun consumeOpenMatch() = _state.update { it.copy(openMatchId = null) }

    private fun options(): AnalysisOptions = AnalysisOptions(
        physical = PhysicalConfig(zones = if (_state.value.amateurZones) SpeedZones.AMATEUR else SpeedZones()),
    )

    fun importUri(uri: Uri) = runBusy {
        val ctx = getApplication<Application>()
        val name = withContext(Dispatchers.IO) {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }
        val track = withContext(Dispatchers.IO) {
            ctx.contentResolver.openInputStream(uri)?.use { TrackImporter.parse(it, name) }
                ?: error("No se pudo abrir el archivo")
        }
        createMatch(track, title = track.name ?: name?.substringBeforeLast('.'))
    }

    fun loadSample() = runBusy {
        val ctx = getApplication<Application>()
        val track = withContext(Dispatchers.IO) { ctx.assets.open("sample_match.gpx").use { TrackImporter.parse(it, "sample_match.gpx") } }
        createMatch(track, title = "Partido de ejemplo")
    }

    fun importSession(session: HcSession) = runBusy {
        when (val r = health.readRoute(session.id)) {
            is RouteResult.Ok -> createMatch(r.track, title = session.title ?: "Partido ${session.start}")
            RouteResult.ConsentRequired -> _state.update { it.copy(routeConsentFor = session) }
            RouteResult.NoRoute -> _state.update {
                it.copy(message = "Esta sesión no tiene ruta GPS en Health Connect. Exporta el entrenamiento como GPX desde Zepp o Strava e impórtalo.")
            }
        }
    }

    /** Resultado del diálogo de consentimiento de ruta de Health Connect. */
    fun onRouteConsent(route: androidx.health.connect.client.records.ExerciseRoute?) {
        val session = _state.value.routeConsentFor ?: return
        _state.update { it.copy(routeConsentFor = null) }
        if (route == null) {
            _state.update { it.copy(message = "No se autorizó el acceso a la ruta.") }
            return
        }
        runBusy {
            when (val r = health.routeToTrack(route, session.title)) {
                is RouteResult.Ok -> createMatch(r.track, title = session.title ?: "Partido")
                else -> _state.update { it.copy(message = "La ruta tiene muy pocos puntos para analizarla.") }
            }
        }
    }

    private suspend fun createMatch(track: Track, title: String?) {
        val hasHc = _state.value.hcAvailability == HcAvailability.AVAILABLE
        val start = Instant.ofEpochMilli(track.startMs)
        val end = Instant.ofEpochMilli(track.endMs)
        // Si el archivo no trae FC, se completa con Health Connect.
        val extHr = if (hasHc && track.heartRateSamples.size < 10) runCatching { health.heartRate(start, end) }.getOrDefault(emptyList()) else emptyList()
        val wellness = if (hasHc) runCatching { health.wellness(start) }.getOrNull() else null
        val record = repo.create(track, _state.value.profile, options(), extHr, wellness, title)
        _state.update { it.copy(matches = repo.list(), openMatchId = record.id) }
    }

    fun openMatch(id: String) = viewModelScope.launch {
        _current.value = null
        _current.value = repo.get(id)
    }

    fun updateOptions(id: String, transform: (AnalysisOptions) -> AnalysisOptions) = runBusy {
        val rec = _current.value ?: repo.get(id) ?: return@runBusy
        _current.value = repo.reanalyze(id, _state.value.profile, transform(rec.options))
        refreshMatches()
    }

    fun rename(id: String, title: String) = viewModelScope.launch {
        repo.rename(id, title)
        _current.value = repo.get(id)
        refreshMatches()
    }

    fun delete(id: String) = viewModelScope.launch {
        repo.delete(id)
        refreshMatches()
    }

    fun saveProfile(p: PlayerProfile, amateur: Boolean) {
        profileStore.save(p)
        profileStore.amateurZones = amateur
        _state.update { it.copy(profile = p, amateurZones = amateur, message = "Perfil guardado. Los partidos nuevos usarán estos valores.") }
    }

    private fun runBusy(block: suspend () -> Unit) = viewModelScope.launch {
        _state.update { it.copy(busy = true) }
        try {
            block()
        } catch (e: Exception) {
            _state.update { it.copy(message = e.message ?: "Error inesperado") }
        } finally {
            _state.update { it.copy(busy = false) }
        }
    }
}
