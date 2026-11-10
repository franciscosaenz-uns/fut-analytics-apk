package com.futbolanalisis.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.futbolanalisis.core.geo.SatellitePlan
import com.futbolanalisis.core.geo.SatellitePlanner
import com.futbolanalisis.core.geo.TileRef
import com.futbolanalisis.core.pitch.PitchCalibration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.hypot

/**
 * Foto satelital de la cancha, ya girada y recortada para que coincida con el dibujo de la
 * cancha en la app (ataque hacia la derecha). Usa las imágenes de Esri World Imagery, igual
 * que la calibración. Se descarga una vez y queda guardada en el teléfono.
 */
class SatelliteStore(context: Context) {
    private val dir = File(context.filesDir, "satellite").apply { mkdirs() }

    companion object {
        const val ATTRIBUTION = "Imágenes © Esri, Maxar, Earthstar Geographics"
        private const val BASE = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer"
        private const val OUT_WIDTH = 1170
    }

    private fun fileFor(calibration: PitchCalibration, attackFlipped: Boolean, marginM: Double): File {
        // La clave no depende del zoom: una sola imagen por cancha y orientación.
        val key = SatellitePlanner.plan(calibration, attackFlipped, marginM, zoom = 19).cacheKey
        return File(dir, "sat_${Integer.toHexString(key.hashCode())}.jpg")
    }

    /** Devuelve la foto alineada o null si no hay conexión ni copia guardada. */
    suspend fun load(calibration: PitchCalibration, attackFlipped: Boolean, marginM: Double): Bitmap? = withContext(Dispatchers.IO) {
        val file = fileFor(calibration, attackFlipped, marginM)
        if (file.exists()) BitmapFactory.decodeFile(file.path)?.let { return@withContext it }
        runCatching {
            val plan = choosePlan(calibration, attackFlipped, marginM)
            val mosaic = Bitmap.createBitmap(plan.mosaicWidth, plan.mosaicHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(mosaic)
            for (t in plan.tiles) {
                val tile = fetch(t) ?: return@runCatching null
                canvas.drawBitmap(tile, (t.x - plan.tileX) * 256f, (t.y - plan.tileY) * 256f, null)
                tile.recycle()
            }
            val out = warp(mosaic, plan)
            mosaic.recycle()
            file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            out
        }.getOrNull()
    }

    /** El mayor zoom con imagen disponible en esa zona (hay lugares sin detalle a zoom 20 o 19). */
    private fun choosePlan(calibration: PitchCalibration, attackFlipped: Boolean, marginM: Double): SatellitePlan {
        for (zoom in listOf(20, 19, 18)) {
            val plan = SatellitePlanner.plan(calibration, attackFlipped, marginM, zoom = zoom, maxTiles = 30)
            if (plan.zoom == zoom && hasImagery(plan)) return plan
        }
        return SatellitePlanner.plan(calibration, attackFlipped, marginM, zoom = 17)
    }

    /** Consulta el mapa de disponibilidad de Esri; ante cualquier duda asume que sí hay imagen a zoom ≤ 19. */
    private fun hasImagery(plan: SatellitePlan): Boolean {
        val x = plan.tileX + plan.tilesX / 2
        val y = plan.tileY + plan.tilesY / 2
        return runCatching {
            val text = open("$BASE/tilemap/${plan.zoom}/$y/$x/1/1?f=json").inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            val data = Regex("\"data\"\\s*:\\s*\\[\\s*(\\d)").find(text)?.groupValues?.get(1)
            if (data != null) data == "1" else plan.zoom <= 19
        }.getOrDefault(plan.zoom <= 19)
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 15_000
        setRequestProperty("User-Agent", "FutbolAnalisis-Android")
    }

    private fun fetch(t: TileRef): Bitmap? = runCatching {
        val conn = open("$BASE/tile/${t.zoom}/${t.y}/${t.x}")
        try {
            if (conn.responseCode != 200) null else conn.inputStream.use { BitmapFactory.decodeStream(it) }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /** Gira y recorta el mosaico para que las esquinas del recuadro queden en las esquinas de la imagen. */
    private fun warp(mosaic: Bitmap, plan: SatellitePlan): Bitmap {
        val (tl, tr, br, bl) = plan.corners
        val wPx = hypot(tr.first - tl.first, tr.second - tl.second)
        val hPx = hypot(bl.first - tl.first, bl.second - tl.second)
        val w = OUT_WIDTH
        val h = (w * hPx / wPx).toInt().coerceAtLeast(1)
        val src = floatArrayOf(
            tl.first.toFloat(), tl.second.toFloat(), tr.first.toFloat(), tr.second.toFloat(),
            br.first.toFloat(), br.second.toFloat(), bl.first.toFloat(), bl.second.toFloat(),
        )
        val dst = floatArrayOf(0f, 0f, w.toFloat(), 0f, w.toFloat(), h.toFloat(), 0f, h.toFloat())
        val matrix = Matrix().apply { setPolyToPoly(src, 0, dst, 0, 4) }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(mosaic, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }
}
