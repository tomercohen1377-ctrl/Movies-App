package com.tcohen.moviesapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CategoryExtTest {

    @Test
    fun `NOW_PLAYING displayName is 'Now Playing'`() {
        assertEquals("Now Playing", Category.NOW_PLAYING.displayName)
    }

    @Test
    fun `TOP_RATED displayName is 'Top Rated'`() {
        assertEquals("Top Rated", Category.TOP_RATED.displayName)
    }

    @Test
    fun `UPCOMING displayName is 'Upcoming'`() {
        assertEquals("Upcoming", Category.UPCOMING.displayName)
    }

    @Test
    fun `all categories have unique displayNames`() {
        val names = Category.entries.map { it.displayName }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `displayNames are non-empty`() {
        Category.entries.forEach { category ->
            assert(category.displayName.isNotBlank()) {
                "$category has blank displayName"
            }
        }
    }

    @Test
    fun `NOW_PLAYING is first in entries`() {
        assertEquals(Category.NOW_PLAYING, Category.entries.first())
    }

    @Test
    fun `UPCOMING is last in entries`() {
        assertEquals(Category.UPCOMING, Category.entries.last())
    }

    @Test
    fun `entries count is 3`() {
        assertEquals(3, Category.entries.size)
    }

    @Test
    fun `TOP_RATED displayName contains a space`() {
        assert(Category.TOP_RATED.displayName.contains(" "))
    }

    @Test
    fun `NOW_PLAYING displayName differs from TOP_RATED`() {
        assertNotEquals(
            Category.NOW_PLAYING.displayName,
            Category.TOP_RATED.displayName
        )
    }
}
