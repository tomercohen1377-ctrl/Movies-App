package com.tcohen.moviesapp.presentation.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tcohen.moviesapp.domain.repository.MovieRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val repository: MovieRepository
) : ViewModel() {

    private val _state = MutableStateFlow(FavoritesState())
    val state: StateFlow<FavoritesState> = _state.asStateFlow()

    private val _effects = Channel<FavoritesEffect>(Channel.BUFFERED)
    val effects: Flow<FavoritesEffect> = _effects.receiveAsFlow()

    init {
        observeFavorites()
    }

    fun processIntent(intent: FavoritesIntent) {
        when (intent) {
            is FavoritesIntent.OpenDetail -> openDetail(intent.movieId)
            is FavoritesIntent.RemoveFavorite -> removeFavorite(intent.movieId)
        }
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            repository.getFavorites().collect { favorites ->
                _state.update { it.copy(favorites = favorites, isEmpty = favorites.isEmpty()) }
            }
        }
    }

    private fun openDetail(movieId: Int) {
        viewModelScope.launch {
            _effects.send(FavoritesEffect.NavigateToDetail(movieId))
        }
    }

    private fun removeFavorite(movieId: Int) {
        val movie = _state.value.favorites.find { it.id == movieId } ?: return
        viewModelScope.launch {
            repository.toggleFavorite(movie)
        }
    }
}
