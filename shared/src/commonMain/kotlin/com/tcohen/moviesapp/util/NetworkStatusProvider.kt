package com.tcohen.moviesapp.util

import kotlinx.coroutines.flow.Flow

/**
 * Platform-agnostic interface for observing network connectivity.
 *
 * Android implementation: [com.tcohen.moviesapp.util.NetworkMonitor]
 * Future iOS/Desktop implementations can provide their own.
 */
interface NetworkStatusProvider {
    /** Cold flow emitting true when connected, false when disconnected. */
    val isOnline: Flow<Boolean>

    /** Synchronous snapshot — true if currently connected. */
    fun isCurrentlyOnline(): Boolean
}
