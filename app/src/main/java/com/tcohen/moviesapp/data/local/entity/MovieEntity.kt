package com.tcohen.moviesapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "movies")
data class MovieEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val overview: String,
    val posterPath: String?,
    val backdropPath: String?,
    val releaseDate: String,
    val voteAverage: Double,
    val voteCount: Int,
    val category: String,
    val page: Int,
    /** Unix epoch millis when this row was fetched from the server. Used for cache expiry. */
    val cachedAt: Long = 0L
)
