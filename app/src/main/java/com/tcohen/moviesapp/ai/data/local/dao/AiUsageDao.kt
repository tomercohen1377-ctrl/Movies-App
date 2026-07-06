package com.tcohen.moviesapp.ai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tcohen.moviesapp.ai.data.local.entity.AiUsageEntity

@Dao
interface AiUsageDao {

    @Insert
    suspend fun insert(usage: AiUsageEntity)

    @Query("SELECT COALESCE(SUM(input_tokens + output_tokens), 0) FROM ai_usage WHERE timestamp >= :sinceMillis")
    suspend fun totalTokensSince(sinceMillis: Long): Int

    @Query("SELECT COALESCE(SUM(input_tokens), 0) FROM ai_usage WHERE timestamp >= :sinceMillis")
    suspend fun inputTokensSince(sinceMillis: Long): Int

    @Query("SELECT COALESCE(SUM(output_tokens), 0) FROM ai_usage WHERE timestamp >= :sinceMillis")
    suspend fun outputTokensSince(sinceMillis: Long): Int

    @Query("DELETE FROM ai_usage WHERE timestamp < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long)
}
