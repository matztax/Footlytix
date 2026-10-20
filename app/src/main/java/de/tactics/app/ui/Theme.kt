package de.tactics.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TacticalDark = darkColorScheme(
    primary = Color(0xFF5DE4AE),
    onPrimary = Color(0xFF062A1D),
    secondary = Color(0xFFFFCA28),
    background = Color(0xFF122335),
    onBackground = Color(0xFFE8F0F5),
    surface = Color(0xFF182C42),
    onSurface = Color(0xFFE8F0F5),
    surfaceVariant = Color(0xFF223A55),
    onSurfaceVariant = Color(0xFFB8C8D6)
)

@Composable
fun TacticsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TacticalDark, content = content)
}
