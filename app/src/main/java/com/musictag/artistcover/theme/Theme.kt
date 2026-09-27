package com.musictag.artistcover.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 设计规范色板。 */
private val Teal = Color(0xFF0F766E)
private val TealLight = Color(0xFF14857C)
private val TealContainer = Color(0xFFD3EFEA)
private val SurfaceBg = Color(0xFFF7F8FA)
private val CardWhite = Color(0xFFFFFFFF)
private val TextPrimary = Color(0xFF1A1A1A)
private val TextSecondary = Color(0xFF6B7280)
private val TextTertiary = Color(0xFF9CA3AF)
private val GreenOk = Color(0xFF10B981)
private val AmberWarn = Color(0xFFF59E0B)
private val RedError = Color(0xFFEF4444)
private val BlueInfo = Color(0xFF3B82F6)

val SuccessColor = GreenOk
val WarningColor = AmberWarn
val ErrorColor = RedError
val InfoColor = BlueInfo
val MutedText = TextSecondary
val FaintText = TextTertiary

private val AppColorScheme = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = TealContainer,
    onPrimaryContainer = Color(0xFF04302C),
    secondary = TealLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2F3F1),
    onSecondaryContainer = Color(0xFF0B3B37),
    tertiary = BlueInfo,
    onTertiary = Color.White,
    background = SurfaceBg,
    onBackground = TextPrimary,
    surface = CardWhite,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFEFF2F5),
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFFD6DBE1),
    outlineVariant = Color(0xFFE7EAEE),
    error = RedError,
    onError = Color.White,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, color = TextPrimary),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, color = TextPrimary),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, color = TextSecondary),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun MusicArtistTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
