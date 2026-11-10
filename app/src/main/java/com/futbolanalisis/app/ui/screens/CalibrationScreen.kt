package com.futbolanalisis.app.ui.screens

import android.graphics.Color as AColor
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.futbolanalisis.app.data.MatchRecord
import com.futbolanalisis.app.data.SavedPitch
import com.futbolanalisis.app.ui.fmt
import com.futbolanalisis.core.pitch.CalibrationMethod
import com.futbolanalisis.core.pitch.PitchCalibration
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

/** Imágenes satelitales de Esri World Imagery (uso libre con atribución, sin API key). */
private object EsriSatellite : OnlineTileSourceBase(
    "EsriWorldImagery", 1, 19, 256, ".jpg",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
    "Imágenes © Esri, Maxar, Earthstar Geographics",
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" + MapTileIndex.getY(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex)
}

private val stepText = listOf(
    "Toca una esquina de la cancha (la que quieras).",
    "Toca la otra esquina de esa MISMA línea de fondo.",
    "Toca la esquina del fondo contrario, del mismo lateral que la primera.",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    record: MatchRecord?,
    pitches: List<SavedPitch>,
    onApply: (PitchCalibration?, String?) -> Unit,
    onDeletePitch: (String) -> Unit,
    onBack: () -> Unit,
) {
    val corners = remember { mutableStateListOf<GeoPoint>() }
    var measure by remember { mutableStateOf(true) }
    var saveName by remember { mutableStateOf("") }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Calibrar cancha") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
            actions = { TextButton(onClick = { corners.clear() }) { Text("Reiniciar") } },
        )
    }) { padding ->
        if (record == null) return@Scaffold
        val report = record.report
        val preview: PitchCalibration? = if (corners.size == 3) runCatching {
            PitchCalibration.fromThreeCorners(
                corners[0].latitude to corners[0].longitude,
                corners[1].latitude to corners[1].longitude,
                corners[2].latitude to corners[2].longitude,
                measureDims = measure,
                fallback = record.options.pitch,
            )
        }.getOrNull() else null

        Column(Modifier.padding(padding).fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val trackPoints = remember(record.id) {
                    val pts = record.track.points
                    val step = (pts.size / 1500).coerceAtLeast(1)
                    pts.filterIndexed { i, _ -> i % step == 0 }
                        .filter { it.timeMs in report.startMs..report.endMs }
                        .map { GeoPoint(it.lat, it.lon) }
                }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        Configuration.getInstance().userAgentValue = ctx.packageName
                        MapView(ctx).apply {
                            setTileSource(EsriSatellite)
                            setMultiTouchControls(true)
                            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                            maxZoomLevel = 21.0
                            val c = report.calibration.toLatLon(
                                com.futbolanalisis.core.pitch.PitchPoint(report.calibration.dims.length / 2, report.calibration.dims.width / 2),
                            )
                            controller.setZoom(18.5)
                            controller.setCenter(GeoPoint(c.first, c.second))
                            onResume()
                        }
                    },
                    onRelease = { it.onDetach() },
                    update = { map ->
                        map.overlays.clear()
                        map.overlays += MapEventsOverlay(object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                if (corners.size < 3) corners += p
                                return true
                            }

                            override fun longPressHelper(p: GeoPoint): Boolean = false
                        })
                        map.overlays += Polyline(map).apply {
                            setPoints(trackPoints)
                            outlinePaint.color = AColor.argb(170, 255, 235, 59)
                            outlinePaint.strokeWidth = 3f
                        }
                        val current = preview ?: report.calibration
                        map.overlays += Polygon(map).apply {
                            val pts = current.cornersLatLon().map { GeoPoint(it.first, it.second) }
                            setPoints(pts + pts.first())
                            fillPaint.color = if (preview != null) AColor.argb(50, 76, 175, 80) else AColor.argb(30, 255, 255, 255)
                            outlinePaint.color = if (preview != null) AColor.rgb(76, 175, 80) else AColor.WHITE
                            outlinePaint.strokeWidth = 5f
                        }
                        corners.forEachIndexed { i, gp ->
                            map.overlays += Marker(map).apply {
                                position = gp
                                setTextIcon("${i + 1}")
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                isDraggable = true
                                setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                                    override fun onMarkerDrag(marker: Marker) {}
                                    override fun onMarkerDragStart(marker: Marker) {}
                                    override fun onMarkerDragEnd(marker: Marker) {
                                        if (i < corners.size) corners[i] = marker.position
                                    }
                                })
                            }
                        }
                        map.invalidate()
                    },
                )
                Card(Modifier.align(Alignment.TopCenter).padding(12.dp)) {
                    Text(
                        if (corners.size < 3) "${corners.size + 1}/3 · ${stepText[corners.size]}" else "Listo. Arrastra los números para ajustar.",
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "En amarillo, tu recorrido. " + if (report.calibration.method == CalibrationMethod.AUTO)
                        "La cancha blanca es la estimada automáticamente." else "La cancha blanca es la calibración actual.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (pitches.isNotEmpty() && corners.isEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pitches.forEach { p ->
                            AssistChip(onClick = { onApply(p.calibration, null); onBack() }, label = { Text("Usar: ${p.name}") })
                        }
                    }
                }
                if (preview != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(measure, { measure = it })
                        Text("Usar el tamaño medido: ${preview.dims.length.fmt(0)} × ${preview.dims.width.fmt(0)} m")
                    }
                    OutlinedTextField(saveName, { saveName = it }, label = { Text("Guardar cancha como (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { onApply(preview, saveName.trim().ifBlank { null }); onBack() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Aplicar calibración")
                    }
                }
                if (report.calibration.method == CalibrationMethod.CORNERS) {
                    OutlinedButton(onClick = { onApply(null, null); onBack() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Volver a la calibración automática")
                    }
                }
                if (pitches.isNotEmpty() && corners.isEmpty()) {
                    Text(
                        "Las canchas guardadas se aplican solas a los partidos nuevos jugados en el mismo lugar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    pitches.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.name, Modifier.weight(1f))
                            TextButton(onClick = { onDeletePitch(p.name) }) { Text("Borrar") }
                        }
                    }
                }
            }
        }
    }
}
