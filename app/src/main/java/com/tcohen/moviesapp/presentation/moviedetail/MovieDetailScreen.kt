package com.tcohen.moviesapp.presentation.moviedetail

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                MovieDetailEffect.NavigateBack -> onNavigateBack()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            }
            state.error != null -> {
                ErrorView(
                    message = state.error!!,
                    onRetry = { viewModel.processIntent(MovieDetailIntent.Reload) }
                )
            }
            state.movie != null -> {
                MovieDetailContent(
                    state = state,
                    modifier = Modifier.fillMaxSize()
                )
            }
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
    modifier: Modifier = Modifier
) {
    val movie = state.movie ?: return

    Column(
        modifier = modifier.verticalScroll(rememberScrollState())
    ) {
        // ── Trailer player at the very top (16:9) ──────────────────────────
        TrailerPlayerSection(
            trailerKey = state.trailerKey,
            backdropUrl = TmdbImageUrl.backdrop(movie.backdropPath)
        )

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

            // Bottom padding so the FAB doesn't overlap the last line
            Spacer(modifier = Modifier.height(88.dp))
        }
    }
}
