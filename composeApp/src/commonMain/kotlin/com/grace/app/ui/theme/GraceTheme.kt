package com.grace.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GraceColorScheme = lightColorScheme(
    primary = GraceColors.Primary,
    onPrimary = Color.White,
    primaryContainer = GraceColors.Primary,
    onPrimaryContainer = Color.White,
    secondary = GraceColors.PrimaryDark,
    onSecondary = Color.White,
    tertiary = GraceColors.Accent,
    onTertiary = Color.White,
    background = GraceColors.Primary,
    onBackground = Color.White,
    surface = GraceColors.Primary,
    onSurface = Color.White,
    error = GraceColors.Accent,
    onError = Color.White
)

private val GraceDarkColorScheme = darkColorScheme(
    primary = GraceColors.Primary,
    onPrimary = Color.White,
    primaryContainer = GraceColors.Primary,
    onPrimaryContainer = Color.White,
    secondary = GraceColors.PrimaryDark,
    onSecondary = Color.White,
    tertiary = GraceColors.Accent,
    onTertiary = Color.White,
    background = GraceColors.Primary,
    onBackground = Color.White,
    surface = GraceColors.Primary,
    onSurface = Color.White,
    error = GraceColors.Accent,
    onError = Color.White
)

/**
 * The app is intentionally not theme-aware: the original used a single light
 * AppCompat theme with fixed brand colours, so we keep one scheme regardless of
 * the system setting (the parameter is kept so a future dark mode is a one-line
 * change).
 */
@Composable
fun GraceTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = GraceColorScheme,
        typography = graceTypography(),
        content = content
    )
}
