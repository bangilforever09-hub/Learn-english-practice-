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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

@Entity(tableName = "vocab_progress")
data class VocabProgress(
    @PrimaryKey val wordId: Int,
    val box: Int,
    val nextReview: Long,
    val lastAnswered: Long,
    val correctCount: Int,
    val wrongCount: Int
)

@Dao
interface VocabProgressDao {
    @Query("SELECT * FROM vocab_progress")
    fun observeAll(): Flow<List<VocabProgress>>

    @Query("SELECT * FROM vocab_progress WHERE wordId = :id LIMIT 1")
    suspend fun getById(id: Int): VocabProgress?

    @Query("SELECT COUNT(*) FROM vocab_progress WHERE lastAnswered = :day")
    suspend fun countAnsweredOn(day: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: VocabProgress)
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `vocab_progress` (" +
                "`wordId` INTEGER NOT NULL, " +
                "`box` INTEGER NOT NULL, " +
                "`nextReview` INTEGER NOT NULL, " +
                "`lastAnswered` INTEGER NOT NULL, " +
                "`correctCount` INTEGER NOT NULL, " +
                "`wrongCount` INTEGER NOT NULL, " +
                "PRIMARY KEY(`wordId`))"
        )
    }
}

@Database(
    entities = [DailyProgress::class, VocabProgress::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dailyProgressDao(): DailyProgressDao
    abstract fun vocabProgressDao(): VocabProgressDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "learn_english.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
