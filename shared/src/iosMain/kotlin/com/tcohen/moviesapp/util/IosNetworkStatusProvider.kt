package com.tcohen.moviesapp.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Network.NWPathMonitor
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_get_main_queue

/**
 * iOS implementation of [NetworkStatusProvider].
 *
 * Uses Apple's `NWPathMonitor` (Network framework) to observe connectivity
 * changes in real time. Emits `true` when a usable network path exists,
 * `false` otherwise.
 *
 * Usage (inject manually — Hilt is Android-only; use Koin or manual DI on iOS):
 * ```kotlin
 * val networkStatusProvider: NetworkStatusProvider = IosNetworkStatusProvider()
 * ```
 */
class IosNetworkStatusProvider : NetworkStatusProvider {

    private val monitor = NWPathMonitor()
    private val _isOnline = MutableStateFlow(true)

    init {
        monitor.setUpdateHandler { path ->
            _isOnline.value = path.status == nw_path_status_satisfied
        }
        monitor.setQueue(dispatch_get_main_queue())
        monitor.start()
    }

    override val isOnline: Flow<Boolean> = _isOnline.asStateFlow()

    override fun isCurrentlyOnline(): Boolean = _isOnline.value

    /** Cancel the underlying [NWPathMonitor] when this provider is no longer needed. */
    fun cancel() {
        monitor.cancel()
    }
}
