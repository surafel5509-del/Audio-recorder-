package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = StudioOrange,
    onPrimary = Color.White,
    primaryContainer = StudioCoral.copy(alpha = 0.2f),
    onPrimaryContainer = StudioCoral,
    secondary = StudioCyan,
    onSecondary = Color.Black,
    secondaryContainer = StudioCyan.copy(alpha = 0.2f),
    onSecondaryContainer = StudioCyan,
    tertiary = StudioAmber,
    onTertiary = Color.Black,
    background = StudioDarkBg,
    onBackground = StudioDarkTextPrimary,
    surface = StudioDarkSurface,
    onSurface = StudioDarkTextPrimary,
    surfaceVariant = StudioDarkSurfaceVariant,
    onSurfaceVariant = StudioDarkTextSecondary,
    outline = StudioDarkBorder
)

private val LightColorScheme = lightColorScheme(
    primary = StudioOrange,
    onPrimary = Color.White,
    primaryContainer = StudioOrange.copy(alpha = 0.12f),
    onPrimaryContainer = StudioOrange,
    secondary = StudioCyan,
    onSecondary = Color.Black,
    secondaryContainer = StudioCyan.copy(alpha = 0.15f),
    onSecondaryContainer = Color(0xFF006064),
    tertiary = StudioAmber,
    onTertiary = Color.Black,
    background = StudioLightBg,
    onBackground = StudioLightTextPrimary,
    surface = StudioLightSurface,
    onSurface = StudioLightTextPrimary,
    surfaceVariant = StudioLightSurfaceVariant,
    onSurfaceVariant = StudioLightTextSecondary,
    outline = StudioLightBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
