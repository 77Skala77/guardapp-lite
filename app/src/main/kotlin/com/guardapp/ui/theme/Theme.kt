// ui/theme/Theme.kt
package com.guardapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4F8EF7),
    secondary = Color(0xFFA78BFA),
    tertiary = Color(0xFF4ECB8D),
    background = Color(0xFF0D0F14),
    surface = Color(0xFF161920),
    surfaceVariant = Color(0xFF1E2230),
    onBackground = Color(0xFFE8ECF4),
    onSurface = Color(0xFFE8ECF4),
    error = Color(0xFFE85D5D)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF3A7AE8),
    secondary = Color(0xFF8B6BE0),
    tertiary = Color(0xFF2BA070),
    error = Color(0xFFE85D5D)
)

@Composable
fun GuardAppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
