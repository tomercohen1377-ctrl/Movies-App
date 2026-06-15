package com.tcohen.moviesapp.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ApiErrorTest {

    // ── enum entries ──────────────────────────────────────────────────────────

    @Test
    fun ApiError_has_4_entries() {
        assertEquals(4, ApiError.entries.size)
    }

    @Test
    fun all_ApiError_entries_have_non_blank_messages() {
        ApiError.entries.forEach { error ->
            assertTrue(error.message.isNotBlank(), "${error.name} has blank message")
        }
    }

    @Test
    fun all_ApiError_messages_are_unique() {
        val messages = ApiError.entries.map { it.message }
        assertEquals(messages.size, messages.toSet().size)
    }

    // ── specific message content ──────────────────────────────────────────────

    @Test
    fun NO_CONNECTION_message_mentions_internet() {
        assertTrue(ApiError.NO_CONNECTION.message.contains("internet", ignoreCase = true))
    }

    @Test
    fun TIMEOUT_message_mentions_timeout_or_timed_out() {
        val msg = ApiError.TIMEOUT.message.lowercase()
        assertTrue(msg.contains("timeout") || msg.contains("timed out"))
    }

    @Test
    fun UNEXPECTED_message_mentions_unexpected() {
        assertTrue(ApiError.UNEXPECTED.message.contains("unexpected", ignoreCase = true))
    }

    // ── NetworkResult.Error integration ──────────────────────────────────────

    @Test
    fun NetworkResult_Error_with_ApiError_message_round_trips_correctly() {
        val result = NetworkResult.Error(ApiError.NO_CONNECTION.message)
        assertEquals(ApiError.NO_CONNECTION.message, result.message)
    }

    @Test
    fun two_different_ApiError_messages_produce_different_Error_messages() {
        val errorA = NetworkResult.Error(ApiError.NO_CONNECTION.message)
        val errorB = NetworkResult.Error(ApiError.TIMEOUT.message)
        assertNotEquals(errorA.message, errorB.message)
    }
}
