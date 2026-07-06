package com.tcohen.moviesapp.data.user

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Provides the userId for talking to the favorites server.
 *
 * The userId is derived from
 * [Settings.Secure.ANDROID_ID](https://developer.android.com/reference/android/provider/Settings.Secure#ANDROID_ID)
 * — a value that is constant per `applicationId + signingKey + user + device`
 * (since Android 8) and **survives app uninstall**. That last bit is
 * the key property: uninstall + reinstall on the same device yields the
 * same userId, so the server-side favorites list is preserved.
 *
 * The ANDROID_ID is salted + hashed before storage and use, so the
 * raw device identifier never leaks into the network or
 * SharedPreferences.
 *
 * Previously installed copies of this app stored a `UUID.randomUUID()`
 * value — those userIds are not migrated; they remain on the server
 * as orphan accounts. The new mechanism takes effect for any
 * uninstall + reinstall (or fresh install).
 */
@Singleton
open class UserIdProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    @UserIdPrefs private val prefs: SharedPreferences,
) {

    suspend fun get(): String = withContext(Dispatchers.IO) {
        prefs.getString(KEY_USER_ID, null)
            ?: error("UserIdProvider.get(): no userId persisted yet. Call generateIfMissing() first.")
    }

    fun getBlocking(): String =
        prefs.getString(KEY_USER_ID, null)
            ?: error("UserIdProvider.getBlocking(): no userId persisted yet - interceptor called pre-sign-up?")

    suspend fun set(value: String) = withContext(Dispatchers.IO) {
        require(value.isNotBlank()) { "userId must not be blank" }
        prefs.edit().putString(KEY_USER_ID, value).commit()
    }

    suspend fun generateIfMissing(): String = withContext(Dispatchers.IO) {
        prefs.getString(KEY_USER_ID, null) ?: deriveFromAndroidId().also { persist(it) }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_USER_ID).commit()
    }

    /**
     * Salted + hashed ANDROID_ID. Salting scopes the derivation to
     * this app — bumping the salt version ("v1" → "v2") is a manual
     * opt-in to invalidate all derived userIds.
     */
    private fun deriveFromAndroidId(): String {
        val raw = resolveAndroidId()
            ?: error("Device returned no ANDROID_ID — cannot derive userId.")
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("$SALT$raw".toByteArray(Charsets.UTF_8))
        // Hex-encode the first 16 bytes into 32 chars. Server's
        // users.user_id is VARCHAR(64) — plenty of room.
        val hex = StringBuilder(32)
        for (i in 0 until 16) {
            val v = bytes[i].toInt() and 0xff
            if (v < 16) hex.append('0')
            hex.append(Integer.toHexString(v))
        }
        return hex.toString()
    }

    /** Made overridable so tests can supply a stand-in ANDROID_ID. */
    protected open fun resolveAndroidId(): String? =
        android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID,
        )

    private fun persist(value: String) {
        prefs.edit().putString(KEY_USER_ID, value).commit()
    }

    companion object {

        const val KEY_USER_ID = "userId"

        const val USER_ID_PREFS_FILE = "server_user_id"

        private const val SALT = "moviesapp:userid:v1"
    }
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UserIdPrefs
