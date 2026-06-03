package com.tcohen.moviesapp.util

/**
 * Thrown when a network request is attempted but the device is offline
 * and no cached data is available for the requested page.
 */
class NetworkUnavailableException(
    message: String = DEFAULT_MESSAGE
) : Exception(message) {

    companion object {
        /** User-facing message shown in the paging footer when cache is exhausted offline. */
        const val DEFAULT_MESSAGE = "No internet connection. Showing cached results."
    }
}
