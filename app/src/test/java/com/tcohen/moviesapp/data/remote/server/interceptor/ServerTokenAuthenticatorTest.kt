package com.tcohen.moviesapp.data.remote.server.interceptor

import com.tcohen.moviesapp.data.auth.AuthRepository
import com.tcohen.moviesapp.data.auth.AuthSnapshot
import com.tcohen.moviesapp.data.remote.server.ServerDefaults
import io.mockk.coEvery
import io.mockk.mockk
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import javax.inject.Provider

class ServerTokenAuthenticatorTest {

    @Test
    fun `returns retried request with fresh bearer on 401 + successful refresh`() {
        val newToken = "eyJ.new.payload.sig"
        val repo = mockk<AuthRepository>()
        coEvery { repo.refreshToken() } returns AuthSnapshot(
            userId = "quiet-amber-fox",
            accessToken = newToken,
            expiresAtEpochMs = System.currentTimeMillis() + 60_000L
        )
        val authenticator = ServerTokenAuthenticator(Provider { repo })

        val original = Request.Builder().url("https://api/users/me/favorites").build()
        val response = response(original, code = 401)
        val retried = authenticator.authenticate(null, response)

        assertNotNull("Expected a retried request, got null", retried)
        assertEquals(
            ServerDefaults.BEARER_PREFIX + newToken,
            retried!!.header(ServerDefaults.AUTH_HEADER)
        )
    }

    @Test
    fun `returns null when refresh fails so caller surfaces 401`() {
        val repo = mockk<AuthRepository>()
        coEvery { repo.refreshToken() } returns null
        val authenticator = ServerTokenAuthenticator(Provider { repo })

        val original = Request.Builder().url("https://api/users/me/favorites").build()
        val response = response(original, code = 401)
        val retried = authenticator.authenticate(null, response)

        assertNull("Expected null (no retry) when refresh fails", retried)
    }

    @Test
    fun `returns null when response is not 401`() {
        val repo = mockk<AuthRepository>()

        val authenticator = ServerTokenAuthenticator(Provider { repo })

        val original = Request.Builder().url("https://api/users/me/favorites").build()
        val response500 = response(original, code = 500)
        val retried = authenticator.authenticate(null, response500)

        assertNull(retried)
    }

    @Test
    fun `keeps the original URL on the retried request`() {
        val repo = mockk<AuthRepository>()
        coEvery { repo.refreshToken() } returns AuthSnapshot(
            userId = "quiet-amber-fox",
            accessToken = "new-token",
            expiresAtEpochMs = System.currentTimeMillis() + 60_000L
        )
        val authenticator = ServerTokenAuthenticator(Provider { repo })

        val original = Request.Builder().url("https://api/users/me/favorites").build()
        val response = response(original, code = 401)
        val retried = authenticator.authenticate(null, response)

        assertEquals(original.url, retried!!.url)
        assertEquals(original.method, retried.method)
    }

    private fun response(request: Request, code: Int): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Status $code")
            .body("".toResponseBody())
            .build()
}
