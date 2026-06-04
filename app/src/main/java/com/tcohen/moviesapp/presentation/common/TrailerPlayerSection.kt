package com.tcohen.moviesapp.presentation.common

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tcohen.moviesapp.presentation.theme.MoviesAppTheme
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

private const val TAG = "TrailerPlayer"

@Composable
fun TrailerPlayerSection(
    trailerKey: String,
    modifier: Modifier = Modifier,
    /** Called once the YouTube player is initialised (before the video plays). */
    onPlayerReady: () -> Unit = {}
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { context ->
            YouTubePlayerView(context).apply {
                lifecycleOwner.lifecycle.addObserver(this)
                addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                    override fun onReady(youTubePlayer: YouTubePlayer) {
                        Log.d(TAG, "Player is ready")
                        onPlayerReady()
                        youTubePlayer.loadVideo(trailerKey, 0f)
                    }

                    override fun onError(
                        youTubePlayer: YouTubePlayer,
                        error: PlayerConstants.PlayerError
                    ) {
                        Log.e(TAG, "Error loading trailer: $error")
                    }
                })
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(250.dp)
    )
}

// The YouTube player is an AndroidView/WebView — it cannot render in the Compose preview
// tool. This preview shows the expected container dimensions and background colour.
@Preview(showBackground = true)
@Composable
private fun TrailerPlayerSectionPreview() {
    MoviesAppTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "YouTube Player (rendered on device)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
