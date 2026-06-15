package com.tcohen.moviesapp.presentation.moviedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tcohen.moviesapp.domain.repository.MovieRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * KMP ViewModel for the Movie Detail screen.
 *
 * Thin wrapper around [MovieDetailStateHolder]. Receives the movie ID directly
 * (extracted from the navigation back-stack in AppNavGraph and injected via Koin parameters).
 *
 * Constructed and provided by Koin (see AppModule: `viewModel { (movieId: Int?) -> ... }`).
 */
class MovieDetailViewModel(
    repository: MovieRepository,
    movieId: Int?
) : ViewModel() {

    private val stateHolder = MovieDetailStateHolder(movieId, repository, viewModelScope)

    /** Observed UI state — forwarded from the shared state holder. */
    val uiState: StateFlow<MovieDetailUiState> = stateHolder.uiState

    fun processIntent(intent: MovieDetailIntent) = stateHolder.processIntent(intent)
}
