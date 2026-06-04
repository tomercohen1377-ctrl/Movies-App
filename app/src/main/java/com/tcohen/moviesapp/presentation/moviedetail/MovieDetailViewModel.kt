package com.tcohen.moviesapp.presentation.moviedetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tcohen.moviesapp.data.mapper.toMovie
import com.tcohen.moviesapp.domain.repository.MovieRepository
import com.tcohen.moviesapp.presentation.navigation.Screen
import com.tcohen.moviesapp.util.NetworkUnavailableException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val repository: MovieRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val movieId: Int = checkNotNull(savedStateHandle[Screen.MovieDetail.ARG_MOVIE_ID])

    // ── State ─────────────────────────────────────────────────────────────────

    private val _state = MutableStateFlow(MovieDetailState())
    val state: StateFlow<MovieDetailState> = _state.asStateFlow()

    // ── Effects ───────────────────────────────────────────────────────────────

    private val _effects = Channel<MovieDetailEffect>(Channel.BUFFERED)
    val effects: Flow<MovieDetailEffect> = _effects.receiveAsFlow()

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        loadDetail()
        observeIsFavorite()
    }

    // ── Intent handler ────────────────────────────────────────────────────────

    fun processIntent(intent: MovieDetailIntent) {
        when (intent) {
            MovieDetailIntent.ToggleFavorite -> toggleFavorite()
            MovieDetailIntent.NavigateBack -> navigateBack()
            MovieDetailIntent.Reload -> loadDetail()
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun loadDetail() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val detail = repository.getMovieDetail(movieId)
                val trailer = repository.getTrailer(movieId)
                _state.update {
                    it.copy(
                        movie = detail,
                        trailerKey = trailer?.key,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.toUserMessage()
                    )
                }
            }
        }
    }

    private fun observeIsFavorite() {
        viewModelScope.launch {
            repository.isFavorite(movieId).collect { isFav ->
                _state.update { it.copy(isFavorite = isFav) }
            }
        }
    }

    private fun toggleFavorite() {
        val movie = _state.value.movie?.toMovie() ?: return
        viewModelScope.launch {
            repository.toggleFavorite(movie)
        }
    }

    private fun navigateBack() {
        viewModelScope.launch {
            _effects.send(MovieDetailEffect.NavigateBack)
        }
    }
}

/**
 * Maps a network or generic [Exception] to a short, user-friendly error string.
 *
 * Raw exception messages (e.g. "Unable to resolve host 'api.themoviedb.org'…") are
 * replaced with plain language that makes sense to end-users.
 */
private fun Exception.toUserMessage(): String = when (this) {
    is NetworkUnavailableException -> "No internet connection"
    is UnknownHostException        -> "No internet connection"
    is SocketTimeoutException      -> "Connection timed out — check your internet and retry"
    is IOException                 -> "No internet connection"
    else                           -> "Failed to load movie details"
}
