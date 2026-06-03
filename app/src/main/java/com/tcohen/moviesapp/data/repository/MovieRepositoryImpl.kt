package com.tcohen.moviesapp.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.tcohen.moviesapp.data.local.dao.FavoriteDao
import com.tcohen.moviesapp.data.local.dao.MovieDao
import com.tcohen.moviesapp.data.local.entity.FavoriteEntity
import com.tcohen.moviesapp.data.mapper.toDomain
import com.tcohen.moviesapp.data.mapper.toEntity
import com.tcohen.moviesapp.data.remote.api.TmdbApiService
import com.tcohen.moviesapp.data.remote.dto.VideoDto
import com.tcohen.moviesapp.data.remote.paging.MoviePagingSource
import com.tcohen.moviesapp.domain.model.Category
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.domain.model.MovieDetail
import com.tcohen.moviesapp.domain.model.VideoResult
import com.tcohen.moviesapp.domain.repository.MovieRepository
import com.tcohen.moviesapp.util.NetworkMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class MovieRepositoryImpl @Inject constructor(
    private val apiService: TmdbApiService,
    private val movieDao: MovieDao,
    private val favoriteDao: FavoriteDao,
    private val networkMonitor: NetworkMonitor
) : MovieRepository {

    override fun getMovies(category: Category): Flow<PagingData<Movie>> {
        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                enablePlaceholders = false,
                prefetchDistance = PREFETCH_DISTANCE
            ),
            pagingSourceFactory = {
                MoviePagingSource(
                    apiService = apiService,
                    movieDao = movieDao,
                    category = category,
                    networkMonitor = networkMonitor
                )
            }
        ).flow
    }

    override suspend fun getMovieDetail(movieId: Int): MovieDetail {
        return apiService.getMovieDetail(movieId).toDomain()
    }

    override suspend fun getTrailer(movieId: Int): VideoResult? {
        if (!networkMonitor.isCurrentlyOnline()) return null
        return apiService.getMovieVideos(movieId).results
            .filter { it.site == VIDEO_SITE_YOUTUBE && it.type == VIDEO_TYPE_TRAILER }
            .sortedWith(
                compareByDescending<VideoDto> { it.official }
                    .thenByDescending { it.publishedAt }
            )
            .firstOrNull()
            ?.toDomain()
    }

    override suspend fun toggleFavorite(movie: Movie) {
        if (favoriteDao.isFavoriteOnce(movie.id)) {
            favoriteDao.deleteById(movie.id)
        } else {
            favoriteDao.insert(movie.toFavoriteEntity())
        }
    }

    override fun getFavorites(): Flow<List<Movie>> {
        return favoriteDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun isFavorite(movieId: Int): Flow<Boolean> {
        return favoriteDao.isFavorite(movieId)
    }

    private fun Movie.toFavoriteEntity() = FavoriteEntity(
        id = id,
        title = title,
        overview = overview,
        posterPath = posterPath,
        backdropPath = backdropPath,
        releaseDate = releaseDate,
        voteAverage = voteAverage,
        voteCount = voteCount
    )

    companion object {
        /** Number of items loaded per page from the TMDB API. */
        private const val PAGE_SIZE = 20

        /** Items remaining before the end of the list that trigger a new page load. */
        private const val PREFETCH_DISTANCE = 5

        /** Video hosting site identifier used to filter YouTube trailers. */
        private const val VIDEO_SITE_YOUTUBE = "YouTube"

        /** Video type used to identify trailers in the TMDB videos response. */
        private const val VIDEO_TYPE_TRAILER = "Trailer"
    }
}
