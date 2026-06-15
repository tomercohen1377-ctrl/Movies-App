package com.tcohen.moviesapp.presentation.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.domain.repository.MovieRepository
import com.tcohen.moviesapp.util.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

/**
 * Android ViewModel for the Favorites screen.
 *
 * Thin wrapper around [FavoritesStateHolder] — delegates state/effect/toggle logic
 * to the shared holder, then builds the Android-only paginated favorites flow on top.
 */
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val repository: MovieRepository,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    private val stateHolder = FavoritesStateHolder(repository, networkMonitor, viewModelScope)

    /** Observed UI state — forwarded from the shared state holder. */
    val state: StateFlow<FavoritesState> = stateHolder.state

    /** One-shot navigation effects — forwarded from the shared state holder. */
    val effects: Flow<FavoritesEffect> = stateHolder.effects

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

    fun processIntent(intent: FavoritesIntent) = stateHolder.processIntent(intent)
}
