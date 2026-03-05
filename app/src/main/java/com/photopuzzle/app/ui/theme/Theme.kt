package com.photopuzzle.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B67CA),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFF43A047),
    background = Color(0xFFF8F9FF),
    surface = Color.White,
    onBackground = Color(0xFF1A1C2E),
    onSurface = Color(0xFF1A1C2E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BA4FF),
    onPrimary = Color(0xFF1A1C2E),
    primaryContainer = Color(0xFF434A9E),
    secondary = Color(0xFF81C784),
    background = Color(0xFF1A1C2E),
    surface = Color(0xFF252740),
    onBackground = Color(0xFFE4E5FF),
    onSurface = Color(0xFFE4E5FF),
)

@Composable
fun PhotoPuzzleTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
