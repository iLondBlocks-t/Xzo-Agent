package com.xzo.agent.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "New chat",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
    val modelId: String = "groq/compound",
    val systemPrompt: String? = null,
    val archived: Boolean = false
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    /** user | assistant | system | tool */
    val role: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val model: String? = null,
    val provider: String? = null,
    val verified: Boolean = false,
    val verdict: String? = null,
    val error: Boolean = false,
    /** JSON-encoded list of ToolTraceDto */
    @ColumnInfo(name = "traceJson") val traceJson: String? = null,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val latencyMs: Long = 0,
    val attachmentsJson: String? = null
)

@Entity(tableName = "memories", indices = [Index(value = ["key"], unique = true)])
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "artifacts")
data class ArtifactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val name: String,
    val uri: String,
    val mime: String,
    val bytes: Int,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * A task Xzo runs on its own schedule (daily briefing, price watch, standup notes…).
 * Executed by WorkManager, the result is saved to a conversation and announced
 * through a notification.
 */
@Entity(tableName = "automations")
data class AutomationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val prompt: String,
    /** Local time of day, 0-23. */
    val hour: Int = 8,
    val minute: Int = 0,
    val enabled: Boolean = true,
    /** CHAT | AGENT | DEEP_RESEARCH */
    val mode: String = "AGENT",
    val notify: Boolean = true,
    val lastRunAt: Long = 0,
    val lastResult: String? = null,
    val lastOk: Boolean = true,
    val conversationId: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)
