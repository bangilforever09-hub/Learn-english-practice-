package com.example.learnenglish.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "daily_progress")
data class DailyProgress(
    @PrimaryKey val date: String,
    val conversationDone: Boolean = false,
    val vocabDone: Boolean = false,
    val noteDone: Boolean = false
)

@Dao
interface DailyProgressDao {
    @Query("SELECT * FROM daily_progress ORDER BY date DESC LIMIT 400")
    fun observeRecent(): Flow<List<DailyProgress>>

    @Query("SELECT * FROM daily_progress WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): DailyProgress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DailyProgress)
}

@Database(entities = [DailyProgress::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dailyProgressDao(): DailyProgressDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "learn_english.db"
                ).build().also { instance = it }
            }
    }
}
