package com.myremote.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkRemoteColors = darkColorScheme(
    primary = Color(0xFF36C9FF),
    onPrimary = Color(0xFF04283E),
    secondary = Color(0xFFADBBD3),
    background = Color(0xFF080F18),
    onBackground = Color(0xFFE4ECFF),
    surface = Color(0xFF152231),
    onSurface = Color(0xFFE4ECFF),
    surfaceVariant = Color(0xFF243448),
    onSurfaceVariant = Color(0xFFBCCBE2),
)

@Composable
fun RemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkRemoteColors, content = content)
}
