package com.tcohen.moviesapp.presentation.moviedetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tcohen.moviesapp.data.favorites.FavoritesChangeNotifier
import com.tcohen.moviesapp.data.mapper.toMovie
import com.tcohen.moviesapp.domain.repository.MovieRepository
import com.tcohen.moviesapp.presentation.navigation.Screen
import com.tcohen.moviesapp.util.NetworkResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val repository: MovieRepository,
    private val notifier: FavoritesChangeNotifier,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val movieId: Int? = savedStateHandle[Screen.MovieDetail.ARG_MOVIE_ID]

    private val _uiState = MutableStateFlow<MovieDetailUiState>(MovieDetailUiState.Loading)
    val uiState: StateFlow<MovieDetailUiState> = _uiState.asStateFlow()

    private val _effects = Channel<MovieDetailEffect>(Channel.BUFFERED)
    val effects: Flow<MovieDetailEffect> = _effects.receiveAsFlow()

    init {
        if (movieId == null) {
            _uiState.value = MovieDetailUiState.Error("Movie ID is missing — please go back and try again.")
        } else {
            load()
        }
    }

    fun processIntent(intent: MovieDetailIntent) {
        when (intent) {
            MovieDetailIntent.ToggleFavorite -> toggleFavorite()
            MovieDetailIntent.Reload -> load()
        }
    }

    private fun load() {
        val id = movieId ?: return
        viewModelScope.launch {
            _uiState.value = MovieDetailUiState.Loading
            val detail = async { repository.getMovieDetail(id) }
            val trailer = async { repository.getTrailer(id) }
            val favorite = async { repository.isFavorite(id) }
            _uiState.value = when (val d = detail.await()) {
                is NetworkResult.Success -> MovieDetailUiState.Success(
                    movie = d.data,
                    trailerKey = (trailer.await() as? NetworkResult.Success)?.data?.key,
                    isFavorite = (favorite.await() as? NetworkResult.Success)?.data ?: false,
                )
                is NetworkResult.Error -> MovieDetailUiState.Error(d.message)
            }
        }
    }

    private fun toggleFavorite() {
        val current = _uiState.value as? MovieDetailUiState.Success ?: return
        val willBeFavorite = !current.isFavorite
        _uiState.value = current.copy(isFavorite = willBeFavorite)
        viewModelScope.launch {
            val movie = current.movie.toMovie()
            when (val result = if (willBeFavorite) repository.addFavorite(movie)
                else repository.removeFavorite(movie)) {
                is NetworkResult.Success -> notifier.notifyChanged()
                is NetworkResult.Error -> {
                    _uiState.value = current.copy(isFavorite = current.isFavorite)
                    _effects.send(MovieDetailEffect.ShowSnackbar(result.message))
                }
            }
        }
    }
}

sealed interface MovieDetailEffect {

    data class ShowSnackbar(val message: String) : MovieDetailEffect
}
