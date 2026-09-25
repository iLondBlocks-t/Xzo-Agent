package com.xzo.agent.agent

import com.xzo.agent.data.remote.ChatRequest
import com.xzo.agent.data.remote.LlmClient
import com.xzo.agent.data.remote.ModelCatalog
import com.xzo.agent.data.remote.WireMessage

/**
 * Suggests the three questions a thoughtful person would ask next.
 *
 * Runs on the fastest brain with a tiny token budget, so it costs almost nothing
 * and never delays the answer — it is generated *after* the reply is on screen.
 */
object FollowUps {

    suspend fun suggest(
        question: String,
        answer: String,
        llm: LlmClient,
        limit: Int = 3
    ): List<String> {
        val spec = ModelCatalog.utility().firstOrNull { llm.hasKeyFor(it.provider) } ?: return emptyList()
        val prompt = buildString {
            appendLine("Given this exchange, propose $limit short follow-up questions the user would")
            appendLine("plausibly ask next. Rules: each under 60 characters, no numbering, no quotes,")
            appendLine("one per line, in the SAME language as the user's question, and each must be")
            appendLine("genuinely useful rather than generic filler.")
            appendLine()
            appendLine("USER: ${question.take(600)}")
            appendLine("ASSISTANT: ${answer.take(1200)}")
        }
        return runCatching {
            llm.complete(
                spec,
                llm.adapt(
                    spec,
                    ChatRequest(
                        model = spec.id,
                        messages = listOf(WireMessage("user", prompt)),
                        temperature = 0.7,
                        maxTokens = 160,
                        reasoningEffort = "low"
                    ),
                    allowBuiltIns = false
                )
            ).content
        }.getOrNull()
            ?.lines()
            ?.map { it.trim().trim('-', '*', '"', '•', '·').trim() }
            ?.filter { it.length in 6..90 && !it.endsWith(":") }
            ?.distinct()
            ?.take(limit)
            .orEmpty()
    }
}

/**
 * Notices durable facts about the user inside a normal conversation and files
 * them into long-term memory, so Xzo gets to know you without being told to.
 */
object MemoryHarvester {

    private val triggers = listOf(
        "i am", "i'm", "my name", "i live", "i work", "i prefer", "i like", "i hate",
        "i use", "i need", "call me", "remember", "my goal", "i'm learning", "i speak",
        "اسمي", "أنا", "أعمل", "أسكن", "أفضل", "أحب", "أكره", "هدفي", "أتعلم", "أتحدث"
    )

    fun looksInteresting(userText: String): Boolean {
        val t = userText.lowercase()
        return userText.length in 8..600 && triggers.any { it in t }
    }

    suspend fun harvest(userText: String, llm: LlmClient, memory: MemoryStore) {
        if (!looksInteresting(userText)) return
        val spec = ModelCatalog.utility().firstOrNull { llm.hasKeyFor(it.provider) } ?: return
        val prompt = """
            Extract durable facts about the user from the message below — things that stay true
            for months (name, languages, job, city, tools, stable preferences, long-term goals).

            Ignore anything temporary, any request, and anything about the assistant.
            Reply with at most 3 lines in exactly this form:
            key = value
            Use short snake_case keys. If there is nothing durable, reply with: NONE

            MESSAGE: ${userText.take(800)}
        """.trimIndent()

        val out = runCatching {
            llm.complete(
                spec,
                llm.adapt(
                    spec,
                    ChatRequest(
                        model = spec.id,
                        messages = listOf(WireMessage("user", prompt)),
                        temperature = 0.0,
                        maxTokens = 140,
                        reasoningEffort = "low"
                    ),
                    allowBuiltIns = false
                )
            ).content
        }.getOrNull().orEmpty()

        if (out.isBlank() || out.trim().equals("NONE", true)) return

        out.lines()
            .mapNotNull { line ->
                val parts = line.split("=", limit = 2)
                if (parts.size != 2) return@mapNotNull null
                val key = parts[0].trim().trim('-', '*', '•').lowercase().replace(Regex("[^a-z0-9_]"), "_").trim('_')
                val value = parts[1].trim()
                if (key.length in 2..40 && value.length in 2..200) key to value else null
            }
            .take(3)
            .forEach { (k, v) -> runCatching { memory.remember(k, v) } }
    }
}
