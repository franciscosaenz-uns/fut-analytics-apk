package com.futbolanalisis.app.ui.components

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.futbolanalisis.app.data.SatelliteStore
import com.futbolanalisis.core.MatchReport

sealed interface SatelliteState {
    data object Off : SatelliteState
    data object Loading : SatelliteState
    data object Failed : SatelliteState
    data class Ready(val image: ImageBitmap, val bitmap: Bitmap) : SatelliteState
}

/** Carga (o descarga la primera vez) la foto satelital alineada con la cancha del partido. */
@Composable
fun rememberSatellite(report: MatchReport, enabled: Boolean): SatelliteState {
    val context = LocalContext.current.applicationContext
    val state by produceState<SatelliteState>(
        initialValue = if (enabled) SatelliteState.Loading else SatelliteState.Off,
        report.calibration, report.tactical.attackFlipped, enabled,
    ) {
        if (!enabled) {
            value = SatelliteState.Off
        } else {
            value = SatelliteState.Loading
            val bmp = SatelliteStore(context).load(report.calibration, report.tactical.attackFlipped, SATELLITE_MARGIN_M)
            value = if (bmp != null) SatelliteState.Ready(bmp.asImageBitmap(), bmp) else SatelliteState.Failed
        }
    }
    return state
}
