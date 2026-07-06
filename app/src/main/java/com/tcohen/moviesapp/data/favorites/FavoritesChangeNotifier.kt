package com.tcohen.moviesapp.data.favorites

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Application-wide bus that signals "the user's favorites set
 * just changed — refresh everywhere that shows them".
 *
 * The server is the source of truth: any time a screen successfully
 * adds or removes a favorite, it calls [notifyChanged]. Screens
 * showing the favorites list subscribe to [changes] and re-fetch
 * from the server when an event arrives.
 *
 * Lifecycle of the bus itself is the application process — kept
 * deliberately simple (one SharedFlow, no replay buffer) so that
 * only currently-collecting subscribers see the events.
 */
@Singleton
class FavoritesChangeNotifier @Inject constructor() {

    private val _changes = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    suspend fun notifyChanged() {
        _changes.emit(Unit)
    }
}
