package com.tcohen.moviesapp.data.remote.server.interceptor

import com.tcohen.moviesapp.data.auth.AuthSnapshot
import com.tcohen.moviesapp.data.auth.AuthStore
import com.tcohen.moviesapp.data.remote.server.ServerDefaults
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerBearerInterceptorTest {

    private val token = "eyJhbGciOiJIUzI1NiJ9.payload.signature"

    private val originalRequest = Request.Builder()
        .url("https://api/users/me/favorites")
        .build()

    private val okResponse = Response.Builder()
        .request(originalRequest)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body("{}".toResponseBody("application/json".toMediaType()))
        .build()

    @Test
    fun `attaches Authorization header when snapshot is present`() {
        val store = mockk<AuthStore>()
        every { store.readSnapshotBlocking() } returns AuthSnapshot(
            userId = "quiet-amber-fox",
            accessToken = token,
            expiresAtEpochMs = System.currentTimeMillis() + 60_000L
        )

        val outgoing = slot<Request>()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns originalRequest
        every { chain.proceed(capture(outgoing)) } returns okResponse

        ServerBearerInterceptor(store).intercept(chain)

        val auth = outgoing.captured.header(ServerDefaults.AUTH_HEADER)
        assertEquals(ServerDefaults.BEARER_PREFIX + token, auth)
    }

    @Test
    fun `omits Authorization header when no snapshot is persisted`() {
        val store = mockk<AuthStore>()
        every { store.readSnapshotBlocking() } returns null

        val outgoing = slot<Request>()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns originalRequest
        every { chain.proceed(capture(outgoing)) } returns okResponse

        ServerBearerInterceptor(store).intercept(chain)

        assertNull(outgoing.captured.header(ServerDefaults.AUTH_HEADER))
    }

    @Test
    fun `passes the original URL through unchanged`() {
        val store = mockk<AuthStore>()
        every { store.readSnapshotBlocking() } returns null

        val original = Request.Builder().url("https://api/auth/token").build()
        val outgoing = slot<Request>()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns original
        every { chain.proceed(capture(outgoing)) } returns okResponse

        ServerBearerInterceptor(store).intercept(chain)

        assertEquals(original.url, outgoing.captured.url)
        assertEquals(original.method, outgoing.captured.method)
    }

    @Test
    fun `interceptor interacts with AuthStore only once per request`() {
        val store = mockk<AuthStore>()
        every { store.readSnapshotBlocking() } returns AuthSnapshot(
            userId = "u", accessToken = "t", expiresAtEpochMs = 1L
        )

        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns originalRequest
        every { chain.proceed(any()) } returns okResponse

        ServerBearerInterceptor(store).intercept(chain)

        verify(exactly = 1) { store.readSnapshotBlocking() }
    }
}
