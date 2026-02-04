package com.mymate.auto.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mymate.auto.data.model.*

@Database(
    entities = [
        ChatMessage::class, 
        ConversationMessage::class,
        ParkingLocation::class,
        Reminder::class,
        Memory::class
    ], 
    version = 3, 
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun conversationDao(): ConversationDao
    abstract fun parkingDao(): ParkingDao
    abstract fun reminderDao(): ReminderDao
    abstract fun memoryDao(): MemoryDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        // Migration from v1 to v2 - add conversation_messages table
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS conversation_messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        content TEXT NOT NULL,
                        isFromUser INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL,
                        topic TEXT
                    )
                """.trimIndent())
            }
        }
        
        // Migration from v2 to v3 - add parking, reminders, memories tables
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Parking locations table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS parking_locations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        accuracy REAL NOT NULL,
                        address TEXT,
                        note TEXT,
                        photoPath TEXT,
                        timestamp INTEGER NOT NULL,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        sentToTelegram INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                
                // Reminders table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS reminders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT,
                        triggerTime INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        isCompleted INTEGER NOT NULL DEFAULT 0,
                        isSynced INTEGER NOT NULL DEFAULT 0,
                        cronJobId TEXT,
                        repeatType TEXT NOT NULL DEFAULT 'NONE'
                    )
                """.trimIndent())
                
                // Memories table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS memories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        content TEXT NOT NULL,
                        category TEXT NOT NULL DEFAULT 'GENERAL',
                        tags TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        isSynced INTEGER NOT NULL DEFAULT 0,
                        source TEXT NOT NULL DEFAULT 'app'
                    )
                """.trimIndent())
            }
        }
        
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mymate_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

/**
 * Type converters for Room
 */
class Converters {
    @androidx.room.TypeConverter
    fun fromRepeatType(value: RepeatType): String = value.name
    
    @androidx.room.TypeConverter
    fun toRepeatType(value: String): RepeatType = RepeatType.valueOf(value)
    
    @androidx.room.TypeConverter
    fun fromMemoryCategory(value: MemoryCategory): String = value.name
    
    @androidx.room.TypeConverter
    fun toMemoryCategory(value: String): MemoryCategory = MemoryCategory.valueOf(value)
}
