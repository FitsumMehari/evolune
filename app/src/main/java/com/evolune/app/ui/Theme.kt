package com.evolune.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.evolune.app.domain.ThemeMode

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC9B8FF),
    onPrimary = Color(0xFF25164E),
    primaryContainer = Color(0xFF3A286A),
    onPrimaryContainer = Color(0xFFE8DEFF),
    secondary = Color(0xFF8FD9C8),
    tertiary = Color(0xFFFFC77D),
    background = Color(0xFF0B1020),
    surface = Color(0xFF11182A),
    surfaceVariant = Color(0xFF1A2337),
    onBackground = Color(0xFFEAF0FF),
    onSurface = Color(0xFFEAF0FF),
    outline = Color(0xFF818AA3)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5D43A8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7DEFF),
    onPrimaryContainer = Color(0xFF21124D),
    secondary = Color(0xFF23695E),
    tertiary = Color(0xFF87520D),
    background = Color(0xFFF7F8FC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE9ECF4),
    onBackground = Color(0xFF171B27),
    onSurface = Color(0xFF171B27),
    outline = Color(0xFF737987)
)

@Composable
fun EvoluneTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = Typography(), content = content)
}
