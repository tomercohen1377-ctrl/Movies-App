package com.tcohen.moviesapp.presentation.moviedetail.morelikethis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.presentation.common.SimilarMoviePosterCard
import com.tcohen.moviesapp.presentation.theme.MoviesAppTheme

private const val PLACEHOLDER_COUNT = 5

/**
 * Subsection embedded inside `MovieDetailContent` that renders movies "similar"
 * to the current movie (per TMDB's `/{id}/similar` endpoint).
 *
 * Pattern mirrors `com.tcohen.moviesapp.ai.presentation.ploexplainer.PlotExplainerSection`:
 * this composable owns its own [MoreLikeThisViewModel] (scoped to its own composition)
 * so the parent `MovieDetailViewModel` doesn't pick up yet another dependency.
 *
 * Render by state:
 *  - [MoreLikeThisState.Idle]    → row is invisible (don't show before first load)
 *  - [MoreLikeThisState.Loading] → header + 5 placeholder cards
 *  - [MoreLikeThisState.Success] → header + horizontal carousel of posters
 *  - [MoreLikeThisState.Empty]   → row is invisible (nothing useful to show)
 *  - [MoreLikeThisState.Error]   → header + small inline message + Retry button
 *
 * The parent detail screen forwards [onMovieClicked] up to its own `NavController`;
 * this section does not navigate directly.
 */
@Composable
fun MoreLikeThisSection(
    movieId: Int,
    onMovieClicked: (Int) -> Unit,
    modifier: Modifier = Modifier,
    // `hiltViewModel` (not Compose's generic `viewModel`) — `MoreLikeThisViewModel`
    // is `@HiltViewModel` with an `@Inject` constructor, so the factory that knows
    // how to build it lives in the Hilt graph. The previous `viewModel(...)` call
    // used the generic ViewModel factory which can't construct Hilt VMs → crash.
    viewModel: MoreLikeThisViewModel = hiltViewModel(
        // Keyed on movieId so a fresh view-model is created when the user navigates
        // between different detail screens in the same composition (no stale state).
        key = "MoreLikeThisViewModel-$movieId"
    )
) {
    val state by viewModel.state.collectAsState()

    // Fire the load once when this section inflates for the given movie.
    LaunchedEffect(movieId) {
        viewModel.processIntent(MoreLikeThisIntent.Load(movieId))
    }

    // Bubble navigation up to the parent detail screen: VM emits an effect,
    // we forward it via the supplied callback.
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MoreLikeThisEffect.NavigateToDetail -> onMovieClicked(effect.movieId)
            }
        }
    }

    when (val s = state) {
        MoreLikeThisState.Idle,
        MoreLikeThisState.Empty -> Unit  // hidden until first load returns content

        MoreLikeThisState.Loading -> LoadingRow(modifier)
        is MoreLikeThisState.Success -> Carousel(
            movies = s.movies,
            modifier = modifier,
            onMovieClicked = { id ->
                viewModel.processIntent(MoreLikeThisIntent.OpenDetail(id))
            }
        )
        is MoreLikeThisState.Error -> ErrorRow(
            message = s.message,
            onRetry = { viewModel.processIntent(MoreLikeThisIntent.Load(movieId)) },
            modifier = modifier
        )
    }
}

// ── Sub-renders ───────────────────────────────────────────────────────────────

@Composable
private fun LoadingRow(modifier: Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        SectionHeader()
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(count = PLACEHOLDER_COUNT) {
                PosterPlaceholder()
            }
        }
    }
}

@Composable
private fun Carousel(
    movies: List<Movie>,
    modifier: Modifier,
    onMovieClicked: (Int) -> Unit
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        SectionHeader()
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items = movies, key = { it.id }) { movie ->
                SimilarMoviePosterCard(
                    movie = movie,
                    onClick = { onMovieClicked(movie.id) }
                )
            }
        }
    }
}

@Composable
private fun ErrorRow(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 16.dp)) {
        SectionHeader()
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Couldn't load similar movies — $message",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(end = 8.dp)
            )
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun SectionHeader() {
    Text(
        text = "More like this",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
    )
}

@Composable
private fun PosterPlaceholder() {
    Box(
        modifier = Modifier
            .width(110.dp)
            .height(165.dp) // 110 * 3/2 to keep 2:3 ratio
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )
}

// ── Previews ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun PosterPlaceholderPreview() {
    MoviesAppTheme { PosterPlaceholder() }
}
