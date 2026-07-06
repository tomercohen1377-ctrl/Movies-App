package com.tcohen.moviesapp.data.remote.server.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerFavoriteDto(
    @SerialName("movieId") val movieId: Int,
    @SerialName("savedAt") val savedAt: Long,
)
