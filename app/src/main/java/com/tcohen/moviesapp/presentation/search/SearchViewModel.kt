package com.tcohen.moviesapp.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.tcohen.moviesapp.data.local.SearchHistoryRepository
import com.tcohen.moviesapp.data.remote.paging.SearchDefaults
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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the Search screen.
 *
 * **The query stream** is constructed as:
 * ```
 * _query.debounce(300ms).distinctUntilChanged().flatMapLatest { q ->
 *     if (q.length < MIN_QUERY_LENGTH) flowOf(PagingData.empty())
 *     else repository.searchMovies(q)
 * }
 * ```
 *
 * `debounce` collapses typing-driven bursts (so we don't fire a request per keystroke).
 * `distinctUntilChanged` collapses re-fires when the same query is re-set (e.g., user
 * types "ab", "abc", backspaces to "ab" — the third settle doesn't refire).
 * `flatMapLatest` cancels the previous Pager when the query changes.
 *
 * `cachedIn(viewModelScope)` is what lets the user navigate to Detail and back without
 * losing the scroll position and the loaded pages.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: MovieRepository,
    private val historyRepository: SearchHistoryRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    // ── State ─────────────────────────────────────────────────────────────────

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    // ── Effects ───────────────────────────────────────────────────────────────

    private val _effects = Channel<SearchEffect>(Channel.BUFFERED)
    val effects: Flow<SearchEffect> = _effects.receiveAsFlow()

    // ── Paging ────────────────────────────────────────────────────────────────

    private val _query = MutableStateFlow("")

    /**
     * Paging flow that drives the Search screen's grid.
     *
     * Below [SearchDefaults.MIN_QUERY_LENGTH] characters the flow emits an empty
     * [PagingData] so the screen can show the "type to search" prompt instead of
     * firing a meaningless API call.
     */
    val results: Flow<PagingData<Movie>> = _query
        .debounce { if (it.length < SearchDefaults.MIN_QUERY_LENGTH) 0L else SearchDefaults.DEBOUNCE_MS }
        .map { it.trim() }
        .distinctUntilChanged()
        .flatMapLatest { q ->
            _state.update { it.copy(hasSearched = q.length >= SearchDefaults.MIN_QUERY_LENGTH) }
            if (q.length < SearchDefaults.MIN_QUERY_LENGTH) {
                flowOf(PagingData.empty())
            } else {
                repository.searchMovies(q)
            }
        }
        .cachedIn(viewModelScope)

    // ── History ───────────────────────────────────────────────────────────────

    /** Live history for the chip row. Exposed as a [StateFlow] for direct collection. */
    val history: StateFlow<List<String>> = historyRepository.history

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        observeNetworkStatus()
    }

    // ── Intent handler ────────────────────────────────────────────────────────

    fun processIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.UpdateQuery -> updateQuery(intent.query)
            is SearchIntent.ClearQuery -> clearQuery()
            is SearchIntent.SelectHistory -> selectHistory(intent.query)
            is SearchIntent.ClearHistory -> historyRepository.clear()
            is SearchIntent.OpenDetail -> openDetail(intent.movieId)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun updateQuery(query: String) {
        _query.value = query
        _state.update { it.copy(query = query) }
    }

    private fun clearQuery() {
        _query.value = ""
        _state.update { it.copy(query = "", hasSearched = false) }
    }

    private fun selectHistory(query: String) {
        // Re-fires the Pager via the same `_query` channel used for keystroke updates.
        _query.value = query
        _state.update { it.copy(query = query) }
    }

    private fun openDetail(movieId: Int) {
        // User "committed" to this query by tapping a result — record it as history.
        val committedQuery = _state.value.query.trim()
        if (committedQuery.isNotEmpty()) {
            historyRepository.add(committedQuery)
        }

        viewModelScope.launch {
            _effects.send(SearchEffect.NavigateToDetail(movieId))
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
