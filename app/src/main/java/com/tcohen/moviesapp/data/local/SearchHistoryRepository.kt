package com.tcohen.moviesapp.data.local

import com.tcohen.moviesapp.data.remote.paging.SearchDefaults
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks the user's recent search queries so the Search screen can offer a
 * type-ahead chooser row of previous terms.
 *
 * **Storage: in-memory ([StateFlow]) for v1.** The history resets on app restart.
 * The interface is intentionally narrow so the storage can be swapped to
 * [androidx.datastore.preferences.core.Preferences] (DataStore) later without
 * touching the Search view-model or screen.
 *
 * Rules:
 * - Capacity is capped at [SearchDefaults.HISTORY_LIMIT]; older entries are FIFO-evicted.
 * - Adding a query that's already in history promotes it to the top (MRU) without
 *   duplicating the slot.
 */
@Singleton
class SearchHistoryRepository @Inject constructor() {

    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    /**
     * Adds [query] to history. No-op for blank queries. Promotes MRU ordering on
     * duplicates so frequent searches stay near the top of the chip row.
     */
    fun add(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        _history.update { existing ->
            // MRU: remove-if-present + prepend.
            val deduped = existing.filterNot { it.equals(trimmed, ignoreCase = true) }
            (listOf(trimmed) + deduped).take(SearchDefaults.HISTORY_LIMIT)
        }
    }

    /** Removes all entries. */
    fun clear() {
        _history.value = emptyList()
    }
}
