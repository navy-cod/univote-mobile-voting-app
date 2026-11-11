package com.univote.evoting.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = NavyContainer,
    onPrimaryContainer = OnNavyContainer,
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = TealContainer,
    onSecondaryContainer = OnTealContainer,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    error = ErrorLight
)

private val DarkColors = darkColorScheme(
    primary = NavyDarkPrimary,
    onPrimary = OnNavyDarkPrimary,
    primaryContainer = NavyDarkContainer,
    onPrimaryContainer = OnNavyDarkContainer,
    secondary = TealDark,
    onSecondary = OnTealDark,
    secondaryContainer = TealDarkContainer,
    onSecondaryContainer = OnTealDarkContainer,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    error = ErrorDark
)

/** Fixed brand colours (no dynamic colour) so contrast stays predictable for the accessibility tests. */
@Composable
fun UniVoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
