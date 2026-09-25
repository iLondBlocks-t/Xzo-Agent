package com.xzo.agent.data.remote

enum class Provider(val label: String, val endpoint: String, val modelsEndpoint: String) {
    GROQ(
        "Groq",
        "https://api.groq.com/openai/v1/chat/completions",
        "https://api.groq.com/openai/v1/models"
    ),
    OPENROUTER(
        "OpenRouter",
        "https://openrouter.ai/api/v1/chat/completions",
        "https://openrouter.ai/api/v1/models"
    )
}

data class ModelSpec(
    val id: String,
    val provider: Provider,
    val label: String,
    val description: String,
    /**
     * Groq server-side tools this model supports: "browser_search" (live web,
     * powered by Exa) and "code_interpreter" (sandboxed Python, powered by E2B).
     * Sent as `{"type": "..."}` entries in the `tools` array.
     */
    val builtInTools: List<String> = emptyList(),
    /** Supports OpenAI-style client-side function calling. */
    val clientTools: Boolean = true,
    val contextTokens: Int = 8192,
    val reasoning: Boolean = false,
    val free: Boolean = false,
    val preview: Boolean = false
) {
    val serverSideTools: Boolean get() = builtInTools.isNotEmpty()
}

object ModelCatalog {

    /* ----------------------------- Groq ----------------------------- */

    /**
     * Flagship open-weight model on Groq with built-in browser search and a
     * Python sandbox — the direct successor to the retired `groq/compound`.
     */
    val GPT_OSS_120B = ModelSpec(
        id = "openai/gpt-oss-120b",
        provider = Provider.GROQ,
        label = "GPT-OSS 120B (agentic)",
        description = "Groq flagship. Built-in browser search + Python sandbox, strong reasoning. Best default.",
        builtInTools = listOf("browser_search", "code_interpreter"),
        contextTokens = 131072,
        reasoning = true
    )

    val GPT_OSS_20B = ModelSpec(
        id = "openai/gpt-oss-20b",
        provider = Provider.GROQ,
        label = "GPT-OSS 20B (fast agentic)",
        description = "~1000 tok/s. Same built-in search + code tools, lighter on rate limits.",
        builtInTools = listOf("browser_search", "code_interpreter"),
        contextTokens = 131072,
        reasoning = true
    )

    val QWEN_38_27B = ModelSpec(
        id = "qwen/qwen3.8-27b",
        provider = Provider.GROQ,
        label = "Qwen 3.8 27B",
        description = "Multilingual (excellent Arabic), tool use, JSON mode. Preview model.",
        contextTokens = 131072,
        reasoning = true,
        preview = true
    )

    val GPT_OSS_SAFEGUARD_20B = ModelSpec(
        id = "openai/gpt-oss-safeguard-20b",
        provider = Provider.GROQ,
        label = "GPT-OSS Safeguard 20B",
        description = "Safety-tuned 20B with browser search. Preview model.",
        builtInTools = listOf("browser_search"),
        contextTokens = 131072,
        preview = true
    )

    /** Speech-to-text model used by the microphone button (not a chat model). */
    const val WHISPER_TURBO = "whisper-large-v3-turbo"

    /* -------------------------- OpenRouter -------------------------- */

    val OR_GPT_OSS_120B_FREE = ModelSpec(
        id = "openai/gpt-oss-120b:free",
        provider = Provider.OPENROUTER,
        label = "OpenRouter · GPT-OSS 120B (free)",
        description = "Free mirror of the flagship open-weight model. Reliable tool use.",
        contextTokens = 131072,
        free = true
    )

    val OR_LLAMA_FREE = ModelSpec(
        id = "meta-llama/llama-3.3-70b-instruct:free",
        provider = Provider.OPENROUTER,
        label = "OpenRouter · Llama 3.3 70B (free)",
        description = "Long-standing free multilingual chat model.",
        contextTokens = 131072,
        free = true
    )

    val OR_QWEN_NEXT_FREE = ModelSpec(
        id = "qwen/qwen3-next-80b-a3b-instruct:free",
        provider = Provider.OPENROUTER,
        label = "OpenRouter · Qwen3 Next 80B (free)",
        description = "Free, strong at long multi-turn tool workflows and Arabic.",
        contextTokens = 262144,
        free = true
    )

    val OR_GPT_OSS_20B_FREE = ModelSpec(
        id = "openai/gpt-oss-20b:free",
        provider = Provider.OPENROUTER,
        label = "OpenRouter · GPT-OSS 20B (free)",
        description = "Lightweight free fallback, good at code.",
        contextTokens = 131072,
        free = true
    )

    val OR_AUTO_FREE = ModelSpec(
        id = "openrouter/free",
        provider = Provider.OPENROUTER,
        label = "OpenRouter · Auto (free)",
        description = "Lets OpenRouter pick whichever free model is currently available.",
        contextTokens = 65536,
        free = true
    )

    val groq: List<ModelSpec> = listOf(GPT_OSS_120B, GPT_OSS_20B, QWEN_38_27B, GPT_OSS_SAFEGUARD_20B)

    val openRouter: List<ModelSpec> = listOf(
        OR_GPT_OSS_120B_FREE, OR_LLAMA_FREE, OR_QWEN_NEXT_FREE, OR_GPT_OSS_20B_FREE, OR_AUTO_FREE
    )

    val all: List<ModelSpec> = groq + openRouter

    val DEFAULT_PRIMARY = GPT_OSS_120B.id
    val DEFAULT_FALLBACK = OR_GPT_OSS_120B_FREE.id

    /**
     * Model IDs Groq has decommissioned, mapped to their live replacement.
     * Stored settings and old conversations are migrated transparently so the
     * app never fires a request that is guaranteed to 400.
     */
    val RETIRED: Map<String, String> = mapOf(
        "groq/compound" to GPT_OSS_120B.id,              // shut down 2026-09-21
        "groq/compound-mini" to GPT_OSS_20B.id,          // shut down 2026-09-21
        "llama-3.3-70b-versatile" to GPT_OSS_120B.id,    // shut down 2026-08-16
        "llama-3.1-8b-instant" to GPT_OSS_20B.id,        // shut down 2026-08-16
        "qwen/qwen3-32b" to GPT_OSS_120B.id,             // shut down 2026-07-17
        "qwen/qwen3.6-27b" to QWEN_38_27B.id,            // shut down 2026-09-14
        "meta-llama/llama-4-scout-17b-16e-instruct" to GPT_OSS_120B.id,
        "moonshotai/kimi-k2-instruct-0905" to GPT_OSS_120B.id,
        "gemma2-9b-it" to GPT_OSS_20B.id,
        "llama3-70b-8192" to GPT_OSS_120B.id,
        "llama3-8b-8192" to GPT_OSS_20B.id,
        "deepseek/deepseek-chat-v3.1:free" to OR_GPT_OSS_120B_FREE.id,
        "qwen/qwen-2.5-72b-instruct:free" to OR_QWEN_NEXT_FREE.id,
        "mistralai/mistral-small-3.2-24b-instruct:free" to OR_GPT_OSS_20B_FREE.id
    )

    fun migrate(id: String): String = RETIRED[id] ?: id

    /** Models discovered at runtime from the provider /models endpoints. */
    @Volatile
    var discovered: List<ModelSpec> = emptyList()

    fun byId(id: String): ModelSpec {
        val migrated = migrate(id)
        return all.firstOrNull { it.id == migrated }
            ?: discovered.firstOrNull { it.id == migrated }
            ?: GPT_OSS_120B
    }

    fun known(): List<ModelSpec> {
        val ids = all.map { it.id }.toSet()
        return all + discovered.filterNot { it.id in ids }
    }

    /** Ordered fallback chain used when the primary model fails or is rate limited. */
    fun fallbackChain(primaryId: String, fallbackId: String): List<ModelSpec> {
        val primary = byId(primaryId)
        val fallback = byId(fallbackId)
        val chain = LinkedHashSet<ModelSpec>()
        chain += primary
        if (primary.provider == Provider.GROQ && primary.id != GPT_OSS_20B.id) chain += GPT_OSS_20B
        chain += fallback
        chain += OR_LLAMA_FREE
        chain += OR_AUTO_FREE
        return chain.toList()
    }

    /** Small, cheap model used for titles, summaries and self-verification. */
    fun utility(): List<ModelSpec> = listOf(GPT_OSS_20B, OR_GPT_OSS_20B_FREE, OR_LLAMA_FREE)
}
