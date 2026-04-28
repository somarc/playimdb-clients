package com.playimdb.tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val Background = Color(0xFF0A1020)
internal val Surface = Color(0xFF1A1A22)
internal val SurfaceFocused = Color(0xFF4A3F1A)
internal val Border = Color(0xFF2A2A38)
internal val Accent = Color(0xFFF5C518)
internal val AccentOrange = Color(0xFFE05C2A)
internal val TextPrimary = Color(0xFFFFFFFF)
internal val TextMuted = Color(0xFF8888AA)
internal val TextSecondary = Color(0xFFAAAACC)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.Black,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
)

@Composable
fun PlayImdbTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
