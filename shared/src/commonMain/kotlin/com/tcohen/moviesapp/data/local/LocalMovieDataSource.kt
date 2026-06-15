package com.tcohen.moviesapp.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import com.tcohen.moviesapp.data.local.db.MoviesDatabase
import com.tcohen.moviesapp.domain.model.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Platform-agnostic local data source backed by SQLDelight.
 *
 * Replaces the Android-only Room [MovieDao] and [FavoriteDao].  All public methods
 * return Kotlin domain types ([Movie]) so callers don't need to know about the
 * SQLDelight-generated row types. Blocking SQLite calls are dispatched to
 * [Dispatchers.Default] (maps to [Dispatchers.IO] on Android at runtime).
 */
class LocalMovieDataSource(private val database: MoviesDatabase) {

    // ── Movie cache ───────────────────────────────────────────────────────────

    /**
     * Inserts (or replaces) all [movies] for the given [category] and [page].
     * Runs inside a single SQLDelight transaction for performance.
     */
    suspend fun insertMovies(movies: List<Movie>, category: String, page: Int) {
        withContext(Dispatchers.Default) {
            database.movieQueries.transaction {
                movies.forEach { movie ->
                    database.movieQueries.insertOrReplace(
                        id         = movie.id.toLong(),
                        title      = movie.title,
                        overview   = movie.overview,
                        posterPath = movie.posterPath,
                        backdropPath = movie.backdropPath,
                        releaseDate  = movie.releaseDate,
                        voteAverage  = movie.voteAverage,
                        voteCount    = movie.voteCount.toLong(),
                        category     = category,
                        page         = page.toLong()
                    )
                }
            }
        }
    }

    /** Returns all cached movies for [category], ordered by page ascending. */
    suspend fun getMoviesByCategory(category: String): List<Movie> {
        return withContext(Dispatchers.Default) {
            database.movieQueries.getByCategory(category)
                .executeAsList()
                .map { row ->
                    Movie(
                        id           = row.id.toInt(),
                        title        = row.title,
                        overview     = row.overview,
                        posterPath   = row.posterPath,
                        backdropPath = row.backdropPath,
                        releaseDate  = row.releaseDate,
                        voteAverage  = row.voteAverage,
                        voteCount    = row.voteCount.toInt()
                    )
                }
        }
    }

    /** Deletes all cached movies for [category] (called before inserting fresh page 1). */
    suspend fun deleteByCategory(category: String) {
        withContext(Dispatchers.Default) {
            database.movieQueries.deleteByCategory(category)
        }
    }

    /**
     * Returns the highest cached page number for [category], or null if nothing
     * is cached (i.e. `MAX(page)` over an empty result set).
     */
    suspend fun getLastCachedPage(category: String): Int? {
        return withContext(Dispatchers.Default) {
            // getLastCachedPage always returns a GetLastCachedPage row; its MAX field is null
            // when the category has no cached rows (SELECT MAX on empty result set).
            database.movieQueries.getLastCachedPage(category)
                .executeAsOne().MAX?.toInt()
        }
    }

    // ── Favorites ─────────────────────────────────────────────────────────────

    /**
     * Inserts or replaces [movie] in the favorites table.
     *
     * @param savedAt epoch-milliseconds timestamp supplied by the caller (platform
     *   code can use `System.currentTimeMillis()` on Android/JVM).
     */
    suspend fun insertFavorite(movie: Movie, savedAt: Long) {
        withContext(Dispatchers.Default) {
            database.favoriteQueries.insertOrReplace(
                id           = movie.id.toLong(),
                title        = movie.title,
                overview     = movie.overview,
                posterPath   = movie.posterPath,
                backdropPath = movie.backdropPath,
                releaseDate  = movie.releaseDate,
                voteAverage  = movie.voteAverage,
                voteCount    = movie.voteCount.toLong(),
                savedAt      = savedAt
            )
        }
    }

    /** Removes the favorite with the given [movieId]. */
    suspend fun deleteFavoriteById(movieId: Int) {
        withContext(Dispatchers.Default) {
            database.favoriteQueries.deleteById(movieId.toLong())
        }
    }

    /**
     * Returns a cold [Flow] that emits `true`/`false` whenever the favorites
     * table changes for [movieId].  Backed by SQLDelight's reactive queries.
     */
    fun observeIsFavorite(movieId: Int): Flow<Boolean> {
        // isFavoriteById returns Query<Boolean> (SQLDelight maps EXISTS to Boolean directly).
        return database.favoriteQueries.isFavoriteById(movieId.toLong())
            .asFlow()
            .mapToOne(Dispatchers.Default)
    }

    /** One-shot check — returns `true` if [movieId] is in the favorites table. */
    suspend fun isFavorite(movieId: Int): Boolean {
        return withContext(Dispatchers.Default) {
            // isFavoriteById returns Query<Boolean> — executeAsOne() gives the Boolean directly.
            database.favoriteQueries.isFavoriteById(movieId.toLong()).executeAsOne()
        }
    }

    /**
     * Returns a page of favorite [Movie]s using offset-based pagination.
     *
     * Fetching [limit] + 1 rows lets the caller check for a next page without
     * an extra `COUNT(*)` query — pass `PAGE_SIZE + 1` for [limit].
     */
    suspend fun getFavoritesPaged(limit: Int, offset: Int): List<Movie> {
        return withContext(Dispatchers.Default) {
            // getFavoritesPaged uses positional ? params; generated names are value_ / value__
            database.favoriteQueries.getFavoritesPaged(
                value_  = limit.toLong(),
                value__ = offset.toLong()
            ).executeAsList().map { row ->
                Movie(
                    id           = row.id.toInt(),
                    title        = row.title,
                    overview     = row.overview,
                    posterPath   = row.posterPath,
                    backdropPath = row.backdropPath,
                    releaseDate  = row.releaseDate,
                    voteAverage  = row.voteAverage,
                    voteCount    = row.voteCount.toInt()
                )
            }
        }
    }
}
