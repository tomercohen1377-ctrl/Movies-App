package com.tcohen.moviesapp.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CategoryExtTest {

    // ── displayName ───────────────────────────────────────────────────────────

    @Test
    fun NOW_PLAYING_displayName_is_Now_Playing() {
        assertEquals("Now Playing", Category.NOW_PLAYING.displayName)
    }

    @Test
    fun TOP_RATED_displayName_is_Top_Rated() {
        assertEquals("Top Rated", Category.TOP_RATED.displayName)
    }

    @Test
    fun UPCOMING_displayName_is_Upcoming() {
        assertEquals("Upcoming", Category.UPCOMING.displayName)
    }

    @Test
    fun all_categories_have_unique_displayNames() {
        val names = Category.entries.map { it.displayName }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun displayNames_are_non_empty() {
        Category.entries.forEach { category ->
            assertTrue(category.displayName.isNotBlank(), "$category has blank displayName")
        }
    }

    // ── enum order ────────────────────────────────────────────────────────────

    @Test
    fun NOW_PLAYING_is_first_in_entries() {
        assertEquals(Category.NOW_PLAYING, Category.entries.first())
    }

    @Test
    fun UPCOMING_is_last_in_entries() {
        assertEquals(Category.UPCOMING, Category.entries.last())
    }

    @Test
    fun entries_count_is_3() {
        assertEquals(3, Category.entries.size)
    }

    // ── display name content ──────────────────────────────────────────────────

    @Test
    fun TOP_RATED_displayName_contains_a_space() {
        assertTrue(Category.TOP_RATED.displayName.contains(" "))
    }

    @Test
    fun NOW_PLAYING_displayName_differs_from_TOP_RATED() {
        assertNotEquals(
            Category.NOW_PLAYING.displayName,
            Category.TOP_RATED.displayName
        )
    }
}
