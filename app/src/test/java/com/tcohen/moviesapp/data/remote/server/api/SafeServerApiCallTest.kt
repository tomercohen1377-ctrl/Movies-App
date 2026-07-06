package com.tcohen.moviesapp.data.remote.server.api

import com.tcohen.moviesapp.util.ApiError
import com.tcohen.moviesapp.util.NetworkMonitor
import com.tcohen.moviesapp.util.NetworkResult
import io.mockk.every
import io.mockk.mockk
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

class SafeServerApiCallTest {

    private fun onlineMonitor(): NetworkMonitor = mockk {
        every { isCurrentlyOnline() } returns true
    }

    private fun offlineMonitor(): NetworkMonitor = mockk {
        every { isCurrentlyOnline() } returns false
    }

    @Test
    fun `offline short-circuits without invoking the block`() = runTest {
        var called = false
        val result = safeServerApiCall(offlineMonitor()) {
            called = true
            "should not run"
        }
        assertTrue("block must not run when offline", !called)
        assertTrue(result is NetworkResult.Error)
        assertEquals(ApiError.NO_CONNECTION.message, (result as NetworkResult.Error).message)
        assertEquals(0, result.httpCode)
    }

    @Test
    fun `offline returns Error with NO_CONNECTION message`() = runTest {
        val result = safeServerApiCall(offlineMonitor()) { "ok" }
        assertTrue(result is NetworkResult.Error)
        val err = result as NetworkResult.Error

        assertEquals(0, err.httpCode)
        assertEquals(ApiError.NO_CONNECTION.message, err.message)
    }

    @Test
    fun `success returns Success`() = runTest {
        val result = safeServerApiCall(onlineMonitor()) { "ok" }
        assertTrue(result is NetworkResult.Success)
        assertEquals("ok", (result as NetworkResult.Success).data)
    }

    @Test
    fun `401 with parseable error body surfaces server message`() = runTest {
        val response = Response.error<Any>(
            401,
            """{"error":"Invalid or expired token"}""".toResponseBody("application/json".toMediaType())
        )
        val result = safeServerApiCall<String>(onlineMonitor()) { throw HttpException(response) }
        assertTrue(result is NetworkResult.Error)
        result as NetworkResult.Error
        assertEquals(401, result.httpCode)
        assertEquals("Invalid or expired token", result.message)
    }

    @Test
    fun `401 with unparseable body falls back to UNAUTHORIZED message`() = runTest {
        val response = Response.error<Any>(
            401,
            "<html>not json</html>".toResponseBody("text/html".toMediaType())
        )
        val result = safeServerApiCall<String>(onlineMonitor()) { throw HttpException(response) }
        assertTrue(result is NetworkResult.Error)
        result as NetworkResult.Error
        assertEquals(401, result.httpCode)
        assertEquals(ApiError.UNAUTHORIZED.message, result.message)
    }

    @Test
    fun `409 conflict surfaces server message`() = runTest {
        val response = Response.error<Any>(
            409,
            """{"error":"UserAlreadyExists"}""".toResponseBody("application/json".toMediaType())
        )
        val result = safeServerApiCall<String>(onlineMonitor()) { throw HttpException(response) }
        assertTrue(result is NetworkResult.Error)
        result as NetworkResult.Error
        assertEquals(409, result.httpCode)
        assertEquals("UserAlreadyExists", result.message)
    }

    @Test
    fun `500 with whitelabel page falls back to SERVER_ERROR message`() = runTest {
        val response = Response.error<Any>(
            500,
            """<html><body>Whitelabel Error Page</body></html>""".toResponseBody("text/html".toMediaType())
        )
        val result = safeServerApiCall<String>(onlineMonitor()) { throw HttpException(response) }
        assertTrue(result is NetworkResult.Error)
        result as NetworkResult.Error
        assertEquals(500, result.httpCode)
        assertEquals(ApiError.SERVER_ERROR.message, result.message)
    }

    @Test
    fun `SocketTimeoutException maps to TIMEOUT`() = runTest {
        val result = safeServerApiCall<String>(onlineMonitor()) {
            throw SocketTimeoutException("read timed out")
        }
        assertTrue(result is NetworkResult.Error)
        assertEquals(ApiError.TIMEOUT.message, (result as NetworkResult.Error).message)
    }

    @Test
    fun `UnknownHostException maps to NO_CONNECTION`() = runTest {
        val result = safeServerApiCall<String>(onlineMonitor()) {
            throw UnknownHostException("no dns")
        }
        assertTrue(result is NetworkResult.Error)
        assertEquals(ApiError.NO_CONNECTION.message, (result as NetworkResult.Error).message)
    }

    @Test
    fun `generic IOException maps to NO_CONNECTION`() = runTest {
        val result = safeServerApiCall<String>(onlineMonitor()) {
            throw IOException("eof")
        }
        assertTrue(result is NetworkResult.Error)
        assertEquals(ApiError.NO_CONNECTION.message, (result as NetworkResult.Error).message)
    }

    @Test
    fun `unexpected exception maps to UNEXPECTED`() = runTest {
        val result = safeServerApiCall<String>(onlineMonitor()) {
            throw IllegalStateException("nope")
        }
        assertTrue(result is NetworkResult.Error)
        assertEquals(ApiError.UNEXPECTED.message, (result as NetworkResult.Error).message)
    }
}
