package com.tcohen.moviesapp.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VideoListResponseDto(
    @SerialName("id") val id: Int,
    @SerialName("results") val results: List<VideoDto> = emptyList()
)

@Serializable
data class VideoDto(
    @SerialName("id") val id: String,
    @SerialName("key") val key: String,
    @SerialName("name") val name: String,
    @SerialName("site") val site: String,
    @SerialName("type") val type: String,
    @SerialName("official") val official: Boolean = false,
    @SerialName("published_at") val publishedAt: String = ""
)
