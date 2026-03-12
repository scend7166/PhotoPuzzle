package com.photopuzzle.app.data.repository

import com.photopuzzle.app.data.db.PlaySessionDao
import com.photopuzzle.app.data.db.PuzzleResultDao
import com.photopuzzle.app.data.models.PlaySession
import com.photopuzzle.app.data.models.PuzzleResult
import com.photopuzzle.app.data.models.SizeStats
import com.photopuzzle.app.data.models.PUZZLE_SIZES
import com.photopuzzle.app.data.models.StatsOverview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepository @Inject constructor(
    private val dao: PuzzleResultDao,
    private val sessionDao: PlaySessionDao
) {
    suspend fun recordResult(pieceCount: Int, completionTimeSeconds: Long, imageUri: String) {
        dao.insert(PuzzleResult(
            pieceCount = pieceCount,
            completionTimeSeconds = completionTimeSeconds,
            imageUri = imageUri
        ))
    }

    suspend fun recordSession(pieceCount: Int, durationSeconds: Long) {
        if (durationSeconds > 0) {
            sessionDao.insert(PlaySession(pieceCount = pieceCount, durationSeconds = durationSeconds))
        }
    }

    fun getStatsOverview(): Flow<StatsOverview> = combine(
        dao.getTotalSolved(),
        dao.getAverageCompletionTime(),
        dao.getAverageTimePerPiece(),
        sessionDao.getTotalPlayTimeSeconds(),
        dao.getAllCompletionDates(),
        dao.getStatsBySize(),
        dao.getBestTimesBySize(),
        sessionDao.getTotalPlayTimeBySize()
    ) { args ->
        val total        = args[0] as Int
        val avgTime      = args[1] as Double?
        val avgPerPiece  = args[2] as Double?
        val totalTime    = args[3] as Long?
        @Suppress("UNCHECKED_CAST")
        val dates        = args[4] as List<Long>
        @Suppress("UNCHECKED_CAST")
        val bySize       = args[5] as List<com.photopuzzle.app.data.db.SizeStatsEntity>
        @Suppress("UNCHECKED_CAST")
        val bestTimes    = args[6] as List<com.photopuzzle.app.data.db.BestTimeEntity>
        @Suppress("UNCHECKED_CAST")
        val totalBySize  = args[7] as List<com.photopuzzle.app.data.db.SizeTotalTimeEntity>
        val bestTimeMap  = bestTimes.associate { it.pieceCount to it.bestTimeSeconds }
        val totalTimeMap = totalBySize.associate { it.pieceCount to it.totalSeconds }
        val bySizeMap    = bySize.associateBy { it.pieceCount }
        // Always emit a row for every puzzle size, zeroed if no games played
        val allSizeStats = PUZZLE_SIZES.map { size ->
            val entry = bySizeMap[size]
            SizeStats(
                pieceCount = size,
                timesSolved = entry?.timesSolved ?: 0,
                averageCompletionSeconds = entry?.averageCompletionSeconds ?: 0.0,
                bestTimeSeconds = bestTimeMap[size],
                totalPlayTimeSeconds = totalTimeMap[size] ?: 0L
            )
        }
        StatsOverview(
            totalSolved = total,
            averageCompletionSeconds = avgTime ?: 0.0,
            averageTimePerPiece = avgPerPiece ?: 0.0,
            totalTimeSeconds = totalTime ?: 0L,
            currentStreakDays = calculateStreak(dates),
            statsBySize = allSizeStats
        )
    }

    /** Counts consecutive calendar days (ending today or yesterday) with at least one solve. */
    private fun calculateStreak(completionTimestamps: List<Long>): Int {
        if (completionTimestamps.isEmpty()) return 0
        fun Long.toDay(): Long {
            val cal = Calendar.getInstance().apply { timeInMillis = this@toDay }
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0);      cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }
        val today = System.currentTimeMillis().toDay()
        val solvedDays = completionTimestamps.map { it.toDay() }.toSortedSet().toList().sortedDescending()
        if (solvedDays.first() < today - TimeUnit.DAYS.toMillis(1)) return 0 // last solve > 1 day ago
        var streak = 1
        for (i in 1 until solvedDays.size) {
            if (solvedDays[i - 1] - solvedDays[i] == TimeUnit.DAYS.toMillis(1)) streak++
            else break
        }
        return streak
    }

    fun getAllResults(): Flow<List<PuzzleResult>> = dao.getAllResults()

    suspend fun clearAll() {
        dao.deleteAll()
        sessionDao.deleteAll()
    }
}
