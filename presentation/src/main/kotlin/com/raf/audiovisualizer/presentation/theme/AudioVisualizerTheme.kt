package com.raf.audiovisualizer.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StudioDarkColorScheme = darkColorScheme(
    primary = Color(0xFF9E8CFF),
    secondary = Color(0xFF4DD0E1),
    tertiary = Color(0xFFFF80AB),
    background = Color(0xFF0A0A10),
    surface = Color(0xFF12121C),
    surfaceVariant = Color(0xFF1C1C2A),
    onPrimary = Color.Black,
    onBackground = Color(0xFFF3F4F8),
    onSurface = Color(0xFFF3F4F8),
    onSurfaceVariant = Color(0xFFB4B8C8),
)

/**
 * Dark studio-grade Material 3 theme optimized for high-contrast audio spectrum and ambient
 * album-art color rendering.
 */
@Composable
fun AudioVisualizerTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = StudioDarkColorScheme,
        content = content,
    )
}
