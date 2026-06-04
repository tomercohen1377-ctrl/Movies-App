package com.tcohen.moviesapp.util

/**
 * Typed result of a network call.
 *
 * - [Success] — the call succeeded; `data` holds the deserialized response.
 * - [Error] — the call failed. `message` is a user-readable string (from the
 *   TMDB error body when available, or from [ApiError] otherwise). `httpCode`
 *   is the HTTP status (0 if the request never reached the server).
 */
sealed class NetworkResult<out T> {
    data class Success<out T>(val data: T) : NetworkResult<T>()
    data class Error(val message: String, val httpCode: Int = 0) : NetworkResult<Nothing>()
}
