package com.tcohen.moviesapp.domain.repository

import androidx.paging.PagingData
import com.tcohen.moviesapp.domain.model.Category
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.domain.model.MovieDetail
import com.tcohen.moviesapp.domain.model.VideoResult
import com.tcohen.moviesapp.util.NetworkResult
import kotlinx.coroutines.flow.Flow

interface MovieRepository {

    fun getMovies(category: Category): Flow<PagingData<Movie>>

    suspend fun getMovieDetail(movieId: Int): NetworkResult<MovieDetail>

    suspend fun getTrailer(movieId: Int): NetworkResult<VideoResult?>

    suspend fun getFavorites(): NetworkResult<List<Movie>>

    suspend fun addFavorite(movie: Movie): NetworkResult<Unit>

    suspend fun removeFavorite(movie: Movie): NetworkResult<Unit>

    suspend fun isFavorite(movieId: Int): NetworkResult<Boolean>
}
