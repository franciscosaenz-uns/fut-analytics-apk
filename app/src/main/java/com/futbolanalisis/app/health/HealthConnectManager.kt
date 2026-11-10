package com.futbolanalisis.app.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.futbolanalisis.core.model.HeartRateSample
import com.futbolanalisis.core.model.Track
import com.futbolanalisis.core.model.TrackPoint
import com.futbolanalisis.core.model.TrackSource
import com.futbolanalisis.core.physio.WellnessSnapshot
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.reflect.KClass

enum class HcAvailability { AVAILABLE, NOT_INSTALLED, UPDATE_REQUIRED, NOT_SUPPORTED }

data class HcSession(
    val id: String,
    val title: String?,
    val start: Instant,
    val end: Instant,
    val exerciseType: Int,
    val origin: String,
) {
    val isSoccer get() = exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_SOCCER
}

sealed interface RouteResult {
    data class Ok(val track: Track) : RouteResult
    /** El usuario debe autorizar la ruta de esta sesión (diálogo de Health Connect). */
    data object ConsentRequired : RouteResult
    data object NoRoute : RouteResult
}

/**
 * Lectura de Health Connect. Zepp (Amazfit) escribe aquí FC, pasos, SpO2 y entrenamientos;
 * la HRV, el VO2 máx y la ruta GPS dependen de la versión de Zepp, así que todo es opcional.
 */
class HealthConnectManager(private val context: Context) {

    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(Vo2MaxRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        "android.permission.health.READ_EXERCISE_ROUTES",
    )

    fun availability(): HcAvailability = when (HealthConnectClient.getSdkStatus(context, PROVIDER)) {
        HealthConnectClient.SDK_AVAILABLE -> HcAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HcAvailability.UPDATE_REQUIRED
        else -> if (android.os.Build.VERSION.SDK_INT >= 28) HcAvailability.NOT_INSTALLED else HcAvailability.NOT_SUPPORTED
    }

    private val client: HealthConnectClient? by lazy {
        if (availability() == HcAvailability.AVAILABLE) HealthConnectClient.getOrCreate(context) else null
    }

    fun permissionContract() = PermissionController.createRequestPermissionResultContract()

    suspend fun grantedPermissions(): Set<String> = client?.permissionController?.getGrantedPermissions() ?: emptySet()

    suspend fun hasAllPermissions(): Boolean = grantedPermissions().containsAll(permissions)

    fun installIntent(): Intent = Intent(Intent.ACTION_VIEW).apply {
        setPackage("com.android.vending")
        data = Uri.parse("market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding")
        putExtra("overlay", true)
        putExtra("callerId", context.packageName)
    }

    /** Sesiones de ejercicio de los últimos [days] días, primero las de fútbol. */
    suspend fun recentSessions(days: Long = 30): List<HcSession> {
        val c = client ?: return emptyList()
        val end = Instant.now()
        val records = readAll(c, ExerciseSessionRecord::class, TimeRangeFilter.between(end.minus(Duration.ofDays(days)), end))
        return records.map {
            HcSession(
                id = it.metadata.id,
                title = it.title,
                start = it.startTime,
                end = it.endTime,
                exerciseType = it.exerciseType,
                origin = it.metadata.dataOrigin.packageName,
            )
        }.sortedWith(compareByDescending<HcSession> { it.isSoccer }.thenByDescending { it.start })
    }

    suspend fun readRoute(sessionId: String): RouteResult {
        val c = client ?: return RouteResult.NoRoute
        val session = c.readRecord(ExerciseSessionRecord::class, sessionId).record
        return when (val r = session.exerciseRouteResult) {
            is ExerciseRouteResult.Data -> routeToTrack(r.exerciseRoute, session.title)
            is ExerciseRouteResult.ConsentRequired -> RouteResult.ConsentRequired
            else -> RouteResult.NoRoute
        }
    }

    fun routeToTrack(route: ExerciseRoute, title: String?): RouteResult {
        val pts = route.route.map {
            TrackPoint(it.time.toEpochMilli(), it.latitude, it.longitude, it.altitude?.inMeters)
        }
        return if (pts.size < 30) RouteResult.NoRoute
        else RouteResult.Ok(Track(name = title, source = TrackSource.HEALTH_CONNECT, points = pts))
    }

    suspend fun heartRate(start: Instant, end: Instant): List<HeartRateSample> {
        val c = client ?: return emptyList()
        if (HealthPermission.getReadPermission(HeartRateRecord::class) !in grantedPermissions()) return emptyList()
        return readAll(c, HeartRateRecord::class, TimeRangeFilter.between(start, end))
            .flatMap { rec -> rec.samples.map { HeartRateSample(it.time.toEpochMilli(), it.beatsPerMinute.toInt()) } }
            .filter { it.timeMs in start.toEpochMilli()..end.toEpochMilli() }
            .sortedBy { it.timeMs }
    }

    /** Estado fisiológico alrededor de [day]: última HRV/SpO2/VO2/FC reposo y pasos del día. */
    suspend fun wellness(day: Instant = Instant.now()): WellnessSnapshot? {
        val c = client ?: return null
        val granted = grantedPermissions()
        fun has(k: KClass<out Record>) = HealthPermission.getReadPermission(k) in granted
        val zone = ZoneId.systemDefault()
        val dayStart = day.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
        val dayEnd = dayStart.plus(Duration.ofDays(1))
        val lookback = TimeRangeFilter.between(dayStart.minus(Duration.ofDays(8)), minOf(dayEnd, Instant.now()))

        val hrv = if (has(HeartRateVariabilityRmssdRecord::class))
            readAll(c, HeartRateVariabilityRmssdRecord::class, lookback).sortedBy { it.time } else emptyList()
        val latestHrv = hrv.lastOrNull()
        val baseline = hrv.filter { latestHrv != null && it.time < latestHrv.time.minus(Duration.ofHours(12)) }
            .map { it.heartRateVariabilityMillis }.takeIf { it.size >= 3 }?.average()

        val spo2 = if (has(OxygenSaturationRecord::class))
            readAll(c, OxygenSaturationRecord::class, lookback).maxByOrNull { it.time }?.percentage?.value else null
        val vo2Range = TimeRangeFilter.between(dayStart.minus(Duration.ofDays(90)), minOf(dayEnd, Instant.now()))
        val vo2 = if (has(Vo2MaxRecord::class))
            readAll(c, Vo2MaxRecord::class, vo2Range).maxByOrNull { it.time }?.vo2MillilitersPerMinuteKilogram else null
        val resting = if (has(RestingHeartRateRecord::class))
            readAll(c, RestingHeartRateRecord::class, lookback).maxByOrNull { it.time }?.beatsPerMinute?.toInt() else null
        val steps = if (has(StepsRecord::class)) runCatching {
            c.aggregate(
                AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(dayStart, minOf(dayEnd, Instant.now())))
            )[StepsRecord.COUNT_TOTAL]
        }.getOrNull() else null

        return WellnessSnapshot(
            restingHr = resting,
            hrvRmssdMs = latestHrv?.heartRateVariabilityMillis,
            hrvBaselineMs = baseline,
            spo2Pct = spo2,
            vo2Max = vo2,
            stepsToday = steps,
        )
    }

    private suspend fun <T : Record> readAll(c: HealthConnectClient, type: KClass<T>, range: TimeRangeFilter): List<T> {
        val out = ArrayList<T>()
        var token: String? = null
        do {
            val resp = c.readRecords(ReadRecordsRequest(type, range, pageSize = 1000, pageToken = token))
            out += resp.records
            token = resp.pageToken
        } while (token != null)
        return out
    }

    companion object {
        const val PROVIDER = "com.google.android.apps.healthdata"
    }
}
