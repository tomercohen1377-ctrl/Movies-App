package com.tcohen.moviesapp.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme()
private val LightColorScheme = lightColorScheme()

/**
 * iOS implementation of [MoviesAppTheme].
 *
 * Uses static Material3 color schemes (dark/light) since iOS does not support
 * Android's dynamic color feature. System-bar appearance is managed by the
 * SwiftUI host — no [SideEffect] needed here.
 */
@Composable
actual fun MoviesAppTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
actual fun defaultDarkTheme(): Boolean = isSystemInDarkTheme()
