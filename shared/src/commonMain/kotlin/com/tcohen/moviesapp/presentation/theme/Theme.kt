package com.tcohen.moviesapp.presentation.theme

import androidx.compose.runtime.Composable

/**
 * Platform-specific Material3 theme wrapper.
 *
 * - **Android**: applies dynamic color (Android 12+) and configures status-bar appearance.
 * - **iOS**: applies a static purple/orange Material3 color scheme; system-bar tinting
 *   is handled by the native SwiftUI host.
 */
@Composable
expect fun MoviesAppTheme(
    darkTheme: Boolean = defaultDarkTheme(),
    content: @Composable () -> Unit
)

/** Platform-specific default: whether the system is currently in dark mode. */
@Composable
expect fun defaultDarkTheme(): Boolean
