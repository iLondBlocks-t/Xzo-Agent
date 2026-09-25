package com.xzo.agent.agent

import com.xzo.agent.data.remote.LlmClient
import com.xzo.agent.data.remote.ModelCatalog
import com.xzo.agent.data.remote.ModelSpec

/**
 * Picks the right model for *this* message instead of forcing one model on every
 * request — cheap questions go to the 1000 tok/s model, hard ones to the flagship,
 * multilingual ones to the multilingual model, images to a vision model.
 *
 * This is deliberately a fast local heuristic: spending an LLM call to decide
 * which LLM to call would double the latency and the rate-limit cost.
 */
object AutoRouter {

    data class Decision(val model: ModelSpec, val reason: String)

    private val heavyHints = listOf(
        "analyse", "analyze", "compare", "research", "investigate", "explain why", "design",
        "architecture", "strategy", "prove", "derive", "optimis", "optimiz", "refactor",
        "debug", "algorithm", "benchmark", "trade-off", "tradeoff", "plan ", "step by step",
        "حلل", "قارن", "ابحث", "اشرح", "صمم", "خطة", "استراتيجية", "برهن", "استنتج"
    )

    private val codeHints = listOf(
        "```", "function ", "class ", "def ", "import ", "stack trace", "exception",
        "compile", "kotlin", "python", "java", "javascript", "typescript", "sql", "regex",
        "كود", "برمج", "خطأ في"
    )

    private val liveHints = listOf(
        "today", "now", "latest", "current", "news", "price", "weather", "202", "release",
        "who won", "how much is", "اليوم", "الآن", "أحدث", "سعر", "أخبار", "الطقس"
    )

    private val arabicRange = Regex("[\\u0600-\\u06FF]")

    fun decide(
        userText: String,
        hasImages: Boolean,
        mode: AgentMode,
        llm: LlmClient,
        userChoice: String
    ): Decision {
        val chosen = ModelCatalog.byId(userChoice)

        // Respect an explicit non-default choice: the user is the boss.
        if (chosen.id != ModelCatalog.DEFAULT_PRIMARY) {
            return Decision(chosen, "your selected model")
        }

        fun available(spec: ModelSpec): Boolean = llm.hasKeyFor(spec.provider)

        val text = userText.lowercase()
        val words = text.split(Regex("\\s+")).size
        val arabic = arabicRange.containsMatchIn(userText)

        // 1. Images need a multimodal model.
        if (hasImages) {
            ModelCatalog.visionChain().firstOrNull { available(it) }?.let {
                return Decision(it, "image attached → vision model")
            }
        }

        // 2. Deep Research orchestrates its own specialists.
        if (mode == AgentMode.DEEP_RESEARCH) {
            return Decision(ModelCatalog.GPT_OSS_120B, "deep research orchestrator")
        }

        val heavy = words > 60 ||
            heavyHints.any { it in text } ||
            codeHints.any { it in text } ||
            userText.count { it == '\n' } > 6

        val live = liveHints.any { it in text }

        return when {
            heavy && available(ModelCatalog.GPT_OSS_120B) ->
                Decision(ModelCatalog.GPT_OSS_120B, "complex request → flagship")

            live && available(ModelCatalog.GPT_OSS_120B) ->
                Decision(ModelCatalog.GPT_OSS_120B, "needs live facts → search-capable flagship")

            arabic && words > 25 && available(ModelCatalog.QWEN_38_27B) ->
                Decision(ModelCatalog.QWEN_38_27B, "long Arabic text → multilingual model")

            words <= 25 && available(ModelCatalog.GPT_OSS_20B) ->
                Decision(ModelCatalog.GPT_OSS_20B, "short question → fastest model")

            available(chosen) -> Decision(chosen, "default")

            else -> Decision(
                ModelCatalog.all.firstOrNull { available(it) } ?: chosen,
                "only provider with a key"
            )
        }
    }
}
