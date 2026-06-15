package com.tcohen.moviesapp.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * JSON body returned by TMDB for every error response (4xx / 5xx).
 *
 * Example:
 * ```json
 * { "status_code": 7, "status_message": "Invalid API key.", "success": false }
 * ```
 */
@Serializable
data class TmdbErrorBody(
    @SerialName("status_code") val statusCode: Int = 0,
    @SerialName("status_message") val statusMessage: String = "",
    @SerialName("success") val success: Boolean = false
)
