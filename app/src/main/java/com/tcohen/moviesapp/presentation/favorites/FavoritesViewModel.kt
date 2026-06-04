package com.tcohen.moviesapp.presentation.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.domain.repository.MovieRepository
import com.tcohen.moviesapp.util.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val repository: MovieRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    // ── State ─────────────────────────────────────────────────────────────────

    private val _state = MutableStateFlow(FavoritesState())
    val state: StateFlow<FavoritesState> = _state.asStateFlow()

    // ── Effects ───────────────────────────────────────────────────────────────

    private val _effects = Channel<FavoritesEffect>(Channel.BUFFERED)
    val effects: Flow<FavoritesEffect> = _effects.receiveAsFlow()

    // ── Paging ────────────────────────────────────────────────────────────────

    /**
     * Paginated favorites stream.
     *
     * Restarts automatically whenever `repository.favoriteChanges` emits — i.e., any time
     * a favorite is added or removed from **any** screen (detail FAB, favorites swipe, etc.).
     * `onStart { emit(Unit) }` ensures the first load fires immediately on collection.
     */
    @Suppress("OPT_IN_USAGE")
    val favoritesFlow: Flow<PagingData<Movie>> = repository.favoriteChanges
        .onStart { emit(Unit) }
        .flatMapLatest { repository.getFavorites() }
        .cachedIn(viewModelScope)

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        observeNetworkStatus()
    }

    // ── Intent handler ────────────────────────────────────────────────────────

    fun processIntent(intent: FavoritesIntent) {
        when (intent) {
            is FavoritesIntent.OpenDetail -> openDetail(intent.movieId)
            is FavoritesIntent.RemoveFavorite -> removeFavorite(intent.movie)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun openDetail(movieId: Int) {
        viewModelScope.launch {
            _effects.send(FavoritesEffect.NavigateToDetail(movieId))
        }
    }

    private fun removeFavorite(movie: Movie) {
        viewModelScope.launch {
            repository.toggleFavorite(movie)
        }
    }

    private fun observeNetworkStatus() {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { isOnline ->
                _state.update { it.copy(isOffline = !isOnline) }
            }
        }
    }
}
