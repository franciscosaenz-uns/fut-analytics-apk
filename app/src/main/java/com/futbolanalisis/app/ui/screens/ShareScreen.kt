package com.futbolanalisis.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.futbolanalisis.app.data.MatchRecord
import com.futbolanalisis.app.data.SatelliteStore
import com.futbolanalisis.app.share.ShareExporter
import com.futbolanalisis.app.share.ShareRenderer
import com.futbolanalisis.app.ui.components.AppIcons
import com.futbolanalisis.app.ui.components.Overline
import com.futbolanalisis.app.ui.components.SATELLITE_MARGIN_M
import com.futbolanalisis.app.ui.components.SatelliteState
import com.futbolanalisis.app.ui.components.rememberSatellite
import com.futbolanalisis.app.ui.dateOnly
import com.futbolanalisis.app.ui.theme.Brand
import com.futbolanalisis.core.insights.MatchComparator
import com.futbolanalisis.core.insights.MatchKpis
import com.futbolanalisis.core.insights.PerformanceScore
import com.futbolanalisis.core.model.PlayerProfile
import com.futbolanalisis.core.pitch.CalibrationMethod
import com.futbolanalisis.core.share.ShareCardData
import com.futbolanalisis.core.share.ShareFormat
import com.futbolanalisis.core.share.ShareStat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Arma los datos de la imagen para compartir a partir del partido. */
fun shareData(record: MatchRecord, profile: PlayerProfile, satellite: Boolean): ShareCardData {
    val r = record.report
    val k = MatchKpis.of(r)
    return ShareCardData(
        title = record.title,
        ownTeam = record.log.ownTeam, rival = record.log.opponent,
        goalsFor = record.log.goalsFor, goalsAgainst = record.log.goalsAgainst,
        dateText = record.dateMs.dateOnly(),
        playerName = profile.name.takeIf { it != "Jugador" }.orEmpty(),
        role = r.tactical.description.archetype,
        score = PerformanceScore.of(k),
        stats = listOf(
            ShareStat("Distancia", MatchComparator.format(k.distanceM / 1000, 2), "km"),
            ShareStat("Vel. máx", MatchComparator.format(k.maxSpeedKmh, 1), "km/h"),
            ShareStat("Sprints", "${k.sprints}"),
            ShareStat("Ritmo", MatchComparator.format(k.metersPerMinute, 0), "m/min"),
        ),
        heatmap = r.tactical.heatmap,
        satelliteMarginM = if (satellite) SATELLITE_MARGIN_M else 3.0,
        attribution = "Imágenes © Esri, Maxar",
        // Con calibración automática las líneas no coinciden con la cancha real de la foto.
        showPitchLines = !satellite || r.calibration.method == CalibrationMethod.CORNERS,
    )
}

/** Vista previa y exportación de la imagen del partido para redes sociales. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(record: MatchRecord?, profile: PlayerProfile, onMessage: (String) -> Unit, onBack: () -> Unit) {
    Scaffold(
        containerColor = Brand.Bg,
        topBar = {
            TopAppBar(
                title = { Text("Compartir partido") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
            )
        },
    ) { padding ->
        if (record == null) return@Scaffold
        val context = LocalContext.current
        var formatIndex by rememberSaveable { mutableIntStateOf(0) }
        var satelliteOn by rememberSaveable { mutableStateOf(true) }
        val format = ShareFormat.entries[formatIndex]
        val sat = rememberSatellite(record.report, satelliteOn)
        val backdrop: Bitmap? = (sat as? SatelliteState.Ready)?.bitmap
        val image by produceState<Bitmap?>(null, record, profile, format, backdrop, satelliteOn) {
            value = withContext(Dispatchers.Default) {
                runCatching { ShareRenderer.render(shareData(record, profile, satelliteOn && backdrop != null), format, backdrop) }.getOrNull()
            }
        }

        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Overline("Formato")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShareFormat.entries.forEachIndexed { i, f -> FilterChip(formatIndex == i, { formatIndex = i }, label = { Text(f.label) }) }
            }
            Overline("Fondo del mapa de calor", modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(satelliteOn, { satelliteOn = true }, label = { Text("Vista satelital") })
                FilterChip(!satelliteOn, { satelliteOn = false }, label = { Text("Cancha dibujada") })
            }
            when (sat) {
                SatelliteState.Loading -> Text("Descargando la vista satelital…", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                SatelliteState.Failed -> Text(
                    "No se pudo descargar la vista satelital (sin conexión). Se usa la cancha dibujada.",
                    style = MaterialTheme.typography.bodySmall, color = Brand.Warn,
                )
                else -> if (satelliteOn && record.report.calibration.method != CalibrationMethod.CORNERS) Text(
                    "Consejo: calibra la cancha (pestaña Mapa) para que el calor encaje exacto sobre la foto.",
                    style = MaterialTheme.typography.bodySmall, color = Brand.Muted,
                )
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().aspectRatio(format.width.toFloat() / format.height).clip(RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = image
                if (bmp != null) Image(bmp.asImageBitmap(), "Vista previa", Modifier.fillMaxSize()) else CircularProgressIndicator()
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { image?.let { runCatching { ShareExporter.share(context, it, record.title) }.onFailure { onMessage("No se pudo compartir la imagen") } } },
                enabled = image != null, modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Icon(Icons.Filled.Share, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp))
                Text("Compartir en Instagram u otra app", fontWeight = FontWeight.Bold)
            }
            if (ShareExporter.canSaveToGallery) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        image?.let { onMessage(if (ShareExporter.saveToGallery(context, it, record.title)) "Imagen guardada en Imágenes/FutbolAnalisis" else "No se pudo guardar la imagen") }
                    },
                    enabled = image != null, modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(AppIcons.Download, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)); Text("Guardar en la galería")
                }
            }
            Text(
                "En el menú de compartir elige Instagram (Publicación o Historia). La imagen sale a 1080 px, lista para subir.",
                style = MaterialTheme.typography.bodySmall, color = Brand.Muted, modifier = Modifier.padding(vertical = 14.dp),
            )
        }
    }
}
