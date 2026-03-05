package com.photopuzzle.app.data.repository

import com.photopuzzle.app.data.db.PuzzleResultDao
import com.photopuzzle.app.data.models.PuzzleResult
import com.photopuzzle.app.data.models.SizeStats
import com.photopuzzle.app.data.models.StatsOverview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepository @Inject constructor(
    private val dao: PuzzleResultDao
) {
    suspend fun recordResult(pieceCount: Int, completionTimeSeconds: Long, imageUri: String) {
        dao.insert(PuzzleResult(
            pieceCount = pieceCount,
            completionTimeSeconds = completionTimeSeconds,
            imageUri = imageUri
        ))
    }

    fun getStatsOverview(): Flow<StatsOverview> = combine(
        dao.getTotalSolved(),
        dao.getAverageCompletionTime(),
        dao.getAverageTimePerPiece(),
        dao.getStatsBySize()
    ) { total, avgTime, avgPerPiece, bySize ->
        StatsOverview(
            totalSolved = total,
            averageCompletionSeconds = avgTime ?: 0.0,
            averageTimePerPiece = avgPerPiece ?: 0.0,
            statsBySize = bySize.map { SizeStats(it.pieceCount, it.timesSolved, it.averageCompletionSeconds) }
        )
    }

    fun getAllResults(): Flow<List<PuzzleResult>> = dao.getAllResults()

    suspend fun clearAll() = dao.deleteAll()
}
