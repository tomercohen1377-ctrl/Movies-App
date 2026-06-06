package com.tcohen.moviesapp.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiErrorTest {

    // ── enum entries ──────────────────────────────────────────────────────────

    @Test
    fun `ApiError has 4 entries`() {
        assertEquals(4, ApiError.entries.size)
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

    // ── specific message content ──────────────────────────────────────────────

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

    // ── NetworkResult.Error integration ──────────────────────────────────────

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
}
