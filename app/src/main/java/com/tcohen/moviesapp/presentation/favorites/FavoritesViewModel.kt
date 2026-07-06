package com.tcohen.moviesapp.presentation.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tcohen.moviesapp.data.favorites.FavoritesChangeNotifier
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.domain.repository.MovieRepository
import com.tcohen.moviesapp.util.NetworkResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val repository: MovieRepository,
    private val notifier: FavoritesChangeNotifier,
) : ViewModel() {

    private val _state = MutableStateFlow<FavoritesState>(FavoritesState.Loading)
    val state: StateFlow<FavoritesState> = _state

    private val _effects = Channel<FavoritesEffect>(Channel.BUFFERED)
    val effects: Flow<FavoritesEffect> = _effects.receiveAsFlow()

    init {
        // Initial fetch — covers first time the screen opens.
        refresh()
        // Refresh whenever any screen reports a server-side change
        // (e.g. user toggled a movie from the detail page).
        viewModelScope.launch {
            notifier.changes.collect { refresh() }
        }
    }

    fun processIntent(intent: FavoritesIntent) {
        when (intent) {
            is FavoritesIntent.RemoveFavorite -> removeFavorite(intent.movie)
            is FavoritesIntent.Refresh -> refresh()
            is FavoritesIntent.OpenDetail -> viewModelScope.launch {
                _effects.send(FavoritesEffect.NavigateToDetail(intent.movieId))
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _state.value = FavoritesState.Loading
            _state.value = when (val result = repository.getFavorites()) {
                is NetworkResult.Success -> if (result.data.isEmpty()) FavoritesState.Empty
                else FavoritesState.Success(result.data)

                is NetworkResult.Error -> FavoritesState.Error(result.httpCode, result.message)
            }
        }
    }

    private fun removeFavorite(movie: Movie) {
        viewModelScope.launch {
            when (val result = repository.removeFavorite(movie)) {
                is NetworkResult.Success -> notifier.notifyChanged()
                is NetworkResult.Error -> _effects.send(
                    FavoritesEffect.ShowSnackbar(result.message)
                )
            }
        }
    }
}
