package com.xzo.agent.agent

import com.xzo.agent.data.remote.ChatRequest
import com.xzo.agent.data.remote.LlmClient
import com.xzo.agent.data.remote.ModelCatalog
import com.xzo.agent.data.remote.ModelSpec
import com.xzo.agent.data.remote.ToolDef
import com.xzo.agent.data.remote.TurnResult
import com.xzo.agent.data.remote.WireMessage

/**
 * Specialist roles.
 *
 * Instead of pushing every request through one generic model, Xzo routes each
 * sub-task to the model that is actually best at it, with a system prompt,
 * temperature, reasoning effort and tool set tuned for that job:
 *
 * | Role        | Job                                   | Wants                              |
 * |-------------|---------------------------------------|------------------------------------|
 * | PLANNER     | decompose the request                 | deep reasoning, no tools           |
 * | RESEARCHER  | find current facts with sources       | browser search, low reasoning      |
 * | ANALYST     | numbers, data, statistics             | Python sandbox, temperature 0      |
 * | CODER       | write and test code                   | Python sandbox, low temperature    |
 * | WRITER      | turn material into prose              | high temperature, no tools         |
 * | TRANSLATOR  | faithful multilingual output          | multilingual model, temperature 0.2|
 * | CRITIC      | find errors before the user does      | temperature 0, no tools            |
 * | VISION      | read images / OCR                     | multimodal model                   |
 */
enum class Role(val label: String, val emoji: String) {
    PLANNER("Planner", "🧭"),
    RESEARCHER("Researcher", "🔎"),
    ANALYST("Analyst", "📊"),
    CODER("Coder", "💻"),
    WRITER("Writer", "✍️"),
    TRANSLATOR("Translator", "🌍"),
    CRITIC("Critic", "🔬"),
    VISION("Vision", "👁️");

    companion object {
        fun from(value: String?): Role =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: RESEARCHER
    }
}

data class RoleProfile(
    val role: Role,
    val system: String,
    val temperature: Double,
    val reasoningEffort: String,
    val maxTokens: Int,
    val builtIns: List<String>,
    /** Preference order; the first one whose provider has a key wins. */
    val models: List<ModelSpec>
)

object Specialists {

    private val researchModels get() = listOf(
        ModelCatalog.GPT_OSS_120B, ModelCatalog.GPT_OSS_20B, ModelCatalog.OR_GPT_OSS_120B_FREE
    )
    private val fastModels get() = listOf(
        ModelCatalog.GPT_OSS_20B, ModelCatalog.GPT_OSS_120B, ModelCatalog.OR_GPT_OSS_20B_FREE
    )
    private val deepModels get() = listOf(
        ModelCatalog.GPT_OSS_120B, ModelCatalog.OR_GPT_OSS_120B_FREE, ModelCatalog.GPT_OSS_20B
    )
    private val multilingualModels get() = listOf(
        ModelCatalog.QWEN_38_27B, ModelCatalog.OR_QWEN_NEXT_FREE, ModelCatalog.GPT_OSS_120B
    )

    fun profile(role: Role): RoleProfile = when (role) {
        Role.PLANNER -> RoleProfile(
            role,
            """
            You are the Planner. Decompose the user's request into the smallest set of concrete,
            independently answerable sub-tasks. Prefer 3-5 sub-tasks; never more than 6.
            Each sub-task must name the specialist that should handle it
            (RESEARCHER, ANALYST, CODER, WRITER, TRANSLATOR, VISION) and be phrased as a
            self-contained instruction that makes sense without the rest of the conversation.
            Output ONLY a JSON array like:
            [{"role":"RESEARCHER","task":"...","why":"..."}]
            No prose, no markdown fences.
            """.trimIndent(),
            temperature = 0.2, reasoningEffort = "high", maxTokens = 1200,
            builtIns = emptyList(), models = deepModels
        )

        Role.RESEARCHER -> RoleProfile(
            role,
            """
            You are the Researcher. Use browser search aggressively to find CURRENT, verifiable facts.
            Rules: prefer primary sources; give exact numbers, dates and names; quote sparingly;
            always finish with a "Sources:" list of the real URLs you actually opened.
            If sources disagree, say so explicitly. Never invent a URL. If you cannot verify
            something, write "unverified" next to it. Be dense: no filler, no preamble.
            """.trimIndent(),
            temperature = 0.3, reasoningEffort = "low", maxTokens = 2600,
            builtIns = listOf("browser_search"), models = researchModels
        )

        Role.ANALYST -> RoleProfile(
            role,
            """
            You are the Analyst. Any number you state must come from code you actually ran in the
            Python sandbox — never from memory or estimation. Show the key figures in a compact
            markdown table, state your assumptions explicitly, and flag anything the data cannot
            support. Round sensibly and always include units.
            """.trimIndent(),
            temperature = 0.0, reasoningEffort = "medium", maxTokens = 2200,
            builtIns = listOf("code_interpreter"), models = researchModels
        )

        Role.CODER -> RoleProfile(
            role,
            """
            You are the Coder. Produce complete, runnable, idiomatic code — no ellipses, no
            "rest of the implementation here". Handle errors and edge cases. Verify the core
            logic by executing it in the Python sandbox when the language allows, and show the
            real output. Explain only what is non-obvious, in short bullets after the code.
            """.trimIndent(),
            temperature = 0.15, reasoningEffort = "medium", maxTokens = 3200,
            builtIns = listOf("code_interpreter"), models = deepModels
        )

        Role.WRITER -> RoleProfile(
            role,
            """
            You are the Writer. Turn the supplied material into clear, well-structured prose in the
            user's language. Lead with the answer, then the detail. Use headings, short paragraphs
            and tables where they help. Keep every fact and every source from the material — you may
            reorganise and compress, but you may never add a claim that is not in the material.
            """.trimIndent(),
            temperature = 0.75, reasoningEffort = "low", maxTokens = 3000,
            builtIns = emptyList(), models = deepModels
        )

        Role.TRANSLATOR -> RoleProfile(
            role,
            """
            You are the Translator. Produce a faithful, natural translation. Preserve markdown,
            numbers, URLs, code and proper nouns exactly. Keep technical terms in their original
            language when that is the local convention. Return only the translation.
            """.trimIndent(),
            temperature = 0.2, reasoningEffort = "low", maxTokens = 3000,
            builtIns = emptyList(), models = multilingualModels
        )

        Role.CRITIC -> RoleProfile(
            role,
            """
            You are the Critic. Attack the draft: find unsupported claims, wrong arithmetic,
            missing parts of the request, stale facts, broken logic and anything the user would
            notice before you do. Be specific and terse. If it is genuinely sound, say so in one line.
            """.trimIndent(),
            temperature = 0.0, reasoningEffort = "high", maxTokens = 1400,
            builtIns = emptyList(), models = deepModels
        )

        Role.VISION -> RoleProfile(
            role,
            """
            You are the Vision analyst. Report only what is actually visible. Transcribe text
            verbatim in its original language, describe charts including their numbers, and state
            plainly when something is unreadable. Never guess.
            """.trimIndent(),
            temperature = 0.2, reasoningEffort = "low", maxTokens = 2200,
            builtIns = emptyList(), models = ModelCatalog.visionChain().ifEmpty { multilingualModels }
        )
    }

    fun pickModel(profile: RoleProfile, llm: LlmClient): ModelSpec? =
        profile.models.firstOrNull { llm.hasKeyFor(it.provider) }
            ?: ModelCatalog.all.firstOrNull { llm.hasKeyFor(it.provider) }

    /** Runs a single specialist turn. Throws on total failure. */
    suspend fun run(
        role: Role,
        task: String,
        context: String,
        llm: LlmClient,
        useBuiltIns: Boolean = true,
        extraMessages: List<WireMessage> = emptyList()
    ): Pair<TurnResult, ModelSpec> {
        val profile = profile(role)
        val candidates = profile.models.filter { llm.hasKeyFor(it.provider) }
            .ifEmpty { listOfNotNull(pickModel(profile, llm)) }
        require(candidates.isNotEmpty()) { "No provider key available for ${role.label}" }

        val userContent = buildString {
            if (context.isNotBlank()) {
                appendLine("## Context")
                appendLine(context.take(24_000))
                appendLine()
            }
            appendLine("## Task")
            append(task)
        }

        var last: Throwable? = null
        for (spec in candidates) {
            val builtIns = if (useBuiltIns && spec.provider == com.xzo.agent.data.remote.Provider.GROQ)
                profile.builtIns.filter { it in spec.builtInTools } else emptyList()
            val request = ChatRequest(
                model = spec.id,
                messages = listOf(WireMessage("system", profile.system)) +
                    extraMessages +
                    listOf(WireMessage("user", userContent)),
                temperature = profile.temperature,
                maxTokens = profile.maxTokens,
                reasoningEffort = profile.reasoningEffort,
                tools = builtIns.map { ToolDef(type = it) }.ifEmpty { null },
                toolChoice = if (builtIns.isEmpty()) null else "auto"
            )
            val res = runCatching {
                llm.complete(spec, llm.adapt(spec, request, allowBuiltIns = builtIns.isNotEmpty()))
            }
            res.onSuccess { if (it.content.isNotBlank()) return it to spec }
            res.onFailure { last = it }
        }
        throw last ?: IllegalStateException("${role.label} produced no output")
    }
}
