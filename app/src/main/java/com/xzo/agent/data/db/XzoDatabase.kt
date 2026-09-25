package com.xzo.agent.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        MemoryEntity::class,
        ArtifactEntity::class,
        AutomationEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class XzoDatabase : RoomDatabase() {
    abstract fun conversations(): ConversationDao
    abstract fun messages(): MessageDao
    abstract fun memories(): MemoryDao
    abstract fun artifacts(): ArtifactDao
    abstract fun automations(): AutomationDao

    companion object {

        /** v1 → v2: adds scheduled automations. Real migration, so nothing is ever wiped. */
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `automations` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `prompt` TEXT NOT NULL,
                        `hour` INTEGER NOT NULL,
                        `minute` INTEGER NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `mode` TEXT NOT NULL,
                        `notify` INTEGER NOT NULL,
                        `lastRunAt` INTEGER NOT NULL,
                        `lastResult` TEXT,
                        `lastOk` INTEGER NOT NULL,
                        `conversationId` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var INSTANCE: XzoDatabase? = null

        fun get(context: Context): XzoDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                XzoDatabase::class.java,
                "xzo-agent.db"
            )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
        }
    }
}
