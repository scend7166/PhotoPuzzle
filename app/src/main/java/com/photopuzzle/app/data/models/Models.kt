package com.photopuzzle.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "play_sessions")
data class PlaySession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pieceCount: Int,
    val durationSeconds: Long,
    val playedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "puzzle_results")
data class PuzzleResult(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val completedAt: Long = System.currentTimeMillis(),
    val pieceCount: Int,
    val completionTimeSeconds: Long,
    val imageUri: String = ""
)

data class StatsOverview(
    val totalSolved: Int,
    val averageCompletionSeconds: Double,
    val averageTimePerPiece: Double,
    val totalTimeSeconds: Long,
    val currentStreakDays: Int,
    val statsBySize: List<SizeStats>
)

data class SizeStats(
    val pieceCount: Int,
    val timesSolved: Int,
    val averageCompletionSeconds: Double,
    val bestTimeSeconds: Long?,
    val totalPlayTimeSeconds: Long = 0L
)

val PUZZLE_SIZES = listOf(9, 25, 50, 100, 150, 200, 250)
