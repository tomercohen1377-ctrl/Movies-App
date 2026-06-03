package com.tcohen.moviesapp.presentation.common

import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Shows an embedded YouTube trailer at the top of the Movie Detail screen (16:9).
 *
 * - When [trailerKey] is available → embeds the trailer via a [WebView] YouTube iframe.
 * - When [trailerKey] is null (offline or no trailer) → shows the backdrop image
 *   with a faded play icon to indicate a trailer would appear here when online.
 *
 * The [WebView] is paused when the composable leaves the composition to stop audio/video.
 */
@Composable
fun TrailerPlayerSection(
    trailerKey: String?,
    backdropUrl: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black)
    ) {
        if (trailerKey != null) {
            EmbeddedYouTubePlayer(videoKey = trailerKey)
        } else {
            // Fallback — backdrop image with a decorative play icon
            MoviePosterImage(
                imageUrl = backdropUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayCircle,
                    contentDescription = "Trailer unavailable offline",
                    tint = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(64.dp)
                )
            }
        }
    }
}

@Composable
private fun EmbeddedYouTubePlayer(videoKey: String) {
    val context = LocalContext.current
    val embedUrl = remember(videoKey) {
        "https://www.youtube.com/embed/$videoKey?autoplay=1&rel=0&showinfo=0"
    }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            @Suppress("DEPRECATION")
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
        }
    }

    DisposableEffect(videoKey) {
        webView.loadUrl(embedUrl)
        onDispose {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.onPause()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = Modifier.fillMaxSize()
    )
}
