package com.xzo.agent.data.repo

import com.xzo.agent.agent.FileBridge
import com.xzo.agent.core.SettingsRepository
import com.xzo.agent.data.db.ConversationEntity
import com.xzo.agent.data.db.MemoryEntity
import com.xzo.agent.data.db.MessageEntity
import com.xzo.agent.data.db.XzoDatabase
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Whole-app backup and restore through SAF — every chat, message, agent trace and
 * memory in one portable JSON file. No cloud, no account: the user owns the file.
 */
class BackupManager(
    private val db: XzoDatabase,
    private val files: FileBridge,
    private val settings: SettingsRepository
) {

    @Serializable
    data class BackupMessage(
        val role: String,
        val content: String,
        val createdAt: Long,
        val model: String? = null,
        val provider: String? = null,
        val verified: Boolean = false,
        val verdict: String? = null,
        val traceJson: String? = null,
        val promptTokens: Int = 0,
        val completionTokens: Int = 0,
        val latencyMs: Long = 0
    )

    @Serializable
    data class BackupConversation(
        val title: String,
        val createdAt: Long,
        val updatedAt: Long,
        val pinned: Boolean,
        val modelId: String,
        val messages: List<BackupMessage>
    )

    @Serializable
    data class BackupMemory(val key: String, val value: String, val updatedAt: Long)

    @Serializable
    data class BackupFile(
        val app: String = "Xzo Agent",
        val schema: Int = 1,
        val exportedAt: Long = System.currentTimeMillis(),
        val conversations: List<BackupConversation> = emptyList(),
        val memories: List<BackupMemory> = emptyList()
    )

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun export(): FileBridge.SavedFile? {
        val conversations = db.conversations().observeAllOnce().map { c ->
            BackupConversation(
                title = c.title,
                createdAt = c.createdAt,
                updatedAt = c.updatedAt,
                pinned = c.pinned,
                modelId = c.modelId,
                messages = db.messages().listFor(c.id).map { m ->
                    BackupMessage(
                        role = m.role,
                        content = m.content,
                        createdAt = m.createdAt,
                        model = m.model,
                        provider = m.provider,
                        verified = m.verified,
                        verdict = m.verdict,
                        traceJson = m.traceJson,
                        promptTokens = m.promptTokens,
                        completionTokens = m.completionTokens,
                        latencyMs = m.latencyMs
                    )
                }
            )
        }
        val memories = db.memories().all().map { BackupMemory(it.key, it.value, it.updatedAt) }
        val payload = BackupFile(conversations = conversations, memories = memories)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        return files.createDocument(
            "xzo-backup-$stamp.json",
            "application/json",
            json.encodeToString(BackupFile.serializer(), payload)
        )
    }

    data class ImportResult(val conversations: Int, val messages: Int, val memories: Int, val error: String? = null)

    suspend fun import(): ImportResult {
        val loaded = files.openDocument(arrayOf("application/json", "text/plain", "*/*"), 4_000_000)
            ?: return ImportResult(0, 0, 0, "cancelled")
        val parsed = runCatching { json.decodeFromString(BackupFile.serializer(), loaded.text) }
            .getOrElse { return ImportResult(0, 0, 0, "not a valid Xzo backup: ${it.message}") }

        var msgCount = 0
        parsed.conversations.forEach { c ->
            val id = db.conversations().insert(
                ConversationEntity(
                    title = c.title,
                    createdAt = c.createdAt,
                    updatedAt = c.updatedAt,
                    pinned = c.pinned,
                    modelId = com.xzo.agent.data.remote.ModelCatalog.migrate(c.modelId)
                )
            )
            c.messages.forEach { m ->
                db.messages().insert(
                    MessageEntity(
                        conversationId = id,
                        role = m.role,
                        content = m.content,
                        createdAt = m.createdAt,
                        model = m.model,
                        provider = m.provider,
                        verified = m.verified,
                        verdict = m.verdict,
                        traceJson = m.traceJson,
                        promptTokens = m.promptTokens,
                        completionTokens = m.completionTokens,
                        latencyMs = m.latencyMs
                    )
                )
                msgCount++
            }
        }
        parsed.memories.forEach {
            db.memories().put(MemoryEntity(key = it.key, value = it.value, updatedAt = it.updatedAt))
        }
        return ImportResult(parsed.conversations.size, msgCount, parsed.memories.size)
    }
}
