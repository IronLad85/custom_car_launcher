package com.example.carheadunit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    background = SurfaceBg,
    surface = SurfaceContainer,
    surfaceContainerLowest = SurfaceLowest,
    surfaceContainerHighest = SurfaceHighest,
    onSurface = OnSurface,
    onSurfaceVariant = OnSurfaceVariant,
    outlineVariant = OutlineVariant,
    primary = Primary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = Color(0xFF00363D),
    secondary = SecondaryFixed,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = Color(0xFF002200),
    error = ErrorRed,
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
