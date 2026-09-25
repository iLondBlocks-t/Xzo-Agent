package com.xzo.agent.agent

import com.xzo.agent.data.db.MemoryDao
import com.xzo.agent.data.db.MemoryEntity

/** Long-term key/value memory the agent can write to and recall across chats. */
class MemoryStore(private val dao: MemoryDao) {

    suspend fun remember(key: String, value: String) {
        dao.put(MemoryEntity(key = key.trim().lowercase(), value = value.trim()))
    }

    suspend fun recall(key: String): String? = dao.get(key.trim().lowercase())?.value

    suspend fun search(query: String): List<Pair<String, String>> =
        dao.search(query.trim()).map { it.key to it.value }

    suspend fun all(): List<Pair<String, String>> = dao.all().map { it.key to it.value }

    suspend fun forget(key: String) = dao.remove(key.trim().lowercase())

    suspend fun clear() = dao.clear()

    /** Compact block injected into the system prompt so the model "knows" the user. */
    suspend fun asPromptBlock(limit: Int = 25): String {
        val items = dao.all().take(limit)
        if (items.isEmpty()) return ""
        return buildString {
            appendLine("Known facts remembered about this user (from previous sessions):")
            items.forEach { appendLine("- ${it.key}: ${it.value}") }
        }.trim()
    }
}
