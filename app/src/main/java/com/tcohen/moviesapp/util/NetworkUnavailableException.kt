package com.tcohen.moviesapp.util

class NetworkUnavailableException(
    message: String = DEFAULT_MESSAGE
) : Exception(message) {

    companion object {

        const val DEFAULT_MESSAGE = "No internet connection. Showing cached results."
    }
}
