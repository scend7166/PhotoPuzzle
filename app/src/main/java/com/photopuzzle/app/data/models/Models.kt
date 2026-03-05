package com.photopuzzle.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

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
    val statsBySize: List<SizeStats>
)

data class SizeStats(
    val pieceCount: Int,
    val timesSolved: Int,
    val averageCompletionSeconds: Double
)

val PUZZLE_SIZES = listOf(9, 25, 50, 100, 150, 200, 250)
