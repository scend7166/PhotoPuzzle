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
import kotlinx.coroutines.runBlocking
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
        sessionDao.getTotalPlayTimeBySize(),
        sessionDao.getSessionCountBySize()
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
        @Suppress("UNCHECKED_CAST")
        val sessionCounts = args[8] as List<com.photopuzzle.app.data.db.SizeSessionCountEntity>

        val bestTimeMap     = bestTimes.associate { it.pieceCount to it.bestTimeSeconds }
        val totalTimeMap    = totalBySize.associate { it.pieceCount to it.totalSeconds }
        val sessionCountMap = sessionCounts.associate { it.pieceCount to it.sessionCount }
        val bySizeMap       = bySize.associateBy { it.pieceCount }

        // PB detection: for each size, check if the latest solve equals the best time
        // and there was a previous solve to beat (i.e. at least 2 solves).
        val recentPBSet = PUZZLE_SIZES.filter { size ->
            val entry = bySizeMap[size] ?: return@filter false
            if (entry.timesSolved < 2) return@filter false
            val best = bestTimeMap[size] ?: return@filter false
            val latestTime = runBlocking { dao.getLatestCompletionTimeForSize(size) } ?: return@filter false
            val secondBest = runBlocking { dao.getSecondBestTimeForSize(size) } ?: return@filter false
            latestTime == best && latestTime < secondBest
        }.toSet()

        val streakPair = calculateStreaks(dates)
        val totalSessions = sessionCounts.sumOf { it.sessionCount }
        val overallRate = if (totalSessions > 0) total.toFloat() / totalSessions else 0f

        val allSizeStats = PUZZLE_SIZES.map { size ->
            val entry    = bySizeMap[size]
            val sessions = sessionCountMap[size] ?: 0
            val solves   = entry?.timesSolved ?: 0
            SizeStats(
                pieceCount             = size,
                timesSolved            = solves,
                averageCompletionSeconds = entry?.averageCompletionSeconds ?: 0.0,
                bestTimeSeconds        = bestTimeMap[size],
                totalPlayTimeSeconds   = totalTimeMap[size] ?: 0L,
                completionRate         = if (sessions > 0) solves.toFloat() / sessions else 0f,
                isRecentPB             = size in recentPBSet
            )
        }

        StatsOverview(
            totalSolved            = total,
            averageCompletionSeconds = avgTime ?: 0.0,
            averageTimePerPiece    = avgPerPiece ?: 0.0,
            totalTimeSeconds       = totalTime ?: 0L,
            currentStreakDays      = streakPair.first,
            longestStreakDays      = streakPair.second,
            overallCompletionRate  = overallRate,
            statsBySize            = allSizeStats
        )
    }

    /** Returns Pair(currentStreak, longestStreak) in days. */
    private fun calculateStreaks(completionTimestamps: List<Long>): Pair<Int, Int> {
        if (completionTimestamps.isEmpty()) return Pair(0, 0)
        fun Long.toDay(): Long {
            val cal = Calendar.getInstance().apply { timeInMillis = this@toDay }
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0);      cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }
        val today = System.currentTimeMillis().toDay()
        val solvedDays = completionTimestamps.map { it.toDay() }.toSortedSet().toList().sortedDescending()

        // Current streak
        val current = if (solvedDays.first() < today - TimeUnit.DAYS.toMillis(1)) 0
        else {
            var s = 1
            for (i in 1 until solvedDays.size) {
                if (solvedDays[i - 1] - solvedDays[i] == TimeUnit.DAYS.toMillis(1)) s++ else break
            }
            s
        }


        // Longest streak — scan all days
        var longest = 1
        var run = 1
        val asc = solvedDays.sortedAscending()
        for (i in 1 until asc.size) {
            run = if (asc[i] - asc[i - 1] == TimeUnit.DAYS.toMillis(1)) run + 1 else 1
            if (run > longest) longest = run
        }

        return Pair(current, maxOf(current, longest))
    }

    private fun List<Long>.sortedAscending() = sorted()

    fun getAllResults(): Flow<List<PuzzleResult>> = dao.getAllResults()

    suspend fun clearAll() {
        dao.deleteAll()
        sessionDao.deleteAll()
    }
}
