package com.tcohen.moviesapp.ai.data.safe

import com.tcohen.moviesapp.util.ApiError
import com.tcohen.moviesapp.util.NetworkResult
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Unit tests for [safeLlmCall].
 *
 * Mirrors the philosophy of the existing `safeApiCall`-shaped utilities:
 * every distinct throwable must map to a specific [ApiError] entry so the UI
 * can show the right copy.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SafeLlmCallTest {

    // ── success path ──────────────────────────────────────────────────────────

    @Test
    fun `success block returns Success with the value`() = runTest {
        val result = safeLlmCall { "hello world" }
        assertTrue(result is NetworkResult.Success)
        assertEquals("hello world", (result as NetworkResult.Success).data)
    }

    // ── HTTP error mapping ────────────────────────────────────────────────────

    @Test
    fun `401 maps to UNAUTHORIZED`() = runTest {
        val result = safeLlmCall<String> { throw httpException(401) }
        result.assertErrorMatching(ApiError.UNAUTHORIZED, expectedCode = 401)
    }

    @Test
    fun `403 maps to UNAUTHORIZED`() = runTest {
        val result = safeLlmCall<String> { throw httpException(403) }
        result.assertErrorMatching(ApiError.UNAUTHORIZED, expectedCode = 403)
    }

    @Test
    fun `429 maps to RATE_LIMITED`() = runTest {
        val result = safeLlmCall<String> { throw httpException(429) }
        result.assertErrorMatching(ApiError.RATE_LIMITED, expectedCode = 429)
    }

    @Test
    fun `500 maps to LLM_UNAVAILABLE`() = runTest {
        val result = safeLlmCall<String> { throw httpException(500) }
        result.assertErrorMatching(ApiError.LLM_UNAVAILABLE, expectedCode = 500)
    }

    @Test
    fun `502 maps to LLM_UNAVAILABLE`() = runTest {
        val result = safeLlmCall<String> { throw httpException(502) }
        result.assertErrorMatching(ApiError.LLM_UNAVAILABLE, expectedCode = 502)
    }

    @Test
    fun `504 maps to TIMEOUT`() = runTest {
        val result = safeLlmCall<String> { throw httpException(504) }
        result.assertErrorMatching(ApiError.TIMEOUT, expectedCode = 504)
    }

    @Test
    fun `408 maps to TIMEOUT`() = runTest {
        val result = safeLlmCall<String> { throw httpException(408) }
        result.assertErrorMatching(ApiError.TIMEOUT, expectedCode = 408)
    }

    @Test
    fun `unknown status code falls through to LLM_UNAVAILABLE`() = runTest {
        val result = safeLlmCall<String> { throw httpException(418) }
        result.assertErrorMatching(ApiError.LLM_UNAVAILABLE, expectedCode = 418)
    }

    // ── connectivity / IO error mapping ──────────────────────────────────────

    @Test
    fun `SocketTimeoutException maps to TIMEOUT`() = runTest {
        val result = safeLlmCall<String> { throw SocketTimeoutException("read timeout") }
        result.assertErrorMatching(ApiError.TIMEOUT, expectedCode = 0)
    }

    @Test
    fun `UnknownHostException maps to NO_CONNECTION`() = runTest {
        val result = safeLlmCall<String> { throw UnknownHostException("no dns") }
        result.assertErrorMatching(ApiError.NO_CONNECTION, expectedCode = 0)
    }

    @Test
    fun `IOException maps to NO_CONNECTION`() = runTest {
        val result = safeLlmCall<String> { throw IOException("connection refused") }
        result.assertErrorMatching(ApiError.NO_CONNECTION, expectedCode = 0)
    }

    // ── last-resort catch ─────────────────────────────────────────────────────

    @Test
    fun `any other Exception maps to UNEXPECTED`() = runTest {
        val result = safeLlmCall<String> { throw IllegalStateException("oops") }
        result.assertErrorMatching(ApiError.UNEXPECTED, expectedCode = 0)
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun <T> NetworkResult<T>.assertErrorMatching(
        expected: ApiError,
        expectedCode: Int
    ) {
        assertTrue("expected Error but was $this", this is NetworkResult.Error)
        val err = this as NetworkResult.Error
        assertEquals(expected.message, err.message)
        assertEquals(expectedCode, err.httpCode)
    }

    private fun httpException(code: Int): HttpException {
        val body = "error-body".toResponseBody("application/json".toMediaType())
        val retrofitResponse: Response<Any> = Response.error(code, body)
        return HttpException(retrofitResponse)
    }
}
