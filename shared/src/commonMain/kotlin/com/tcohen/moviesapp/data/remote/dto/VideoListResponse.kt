package com.tcohen.moviesapp.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VideoListResponse(
    @SerialName("id") val id: Int,
    @SerialName("results") val results: List<VideoResponse> = emptyList()
)

@Serializable
data class VideoResponse(
    @SerialName("key") val key: String,
    @SerialName("site") val site: String,
    @SerialName("type") val type: String,
    @SerialName("official") val official: Boolean = false,
    @SerialName("published_at") val publishedAt: String = ""
)
