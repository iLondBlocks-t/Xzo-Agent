package com.xzo.agent.data.repo

import com.xzo.agent.agent.AgentEngine
import com.xzo.agent.agent.FileBridge
import com.xzo.agent.core.SettingsRepository
import com.xzo.agent.data.db.ArtifactEntity
import com.xzo.agent.data.db.ConversationEntity
import com.xzo.agent.data.db.MessageEntity
import com.xzo.agent.data.db.XzoDatabase
import com.xzo.agent.data.remote.WireMessage
import kotlinx.coroutines.flow.Flow

class ChatRepository(
    private val db: XzoDatabase,
    private val engine: AgentEngine,
    private val settings: SettingsRepository,
    private val files: FileBridge
) {
    fun conversations(): Flow<List<ConversationEntity>> = db.conversations().observeAll()
    fun messages(cid: Long): Flow<List<MessageEntity>> = db.messages().observeFor(cid)
    fun conversation(cid: Long): Flow<ConversationEntity?> = db.conversations().observeById(cid)
    fun artifacts(): Flow<List<ArtifactEntity>> = db.artifacts().observeAll()

    suspend fun newConversation(modelId: String): Long =
        db.conversations().insert(ConversationEntity(modelId = modelId))

    suspend fun ensureConversation(cid: Long?, modelId: String): Long =
        cid?.takeIf { it > 0 && db.conversations().byId(it) != null } ?: newConversation(modelId)

    suspend fun addMessage(m: MessageEntity): Long {
        val id = db.messages().insert(m)
        db.conversations().touch(m.conversationId)
        return id
    }

    suspend fun updateMessage(m: MessageEntity) = db.messages().update(m)
    suspend fun deleteMessage(id: Long) = db.messages().delete(id)
    suspend fun clearConversation(cid: Long) = db.messages().clearConversation(cid)
    suspend fun deleteConversation(cid: Long) = db.conversations().delete(cid)
    suspend fun rename(cid: Long, title: String) = db.conversations().rename(cid, title)
    suspend fun setPinned(cid: Long, pinned: Boolean) = db.conversations().setPinned(cid, pinned)
    suspend fun setModel(cid: Long, modelId: String) = db.conversations().setModel(cid, modelId)
    suspend fun searchMessages(q: String) = db.messages().search(q)
    suspend fun recordArtifact(a: ArtifactEntity) = db.artifacts().insert(a)
    suspend fun deleteArtifact(id: Long) = db.artifacts().delete(id)

    /** Deletes [fromId] and everything after it in the conversation. */
    suspend fun truncateFrom(cid: Long, fromId: Long) {
        db.messages().listFor(cid).filter { it.id >= fromId }.forEach { db.messages().delete(it.id) }
    }

    /** Copies the conversation up to (and including) [uptoId] into a brand new chat. */
    suspend fun branch(cid: Long, uptoId: Long): Long {
        val src = db.conversations().byId(cid) ?: return cid
        val msgs = db.messages().listFor(cid).filter { it.id <= uptoId }
        val newId = db.conversations().insert(
            ConversationEntity(
                title = "${src.title} (branch)",
                modelId = src.modelId,
                systemPrompt = src.systemPrompt
            )
        )
        msgs.forEach { m -> db.messages().insert(m.copy(id = 0, conversationId = newId)) }
        return newId
    }

    suspend fun stats(): Triple<Int, Int, Int> = Triple(
        db.conversations().count(),
        db.messages().count(),
        db.messages().totalTokens() ?: 0
    )

    /** Build the wire history (excluding the message currently being sent). */
    suspend fun wireHistory(cid: Long, window: Int): List<WireMessage> =
        db.messages().listFor(cid)
            .filter { it.role == "user" || (it.role == "assistant" && !it.error && it.content.isNotBlank()) }
            .takeLast(window)
            .map { WireMessage(it.role, it.content) }

    suspend fun maybeAutoTitle(cid: Long, firstMessage: String) {
        val c = db.conversations().byId(cid) ?: return
        if (c.title != "New chat") return
        val title = engine.titleFor(firstMessage) ?: firstMessage.take(40)
        db.conversations().rename(cid, title.ifBlank { "New chat" })
    }

    /** Export a conversation as markdown through SAF. */
    suspend fun exportConversation(cid: Long): FileBridge.SavedFile? {
        val convo = db.conversations().byId(cid) ?: return null
        val msgs = db.messages().listFor(cid)
        val md = buildString {
            appendLine("# ${convo.title}")
            appendLine()
            appendLine("_Exported from Xzo Agent · ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())}_")
            appendLine()
            msgs.forEach { m ->
                val who = when (m.role) {
                    "user" -> "🧑 You"
                    "assistant" -> "🤖 Xzo${m.model?.let { " ($it)" } ?: ""}"
                    else -> m.role
                }
                appendLine("## $who")
                appendLine()
                appendLine(m.content)
                appendLine()
            }
        }
        val safeName = convo.title.replace(Regex("[^A-Za-z0-9\\u0600-\\u06FF _-]"), "").trim().ifBlank { "chat" }
        return files.createDocument("$safeName.md", "text/markdown", md)
    }
}
