package com.tcohen.moviesapp.data.remote.api

import com.tcohen.moviesapp.data.remote.dto.TmdbErrorBody
import com.tcohen.moviesapp.util.ApiError
import com.tcohen.moviesapp.util.NetworkResult
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

private val errorJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

/**
 * Executes [block] and wraps the outcome in a [NetworkResult].
 *
 * - [ClientRequestException] (4xx): parses the TMDB error body for a user-facing message.
 * - [ServerResponseException] (5xx): maps to [ApiError.SERVER_ERROR].
 * - Other exceptions: classified by class / message name into TIMEOUT, NO_CONNECTION,
 *   or UNEXPECTED. This heuristic works on JVM/Android and degrades gracefully on
 *   other KMP targets until platform-specific handling is added.
 */
suspend fun <T> safeApiCall(block: suspend () -> T): NetworkResult<T> {
    return try {
        NetworkResult.Success(block())
    } catch (e: ClientRequestException) {
        val body = runCatching { e.response.bodyAsText() }.getOrNull()
        val tmdbMessage = body?.let {
            runCatching { errorJson.decodeFromString<TmdbErrorBody>(it) }
                .getOrNull()?.statusMessage?.ifEmpty { null }
        }
        NetworkResult.Error(
            message = tmdbMessage ?: ApiError.SERVER_ERROR.message,
            httpCode = e.response.status.value
        )
    } catch (e: ServerResponseException) {
        NetworkResult.Error(
            message = ApiError.SERVER_ERROR.message,
            httpCode = e.response.status.value
        )
    } catch (e: Exception) {
        val className = e::class.simpleName?.lowercase() ?: ""
        val message = e.message?.lowercase() ?: ""
        when {
            "timeout" in className || "timeout" in message ->
                NetworkResult.Error(ApiError.TIMEOUT.message)
            "unknown" in className || "unreachable" in className ||
            "connect" in className || "io" in className ||
            "network" in message || "connection" in message || "host" in message ->
                NetworkResult.Error(ApiError.NO_CONNECTION.message)
            else ->
                NetworkResult.Error(ApiError.UNEXPECTED.message)
        }
    }
}
