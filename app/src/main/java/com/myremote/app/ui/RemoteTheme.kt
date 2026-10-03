package com.myremote.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkRemoteColors = darkColorScheme(
    primary = Color(0xFF99C2FF),
    onPrimary = Color(0xFF092544),
    secondary = Color(0xFFB6C8E1),
    background = Color(0xFF10131B),
    onBackground = Color(0xFFE8EAF1),
    surface = Color(0xFF1A2030),
    onSurface = Color(0xFFE8EAF1),
    surfaceVariant = Color(0xFF293247),
    onSurfaceVariant = Color(0xFFCDD5E6),
)

@Composable
fun RemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkRemoteColors, content = content)
}
