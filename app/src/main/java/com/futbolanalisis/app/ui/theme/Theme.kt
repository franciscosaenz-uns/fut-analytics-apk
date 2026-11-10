package com.futbolanalisis.app.ui.theme

import android.graphics.Typeface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Paleta de la marca: fondo casi negro, superficies azul noche y un acento lima. */
object Brand {
    val Bg = Color(0xFF0A0E13)
    val Surface = Color(0xFF121A24)
    val SurfaceHigh = Color(0xFF1A2431)
    val Outline = Color(0xFF263445)
    val Accent = Color(0xFFC5F82A)
    val OnAccent = Color(0xFF0A0E13)
    val Text = Color(0xFFF3F6F8)
    val Muted = Color(0xFF8D9BAB)
    val Good = Color(0xFF3DDC97)
    val Bad = Color(0xFFFF6B6B)
    val Warn = Color(0xFFFFB547)
    val Info = Color(0xFF4DA3FF)
    val Turf = Color(0xFF15402B)
    val TurfAlt = Color(0xFF184A31)
}

// Se conservan para las pantallas que dibujan la cancha.
val PitchGreen = Brand.Turf
val PitchGreenDark = Color(0xFF0F2E1F)

private val Scheme = darkColorScheme(
    primary = Brand.Accent,
    onPrimary = Brand.OnAccent,
    primaryContainer = Color(0xFF2B3A0F),
    onPrimaryContainer = Color(0xFFE4FF9A),
    secondary = Brand.Info,
    onSecondary = Brand.OnAccent,
    secondaryContainer = Brand.SurfaceHigh,
    onSecondaryContainer = Brand.Text,
    tertiary = Brand.Warn,
    onTertiary = Brand.OnAccent,
    tertiaryContainer = Color(0xFF3A2C10),
    onTertiaryContainer = Color(0xFFFFE2B0),
    background = Brand.Bg,
    onBackground = Brand.Text,
    surface = Brand.Bg,
    onSurface = Brand.Text,
    surfaceVariant = Brand.SurfaceHigh,
    onSurfaceVariant = Brand.Muted,
    surfaceContainerLowest = Brand.Bg,
    surfaceContainerLow = Brand.Surface,
    surfaceContainer = Brand.Surface,
    surfaceContainerHigh = Brand.SurfaceHigh,
    surfaceContainerHighest = Brand.SurfaceHigh,
    outline = Color(0xFF3A4A5E),
    outlineVariant = Brand.Outline,
    error = Brand.Bad,
    onError = Brand.OnAccent,
    errorContainer = Color(0xFF3B1719),
    onErrorContainer = Color(0xFFFFC9C9),
    inverseSurface = Brand.Text,
    inverseOnSurface = Brand.Bg,
    scrim = Color.Black,
)

/** Fuente condensada del sistema para los números grandes, como en los marcadores deportivos. */
val Condensed: FontFamily = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))

/** Estilo para cifras destacadas. */
fun numberStyle(size: Int): TextStyle = TextStyle(
    fontFamily = Condensed,
    fontWeight = FontWeight.Bold,
    fontSize = size.sp,
    lineHeight = (size * 1.05f).sp,
)

/** Etiqueta pequeña en mayúsculas con espaciado. */
val OverlineStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.12.em, lineHeight = 14.sp)

private val base = Typography()
private val AppTypography = Typography(
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun FutbolTheme(content: @Composable () -> Unit) {
    // Tema oscuro fijo: es la identidad de la app y hace resaltar mapas y gráficos.
    MaterialTheme(colorScheme = Scheme, typography = AppTypography, shapes = AppShapes, content = content)
}
