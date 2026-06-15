package com.tcohen.moviesapp.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform-specific trailer player section.
 *
 * - **Android**: renders an embedded YouTube player (`YouTubePlayerView`) tied to the
 *   lifecycle owner, plays the video automatically once the player is ready.
 * - **iOS / Desktop**: shows a placeholder composable (no native YouTube SDK available).
 *
 * [onPlayerReady] is fired once the player has initialised (Android only). On other
 * platforms it is called immediately so the loading overlay is dismissed.
 */
@Composable
expect fun TrailerPlayerSection(
    trailerKey: String,
    modifier: Modifier = Modifier,
    onPlayerReady: () -> Unit = {}
)
