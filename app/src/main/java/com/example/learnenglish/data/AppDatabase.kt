package com.example.learnenglish.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
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

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val text: String,
    val extra: String,
    val createdDay: Long,
    val reviewsDone: Int,
    val nextReview: Long
)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY createdDay DESC, id DESC")
    fun observeAll(): Flow<List<Note>>

    @Query("SELECT COUNT(*) FROM notes WHERE nextReview <= :today")
    suspend fun countDue(today: Long): Int

    @Insert
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Delete
    suspend fun delete(note: Note)
}

@Entity(tableName = "chat_stats")
data class ChatStat(
    @PrimaryKey val day: Long,
    val userMessages: Int = 0,
    val userWords: Int = 0,
    val mistakes: Int = 0,
    val goodMessages: Int = 0
)

@Dao
interface ChatStatDao {
    @Query("SELECT * FROM chat_stats ORDER BY day DESC LIMIT 400")
    fun observeAll(): Flow<List<ChatStat>>

    @Query("SELECT * FROM chat_stats WHERE day = :day LIMIT 1")
    suspend fun getByDay(day: Long): ChatStat?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ChatStat)
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

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `notes` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`text` TEXT NOT NULL, " +
                "`extra` TEXT NOT NULL, " +
                "`createdDay` INTEGER NOT NULL, " +
                "`reviewsDone` INTEGER NOT NULL, " +
                "`nextReview` INTEGER NOT NULL)"
        )
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `chat_stats` (" +
                "`day` INTEGER NOT NULL, " +
                "`userMessages` INTEGER NOT NULL, " +
                "`userWords` INTEGER NOT NULL, " +
                "`mistakes` INTEGER NOT NULL, " +
                "`goodMessages` INTEGER NOT NULL, " +
                "PRIMARY KEY(`day`))"
        )
    }
}

@Database(
    entities = [DailyProgress::class, VocabProgress::class, Note::class, ChatStat::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dailyProgressDao(): DailyProgressDao
    abstract fun vocabProgressDao(): VocabProgressDao
    abstract fun noteDao(): NoteDao
    abstract fun chatStatDao(): ChatStatDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "learn_english.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
    }
}
