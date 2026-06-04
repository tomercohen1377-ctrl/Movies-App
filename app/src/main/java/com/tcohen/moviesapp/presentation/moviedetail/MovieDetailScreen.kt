package com.tcohen.moviesapp.presentation.moviedetail

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tcohen.moviesapp.domain.model.MovieDetail
import com.tcohen.moviesapp.presentation.common.ErrorView
import com.tcohen.moviesapp.presentation.common.MoviePosterImage
import com.tcohen.moviesapp.presentation.common.RatingBadge
import com.tcohen.moviesapp.presentation.common.TrailerPlayerSection
import com.tcohen.moviesapp.util.TmdbImageUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: MovieDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Tracks whether the YouTube player has fired its onReady callback.
    // Until it does (and we have a trailerKey), we keep the loading overlay visible.
    var playerReady by remember { mutableStateOf(false) }

    // Loading is "done" only once the API response is received AND the player is
    // initialised (or there is no trailer to wait for).
    val isFullyLoading = state.isLoading ||
        (state.movie != null && state.trailerKey != null && !playerReady)

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                MovieDetailEffect.NavigateBack -> onNavigateBack()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Render content as soon as movie data exists so the player can load in the
        // background while the loading overlay is still shown on top.
        if (state.movie != null) {
            MovieDetailContent(
                state = state,
                onPlayerReady = { playerReady = true },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Loading overlay — opaque, covers the content until everything is ready.
        if (isFullyLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        // Error state
        if (state.error != null) {
            ErrorView(
                message = state.error!!,
                onRetry = { viewModel.processIntent(MovieDetailIntent.Reload) }
            )
        }

        // Back button — overlaid on top-left, always visible
        IconButton(
            onClick = { viewModel.processIntent(MovieDetailIntent.NavigateBack) },
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }

        // Favorite FAB — bottom-right, only when detail is loaded
        if (state.movie != null) {
            val favColor by animateColorAsState(
                targetValue = if (state.isFavorite) MaterialTheme.colorScheme.primary
                              else MaterialTheme.colorScheme.surface,
                animationSpec = tween(durationMillis = 300),
                label = "fav_color"
            )
            FloatingActionButton(
                onClick = { viewModel.processIntent(MovieDetailIntent.ToggleFavorite) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(16.dp),
                containerColor = favColor
            ) {
                Icon(
                    imageVector = if (state.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (state.isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (state.isFavorite) Color.White else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MovieDetailContent(
    state: MovieDetailState,
    onPlayerReady: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val movie = state.movie ?: return

    Column(
        modifier = modifier.verticalScroll(rememberScrollState())
    ) {
        // ── Trailer player OR backdrop image at the very top ─────────────────
        if (state.trailerKey != null) {
            TrailerPlayerSection(
                trailerKey = state.trailerKey,
                onPlayerReady = onPlayerReady
            )
        } else {
            AsyncImage(
                model = TmdbImageUrl.backdrop(movie.backdropPath)
                    ?: TmdbImageUrl.posterLarge(movie.posterPath),
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            )
        }

        // ── Movie metadata ─────────────────────────────────────────────────
        Column(modifier = Modifier.padding(16.dp)) {

            // Poster thumbnail + title block
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(shape = RoundedCornerShape(8.dp)) {
                    MoviePosterImage(
                        imageUrl = TmdbImageUrl.posterLarge(movie.posterPath),
                        contentDescription = movie.title,
                        modifier = Modifier
                            .width(100.dp)
                            .aspectRatio(2f / 3f)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = movie.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (movie.releaseDate.length >= 4) {
                        Text(
                            text = movie.releaseDate.take(4),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    movie.runtime?.let { runtime ->
                        Text(
                            text = "${runtime}m",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    RatingBadge(rating = movie.voteAverage)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Genre chips
            if (movie.genres.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    movie.genres.forEach { genre ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(genre.name) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Tagline
            if (!movie.tagline.isNullOrBlank()) {
                Text(
                    text = "\"${movie.tagline}\"",
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Overview
            Text(
                text = movie.overview,
                style = MaterialTheme.typography.bodyMedium
            )

            // Bottom spacer so the FAB doesn't cover the last line
            Spacer(modifier = Modifier.height(88.dp))
        }
    }
}
