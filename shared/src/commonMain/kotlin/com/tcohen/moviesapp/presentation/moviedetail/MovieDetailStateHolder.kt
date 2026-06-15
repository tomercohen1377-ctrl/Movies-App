package com.tcohen.moviesapp.presentation.moviedetail

import com.tcohen.moviesapp.data.mapper.toMovie
import com.tcohen.moviesapp.domain.repository.MovieRepositoryBase
import com.tcohen.moviesapp.util.NetworkResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Platform-agnostic state holder for the Movie Detail screen.
 *
 * Encapsulates all business logic: parallel detail + trailer fetching,
 * error handling, favorite observation, and toggle-favorite.
 * The Android ViewModel is a thin wrapper that supplies [movieId] (from
 * `SavedStateHandle`) and [scope] (`viewModelScope`).
 *
 * @param movieId    the movie to display; null triggers an immediate error state
 *                   (e.g. missing navigation argument).
 * @param repository [MovieRepositoryBase] for remote and local operations.
 * @param scope      the coroutine scope for all async work.
 */
class MovieDetailStateHolder(
    private val movieId: Int?,
    private val repository: MovieRepositoryBase,
    private val scope: CoroutineScope
) {

    // ── State ─────────────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow<MovieDetailUiState>(MovieDetailUiState.Loading)
    val uiState: StateFlow<MovieDetailUiState> = _uiState.asStateFlow()

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        if (movieId == null) {
            _uiState.value = MovieDetailUiState.Error(
                "Movie ID is missing — please go back and try again."
            )
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
        scope.launch {
            _uiState.value = MovieDetailUiState.Loading

            // Fetch detail and trailer in parallel — neither depends on the other.
            val detailDeferred = async { repository.getMovieDetail(id) }
            val trailerDeferred = async { repository.getTrailer(id) }

            val detailResult = detailDeferred.await()
            val trailerResult = trailerDeferred.await()

            _uiState.value = when (detailResult) {
                is NetworkResult.Success -> {
                    // Trailer failure is graceful degradation — movie still shows without it.
                    val trailerKey = (trailerResult as? NetworkResult.Success)?.data?.key
                    val isFavorite = repository.isFavorite(id)
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
        scope.launch {
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
        scope.launch {
            repository.toggleFavorite(success.movie.toMovie())
        }
    }
}
