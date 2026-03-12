package com.photopuzzle.app.data

import android.content.Context
import androidx.room.Room
import com.photopuzzle.app.data.db.AppDatabase
import com.photopuzzle.app.data.db.PlaySessionDao
import com.photopuzzle.app.data.db.PuzzleResultDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "photopuzzle.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun providePuzzleResultDao(db: AppDatabase): PuzzleResultDao = db.puzzleResultDao()

    @Provides
    fun providePlaySessionDao(db: AppDatabase): PlaySessionDao = db.playSessionDao()
}
