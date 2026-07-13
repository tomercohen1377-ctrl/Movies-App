package com.tcohen.moviesapp.presentation.moviedetail.morelikethis

import com.tcohen.moviesapp.domain.model.Movie

/**
 * UI state for the **"More like this"** subsection embedded inside the
 * `MovieDetailScreen`. Lives below the AI plot-explainer card.
 *
 * Mirrors the sealed-interface pattern used by `MovieDetailUiState` and
 * `PlotExplainerState`: each branch is exhaustive so the composable's
 * `when (state)` is compiler-checked.
 *
 * - [Idle]    — initial state; section is hidden entirely until the first
 *               [MoreLikeThisIntent.Load] fires (we don't fire on view init; lazy).
 * - [Loading] — request in flight; row of placeholders shown.
 * - [Success] — TMDB returned ≥ 1 similar movie; horizontal carousel of posters.
 * - [Empty]   — TMDB returned 0; section header is omitted (don't show "More like
 *               this: nothing").
 * - [Error]   — non-recoverable failure; a small inline message + Retry button,
 *               the rest of the detail screen continues to work.
 */
sealed interface MoreLikeThisState {
    data object Idle : MoreLikeThisState
    data object Loading : MoreLikeThisState
    data class Success(val movies: List<Movie>) : MoreLikeThisState
    data object Empty : MoreLikeThisState
    data class Error(val message: String) : MoreLikeThisState
}

/**
 * Intents dispatched from `MoreLikeThisSection`.
 *
 * - [Load] is fired once from a `LaunchedEffect(uiState.movie.id)` callback
 *   inside the section, NOT from the surrounding `MovieDetailViewModel` —
 *   mirroring how `PlotExplainerSection` keeps itself decoupled.
 * - [OpenDetail] is fired when the user taps a poster in the carousel.
 */
sealed interface MoreLikeThisIntent {
    data class Load(val movieId: Int) : MoreLikeThisIntent
    data class OpenDetail(val movieId: Int) : MoreLikeThisIntent
}

/**
 * One-shot effects surfaced to the parent `MovieDetailScreen`, which is the only
 * place that owns a `NavController` and therefore the only place that can navigate.
 */
sealed interface MoreLikeThisEffect {
    data class NavigateToDetail(val movieId: Int) : MoreLikeThisEffect
}
