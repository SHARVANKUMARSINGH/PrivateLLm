package com.privtellm.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Google Material You Dark Color Scheme (Pixel / Gemini Clean Aesthetic)
private val DarkColorScheme = darkColorScheme(
    primary = GoogleBlueDark,
    onPrimary = Color(0xFF041E49),
    primaryContainer = GoogleBlueContainerDark,
    onPrimaryContainer = GoogleOnBlueContainerDark,
    secondary = Color(0xFF7FCFFF),
    onSecondary = Color(0xFF00344F),
    secondaryContainer = Color(0xFF004D74),
    onSecondaryContainer = Color(0xFFC2E7FF),
    tertiary = GoogleGreenDark,
    onTertiary = Color(0xFF003915),
    tertiaryContainer = GoogleGreenContainerDark,
    onTertiaryContainer = Color(0xFFA6F5B4),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkBorderSubtle,
    error = GoogleRedDark,
    onError = Color(0xFF601410),
    errorContainer = GoogleRedContainerDark,
    onErrorContainer = Color(0xFFF9DEDC)
)

// Google Material You Light Color Scheme (Google Workspace / Google Messages Clean Aesthetic)
private val LightColorScheme = lightColorScheme(
    primary = GoogleBlue,
    onPrimary = Color.White,
    primaryContainer = GoogleBlueContainerLight,
    onPrimaryContainer = GoogleOnBlueContainerLight,
    secondary = Color(0xFF00639B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC2E7FF),
    onSecondaryContainer = Color(0xFF001D32),
    tertiary = GoogleGreen,
    onTertiary = Color.White,
    tertiaryContainer = GoogleGreenContainerLight,
    onTertiaryContainer = Color(0xFF07210C),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = LightBorderSubtle,
    error = GoogleRed,
    onError = Color.White,
    errorContainer = GoogleRedContainerLight,
    onErrorContainer = Color(0xFF410002)
)

@Composable
fun PrivteLLMTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.surface.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
