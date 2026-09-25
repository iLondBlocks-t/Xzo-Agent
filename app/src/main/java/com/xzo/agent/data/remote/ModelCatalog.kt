package com.xzo.agent.data.remote

/**
 * Compute back-ends. Only the Xzo-facing names are ever shown in the app;
 * the endpoints are an implementation detail.
 */
enum class Provider(val label: String, val endpoint: String, val modelsEndpoint: String) {
    GROQ(
        "Xzo Core",
        "https://api.groq.com/openai/v1/chat/completions",
        "https://api.groq.com/openai/v1/models"
    ),
    OPENROUTER(
        "Xzo Relay",
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
     * server-side tools this model supports: "browser_search" (live web,
     * powered by Exa) and "code_interpreter" (sandboxed Python, powered by E2B).
     * Sent as `{"type": "..."}` entries in the `tools` array.
     */
    val builtInTools: List<String> = emptyList(),
    /** Supports OpenAI-style client-side function calling. */
    val clientTools: Boolean = true,
    val contextTokens: Int = 8192,
    val reasoning: Boolean = false,
    val free: Boolean = false,
    val preview: Boolean = false,
    /** Accepts image parts in the message content (vision). */
    val vision: Boolean = false
) {
    val serverSideTools: Boolean get() = builtInTools.isNotEmpty()
}

object ModelCatalog {

    /* ----------------------------- the primary route ----------------------------- */

    /**
     * Flagship open-weight model on the primary route with built-in browser search and a
     * Python sandbox — the direct successor to the retired `groq/compound`.
     */
    val GPT_OSS_120B = ModelSpec(
        id = "openai/gpt-oss-120b",
        provider = Provider.GROQ,
        label = "Xzo Ultra",
        description = "The flagship brain. Live web browsing, a real code sandbox and deep reasoning. Best default.",
        builtInTools = listOf("browser_search", "code_interpreter"),
        contextTokens = 131072,
        reasoning = true
    )

    val GPT_OSS_20B = ModelSpec(
        id = "openai/gpt-oss-20b",
        provider = Provider.GROQ,
        label = "Xzo Swift",
        description = "Near-instant replies with the same web and code powers. Lightest on your free quota.",
        builtInTools = listOf("browser_search", "code_interpreter"),
        contextTokens = 131072,
        reasoning = true
    )

    val QWEN_38_27B = ModelSpec(
        id = "qwen/qwen3.8-27b",
        provider = Provider.GROQ,
        label = "Xzo Lingua",
        description = "Multilingual specialist with excellent Arabic, plus image understanding.",
        contextTokens = 131072,
        reasoning = true,
        preview = true,
        vision = true
    )

    val GPT_OSS_SAFEGUARD_20B = ModelSpec(
        id = "openai/gpt-oss-safeguard-20b",
        provider = Provider.GROQ,
        label = "Xzo Guard",
        description = "Safety-tuned variant with live web browsing.",
        builtInTools = listOf("browser_search"),
        contextTokens = 131072,
        preview = true
    )

    /** Speech-to-text model used by the microphone button (not a chat model). */
    const val WHISPER_TURBO = "whisper-large-v3-turbo"

    /* -------------------------- the backup route -------------------------- */

    val OR_GPT_OSS_120B_FREE = ModelSpec(
        id = "openai/gpt-oss-120b:free",
        provider = Provider.OPENROUTER,
        label = "Xzo Ultra · Relay",
        description = "Backup route to the flagship brain. Used automatically if the main route is busy.",
        contextTokens = 131072,
        free = true
    )

    val OR_LLAMA_FREE = ModelSpec(
        id = "meta-llama/llama-3.3-70b-instruct:free",
        provider = Provider.OPENROUTER,
        label = "Xzo Classic · Relay",
        description = "Dependable multilingual backup route.",
        contextTokens = 131072,
        free = true
    )

    val OR_GEMMA_VISION_FREE = ModelSpec(
        id = "google/gemma-4-31b-it:free",
        provider = Provider.OPENROUTER,
        label = "Xzo Vision · Relay",
        description = "Sees images and speaks 140+ languages. Backup route for picture questions.",
        contextTokens = 262144,
        free = true,
        vision = true
    )

    val OR_QWEN_NEXT_FREE = ModelSpec(
        id = "qwen/qwen3-next-80b-a3b-instruct:free",
        provider = Provider.OPENROUTER,
        label = "Xzo Lingua · Relay",
        description = "Backup route tuned for long multi-step work and Arabic.",
        contextTokens = 262144,
        free = true
    )

    val OR_GPT_OSS_20B_FREE = ModelSpec(
        id = "openai/gpt-oss-20b:free",
        provider = Provider.OPENROUTER,
        label = "Xzo Swift · Relay",
        description = "Light, fast backup route. Good at code.",
        contextTokens = 131072,
        free = true
    )

    val OR_AUTO_FREE = ModelSpec(
        id = "openrouter/free",
        provider = Provider.OPENROUTER,
        label = "Xzo Auto · Relay",
        description = "Last-resort route: always picks whatever capacity is free right now.",
        contextTokens = 65536,
        free = true
    )

    val groq: List<ModelSpec> = listOf(GPT_OSS_120B, GPT_OSS_20B, QWEN_38_27B, GPT_OSS_SAFEGUARD_20B)

    val openRouter: List<ModelSpec> = listOf(
        OR_GPT_OSS_120B_FREE, OR_GEMMA_VISION_FREE, OR_LLAMA_FREE, OR_QWEN_NEXT_FREE,
        OR_GPT_OSS_20B_FREE, OR_AUTO_FREE
    )

    val all: List<ModelSpec> = groq + openRouter

    val DEFAULT_PRIMARY = GPT_OSS_120B.id
    val DEFAULT_FALLBACK = OR_GPT_OSS_120B_FREE.id

    /**
     * Model IDs the primary route has decommissioned, mapped to their live replacement.
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

    /**
     * Turns a raw back-end identifier into an Xzo-style name. Nothing in the UI
     * should ever surface a vendor or a model number.
     */
    fun friendlyName(id: String, vendorName: String? = null): String {
        val base = id.substringAfterLast('/').removeSuffix(":free")
        val pretty = base.split('-', '.', '_')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
        return "Xzo " + pretty.take(22).ifBlank { "Model" }
    }

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

    /** Models that can look at images, in preference order. */
    fun visionChain(): List<ModelSpec> = (all + discovered).filter { it.vision }

    /** Small, cheap model used for titles, summaries and self-verification. */
    fun utility(): List<ModelSpec> = listOf(GPT_OSS_20B, OR_GPT_OSS_20B_FREE, OR_LLAMA_FREE)
}
