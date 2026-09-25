package com.xzo.agent.data.remote

enum class Provider(val label: String, val endpoint: String) {
    GROQ("Groq", "https://api.groq.com/openai/v1/chat/completions"),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1/chat/completions")
}

data class ModelSpec(
    val id: String,
    val provider: Provider,
    val label: String,
    val description: String,
    /** Server-side agentic model with built-in web search + code execution. */
    val serverSideTools: Boolean = false,
    /** Supports OpenAI-style client tool calling. */
    val clientTools: Boolean = true,
    val contextTokens: Int = 8192
)

object ModelCatalog {

    val GROQ_COMPOUND = ModelSpec(
        id = "groq/compound",
        provider = Provider.GROQ,
        label = "Groq Compound (agentic)",
        description = "Server-side web search + code execution built in. Best default for research tasks.",
        serverSideTools = true,
        clientTools = true,
        contextTokens = 131072
    )

    val GROQ_COMPOUND_MINI = ModelSpec(
        id = "groq/compound-mini",
        provider = Provider.GROQ,
        label = "Groq Compound Mini",
        description = "Lighter, faster agentic variant – single tool call per turn.",
        serverSideTools = true,
        contextTokens = 131072
    )

    val GROQ_LLAMA_70B = ModelSpec(
        id = "llama-3.3-70b-versatile",
        provider = Provider.GROQ,
        label = "Llama 3.3 70B Versatile",
        description = "Strong general chat model with client-side tool calling.",
        contextTokens = 131072
    )

    val GROQ_LLAMA_8B = ModelSpec(
        id = "llama-3.1-8b-instant",
        provider = Provider.GROQ,
        label = "Llama 3.1 8B Instant",
        description = "Very fast, cheap on rate limits. Good for short chats.",
        contextTokens = 131072
    )

    val GROQ_GEMMA = ModelSpec(
        id = "gemma2-9b-it",
        provider = Provider.GROQ,
        label = "Gemma 2 9B IT",
        description = "Compact Google model, fast replies.",
        clientTools = false,
        contextTokens = 8192
    )

    val OR_LLAMA_FREE = ModelSpec(
        id = "meta-llama/llama-3.3-70b-instruct:free",
        provider = Provider.OPENROUTER,
        label = "OpenRouter · Llama 3.3 70B (free)",
        description = "Free fallback when Groq is rate limited.",
        contextTokens = 65536
    )

    val OR_DEEPSEEK_FREE = ModelSpec(
        id = "deepseek/deepseek-chat-v3.1:free",
        provider = Provider.OPENROUTER,
        label = "OpenRouter · DeepSeek V3.1 (free)",
        description = "Free reasoning-capable fallback.",
        contextTokens = 65536
    )

    val OR_QWEN_FREE = ModelSpec(
        id = "qwen/qwen-2.5-72b-instruct:free",
        provider = Provider.OPENROUTER,
        label = "OpenRouter · Qwen 2.5 72B (free)",
        description = "Free multilingual fallback (strong Arabic).",
        contextTokens = 32768
    )

    val OR_MISTRAL_FREE = ModelSpec(
        id = "mistralai/mistral-small-3.2-24b-instruct:free",
        provider = Provider.OPENROUTER,
        label = "OpenRouter · Mistral Small 3.2 (free)",
        description = "Free lightweight fallback.",
        contextTokens = 32768
    )

    val groq: List<ModelSpec> = listOf(
        GROQ_COMPOUND, GROQ_COMPOUND_MINI, GROQ_LLAMA_70B, GROQ_LLAMA_8B, GROQ_GEMMA
    )

    val openRouter: List<ModelSpec> = listOf(
        OR_LLAMA_FREE, OR_DEEPSEEK_FREE, OR_QWEN_FREE, OR_MISTRAL_FREE
    )

    val all: List<ModelSpec> = groq + openRouter

    fun byId(id: String): ModelSpec = all.firstOrNull { it.id == id } ?: GROQ_COMPOUND

    /** Ordered fallback chain used when the primary model fails or is rate limited. */
    fun fallbackChain(primaryId: String, fallbackId: String): List<ModelSpec> {
        val primary = byId(primaryId)
        val fallback = byId(fallbackId)
        val chain = LinkedHashSet<ModelSpec>()
        chain += primary
        if (primary.provider == Provider.GROQ && primary.id != GROQ_LLAMA_70B.id) chain += GROQ_LLAMA_70B
        chain += fallback
        chain += OR_DEEPSEEK_FREE
        chain += OR_QWEN_FREE
        return chain.toList()
    }
}
