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
        ArtifactEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class XzoDatabase : RoomDatabase() {
    abstract fun conversations(): ConversationDao
    abstract fun messages(): MessageDao
    abstract fun memories(): MemoryDao
    abstract fun artifacts(): ArtifactDao

    companion object {
        @Volatile
        private var INSTANCE: XzoDatabase? = null

        fun get(context: Context): XzoDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                XzoDatabase::class.java,
                "xzo-agent.db"
            )
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
        }
    }
}
