package com.tcohen.moviesapp.data.mapper

import com.tcohen.moviesapp.data.remote.dto.GenreResponse
import com.tcohen.moviesapp.domain.model.Category
import com.tcohen.moviesapp.domain.model.Genre
import com.tcohen.moviesapp.fakeMovieDetailDto
import com.tcohen.moviesapp.fakeMovieDto
import com.tcohen.moviesapp.fakeMovieEntity
import com.tcohen.moviesapp.fakeMovie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MovieMapperTest {

    @Test
    fun `MovieDto toDomain maps all fields correctly`() {
        val dto = fakeMovieDto(id = 5)
        val domain = dto.toDomain()

        assertEquals(5, domain.id)
        assertEquals("Test Movie 5", domain.title)
        assertEquals("Overview 5", domain.overview)
        assertEquals("/poster5.jpg", domain.posterPath)
        assertEquals("/backdrop5.jpg", domain.backdropPath)
        assertEquals("2024-01-01", domain.releaseDate)
        assertEquals(7.5, domain.voteAverage, 0.001)
        assertEquals(1000, domain.voteCount)
    }

    @Test
    fun `MovieDto toDomain handles null posterPath`() {
        val dto = fakeMovieDto().copy(posterPath = null)
        val domain = dto.toDomain()
        assertNull(domain.posterPath)
    }

    @Test
    fun `MovieDto toDomain handles null backdropPath`() {
        val dto = fakeMovieDto().copy(backdropPath = null)
        val domain = dto.toDomain()
        assertNull(domain.backdropPath)
    }

    @Test
    fun `MovieDetailDto toDomain maps all fields correctly`() {
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
    fun `MovieDetailDto toDomain handles empty genres`() {
        val dto = fakeMovieDetailDto().copy(genres = emptyList())
        val domain = dto.toDomain()
        assertEquals(emptyList<Genre>(), domain.genres)
    }

    @Test
    fun `MovieDetailDto toDomain handles null runtime`() {
        val dto = fakeMovieDetailDto().copy(runtime = null)
        val domain = dto.toDomain()
        assertNull(domain.runtime)
    }

    @Test
    fun `MovieDetailDto toDomain maps multiple genres`() {
        val dto = fakeMovieDetailDto().copy(
            genres = listOf(GenreResponse(28, "Action"), GenreResponse(12, "Adventure"))
        )
        val domain = dto.toDomain()
        assertEquals(2, domain.genres.size)
        assertEquals("Action", domain.genres[0].name)
        assertEquals("Adventure", domain.genres[1].name)
    }

    @Test
    fun `GenreDto toDomain maps id and name`() {
        val dto = GenreResponse(id = 99, name = "Sci-Fi")
        val domain = dto.toDomain()
        assertEquals(99, domain.id)
        assertEquals("Sci-Fi", domain.name)
    }

    @Test
    fun `MovieEntity toDomain maps all fields correctly`() {
        val entity = fakeMovieEntity(id = 7)
        val domain = entity.toDomain()

        assertEquals(7, domain.id)
        assertEquals("Test Movie 7", domain.title)
        assertEquals(7.5, domain.voteAverage, 0.001)
    }

    @Test
    fun `Movie toEntity maps category and page correctly`() {
        val movie = fakeMovie(id = 2)
        val entity = movie.toEntity(Category.TOP_RATED, page = 3)

        assertEquals(2, entity.id)
        assertEquals(Category.TOP_RATED.name, entity.category)
        assertEquals(3, entity.page)
    }
}
