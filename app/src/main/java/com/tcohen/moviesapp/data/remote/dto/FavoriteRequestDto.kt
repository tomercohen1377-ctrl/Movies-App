package com.tcohen.moviesapp.data.remote.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request body for `POST /account/{account_id}/favorite`.
 *
 * Set [favorite] = true to add a movie, false to remove it.
 *
 * [mediaType] always serializes as `"movie"` — `@EncodeDefault` is required because
 * `kotlinx.serialization` skips fields that equal their default value by default
 * (`encodeDefaults = false`), which caused TMDB to return a 400 "Invalid parameters".
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class FavoriteRequestDto(
    @EncodeDefault @SerialName("media_type") val mediaType: String = "movie",
    @SerialName("media_id") val mediaId: Int,
    @SerialName("favorite") val favorite: Boolean
)
