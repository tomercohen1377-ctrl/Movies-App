package com.tcohen.moviesapp.presentation.favorites

import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.domain.repository.MovieRepositoryBase
import com.tcohen.moviesapp.util.NetworkStatusProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Platform-agnostic state holder for the Favorites screen.
 *
 * Handles navigation effects, offline state, and favorite-removal logic.
 * Paginated favorites ([androidx.paging.PagingData]) remain in the Android
 * ViewModel layer since they depend on `PagingData.cachedIn`.
 *
 * @param repository    [MovieRepositoryBase] for toggling favorites.
 * @param networkStatus provides connectivity status across platforms.
 * @param scope         the coroutine scope for the holder's internal coroutines.
 */
class FavoritesStateHolder(
    private val repository: MovieRepositoryBase,
    private val networkStatus: NetworkStatusProvider,
    private val scope: CoroutineScope
) {

    // ── State ─────────────────���───────────────────────────────────────────────

    private val _state = MutableStateFlow(FavoritesState())
    val state: StateFlow<FavoritesState> = _state.asStateFlow()

    // ── Effects ───────────────────────────────────────────────────────────────

    private val _effects = Channel<FavoritesEffect>(Channel.BUFFERED)
    val effects: Flow<FavoritesEffect> = _effects.receiveAsFlow()

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
        scope.launch {
            _effects.send(FavoritesEffect.NavigateToDetail(movieId))
        }
    }

    private fun removeFavorite(movie: Movie) {
        scope.launch {
            repository.toggleFavorite(movie)
        }
    }

    private fun observeNetworkStatus() {
        scope.launch {
            networkStatus.isOnline.collect { isOnline ->
                _state.update { it.copy(isOffline = !isOnline) }
            }
        }
    }
}
