package com.tcohen.moviesapp.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TmdbImageUrlTest {

    // ── poster (w342) ─────────────────────────────────────────────────────────

    @Test
    fun poster_returns_null_for_null_path() {
        assertNull(TmdbImageUrl.poster(null))
    }

    @Test
    fun poster_builds_correct_w342_url() {
        assertEquals(
            "https://image.tmdb.org/t/p/w342/abc.jpg",
            TmdbImageUrl.poster("/abc.jpg")
        )
    }

    @Test
    fun poster_includes_leading_slash_from_path() {
        val url = TmdbImageUrl.poster("/poster123.jpg")
        assertEquals("https://image.tmdb.org/t/p/w342/poster123.jpg", url)
    }

    // ── posterLarge (w500) ────────────────────────────────────────────────────

    @Test
    fun posterLarge_returns_null_for_null_path() {
        assertNull(TmdbImageUrl.posterLarge(null))
    }

    @Test
    fun posterLarge_builds_correct_w500_url() {
        assertEquals(
            "https://image.tmdb.org/t/p/w500/abc.jpg",
            TmdbImageUrl.posterLarge("/abc.jpg")
        )
    }

    // ── backdrop (w780) ───────────────────────────────────────────────────────

    @Test
    fun backdrop_returns_null_for_null_path() {
        assertNull(TmdbImageUrl.backdrop(null))
    }

    @Test
    fun backdrop_builds_correct_w780_url() {
        assertEquals(
            "https://image.tmdb.org/t/p/w780/backdrop.jpg",
            TmdbImageUrl.backdrop("/backdrop.jpg")
        )
    }

    @Test
    fun backdrop_is_wider_than_poster() {
        val backdropUrl = TmdbImageUrl.backdrop("/img.jpg") ?: ""
        val posterUrl = TmdbImageUrl.poster("/img.jpg") ?: ""
        // w780 vs w342 — backdrop URL should contain the wider size
        assertTrue(backdropUrl.contains("w780"))
        assertTrue(posterUrl.contains("w342"))
    }

    // ── consistent base URL ───────────────────────────────────────────────────

    @Test
    fun all_sizes_share_the_same_base_domain() {
        val base = "https://image.tmdb.org/t/p/"
        assertTrue(TmdbImageUrl.poster("/x.jpg")!!.startsWith(base))
        assertTrue(TmdbImageUrl.posterLarge("/x.jpg")!!.startsWith(base))
        assertTrue(TmdbImageUrl.backdrop("/x.jpg")!!.startsWith(base))
    }
}
