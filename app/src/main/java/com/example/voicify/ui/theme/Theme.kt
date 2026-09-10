package com.example.voicify.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val VoicifyColorScheme = darkColorScheme(
    primary = SpotifyGreen,
    onPrimary = BackgroundBlack,
    secondary = SpotifyGreenDim,
    background = BackgroundBlack,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed
)

@Composable
fun VoicifyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VoicifyColorScheme,
        typography = VoicifyTypography,
        content = content
    )
}