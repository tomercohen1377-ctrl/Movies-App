package com.tcohen.moviesapp.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * iOS placeholder for the trailer player.
 * Calls [onPlayerReady] immediately so the loading overlay is dismissed.
 */
@Composable
actual fun TrailerPlayerSection(
    trailerKey: String,
    modifier: Modifier,
    onPlayerReady: () -> Unit
) {
    // Dismiss loading overlay immediately since there is no async init on iOS.
    LaunchedEffect(trailerKey) { onPlayerReady() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(250.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Trailer unavailable on this platform",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
