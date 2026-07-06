package com.tcohen.moviesapp.ai.data.safe

import com.tcohen.moviesapp.util.ApiError
import com.tcohen.moviesapp.util.NetworkResult
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal suspend fun <T> safeLlmCall(block: suspend () -> T): NetworkResult<T> {
    return try {
        NetworkResult.Success(block())
    } catch (e: HttpException) {
        NetworkResult.Error(
            message = llmHttpErrorMessage(e),
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

internal fun llmHttpErrorMessage(e: HttpException): String = when (e.code()) {
    401, 403 -> ApiError.UNAUTHORIZED.message
    429 -> ApiError.RATE_LIMITED.message
    408, 504 -> ApiError.TIMEOUT.message
    in 500..599 -> ApiError.LLM_UNAVAILABLE.message
    else -> ApiError.LLM_UNAVAILABLE.message
}
