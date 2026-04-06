package com.micplugin.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.sp

// ── Studio color palette ──────────────────────────────────────────────────────
object StudioColors {
    val Background   = Color(0xFF050508)
    val Surface      = Color(0xFF0E0E14)
    val Card         = Color(0xFF15151E)
    val CardElevated = Color(0xFF1C1C2A)
    val Accent       = Color(0xFF7C5CFC)
    val AccentDim    = Color(0xFF4A3598)
    val Secondary    = Color(0xFFFC5CF8)
    val MeterGreen   = Color(0xFF3DFCAC)
    val MeterAmber   = Color(0xFFFCB43D)
    val MeterRed     = Color(0xFFFC3D5C)
    val GainReduction = Color(0xFFFC8C3D)
    val TextPrimary  = Color(0xFFE8E8F0)
    val TextMuted    = Color(0xFF666680)
    val Border       = Color(0xFF252535)

    // Plugin format badge colors
    val Lv2Color     = Color(0xFF00BFA5)
    val ClapColor    = Color(0xFF69F0AE)
    val Vst3Color    = Color(0xFFAA00FF)
    val ApkColor     = Color(0xFFFF6D00)
}

private val darkColorScheme = darkColorScheme(
    primary          = StudioColors.Accent,
    onPrimary        = Color.White,
    primaryContainer = StudioColors.AccentDim,
    secondary        = StudioColors.Secondary,
    background       = StudioColors.Background,
    surface          = StudioColors.Surface,
    surfaceVariant   = StudioColors.Card,
    onBackground     = StudioColors.TextPrimary,
    onSurface        = StudioColors.TextPrimary,
    onSurfaceVariant = StudioColors.TextMuted,
    outline          = StudioColors.Border,
    error            = StudioColors.MeterRed,
)

@Composable
fun MicPluginTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme,
        content     = content,
    )
}
