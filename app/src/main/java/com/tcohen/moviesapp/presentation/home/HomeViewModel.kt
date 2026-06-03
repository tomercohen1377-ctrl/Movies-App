package com.tcohen.moviesapp.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.tcohen.moviesapp.domain.model.Category
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.domain.repository.MovieRepository
import com.tcohen.moviesapp.util.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MovieRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    // ── State ─────────────────────────────────────────────────────────────────

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    // ── Effects ───────────────────────────────────────────────────────────────

    private val _effects = Channel<HomeEffect>(Channel.BUFFERED)
    val effects: Flow<HomeEffect> = _effects.receiveAsFlow()

    // ── Paging ────────────────────────────────────────────────────────────────

    /**
     * Drives the active category for paging. Swapping the value triggers
     * [flatMapLatest] to cancel the current paging flow and start a fresh one.
     */
    private val _category = MutableStateFlow(Category.NOW_PLAYING)

    @OptIn(ExperimentalCoroutinesApi::class)
    val moviesFlow: Flow<PagingData<Movie>> = _category
        .flatMapLatest { category -> repository.getMovies(category) }
        .cachedIn(viewModelScope)

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        observeNetworkStatus()
    }

    // ── Intent handler ────────────────────────────────────────────────────────

    fun processIntent(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.SelectCategory -> selectCategory(intent.category)
            is HomeIntent.OpenDetail -> openDetail(intent.movieId)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun selectCategory(category: Category) {
        _category.value = category
        _state.update { it.copy(selectedCategory = category) }
    }

    private fun openDetail(movieId: Int) {
        viewModelScope.launch {
            _effects.send(HomeEffect.NavigateToDetail(movieId))
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
