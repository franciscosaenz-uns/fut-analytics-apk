package com.futbolanalisis.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val PitchGreen = Color(0xFF2E7D32)
val PitchGreenDark = Color(0xFF1B5E20)

private val Light = lightColorScheme(
    primary = Color(0xFF1B5E20),
    secondary = Color(0xFFFF8F00),
    tertiary = Color(0xFF1565C0),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF81C784),
    secondary = Color(0xFFFFB74D),
    tertiary = Color(0xFF64B5F6),
)

@Composable
fun FutbolTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
