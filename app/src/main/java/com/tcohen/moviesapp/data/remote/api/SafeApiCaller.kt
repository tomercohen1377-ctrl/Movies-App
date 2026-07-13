package com.tcohen.moviesapp.data.remote.api

import com.tcohen.moviesapp.data.remote.dto.TmdbErrorBody
import com.tcohen.moviesapp.util.ApiError
import com.tcohen.moviesapp.util.NetworkMonitor
import com.tcohen.moviesapp.util.NetworkResult
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central guard around every TMDB API call.
 *
 * Two responsibilities, in this order:
 *
 * 1. **Offline short-circuit.** Before running [block], check [NetworkMonitor.isCurrentlyOnline].
 *    When the device is offline, return [NetworkResult.Error] with [ApiError.NO_CONNECTION.message]
 *    immediately rather than waiting for OkHttp to raise [UnknownHostException] /
 *    [SocketTimeoutException]. Every caller benefits — no more duplicate
 *    `isCurrentlyOnline()` pre-checks scattered around repository / paging-source code.
 *
 *    Callers that *intentionally* want a different behaviour when offline (currently
 *    only `MovieRepositoryImpl.getTrailer`, which returns a `Success(null)` so the trailer
 *    section degrades quietly to the backdrop image) opt out via [bypassOfflineCheck] = true
 *    and perform their own routing.
 *
 * 2. **Exception → [NetworkResult] mapping.** HTTP errors parse the TMDB `status_message`
 *    body; connectivity failures map to other [ApiError] entries. The mapping table is the
 *    single source of truth for "what does exception X mean to the user?".
 *
 * Cancellation ([kotlinx.coroutines.CancellationException]) is deliberately NOT caught —
 * it must propagate so `viewModelScope` cleanup works.
 */
@Singleton
class SafeApiCaller @Inject constructor(
    private val networkMonitor: NetworkMonitor
) {

    /**
     * Run [block] under the offline pre-check + exception mapping described in the class KDoc.
     *
     * @param bypassOfflineCheck When `true`, skip the offline pre-check and run [block]
     *   unconditionally regardless of connectivity. Reserved for callers that need
     *   bespoke offline behaviour (e.g. [com.tcohen.moviesapp.data.repository.MovieRepositoryImpl.getTrailer]).
     */
    suspend operator fun <T> invoke(
        bypassOfflineCheck: Boolean = false,
        block: suspend () -> T
    ): NetworkResult<T> {
        if (!bypassOfflineCheck && !networkMonitor.isCurrentlyOnline()) {
            return NetworkResult.Error(ApiError.NO_CONNECTION.message)
        }
        return wrapErrors(block)
    }

    private suspend fun <T> wrapErrors(block: suspend () -> T): NetworkResult<T> {
        return try {
            NetworkResult.Success(block())
        } catch (e: HttpException) {
            val tmdbMessage = e.response()?.errorBody()?.string()
                ?.let { body -> runCatching { errorJson.decodeFromString<TmdbErrorBody>(body) }.getOrNull() }
                ?.statusMessage
                ?.ifEmpty { null }
            NetworkResult.Error(
                message = tmdbMessage ?: ApiError.SERVER_ERROR.message,
                httpCode = e.code()
            )
        } catch (_: SocketTimeoutException) {
            NetworkResult.Error(ApiError.TIMEOUT.message)
        } catch (_: UnknownHostException) {
            NetworkResult.Error(ApiError.NO_CONNECTION.message)
        } catch (_: IOException) {
            NetworkResult.Error(ApiError.NO_CONNECTION.message)
        } catch (_: Exception) {
            NetworkResult.Error(ApiError.UNEXPECTED.message)
        }
    }

    private companion object {
        /** Shared error JSON parser — one instance avoids re-allocating per call. */
        val errorJson = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }
}
