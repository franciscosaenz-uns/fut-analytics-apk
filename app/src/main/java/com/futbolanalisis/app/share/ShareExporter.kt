package com.futbolanalisis.app.share

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

object ShareExporter {
    /** Guardar en la galería sin pedir permisos solo es posible desde Android 10. */
    val canSaveToGallery: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** Abre el menú de compartir del sistema (Instagram, WhatsApp, etc.) con la imagen. */
    fun share(context: Context, bitmap: Bitmap, name: String) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "${safe(name)}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Compartir partido").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Guarda la imagen en Imágenes/FutbolAnalisis. Devuelve true si se guardó. */
    fun saveToGallery(context: Context, bitmap: Bitmap, name: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${safe(name)}_${System.currentTimeMillis() / 1000}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FutbolAnalisis")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return runCatching {
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: false
        }.getOrDefault(false)
    }

    private fun safe(name: String) = name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "partido" }.take(40)
}
