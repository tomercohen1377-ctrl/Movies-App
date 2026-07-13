package com.tcohen.moviesapp.data.local

import com.tcohen.moviesapp.data.remote.paging.SearchDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SearchHistoryRepositoryTest {

    private lateinit var repo: SearchHistoryRepository

    @Before
    fun setUp() {
        repo = SearchHistoryRepository()
    }

    // ── Initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial history is empty`() {
        assertTrue(repo.history.value.isEmpty())
    }

    // ── Adding entries ────────────────────────────────────────────────────────

    @Test
    fun `add puts a fresh entry at the top`() {
        repo.add("dune")
        assertEquals(listOf("dune"), repo.history.value)
    }

    @Test
    fun `add of subsequent terms prepends in reverse order (MRU)`() {
        repo.add("dune")
        repo.add("tenet")
        repo.add("inception")

        assertEquals(listOf("inception", "tenet", "dune"), repo.history.value)
    }

    @Test
    fun `add of an existing term promotes it to front without duplicates`() {
        repo.add("dune")
        repo.add("tenet")
        repo.add("dune") // re-add: should jump to top, no duplicate slot

        assertEquals(listOf("dune", "tenet"), repo.history.value)
    }

    @Test
    fun `add of an existing term is case-insensitive dedup`() {
        repo.add("Dune")
        repo.add("dune")

        assertEquals(listOf("dune"), repo.history.value)
    }

    @Test
    fun `add trims surrounding whitespace`() {
        repo.add("   dune   ")
        assertEquals(listOf("dune"), repo.history.value)
    }

    @Test
    fun `add ignores blank inputs`() {
        repo.add("")
        repo.add("   ")
        assertTrue(repo.history.value.isEmpty())
    }

    // ── Capacity ───────────────────────────────────────────────────────────────

    @Test
    fun `history is FIFO-capped at HISTORY_LIMIT`() {
        val limit = SearchDefaults.HISTORY_LIMIT
        repeat(limit + 3) { idx ->
            repo.add("q$idx")
        }
        assertEquals(limit, repo.history.value.size)
        // Most recent entries survive; the plus-3 oldest are evicted.
        assertEquals("q${limit + 2}", repo.history.value.first())
        assertEquals("q${3}", repo.history.value.last())
    }

    // ── Clear ──────────────────────────────────────────────────────────────────

    @Test
    fun `clear empties the history`() {
        repo.add("a")
        repo.add("b")
        repo.clear()
        assertTrue(repo.history.value.isEmpty())
    }
}
