package com.tcohen.moviesapp.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthStore @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        AuthDefaults.AUTH_PREFS_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    suspend fun readSnapshot(): AuthSnapshot? = withContext(Dispatchers.IO) {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return@withContext null
        val token = prefs.getString(AuthDefaults.ACCESS_TOKEN_KEY, null)
            ?: return@withContext null
        val expires = prefs.getLong(AuthDefaults.EXPIRES_AT_KEY, 0L)
        AuthSnapshot(userId = userId, accessToken = token, expiresAtEpochMs = expires)
    }

    fun readSnapshotBlocking(): AuthSnapshot? {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val token = prefs.getString(AuthDefaults.ACCESS_TOKEN_KEY, null) ?: return null
        val expires = prefs.getLong(AuthDefaults.EXPIRES_AT_KEY, 0L)
        return AuthSnapshot(userId = userId, accessToken = token, expiresAtEpochMs = expires)
    }

    suspend fun writeSnapshot(snapshot: AuthSnapshot) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(KEY_USER_ID, snapshot.userId)
            .putString(AuthDefaults.ACCESS_TOKEN_KEY, snapshot.accessToken)
            .putLong(AuthDefaults.EXPIRES_AT_KEY, snapshot.expiresAtEpochMs)
            .commit()
    }

    suspend fun readPassword(): String? = withContext(Dispatchers.IO) {
        prefs.getString(AuthDefaults.PASSWORD_KEY, null)
    }

    suspend fun writePassword(password: String) = withContext(Dispatchers.IO) {
        require(password.length >= AuthDefaults.MIN_PASSWORD_LENGTH) {
            "password must be at least ${AuthDefaults.MIN_PASSWORD_LENGTH} chars"
        }
        prefs.edit().putString(AuthDefaults.PASSWORD_KEY, password).commit()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().clear().commit()
    }

    private companion object {

        const val KEY_USER_ID = "userId"
    }
}
