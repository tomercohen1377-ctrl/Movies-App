package com.tcohen.moviesapp.data.remote.server

internal object ServerDefaults {

    const val AUTH_HEADER = "Authorization"

    const val BEARER_PREFIX = "Bearer "

    const val USER_ID_HEADER = "X-User-Id"

    const val PASSWORD_HEADER = "X-Password"

    const val AUTH_HEADER_REDACTED_VALUE = "[REDACTED]"

    const val SERVER_TIMEOUT_MS = 5_000L
}
