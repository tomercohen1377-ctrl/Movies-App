package com.tcohen.moviesapp.util

/**
 * Fallback error messages for cases where the server cannot provide one
 * (connectivity failures, unknown HTTP codes, unexpected exceptions).
 */
enum class ApiError(val message: String) {
    SERVER_ERROR("Server error"),
    NO_CONNECTION("No internet connection"),
    TIMEOUT("Connection timed out — check your internet and retry"),
    UNEXPECTED("An unexpected error occurred")
}
