package com.tcohen.moviesapp.presentation.moviedetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tcohen.moviesapp.data.mapper.toMovie
import com.tcohen.moviesapp.domain.repository.MovieRepository
import com.tcohen.moviesapp.presentation.navigation.Screen
import com.tcohen.moviesapp.util.NetworkResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val repository: MovieRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /**
     * `null` when the navigation argument is missing (should never happen in normal flow,
     * but we handle it gracefully instead of crashing via `checkNotNull`).
     */
    private val movieId: Int? = savedStateHandle[Screen.MovieDetail.ARG_MOVIE_ID]

    // ── State ─────────────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow<MovieDetailUiState>(MovieDetailUiState.Loading)
    val uiState: StateFlow<MovieDetailUiState> = _uiState.asStateFlow()

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        if (movieId == null) {
            _uiState.value = MovieDetailUiState.Error("Movie ID is missing — please go back and try again.")
        } else {
            loadDetail()
            observeIsFavorite()
        }
    }

    // ── Intent handler ────────────────────────────────────────────────────────

    fun processIntent(intent: MovieDetailIntent) {
        when (intent) {
            MovieDetailIntent.ToggleFavorite -> toggleFavorite()
            MovieDetailIntent.Reload -> loadDetail()
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun loadDetail() {
        val id = movieId ?: return
        viewModelScope.launch {
            _uiState.value = MovieDetailUiState.Loading

            // Fetch movie detail and trailer in parallel — neither call depends on the other.
            val detailDeferred = async { repository.getMovieDetail(id) }
            val trailerDeferred = async { repository.getTrailer(id) }

            val detailResult = detailDeferred.await()
            val trailerResult = trailerDeferred.await()

            _uiState.value = when (detailResult) {
                is NetworkResult.Success -> {
                    // Trailer failure is graceful degradation — movie still shows without it.
                    val trailerKey = (trailerResult as? NetworkResult.Success)?.data?.key
                    val isFavorite  = repository.isFavorite(movieId)
                    MovieDetailUiState.Success(
                        movie = detailResult.data,
                        trailerKey = trailerKey,
                        isFavorite = isFavorite
                    )
                }
                is NetworkResult.Error -> MovieDetailUiState.Error(detailResult.message)
            }
        }
    }

    private fun observeIsFavorite() {
        val id = movieId ?: return
        viewModelScope.launch {
            repository.observeIsFavorite(id).collect { isFav ->
                val current = _uiState.value
                if (current is MovieDetailUiState.Success) {
                    _uiState.value = current.copy(isFavorite = isFav)
                }
            }
        }
    }

    private fun toggleFavorite() {
        val success = _uiState.value as? MovieDetailUiState.Success ?: return
        viewModelScope.launch {
            repository.toggleFavorite(success.movie.toMovie())
        }
    }
}
