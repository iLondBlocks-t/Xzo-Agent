package com.xzo.agent.agent.tools

import com.xzo.agent.agent.AgentTool
import com.xzo.agent.agent.ToolContext
import com.xzo.agent.agent.ToolResult
import com.xzo.agent.agent.intOr
import com.xzo.agent.agent.schema
import com.xzo.agent.agent.str
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.util.Locale

/**
 * `list_folder` — the user grants access to one folder (ACTION_OPEN_DOCUMENT_TREE)
 * and the agent can then enumerate it and read the text files inside, which makes
 * batch tasks ("summarise every note in this folder") possible without any
 * broad storage permission.
 */
object ListFolderTool : AgentTool {
    override val name = "list_folder"
    override val description =
        "Ask the user to grant access to a folder on their device, then list the files inside it " +
            "(name, type, size). Use before read_folder_file when the user refers to 'my notes', " +
            "'that folder', or wants a batch operation."

    override val interactive = true

    override val parameters: JsonElement = schema {
        str("reason", "Short explanation of why you need the folder.")
        int("max_files", "Maximum entries to list (1-200).", 60)
    }

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        ctx.emit("Waiting for you to pick a folder…")
        val limit = args.intOr("max_files", 60).coerceIn(1, 200)
        val entries = ctx.files.openFolder(limit)
            ?: return ToolResult.fail("The user cancelled the folder picker.")
        if (entries.isEmpty()) return ToolResult.ok("The selected folder is empty.", "Empty folder")
        val body = buildString {
            appendLine("Files in the selected folder (${entries.size}):")
            entries.forEach { e ->
                appendLine("- ${e.name} · ${e.mime} · ${CreateFileTool.humanBytes(e.size.toInt())}")
            }
            appendLine()
            appendLine("Call read_folder_file with an exact name to read one of them.")
        }
        return ToolResult.ok(body, "${entries.size} files listed")
    }
}

object ReadFolderFileTool : AgentTool {
    override val name = "read_folder_file"
    override val description =
        "Read one text file, by exact name, from the folder the user already granted with list_folder."

    override val parameters: JsonElement = schema(required = listOf("name")) {
        str("name", "Exact file name as returned by list_folder.")
        int("max_chars", "Maximum characters to read (1000-120000).", 40000)
    }

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val name = args.str("name").trim()
        if (name.isEmpty()) return ToolResult.fail("name is required")
        val max = args.intOr("max_chars", 40000).coerceIn(1000, 120_000)
        val file = ctx.files.readFromFolder(name, max)
            ?: return ToolResult.fail(
                "No folder has been granted yet, or \"$name\" is not in it. Call list_folder first."
            )
        if (file.text.isBlank()) return ToolResult.fail("\"$name\" has no readable text.")
        return ToolResult.ok(
            "--- FILE: ${file.name} (${file.mime}, ${file.text.length} chars) ---\n${file.text}",
            "Read ${file.name}"
        )
    }
}

/** Dedicated translation tool so the model commits to a faithful, full translation. */
object TranslateTool : AgentTool {
    override val name = "translate"
    override val description =
        "Translate text faithfully into a target language, preserving formatting, numbers, names and code."

    override val parameters: JsonElement = schema(required = listOf("text", "target_language")) {
        str("text", "The text to translate.")
        str("target_language", "Target language, e.g. 'Arabic', 'English', 'French'.")
        str("register", "formal | neutral | casual", listOf("formal", "neutral", "casual"))
    }

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val text = args.str("text")
        val target = args.str("target_language").ifBlank { "English" }
        val register = args.str("register").ifBlank { "neutral" }
        if (text.isBlank()) return ToolResult.fail("text is required")
        val spec = com.xzo.agent.data.remote.ModelCatalog.utility()
            .firstOrNull { ctx.llm.hasKeyFor(it.provider) }
            ?: return ToolResult.fail("No provider available for translation.")
        ctx.emit("Translating to $target…")
        return runCatching {
            val res = ctx.llm.complete(
                spec,
                ctx.llm.adapt(
                    spec,
                    com.xzo.agent.data.remote.ChatRequest(
                        model = spec.id,
                        messages = listOf(
                            com.xzo.agent.data.remote.WireMessage(
                                "system",
                                "Translate the user's text into $target using a $register register. " +
                                    "Return ONLY the translation. Preserve markdown, numbers, URLs and code verbatim."
                            ),
                            com.xzo.agent.data.remote.WireMessage("user", text.take(12000))
                        ),
                        temperature = 0.2,
                        maxTokens = 2000,
                        reasoningEffort = "low"
                    ),
                    allowBuiltIns = false
                )
            )
            ToolResult.ok(res.content, "Translated to ${target.lowercase(Locale.ROOT)}")
        }.getOrElse { ToolResult.fail("Translation failed: ${it.message}") }
    }
}
