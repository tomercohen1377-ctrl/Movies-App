package com.tcohen.moviesapp.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.paging.PagingData
import app.cash.paging.cachedIn
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.domain.repository.MovieRepository
import com.tcohen.moviesapp.util.NetworkStatusProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * KMP ViewModel for the Home screen.
 *
 * Thin wrapper around [HomeStateHolder] — delegates all state/effect management
 * to the shared holder, then adds the paging flow on top.
 *
 * [ViewModel] and [viewModelScope] come from the JetBrains lifecycle KMP port
 * (`org.jetbrains.androidx.lifecycle:lifecycle-viewmodel`), which is available in
 * [commonMain] and maps to [androidx.lifecycle.ViewModel] on Android.
 *
 * Constructed and provided by Koin (see AppModule).
 */
class HomeViewModel(
    private val repository: MovieRepository,
    networkMonitor: NetworkStatusProvider
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
