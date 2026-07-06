package com.tcohen.moviesapp.data.auth

internal object AuthDefaults {

    const val AUTH_PREFS_FILE = "auth_credentials"

    const val PASSWORD_KEY = "password"

    const val ACCESS_TOKEN_KEY = "access_token"

    const val EXPIRES_AT_KEY = "expires_at"

    const val TOKEN_TTL_MS: Long = 24L * 60 * 60 * 1000

    const val TOKEN_EXPIRY_GRACE_SECONDS: Long = 60L

    const val MIN_PASSWORD_LENGTH = 20

    @Suppress("unused")
    private const val RESERVED_FOR_FUTURE_LOGIN_FLOW = 0

    const val MAX_AUTH_REFRESH_ATTEMPTS = 1
}
