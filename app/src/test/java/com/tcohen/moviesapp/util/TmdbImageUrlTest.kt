package com.tcohen.moviesapp.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbImageUrlTest {

    @Test
    fun `poster returns null for null path`() {
        assertNull(TmdbImageUrl.poster(null))
    }

    @Test
    fun `poster builds correct w342 URL`() {
        assertEquals(
            "https://image.tmdb.org/t/p/w342/abc.jpg",
            TmdbImageUrl.poster("/abc.jpg")
        )
    }

    @Test
    fun `poster includes leading slash from path`() {
        val url = TmdbImageUrl.poster("/poster123.jpg")
        assertEquals("https://image.tmdb.org/t/p/w342/poster123.jpg", url)
    }

    @Test
    fun `posterLarge returns null for null path`() {
        assertNull(TmdbImageUrl.posterLarge(null))
    }

    @Test
    fun `posterLarge builds correct w500 URL`() {
        assertEquals(
            "https://image.tmdb.org/t/p/w500/abc.jpg",
            TmdbImageUrl.posterLarge("/abc.jpg")
        )
    }

    @Test
    fun `backdrop returns null for null path`() {
        assertNull(TmdbImageUrl.backdrop(null))
    }

    @Test
    fun `backdrop builds correct w780 URL`() {
        assertEquals(
            "https://image.tmdb.org/t/p/w780/backdrop.jpg",
            TmdbImageUrl.backdrop("/backdrop.jpg")
        )
    }

    @Test
    fun `backdrop is wider than poster`() {
        val backdropUrl = TmdbImageUrl.backdrop("/img.jpg") ?: ""
        val posterUrl = TmdbImageUrl.poster("/img.jpg") ?: ""

        assert(backdropUrl.contains("w780"))
        assert(posterUrl.contains("w342"))
    }

    @Test
    fun `all sizes share the same base domain`() {
        val base = "https://image.tmdb.org/t/p/"
        assert(TmdbImageUrl.poster("/x.jpg")!!.startsWith(base))
        assert(TmdbImageUrl.posterLarge("/x.jpg")!!.startsWith(base))
        assert(TmdbImageUrl.backdrop("/x.jpg")!!.startsWith(base))
    }
}
