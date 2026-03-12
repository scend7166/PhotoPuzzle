package com.photopuzzle.app.data.db

import androidx.room.*
import com.photopuzzle.app.data.models.PuzzleResult
import kotlinx.coroutines.flow.Flow

@Dao
interface PuzzleResultDao {

    @Insert
    suspend fun insert(result: PuzzleResult): Long

    @Query("SELECT * FROM puzzle_results ORDER BY completedAt DESC")
    fun getAllResults(): Flow<List<PuzzleResult>>

    @Query("SELECT COUNT(*) FROM puzzle_results")
    fun getTotalSolved(): Flow<Int>

    @Query("SELECT AVG(completionTimeSeconds) FROM puzzle_results")
    fun getAverageCompletionTime(): Flow<Double?>

    @Query("SELECT AVG(CAST(completionTimeSeconds AS REAL) / pieceCount) FROM puzzle_results")
    fun getAverageTimePerPiece(): Flow<Double?>

    @Query("""
        SELECT pieceCount, COUNT(*) as timesSolved, AVG(completionTimeSeconds) as averageCompletionSeconds
        FROM puzzle_results
        GROUP BY pieceCount
        ORDER BY pieceCount ASC
    """)
    fun getStatsBySize(): Flow<List<SizeStatsEntity>>

    @Query("SELECT MIN(completionTimeSeconds) FROM puzzle_results WHERE pieceCount = :pieceCount")
    fun getBestTimeForSize(pieceCount: Int): Flow<Long?>

    @Query("SELECT SUM(completionTimeSeconds) FROM puzzle_results")
    fun getTotalTimeSeconds(): Flow<Long?>

    @Query("""
        SELECT pieceCount, MIN(completionTimeSeconds) as bestTimeSeconds
        FROM puzzle_results
        GROUP BY pieceCount
    """)
    fun getBestTimesBySize(): Flow<List<BestTimeEntity>>

    @Query("SELECT completedAt FROM puzzle_results ORDER BY completedAt DESC")
    fun getAllCompletionDates(): Flow<List<Long>>

    @Query("DELETE FROM puzzle_results")
    suspend fun deleteAll()
}

data class BestTimeEntity(
    val pieceCount: Int,
    val bestTimeSeconds: Long
)

data class SizeStatsEntity(
    val pieceCount: Int,
    val timesSolved: Int,
    val averageCompletionSeconds: Double
)
