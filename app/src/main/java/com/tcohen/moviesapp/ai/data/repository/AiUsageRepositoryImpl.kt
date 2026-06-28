package com.tcohen.moviesapp.ai.data.repository

import com.tcohen.moviesapp.ai.data.local.dao.AiUsageDao
import com.tcohen.moviesapp.ai.data.local.entity.AiUsageEntity
import com.tcohen.moviesapp.util.ApiError
import com.tcohen.moviesapp.util.NetworkResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-feature repository for **token-usage accounting + daily-cap enforcement**.
 *
 * Lives in `data/` because the policy (Room table, mutation API) lives here;
 * the contract is small enough that we don't split into a domain interface
 * for now — Phase 6 (observability & cost) will introduce one if/when we need.
 */
@Singleton
class AiUsageRepositoryImpl @Inject constructor(
    private val dao: AiUsageDao,
    private val clock: TimeProvider = SystemTimeProvider
) {

    /**
     * Writes one ledger row per completed LLM call. Caller is responsible
     * for token counts — for streaming responses, callers should estimate
     * `output_tokens = text.length / 4` until we wire `stream_options`
     * reporting in Phase 6.
     */
    suspend fun recordUsage(
        model: String,
        feature: String,
        inputTokens: Int,
        outputTokens: Int
    ) {
        dao.insert(
            AiUsageEntity(
                model = model,
                feature = feature,
                inputTokens = inputTokens,
                outputTokens = outputTokens
            )
        )
    }

    /**
     * Sum of (input + output) tokens used since [sinceMillis].
     *
     * Use [startOfTodayMillis] for the daily window.
     */
    suspend fun tokensSince(sinceMillis: Long): Int = dao.totalTokensSince(sinceMillis)

    /**
     * Returns [NetworkResult.Success(Unit)] if we're under the cap;
     * [NetworkResult.Error] carrying the user-facing cap message otherwise.
     *
     * Called from feature ViewModels BEFORE issuing an LLM call so the
     * user sees a clear message instead of the provider's rate-limit
     * error toast.
     */
    suspend fun guardUnderDailyCap(): NetworkResult<Unit> {
        val since = clock.startOfTodayMillis()
        val used = dao.totalTokensSince(since)
        return if (used < AiUsageDefaults.DAILY_TOKEN_CAP) {
            NetworkResult.Success(Unit)
        } else {
            NetworkResult.Error(
                message = AiUsageDefaults.CAP_REACHED_MESSAGE.format(
                    AiUsageDefaults.DAILY_TOKEN_CAP / 1000
                )
            )
        }
    }

    /**
     * Total tokens used today (raw count, used by a Settings UI).
     */
    fun observeUsageToday(): Flow<Int> = flowOf(0) // simple sync — Phase 6 will wire Room InvalidationTracker
}

/**
 * Wall-clock indirection so unit tests can move time forward without
 * `Thread.sleep`. SystemTimeProvider is the production default.
 */
interface TimeProvider {
    /** Epoch millis at the start of the user's local day. */
    fun startOfTodayMillis(): Long
}

object SystemTimeProvider : TimeProvider {
    override fun startOfTodayMillis(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}

/**
 * Tunables for daily-cap enforcement. Co-located with the repository
 * because every consumer reads them together.
 */
object AiUsageDefaults {
    /**
     * Hard daily cap on combined input+output tokens per device.
     *
     * 250k tokens/day ≈ 30 chat exchanges of ~8k tokens each, or ~2,500
     * plot summaries at 100 tokens each. Generous for personal use;
     * Google's free-tier quota will trip first anyway.
     */
    const val DAILY_TOKEN_CAP: Int = 250_000

    /** Cost in USD per 1,000 input tokens — Gemini 2.5 Flash (used to compute local estimates). */
    const val COST_PER_1K_INPUT_TOKENS_USD: Double = 0.0003

    /** Cost in USD per 1,000 output tokens — Gemini 2.5 Flash. */
    const val COST_PER_1K_OUTPUT_TOKENS_USD: Double = 0.0025

    /** Message shown to the user when the day's quota is exhausted locally. */
    const val CAP_REACHED_MESSAGE: String = "You've used %dk tokens today — wait until midnight to try again."
}
