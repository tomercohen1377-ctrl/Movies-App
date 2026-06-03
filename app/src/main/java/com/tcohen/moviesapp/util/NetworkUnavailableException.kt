package com.tcohen.moviesapp.util

/**
 * Thrown when a network request is attempted but the device is offline
 * and no cached data is available for the requested page.
 */
class NetworkUnavailableException(
    message: String = "No internet connection. Showing cached results."
) : Exception(message)
