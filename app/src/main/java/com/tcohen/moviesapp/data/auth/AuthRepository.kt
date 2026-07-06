package com.tcohen.moviesapp.data.auth

import com.tcohen.moviesapp.data.remote.server.api.ServerAuthService
import com.tcohen.moviesapp.data.remote.server.api.safeServerApiCall
import com.tcohen.moviesapp.data.user.UserIdProvider
import com.tcohen.moviesapp.util.NetworkMonitor
import com.tcohen.moviesapp.util.NetworkResult
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authStore: AuthStore,
    private val userIdProvider: UserIdProvider,
    private val serverAuth: ServerAuthService,
    private val networkMonitor: NetworkMonitor,
) {

    suspend fun ensureValidToken(): AuthSnapshot? {
        val current = authStore.readSnapshot() ?: return signUpIfNeeded()
        val now = System.currentTimeMillis()
        val graceMs = AuthDefaults.TOKEN_EXPIRY_GRACE_SECONDS * 1000L
        return if (current.expiresAtEpochMs > now + graceMs) {
            current
        } else {
            refreshToken() ?: current
        }
    }

    /**
     * Idempotent on-device bootstrap. On a first install:
     * 1. Derives a stable `userId` from `Settings.Secure.ANDROID_ID`
     *    (same device → same hash, survives uninstall/reinstall).
     * 2. Generates a fresh random password.
     * 3. POSTs `/auth/register`. Server upserts the row and returns a JWT
     *    (idempotent on `userId` — on a reinstall, the password is
     *    rotated, the user's favorites row is preserved).
     * 4. Caches the resulting snapshot.
     *
     * Never throws in the success path. Network failures surface as
     * [SignUpFailure] which the app swallows via `runCatching` so the
     * UI can run unauthenticated.
     */
    suspend fun signUpIfNeeded(): AuthSnapshot {
        authStore.readSnapshot()?.let { return it }

        val userId = userIdProvider.generateIfMissing()
        val password = generatePassword()
        authStore.writePassword(password)
        val result = safeServerApiCall(networkMonitor) {
            serverAuth.register(userId, password)
        }
        return when (result) {
            is NetworkResult.Success -> {
                val snapshot = snapshotFromToken(userId, result.data.accessToken)
                authStore.writeSnapshot(snapshot)
                snapshot
            }
            is NetworkResult.Error -> {
                throw SignUpFailure(
                    userId = userId,
                    error = result
                )
            }
        }
    }

    suspend fun refreshToken(): AuthSnapshot? {
        val userId = runCatching { userIdProvider.get() }.getOrNull() ?: return null
        val password = authStore.readPassword() ?: return null
        val result = safeServerApiCall(networkMonitor) {
            serverAuth.token(userId, password)
        }
        return when (result) {
            is NetworkResult.Success -> {
                val snapshot = snapshotFromToken(userId, result.data.accessToken)
                authStore.writeSnapshot(snapshot)
                snapshot
            }
            is NetworkResult.Error -> null
        }
    }

    private fun snapshotFromToken(userId: String, token: String): AuthSnapshot =
        AuthSnapshot(
            userId = userId,
            accessToken = token,
            expiresAtEpochMs = System.currentTimeMillis() + AuthDefaults.TOKEN_TTL_MS
        )

    private fun generatePassword(length: Int = AuthDefaults.MIN_PASSWORD_LENGTH): String {
        val pool = ALPHABET
        val rng = SecureRandom()
        val chars = CharArray(length)
        repeat(length) { idx -> chars[idx] = pool[rng.nextInt(pool.length)] }
        return String(chars)
    }

    class SignUpFailure(
        val userId: String,
        val error: NetworkResult.Error?,
    ) : RuntimeException(
        "Sign up failed for userId=$userId (httpCode=${error?.httpCode ?: -1})"
    )

    private companion object {

        private const val ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    }
}
