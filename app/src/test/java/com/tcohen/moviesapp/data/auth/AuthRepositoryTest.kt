package com.tcohen.moviesapp.data.auth

import com.tcohen.moviesapp.data.remote.server.api.ServerAuthService
import com.tcohen.moviesapp.data.remote.server.dto.ServerTokenResponse
import com.tcohen.moviesapp.data.user.UserIdProvider
import com.tcohen.moviesapp.util.NetworkMonitor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRepositoryTest {

    private val networkMonitor: NetworkMonitor = mockk(relaxed = true) {
        every { isCurrentlyOnline() } returns true
    }

    @Test
    fun `ensureValidToken returns in-memory snapshot when not expired`() = runTest {
        val store = mockk<AuthStore>(relaxed = true)
        val provider = mockk<UserIdProvider>(relaxed = true)
        val api = mockk<ServerAuthService>(relaxed = true)
        val future = System.currentTimeMillis() + AuthDefaults.TOKEN_TTL_MS / 2
        coEvery { store.readSnapshot() } returns AuthSnapshot(
            userId = "quiet-amber-fox",
            accessToken = "old-token",
            expiresAtEpochMs = future
        )

        val repository = AuthRepository(store, provider, api, networkMonitor)
        val snapshot = repository.ensureValidToken()

        assertNotNull(snapshot)
        assertEquals("old-token", snapshot!!.accessToken)
        coVerify(exactly = 0) { api.token(any(), any()) }
    }

    @Test
    fun `ensureValidToken refreshes proactively when inside grace window`() = runTest {
        val store = mockk<AuthStore>(relaxed = true)
        val provider = mockk<UserIdProvider>(relaxed = true)
        val api = mockk<ServerAuthService>(relaxed = true)
        val now = System.currentTimeMillis()
        coEvery { store.readSnapshot() } returns AuthSnapshot(
            userId = "quiet-amber-fox",
            accessToken = "old-token",
            expiresAtEpochMs = now - 1L
        )
        coEvery { provider.get() } returns "quiet-amber-fox"
        coEvery { store.readPassword() } returns "p".repeat(AuthDefaults.MIN_PASSWORD_LENGTH)
        coEvery { api.token(any(), any()) } returns ServerTokenResponse(accessToken = "new-token")

        val repository = AuthRepository(store, provider, api, networkMonitor)
        val snapshot = repository.ensureValidToken()

        assertEquals("new-token", snapshot!!.accessToken)
        coVerify(exactly = 1) { api.token("quiet-amber-fox", any()) }
    }

    @Test
    fun `ensureValidToken falls back to signUpIfNeeded when no snapshot exists`() = runTest {
        val store = mockk<AuthStore>(relaxed = true)
        val provider = mockk<UserIdProvider>(relaxed = true)
        val api = mockk<ServerAuthService>(relaxed = true)
        coEvery { store.readSnapshot() } returns null
        coEvery { provider.generateIfMissing() } returns "bright-velvet-moth"
        coEvery { api.register(any(), any()) } returns ServerTokenResponse(accessToken = "first-time-token")

        val repository = AuthRepository(store, provider, api, networkMonitor)
        val snapshot = repository.ensureValidToken()

        assertEquals("first-time-token", snapshot!!.accessToken)
    }

    @Test
    fun `signUpIfNeeded returns existing snapshot when already registered`() = runTest {
        val store = mockk<AuthStore>(relaxed = true)
        val provider = mockk<UserIdProvider>(relaxed = true)
        val api = mockk<ServerAuthService>(relaxed = true)
        coEvery { store.readSnapshot() } returns AuthSnapshot(
            userId = "already-here",
            accessToken = "existing",
            expiresAtEpochMs = System.currentTimeMillis() + AuthDefaults.TOKEN_TTL_MS
        )

        val repository = AuthRepository(store, provider, api, networkMonitor)
        val snapshot = repository.signUpIfNeeded()

        assertEquals("existing", snapshot.accessToken)
        coVerify(exactly = 0) { api.register(any(), any()) }
    }

    @Test
    fun `signUpIfNeeded treats a server Success as the same path on reinstall`() = runTest {
        val store = mockk<AuthStore>(relaxed = true)
        val provider = mockk<UserIdProvider>(relaxed = true)
        val api = mockk<ServerAuthService>(relaxed = true)
        coEvery { store.readSnapshot() } returns null
        coEvery { provider.generateIfMissing() } returns "android-id-hash"
        coEvery { api.register("android-id-hash", any()) } returns
            ServerTokenResponse(accessToken = "rotated-jwt")

        val repository = AuthRepository(store, provider, api, networkMonitor)
        val snapshot = repository.signUpIfNeeded()

        assertNotNull(snapshot)
        assertEquals("rotated-jwt", snapshot!!.accessToken)
        assertEquals("android-id-hash", snapshot.userId)
        coVerify(exactly = 1) { store.writeSnapshot(snapshot) }
    }

    @Test
    fun `signUpIfNeeded on idempotent register call only invokes the server once`() = runTest {
        val store = mockk<AuthStore>(relaxed = true)
        val provider = mockk<UserIdProvider>(relaxed = true)
        val api = mockk<ServerAuthService>(relaxed = true)
        coEvery { store.readSnapshot() } returns null
        coEvery { provider.generateIfMissing() } returns "android-id-hash"
        coEvery { api.register(any(), any()) } returns
            ServerTokenResponse(accessToken = "new-jwt")

        val repository = AuthRepository(store, provider, api, networkMonitor)
        repository.signUpIfNeeded()

        coVerify(exactly = 1) { api.register(any(), any()) }
    }

    @Test
    fun `refreshToken returns null on auth failure`() = runTest {
        val store = mockk<AuthStore>(relaxed = true)
        val provider = mockk<UserIdProvider>(relaxed = true)
        val api = mockk<ServerAuthService>(relaxed = true)
        coEvery { provider.get() } returns "quiet-amber-fox"
        coEvery { store.readPassword() } returns "p".repeat(AuthDefaults.MIN_PASSWORD_LENGTH)
        coEvery { api.token(any(), any()) } throws fakeHttp401()

        val repository = AuthRepository(store, provider, api, networkMonitor)
        assertNull(repository.refreshToken())
    }

    @Test
    fun `refreshToken persists the new snapshot on success`() = runTest {
        val store = mockk<AuthStore>(relaxed = true)
        val provider = mockk<UserIdProvider>(relaxed = true)
        val api = mockk<ServerAuthService>(relaxed = true)
        coEvery { provider.get() } returns "quiet-amber-fox"
        coEvery { store.readPassword() } returns "p".repeat(AuthDefaults.MIN_PASSWORD_LENGTH)
        coEvery { api.token(any(), any()) } returns ServerTokenResponse(accessToken = "refreshed")

        val repository = AuthRepository(store, provider, api, networkMonitor)
        val snapshot = repository.refreshToken()

        assertNotNull(snapshot)
        assertEquals("refreshed", snapshot!!.accessToken)
        assertEquals("quiet-amber-fox", snapshot.userId)
        assertTrue("expiresAt should be in the future", snapshot.expiresAtEpochMs > System.currentTimeMillis())

        coVerify { store.writeSnapshot(snapshot) }
    }

    @Test
    fun `refreshToken returns null when no password is stored`() = runTest {
        val store = mockk<AuthStore>(relaxed = true)
        val provider = mockk<UserIdProvider>(relaxed = true)
        val api = mockk<ServerAuthService>(relaxed = true)
        coEvery { provider.get() } returns "quiet-amber-fox"
        coEvery { store.readPassword() } returns null

        val repository = AuthRepository(store, provider, api, networkMonitor)
        assertNull(repository.refreshToken())
    }

    private fun fakeHttp401(): Exception =
        retrofit2.HttpException(
            retrofit2.Response.error<Any>(
                401,
                """{"error":"InvalidCredentials"}"""
                    .toResponseBody("application/json".toMediaType())
            )
        )

}
