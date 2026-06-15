package com.tcohen.moviesapp.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response body from `POST /account/{account_id}/favorite`.
 *
 * A [statusCode] of 1 (Created) or 12 (Updated) indicates success.
 */
@Serializable
data class FavoriteResponse(
    @SerialName("success") val success: Boolean = false,
    @SerialName("status_code") val statusCode: Int = 0,
    @SerialName("status_message") val statusMessage: String = ""
)
