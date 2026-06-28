package com.tcohen.moviesapp.ai.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per LLM call — gate for the per-day token cap and the in-app
 * "You've used X of Y tokens today" surface.
 *
 * Kept tiny on purpose:
 * - No PII (prompt text intentionally NOT stored — only token counts).
 * - Indexed on [timestamp] so today/total aggregations don't full-scan
 *   the table once usage grows.
 */
@Entity(
    tableName = "ai_usage",
    indices = [Index(value = ["timestamp"])]
)
data class AiUsageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val model: String,
    @ColumnInfo(name = "feature") val feature: String,
    @ColumnInfo(name = "input_tokens") val inputTokens: Int,
    @ColumnInfo(name = "output_tokens") val outputTokens: Int,
    val timestamp: Long = System.currentTimeMillis()
)
