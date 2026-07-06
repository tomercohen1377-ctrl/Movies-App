package com.tcohen.moviesapp.ai.data.repository

import com.tcohen.moviesapp.ai.data.local.dao.AiUsageDao
import com.tcohen.moviesapp.ai.data.local.entity.AiUsageEntity
import com.tcohen.moviesapp.util.NetworkResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiUsageRepositoryImpl @Inject constructor(
    private val dao: AiUsageDao,
    private val clock: TimeProvider = SystemTimeProvider
) {

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

    suspend fun tokensSince(sinceMillis: Long): Int = dao.totalTokensSince(sinceMillis)

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

    fun observeUsageToday(): Flow<Int> = flowOf(0)
}

interface TimeProvider {

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

object AiUsageDefaults {

    const val DAILY_TOKEN_CAP: Int = 250_000

    const val COST_PER_1K_INPUT_TOKENS_USD: Double = 0.0003

    const val COST_PER_1K_OUTPUT_TOKENS_USD: Double = 0.0025

    const val CAP_REACHED_MESSAGE: String = "You've used %dk tokens today — wait until midnight to try again."
}
