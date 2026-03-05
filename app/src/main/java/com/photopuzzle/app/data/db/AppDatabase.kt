package com.photopuzzle.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.photopuzzle.app.data.models.PuzzleResult

@Database(entities = [PuzzleResult::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun puzzleResultDao(): PuzzleResultDao
}
