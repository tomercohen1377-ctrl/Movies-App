package com.tcohen.moviesapp.data.remote.server.api

import kotlin.coroutines.Continuation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST

class ServerApiServiceContractTest {

    private fun userVisibleParams(method: java.lang.reflect.Method) =
        method.parameters.filter { it.type != Continuation::class.java }

    @Test
    fun `getFavorites is annotated GET users favorites with one userId param`() {
        val method = ServerApiService::class.java.declaredMethods
            .firstOrNull { it.name == "getFavorites" }
        assertNotNull("getFavorites should exist", method)
        val get = method!!.getAnnotation(GET::class.java)
        assertNotNull("getFavorites should carry @GET", get)
        assertEquals(
            "users/{userId}/favorites",
            get.value
        )
        val params = userVisibleParams(method)
        assertEquals(1, params.size)
        assertEquals("userId", params[0].getAnnotation(retrofit2.http.Path::class.java).value)
    }

    @Test
    fun `isFavorite is annotated GET users favorites movieId with two path params`() {
        val method = ServerApiService::class.java.declaredMethods
            .firstOrNull { it.name == "isFavorite" }
        assertNotNull(method)
        val get = method!!.getAnnotation(GET::class.java)
        assertNotNull(get)
        assertEquals(
            "users/{userId}/favorites/{movieId}",
            get.value
        )
    }

    @Test
    fun `addFavorite is annotated POST users favorites movieId`() {
        val method = ServerApiService::class.java.declaredMethods
            .firstOrNull { it.name == "addFavorite" }
        assertNotNull(method)
        val post = method!!.getAnnotation(POST::class.java)
        assertNotNull(post)
        assertEquals(
            "users/{userId}/favorites/{movieId}",
            post.value
        )
    }

    @Test
    fun `removeFavorite is annotated DELETE users favorites movieId`() {
        val method = ServerApiService::class.java.declaredMethods
            .firstOrNull { it.name == "removeFavorite" }
        assertNotNull(method)
        val delete = method!!.getAnnotation(DELETE::class.java)
        assertNotNull(delete)
        assertEquals(
            "users/{userId}/favorites/{movieId}",
            delete.value
        )
    }
}
