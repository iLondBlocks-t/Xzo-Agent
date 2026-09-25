package com.xzo.agent.agent.tools

import android.os.Build
import com.xzo.agent.agent.AgentTool
import com.xzo.agent.agent.ToolContext
import com.xzo.agent.agent.ToolResult
import com.xzo.agent.agent.schema
import com.xzo.agent.agent.str
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Current date/time – models have no clock, so this prevents confident wrong dates. */
object ClockTool : AgentTool {
    override val name = "current_datetime"
    override val description =
        "Get the exact current date, time, timezone and epoch on the user's device. " +
            "Call this before any answer that depends on 'today', 'now', ages, deadlines or durations."

    override val parameters: JsonElement = schema {
        str("timezone", "Optional IANA timezone id, e.g. 'UTC', 'Asia/Riyadh'. Defaults to the device timezone.")
    }

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val tzId = args.str("timezone").trim()
        val tz = if (tzId.isNotEmpty()) TimeZone.getTimeZone(tzId) else TimeZone.getDefault()
        val now = Date()
        val fmt = SimpleDateFormat("EEEE, d MMMM yyyy 'at' HH:mm:ss", Locale.ENGLISH).apply { timeZone = tz }
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply { timeZone = tz }
        val body = buildString {
            appendLine("Current date/time: ${fmt.format(now)}")
            appendLine("ISO-8601: ${iso.format(now)}")
            appendLine("Timezone: ${tz.id} (offset ${tz.getOffset(now.time) / 3600000}h)")
            appendLine("Unix epoch ms: ${now.time}")
        }
        return ToolResult.ok(body, fmt.format(now))
    }
}

/** Device/runtime facts so the agent can give device-aware advice. */
object DeviceInfoTool : AgentTool {
    override val name = "device_info"
    override val description =
        "Get information about the Android device running this app: model, Android version, ABI, " +
            "locale, screen and available memory. Useful for troubleshooting or tailored advice."

    override val parameters: JsonElement = schema { }

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val rt = Runtime.getRuntime()
        val cfg = ctx.appContext.resources.configuration
        val dm = ctx.appContext.resources.displayMetrics
        val body = buildString {
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Locale: ${cfg.locales[0]}")
            appendLine("Screen: ${dm.widthPixels}x${dm.heightPixels} @${dm.densityDpi}dpi")
            appendLine("JVM heap: used ${(rt.totalMemory() - rt.freeMemory()) / 1048576} MB / max ${rt.maxMemory() / 1048576} MB")
            appendLine("App: Xzo Agent ${com.xzo.agent.BuildConfig.VERSION_NAME} (arm32 build)")
        }
        return ToolResult.ok(body, "${Build.MANUFACTURER} ${Build.MODEL} · API ${Build.VERSION.SDK_INT}")
    }
}

/** Persistent cross-session memory. */
object RememberTool : AgentTool {
    override val name = "remember"
    override val description =
        "Store a durable fact about the user or project (name, preferences, goals, constraints) so you can " +
            "recall it in future conversations. Keep keys short and stable."

    override val parameters: JsonElement = schema(required = listOf("key", "value")) {
        str("key", "Short stable identifier, e.g. 'user_name', 'preferred_language', 'project_stack'.")
        str("value", "The fact to remember, in one sentence.")
    }

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val k = args.str("key").trim()
        val v = args.str("value").trim()
        if (k.isEmpty() || v.isEmpty()) return ToolResult.fail("Both key and value are required")
        ctx.memory.remember(k, v)
        return ToolResult.ok("Remembered $k = $v", "Remembered “$k”")
    }
}

object RecallTool : AgentTool {
    override val name = "recall"
    override val description =
        "Look up previously remembered facts. Pass a query to search, or leave empty to list everything."

    override val parameters: JsonElement = schema {
        str("query", "Optional search text. Empty lists all stored memories.")
    }

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val q = args.str("query").trim()
        val hits = if (q.isEmpty()) ctx.memory.all() else ctx.memory.search(q)
        if (hits.isEmpty()) return ToolResult.ok("No stored memories match.", "No memories")
        val body = hits.joinToString("\n") { "- ${it.first}: ${it.second}" }
        return ToolResult.ok("Stored memories:\n$body", "${hits.size} memories")
    }
}

/** Long-text summariser / structurer that runs on the fast model to save context. */
object SummarizeTool : AgentTool {
    override val name = "summarize_text"
    override val description =
        "Condense long text (an article, a file, logs) into a structured summary. Use this on large tool " +
            "outputs before reasoning over them so you do not run out of context."

    override val parameters: JsonElement = schema(required = listOf("text")) {
        str("text", "The text to summarise.")
        str("style", "bullets | paragraph | outline | key_facts", listOf("bullets", "paragraph", "outline", "key_facts"))
        str("language", "Output language, e.g. 'English', 'Arabic'. Defaults to the language of the text.")
    }

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val text = args.str("text")
        if (text.isBlank()) return ToolResult.fail("text is required")
        val style = args.str("style").ifBlank { "bullets" }
        val lang = args.str("language").ifBlank { "the same language as the input" }
        ctx.emit("Summarising ${text.length} characters…")
        return try {
            val res = ctx.llm.complete(
                com.xzo.agent.data.remote.ModelCatalog.GROQ_LLAMA_8B,
                com.xzo.agent.data.remote.ChatRequest(
                    model = com.xzo.agent.data.remote.ModelCatalog.GROQ_LLAMA_8B.id,
                    messages = listOf(
                        com.xzo.agent.data.remote.WireMessage(
                            "system",
                            "Summarise the user's text as $style in $lang. Preserve numbers, names, dates and URLs. Be faithful; never invent."
                        ),
                        com.xzo.agent.data.remote.WireMessage("user", text.take(24000))
                    ),
                    temperature = 0.2,
                    maxTokens = 1200
                )
            )
            ToolResult.ok(res.content, "Summarised ${text.length} chars")
        } catch (t: Throwable) {
            // Offline heuristic fallback: first sentences of each paragraph.
            val fallback = text.split(Regex("\n{2,}"))
                .take(12)
                .mapNotNull { p -> p.trim().split(Regex("(?<=[.!؟?])\\s")).firstOrNull()?.trim() }
                .filter { it.isNotBlank() }
                .joinToString("\n") { "- $it" }
            ToolResult.ok(fallback.ifBlank { text.take(1200) }, "Local summary")
        }
    }
}
