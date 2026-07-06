package com.tcohen.moviesapp.data.remote.server.api

import com.tcohen.moviesapp.data.remote.server.dto.ServerErrorBody
import com.tcohen.moviesapp.util.ApiError
import com.tcohen.moviesapp.util.NetworkMonitor
import com.tcohen.moviesapp.util.NetworkResult
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

suspend fun <T> safeServerApiCall(
    networkMonitor: NetworkMonitor,
    block: suspend () -> T,
): NetworkResult<T> {

    if (!networkMonitor.isCurrentlyOnline()) {
        return NetworkResult.Error(ApiError.NO_CONNECTION.message, httpCode = 0)
    }

    return try {
        NetworkResult.Success(block())
    } catch (e: HttpException) {
        val serverMessage = e.response()?.errorBody()?.string()
            ?.let { body ->
                runCatching { SERVER_ERROR_JSON.decodeFromString<ServerErrorBody>(body) }.getOrNull()
            }
            ?.error
            ?.ifEmpty { null }
        val fallbackForCode = when (e.code()) {
            401 -> ApiError.UNAUTHORIZED.message
            else -> ApiError.SERVER_ERROR.message
        }
        NetworkResult.Error(
            message = serverMessage ?: fallbackForCode,
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

private val SERVER_ERROR_JSON: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}
