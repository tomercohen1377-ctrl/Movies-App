package com.tcohen.moviesapp.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request body for `POST /list/{list_id}/remove_item`.
 *
 * Used to remove a movie from the TMDB favorites list identified by
 * the configured `TMDB_FAVORITES_LIST_ID`.
 */
@Serializable
data class RemoveFromListRequestDto(
    @SerialName("media_id") val mediaId: Int
)
