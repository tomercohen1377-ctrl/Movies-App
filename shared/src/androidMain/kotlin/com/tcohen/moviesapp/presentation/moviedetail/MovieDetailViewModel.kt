package com.tcohen.moviesapp.presentation.moviedetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tcohen.moviesapp.domain.repository.MovieRepository
import com.tcohen.moviesapp.presentation.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Android ViewModel for the Movie Detail screen.
 *
 * Thin wrapper around [MovieDetailStateHolder] — extracts the movie ID from
 * [SavedStateHandle] and delegates all logic to the shared state holder.
 */
@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    repository: MovieRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /**
     * `null` when the navigation argument is missing (handled gracefully by the
     * state holder instead of crashing via `checkNotNull`).
     */
    private val movieId: Int? = savedStateHandle[Screen.MovieDetail.ARG_MOVIE_ID]

    private val stateHolder = MovieDetailStateHolder(movieId, repository, viewModelScope)

    /** Observed UI state — forwarded from the shared state holder. */
    val uiState: StateFlow<MovieDetailUiState> = stateHolder.uiState

    fun processIntent(intent: MovieDetailIntent) = stateHolder.processIntent(intent)
}
