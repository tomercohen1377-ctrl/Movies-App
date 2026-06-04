package com.tcohen.moviesapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tcohen.moviesapp.data.local.entity.MovieEntity

@Dao
interface MovieDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(movies: List<MovieEntity>)

    @Query("SELECT * FROM movies WHERE category = :category ORDER BY page ASC")
    suspend fun getMoviesByCategory(category: String): List<MovieEntity>

    @Query("DELETE FROM movies WHERE category = :category")
    suspend fun deleteByCategory(category: String)

    @Query("SELECT MAX(page) FROM movies WHERE category = :category")
    suspend fun getLastCachedPage(category: String): Int?

}
