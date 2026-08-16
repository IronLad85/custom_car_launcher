package com.example.carheadunit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AccentViolet,
    secondary = AccentViolet,
    background = GradientStart,
    surface = Color(0xFF16214A),
    onPrimary = GradientStart,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

// Fixed dark scheme: the car UI always renders the same way, regardless of system theme.
@Composable
fun CarHeadUnitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content,
    )
}
