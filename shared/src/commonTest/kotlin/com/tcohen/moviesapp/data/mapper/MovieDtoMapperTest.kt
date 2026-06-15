package com.tcohen.moviesapp.data.mapper

import com.tcohen.moviesapp.data.remote.dto.GenreResponse
import com.tcohen.moviesapp.domain.model.Genre
import com.tcohen.moviesapp.fakeMovie
import com.tcohen.moviesapp.fakeMovieDetail
import com.tcohen.moviesapp.fakeMovieDetailDto
import com.tcohen.moviesapp.fakeMovieDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MovieDtoMapperTest {

    // ── MovieResponse → Movie ─────────────────────────────────────────────────

    @Test
    fun MovieDto_toDomain_maps_all_fields_correctly() {
        val dto = fakeMovieDto(id = 5)
        val domain = dto.toDomain()

        assertEquals(5, domain.id)
        assertEquals("Test Movie 5", domain.title)
        assertEquals("Overview 5", domain.overview)
        assertEquals("/poster5.jpg", domain.posterPath)
        assertEquals("/backdrop5.jpg", domain.backdropPath)
        assertEquals("2024-01-01", domain.releaseDate)
        assertEquals(7.5, domain.voteAverage)
        assertEquals(1000, domain.voteCount)
    }

    @Test
    fun MovieDto_toDomain_handles_null_posterPath() {
        val dto = fakeMovieDto().copy(posterPath = null)
        assertNull(dto.toDomain().posterPath)
    }

    @Test
    fun MovieDto_toDomain_handles_null_backdropPath() {
        val dto = fakeMovieDto().copy(backdropPath = null)
        assertNull(dto.toDomain().backdropPath)
    }

    // ── MovieDetailsResponse → MovieDetail ────────────────────────────────────

    @Test
    fun MovieDetailDto_toDomain_maps_all_fields_correctly() {
        val dto = fakeMovieDetailDto(id = 3)
        val domain = dto.toDomain()

        assertEquals(3, domain.id)
        assertEquals("Test Movie 3", domain.title)
        assertEquals(120, domain.runtime)
        assertEquals("A tagline", domain.tagline)
        assertEquals(1, domain.genres.size)
        assertEquals("Action", domain.genres[0].name)
        assertEquals(28, domain.genres[0].id)
    }

    @Test
    fun MovieDetailDto_toDomain_handles_empty_genres() {
        val dto = fakeMovieDetailDto().copy(genres = emptyList())
        assertEquals(emptyList<Genre>(), dto.toDomain().genres)
    }

    @Test
    fun MovieDetailDto_toDomain_handles_null_runtime() {
        assertNull(fakeMovieDetailDto().copy(runtime = null).toDomain().runtime)
    }

    @Test
    fun MovieDetailDto_toDomain_maps_multiple_genres() {
        val dto = fakeMovieDetailDto().copy(
            genres = listOf(GenreResponse(28, "Action"), GenreResponse(12, "Adventure"))
        )
        val domain = dto.toDomain()
        assertEquals(2, domain.genres.size)
        assertEquals("Action", domain.genres[0].name)
        assertEquals("Adventure", domain.genres[1].name)
    }

    // ── GenreResponse → Genre ─────────────────────────────────────────────────

    @Test
    fun GenreDto_toDomain_maps_id_and_name() {
        val dto = GenreResponse(id = 99, name = "Sci-Fi")
        assertEquals(99, dto.toDomain().id)
        assertEquals("Sci-Fi", dto.toDomain().name)
    }

    // ── MovieDetail → Movie ───────────────────────────────────────────────────

    @Test
    fun MovieDetail_toMovie_preserves_all_base_fields() {
        val detail = fakeMovieDetail(id = 11)
        val movie = detail.toMovie()

        assertEquals(11, movie.id)
        assertEquals("Test Movie Detail 11", movie.title)
        assertEquals("/poster11.jpg", movie.posterPath)
        assertEquals(7.5, movie.voteAverage)
    }
}
