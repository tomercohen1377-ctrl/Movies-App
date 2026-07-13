package com.tcohen.moviesapp.data.remote.api

import com.tcohen.moviesapp.util.ApiError
import com.tcohen.moviesapp.util.MainDispatcherRule
import com.tcohen.moviesapp.util.NetworkMonitor
import com.tcohen.moviesapp.util.NetworkResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Unit tests for [SafeApiCaller].
 *
 * Two responsibilities to cover:
 * 1. **Offline short-circuit** — when [NetworkMonitor.isCurrentlyOnline] is `false` and
 *    the caller has not opted out via `bypassOfflineCheck`, the inner block must NOT run;
 *    the wrapper returns [NetworkResult.Error] with [ApiError.NO_CONNECTION.message].
 * 2. **Exception mapping** — every exception thrown by the block is mapped to a typed
 *    [NetworkResult.Error] using the same [ApiError] enum the UI layer reads.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SafeApiCallTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val networkMonitor: NetworkMonitor = mockk()

    private val safeApiCaller = SafeApiCaller(networkMonitor)

    // ── Offline short-circuit ────────────────────────────────────────────────

    @Test
    fun `offline + bypass=false returns Error(NO_CONNECTION) without running the block`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns false
        var blockInvocations = 0

        val result = safeApiCaller {
            blockInvocations++
            "should never run"
        }

        assertTrue(result is NetworkResult.Error)
        assertEquals(ApiError.NO_CONNECTION.message, (result as NetworkResult.Error).message)
        assertEquals(0, blockInvocations)
    }

    @Test
    fun `offline + bypass=true runs the block anyway`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns false
        val expected = "live call result"

        val result = safeApiCaller(bypassOfflineCheck = true) { expected }

        assertTrue(result is NetworkResult.Success)
        assertEquals(expected, (result as NetworkResult.Success).data)
    }

    @Test
    fun `online runs the block normally`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        val expected = "live payload"

        val result = safeApiCaller { expected }

        assertTrue(result is NetworkResult.Success)
        assertEquals(expected, (result as NetworkResult.Success).data)
    }

    // ── Exception mapping ────────────────────────────────────────────────────

    @Test
    fun `IOException maps to Error(NO_CONNECTION)`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        val io = IOException("connection reset")

        val result = safeApiCaller<String> { throw io }

        assertTrue(result is NetworkResult.Error)
        assertEquals(ApiError.NO_CONNECTION.message, (result as NetworkResult.Error).message)
    }

    @Test
    fun `SocketTimeoutException maps to Error(TIMEOUT)`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        val timeout = SocketTimeoutException("read timed out")

        val result = safeApiCaller<String> { throw timeout }

        assertTrue(result is NetworkResult.Error)
        assertEquals(ApiError.TIMEOUT.message, (result as NetworkResult.Error).message)
    }

    @Test
    fun `UnknownHostException maps to Error(NO_CONNECTION)`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        val unknown = UnknownHostException("api.themoviedb.org")

        val result = safeApiCaller<String> { throw unknown }

        assertTrue(result is NetworkResult.Error)
        assertEquals(ApiError.NO_CONNECTION.message, (result as NetworkResult.Error).message)
    }

    @Test
    fun `HttpException with TMDB status_message returns parsed message`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        // Stub the response() chain to return a JSON body the wrapper can parse.
        val httpException: HttpException = mockk {
            every { code() } returns 401
            every { response() } returns mockk {
                every { errorBody() } returns mockk {
                    every { string() } returns "{\"status_code\":7,\"status_message\":\"Invalid API key\"}"
                }
            }
        }

        val result = safeApiCaller<String> { throw httpException }

        assertTrue(result is NetworkResult.Error)
        val err = result as NetworkResult.Error
        assertEquals("Invalid API key", err.message)
        assertEquals(401, err.httpCode)
    }

    @Test
    fun `HttpException with empty status_message falls back to SERVER_ERROR`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        val httpException: HttpException = mockk {
            every { code() } returns 500
            every { response() } returns mockk {
                every { errorBody() } returns mockk {
                    every { string() } returns "{\"status_code\":22,\"status_message\":\"\"}"
                }
            }
        }

        val result = safeApiCaller<String> { throw httpException }

        assertTrue(result is NetworkResult.Error)
        val err = result as NetworkResult.Error
        assertEquals(ApiError.SERVER_ERROR.message, err.message)
        assertEquals(500, err.httpCode)
    }

    @Test
    fun `HttpException with unparseable body falls back to SERVER_ERROR`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        val httpException: HttpException = mockk {
            every { code() } returns 500
            every { response() } returns mockk {
                every { errorBody() } returns mockk {
                    every { string() } returns "<html>nginx error page</html>"
                }
            }
        }

        val result = safeApiCaller<String> { throw httpException }

        assertTrue(result is NetworkResult.Error)
        val err = result as NetworkResult.Error
        assertEquals(ApiError.SERVER_ERROR.message, err.message)
        assertEquals(500, err.httpCode)
    }

    @Test
    fun `HttpException with null errorBody falls back to SERVER_ERROR`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        val httpException: HttpException = mockk {
            every { code() } returns 500
            every { response() } returns null
        }

        val result = safeApiCaller<String> { throw httpException }

        assertTrue(result is NetworkResult.Error)
        val err = result as NetworkResult.Error
        assertEquals(ApiError.SERVER_ERROR.message, err.message)
        assertEquals(500, err.httpCode)
    }

    @Test
    fun `unexpected Exception maps to Error(UNEXPECTED)`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        val unexpected = IllegalStateException("boom")

        val result = safeApiCaller<String> { throw unexpected }

        assertTrue(result is NetworkResult.Error)
        assertEquals(ApiError.UNEXPECTED.message, (result as NetworkResult.Error).message)
    }

    @Test
    fun `CancellationException is not swallowed (propagates for viewModelScope cleanup)`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true

        val thrown = runCatching {
            safeApiCaller<String> { throw kotlinx.coroutines.CancellationException("cancelled") }
        }.exceptionOrNull()

        assertTrue("expected CancellationException to propagate", thrown is kotlinx.coroutines.CancellationException)
    }

    // ── Backwards compatibility ─────────────────────────────────────────────

    @Test
    fun `default value of bypassOfflineCheck is false (offline guard active)`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns false

        // No bypassOfflineCheck argument at all — should still short-circuit.
        val result = safeApiCaller<String> { "should not run" }

        assertTrue(result is NetworkResult.Error)
        assertEquals(ApiError.NO_CONNECTION.message, (result as NetworkResult.Error).message)
    }
}
