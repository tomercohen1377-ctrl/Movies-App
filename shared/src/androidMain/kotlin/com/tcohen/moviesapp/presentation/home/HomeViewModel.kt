package com.tcohen.moviesapp.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.domain.repository.MovieRepository
import com.tcohen.moviesapp.util.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

/**
 * Android ViewModel for the Home screen.
 *
 * Thin wrapper around [HomeStateHolder] — delegates all state/effect management
 * to the shared holder, then adds the Android-only paging flow on top.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MovieRepository,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    private val stateHolder = HomeStateHolder(networkMonitor, viewModelScope)

    /** Observed UI state — forwarded from the shared state holder. */
    val state: StateFlow<HomeState> = stateHolder.state

    /** One-shot navigation effects — forwarded from the shared state holder. */
    val effects: Flow<HomeEffect> = stateHolder.effects

    /** Paginated movie stream that re-starts when the selected category changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val moviesFlow: Flow<PagingData<Movie>> = stateHolder.categoryFlow
        .flatMapLatest { category -> repository.getMovies(category) }
        .cachedIn(viewModelScope)

    fun processIntent(intent: HomeIntent) = stateHolder.processIntent(intent)
}
