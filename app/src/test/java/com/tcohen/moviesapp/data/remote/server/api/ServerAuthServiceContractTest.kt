package com.tcohen.moviesapp.data.remote.server.api

import kotlin.coroutines.Continuation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import retrofit2.http.GET
import retrofit2.http.POST

class ServerAuthServiceContractTest {

    private fun userVisibleParams(method: java.lang.reflect.Method) =
        method.parameters.filter { it.type != Continuation::class.java }

    @Test
    fun `register is POST auth register with X-User-Id and X-Password headers`() {
        val method = ServerAuthService::class.java.declaredMethods
            .firstOrNull { it.name == "register" }
        assertNotNull(method)
        val post = method!!.getAnnotation(POST::class.java)
        assertNotNull(post)
        assertEquals("auth/register", post.value)
        val params = userVisibleParams(method)
        assertEquals(2, params.size)
        assertEquals(
            "X-User-Id",
            params[0].getAnnotation(retrofit2.http.Header::class.java).value
        )
        assertEquals(
            "X-Password",
            params[1].getAnnotation(retrofit2.http.Header::class.java).value
        )
    }

    @Test
    fun `token is POST auth token with X-User-Id and X-Password headers`() {
        val method = ServerAuthService::class.java.declaredMethods
            .firstOrNull { it.name == "token" }
        assertNotNull(method)
        val post = method!!.getAnnotation(POST::class.java)
        assertNotNull(post)
        assertEquals("auth/token", post.value)
        val params = userVisibleParams(method)
        assertEquals(2, params.size)
        assertEquals(
            "X-User-Id",
            params[0].getAnnotation(retrofit2.http.Header::class.java).value
        )
        assertEquals(
            "X-Password",
            params[1].getAnnotation(retrofit2.http.Header::class.java).value
        )
    }

    @Test
    fun `whoami is GET auth whoami`() {
        val method = ServerAuthService::class.java.declaredMethods
            .firstOrNull { it.name == "whoami" }
        assertNotNull(method)
        val get = method!!.getAnnotation(GET::class.java)
        assertNotNull(get)
        assertEquals("auth/whoami", get.value)
    }
}
