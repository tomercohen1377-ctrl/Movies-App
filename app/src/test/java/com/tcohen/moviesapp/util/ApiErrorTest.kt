package com.tcohen.moviesapp.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiErrorTest {

    @Test
    fun `ApiError has 8 entries (4 TMDB + 4 LLM)`() {

        assertEquals(8, ApiError.entries.size)
    }

    @Test
    fun `all ApiError entries have non-blank messages`() {
        ApiError.entries.forEach { error ->
            assertTrue("${error.name} has blank message", error.message.isNotBlank())
        }
    }

    @Test
    fun `all ApiError messages are unique`() {
        val messages = ApiError.entries.map { it.message }
        assertEquals(messages.size, messages.toSet().size)
    }

    @Test
    fun `NO_CONNECTION message mentions internet`() {
        assertTrue(ApiError.NO_CONNECTION.message.contains("internet", ignoreCase = true))
    }

    @Test
    fun `TIMEOUT message mentions timeout or timed out`() {
        val msg = ApiError.TIMEOUT.message.lowercase()
        assertTrue(msg.contains("timeout") || msg.contains("timed out"))
    }

    @Test
    fun `UNEXPECTED message mentions unexpected`() {
        assertTrue(ApiError.UNEXPECTED.message.contains("unexpected", ignoreCase = true))
    }

    @Test
    fun `UNAUTHORIZED message mentions auth or api key`() {
        val msg = ApiError.UNAUTHORIZED.message.lowercase()
        assertTrue(
            "UNAUTHORIZED should hint at credential problem, was: '${ApiError.UNAUTHORIZED.message}'",
            msg.contains("auth") || msg.contains("api key") || msg.contains("key")
        )
    }

    @Test
    fun `RATE_LIMITED message mentions slowing down`() {
        val msg = ApiError.RATE_LIMITED.message.lowercase()
        assertTrue(
            "RATE_LIMITED should hint at throttling, was: '${ApiError.RATE_LIMITED.message}'",
            msg.contains("slow") || msg.contains("too many") || msg.contains("rate")
        )
    }

    @Test
    fun `LLM_UNAVAILABLE message mentions unavailability`() {
        assertTrue(ApiError.LLM_UNAVAILABLE.message.contains("unavailable", ignoreCase = true))
    }

    @Test
    fun `CONTEXT_TOO_LONG message mentions length`() {
        val msg = ApiError.CONTEXT_TOO_LONG.message.lowercase()
        assertTrue(
            "CONTEXT_TOO_LONG should hint at length, was: '${ApiError.CONTEXT_TOO_LONG.message}'",
            msg.contains("long") || msg.contains("length") || msg.contains("context")
        )
    }

    @Test
    fun `NetworkResult Error with ApiError message round-trips correctly`() {
        val result = NetworkResult.Error(ApiError.NO_CONNECTION.message)
        assertEquals(ApiError.NO_CONNECTION.message, result.message)
    }

    @Test
    fun `two different ApiError messages produce different Error messages`() {
        val errorA = NetworkResult.Error(ApiError.NO_CONNECTION.message)
        val errorB = NetworkResult.Error(ApiError.TIMEOUT.message)
        assertNotEquals(errorA.message, errorB.message)
    }

    @Test
    fun `LLM UNAUTHORIZED and RATE_LIMITED are not interchangeable`() {
        assertNotEquals(
            NetworkResult.Error(ApiError.UNAUTHORIZED.message).message,
            NetworkResult.Error(ApiError.RATE_LIMITED.message).message
        )
    }
}
