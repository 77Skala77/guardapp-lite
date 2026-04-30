// data/EventLog.kt
package com.guardapp.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Журнал срабатываний. Хранит ТОЛЬКО метаданные.
 * Никаких изображений, скриншотов, URL — нет.
 */
@Entity(tableName = "log_entries")
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String,            // "triggered" / "closed" / "false_positive"
    val sensitivity: String? = null,  // soft/balanced/strict
    val score: Float? = null,
    val anchorType: String? = null,   // video/image/audio/image_and_audio
    val closedAfter60s: Boolean? = null,
    val isFalsePositive: Boolean = false
)

@Dao
interface LogEntryDao {
    @Query("SELECT * FROM log_entries ORDER BY timestamp DESC LIMIT 200")
    fun getRecent(): Flow<List<LogEntry>>

    @Query("SELECT COUNT(*) FROM log_entries WHERE eventType = 'triggered' AND timestamp >= :startMillis")
    suspend fun countTriggeredSince(startMillis: Long): Int

    @Insert
    suspend fun insert(entry: LogEntry): Long

    @Update
    suspend fun update(entry: LogEntry)

    @Query("UPDATE log_entries SET isFalsePositive = 1 WHERE id = :id")
    suspend fun markFalsePositive(id: Long)

    @Query("DELETE FROM log_entries")
    suspend fun deleteAll()

    @Query("SELECT * FROM log_entries WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): LogEntry?
}

@Database(entities = [LogEntry::class], version = 1, exportSchema = false)
abstract class GuardDatabase : RoomDatabase() {
    abstract fun logEntryDao(): LogEntryDao

    companion object {
        @Volatile private var instance: GuardDatabase? = null
        fun getInstance(context: Context): GuardDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    GuardDatabase::class.java,
                    "guardapp_lite.db"
                ).build().also { instance = it }
            }
    }
}

class EventLog(context: Context) {
    private val dao = GuardDatabase.getInstance(context).logEntryDao()

    fun recent(): Flow<List<LogEntry>> = dao.getRecent()

    suspend fun append(entry: LogEntry): Long = dao.insert(entry)

    suspend fun markFalsePositive(id: Long) = dao.markFalsePositive(id)

    suspend fun update(entry: LogEntry) = dao.update(entry)

    suspend fun findById(id: Long): LogEntry? = dao.findById(id)

    suspend fun todayTriggerCount(): Int {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
        }
        return dao.countTriggeredSince(cal.timeInMillis)
    }

    suspend fun clear() = dao.deleteAll()

    companion object {
        @Volatile private var instance: EventLog? = null
        fun getInstance(context: Context): EventLog =
            instance ?: synchronized(this) {
                instance ?: EventLog(context.applicationContext).also { instance = it }
            }
    }
}
