package com.tcohen.moviesapp.presentation.home

import com.tcohen.moviesapp.domain.model.Category
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
 * Platform-agnostic state holder for the Home screen.
 *
 * Manages UI state ([HomeState]), one-shot effects ([HomeEffect]), and the active
 * category flow. Paging ([androidx.paging.PagingData]) is intentionally excluded —
 * the Android ViewModel builds the paging flow using [categoryFlow].
 *
 * @param networkStatus provides connectivity status across platforms.
 * @param scope         the coroutine scope for the holder's internal coroutines
 *                      (use `viewModelScope` on Android, a test scope in tests).
 */
class HomeStateHolder(
    private val networkStatus: NetworkStatusProvider,
    private val scope: CoroutineScope
) {

    // ── State ─────────────────────────────────────────────────────────────────

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    // ── Effects ───────────────────────────────────────────────────────────────

    private val _effects = Channel<HomeEffect>(Channel.BUFFERED)
    val effects: Flow<HomeEffect> = _effects.receiveAsFlow()

    // ── Category (drives paging in Android layer) ────────────────────��────────

    private val _category = MutableStateFlow(Category.NOW_PLAYING)

    /** Exposes the active category so the Android ViewModel can drive paging. */
    val categoryFlow: StateFlow<Category> = _category.asStateFlow()

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
        scope.launch {
            _effects.send(HomeEffect.NavigateToDetail(movieId))
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
