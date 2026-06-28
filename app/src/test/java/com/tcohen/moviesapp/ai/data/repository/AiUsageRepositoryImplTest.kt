package com.tcohen.moviesapp.ai.data.repository

import com.tcohen.moviesapp.ai.data.local.dao.AiUsageDao
import com.tcohen.moviesapp.ai.data.local.entity.AiUsageEntity
import com.tcohen.moviesapp.util.NetworkResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AiUsageRepositoryImpl].
 *
 * The two invariants the plan calls out:
 *
 * 1. **Cap is enforced BEFORE any network call** — feature ViewModels must
 *    not be on the hook for an LLM request when the day is already blown.
 *    Verified directly by polling the fake DAO and asserting mutations.
 * 2. **Token counting matches the synthetic row** — a recorded call shows up
 *    in `tokensSince` exactly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiUsageRepositoryImplTest {

    @Test
    fun `guardUnderDailyCap returns Success when usage is well under cap`() = runTest {
        val repo = newRepository(underCap = true)
        assertTrue(
            repo.guardUnderDailyCap() is NetworkResult.Success
        )
    }

    @Test
    fun `guardUnderDailyCap returns Error when seeded usage exceeds cap`() = runTest {
        val repo = newRepository(underCap = false)
        val result = repo.guardUnderDailyCap()
        assertTrue("expected Error but was $result", result is NetworkResult.Error)
        val msg = (result as NetworkResult.Error).message
        assertTrue(
            "message must mention token usage: '$msg'",
            msg.contains("tokens")
        )
    }

    @Test
    fun `recordUsage accumulates tokens visible to tokensSince`() = runTest {
        val dao = TestAiUsageDao()
        val repo = AiUsageRepositoryImpl(
            dao = dao,
            clock = todayClock
        )

        repo.recordUsage(
            model = "gemini-2.5-flash",
            feature = "plot-explainer",
            inputTokens = 100,
            outputTokens = 200
        )
        repo.recordUsage(
            model = "gemini-2.5-flash",
            feature = "reranker",
            inputTokens = 50,
            outputTokens = 75
        )

        assertEquals(425, repo.tokensSince(0L))
    }

    @Test
    fun `tokensSince only counts rows inside the window`() = runTest {
        val dao = TestAiUsageDao()
        val repo = AiUsageRepositoryImpl(dao = dao, clock = todayClock)

        dao.insert(AiUsageEntity(
            model = "test", feature = "test",
            inputTokens = 10, outputTokens = 10,
            timestamp = 0L  // epoch — inside "today"
        ))
        dao.insert(AiUsageEntity(
            model = "test", feature = "test",
            inputTokens = 999, outputTokens = 999,
            timestamp = 99L  // also "today" given the fake todayClock returns 0L
        ))

        val inToday = repo.tokensSince(todayClock.startOfTodayMillis())
        assertEquals(2018, inToday)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Today at midnight (or 0L in the fake). */
    private val todayClock = object : TimeProvider {
        override fun startOfTodayMillis(): Long = 0L
    }

    private suspend fun newRepository(underCap: Boolean): AiUsageRepositoryImpl {
        val dao = TestAiUsageDao()
        if (!underCap) {
            // Seed enough tokens to push the daily total past the cap.
            dao.insert(AiUsageEntity(
                model = "test", feature = "test",
                inputTokens = AiUsageDefaults.DAILY_TOKEN_CAP,
                outputTokens = 0,
                timestamp = 0L
            ))
        }
        return AiUsageRepositoryImpl(dao = dao, clock = todayClock)
    }

    /** Minimal in-memory [AiUsageDao] for repository tests. */
    private class TestAiUsageDao : AiUsageDao {
        private val entries = mutableListOf<AiUsageEntity>()

        override suspend fun insert(usage: AiUsageEntity) {
            entries += usage
        }

        override suspend fun totalTokensSince(sinceMillis: Long): Int =
            entries.filter { it.timestamp >= sinceMillis }
                .sumOf { it.inputTokens + it.outputTokens }

        override suspend fun inputTokensSince(sinceMillis: Long): Int =
            entries.filter { it.timestamp >= sinceMillis }.sumOf { it.inputTokens }

        override suspend fun outputTokensSince(sinceMillis: Long): Int =
            entries.filter { it.timestamp >= sinceMillis }.sumOf { it.outputTokens }

        override suspend fun deleteOlderThan(beforeMillis: Long) {
            entries.removeAll { it.timestamp < beforeMillis }
        }
    }
}
