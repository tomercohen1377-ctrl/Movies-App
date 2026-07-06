package com.tcohen.moviesapp.data.remote.server.interceptor

import com.tcohen.moviesapp.data.auth.AuthDefaults
import com.tcohen.moviesapp.data.auth.AuthRepository
import com.tcohen.moviesapp.data.auth.AuthSnapshot
import com.tcohen.moviesapp.data.remote.server.ServerDefaults
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Provider

class ServerTokenAuthenticator @Inject constructor(

    private val authRepositoryProvider: Provider<AuthRepository>,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {

        if (responseCount(response) >= AuthDefaults.MAX_AUTH_REFRESH_ATTEMPTS + 1) {
            return null
        }

        if (response.code != 401) return null

        val refreshed: AuthSnapshot? = runBlocking {
            authRepositoryProvider.get().refreshToken()
        }
        return refreshed?.let { snap ->
            response.request.newBuilder()
                .header(
                    ServerDefaults.AUTH_HEADER,
                    ServerDefaults.BEARER_PREFIX + snap.accessToken
                )
                .build()
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
