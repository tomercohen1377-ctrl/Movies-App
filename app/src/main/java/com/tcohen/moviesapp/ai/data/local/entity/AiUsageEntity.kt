package com.tcohen.moviesapp.ai.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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
