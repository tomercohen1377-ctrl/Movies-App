package com.tcohen.moviesapp.data.remote.server.interceptor

import com.tcohen.moviesapp.data.auth.AuthStore
import com.tcohen.moviesapp.data.remote.server.ServerDefaults
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class ServerBearerInterceptor @Inject constructor(
    private val authStore: AuthStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val snapshot = authStore.readSnapshotBlocking()
            ?: return chain.proceed(original)
        val authed = original.newBuilder()
            .header(
                ServerDefaults.AUTH_HEADER,
                ServerDefaults.BEARER_PREFIX + snapshot.accessToken
            )
            .build()
        return chain.proceed(authed)
    }
}
