package com.xzo.agent.agent.tools

import com.xzo.agent.agent.AgentTool
import com.xzo.agent.agent.ToolContext
import com.xzo.agent.agent.ToolResult
import com.xzo.agent.agent.schema
import com.xzo.agent.agent.str
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * `create_file` / `write_file`
 *
 * Always routed through the Storage Access Framework: the user picks the exact
 * destination (Downloads, Drive, SD card…). Nothing is ever written silently to
 * app-private storage.
 */
object CreateFileTool : AgentTool {
    override val name = "create_file"
    override val description =
        "Generate a real file (text, markdown, code, CSV, JSON, HTML…) and save it to the user's device. " +
            "The user is shown a save dialog and chooses the location. Provide the complete final file content."

    override val interactive = true

    override val parameters: JsonElement = schema(required = listOf("filename", "content")) {
        str("filename", "File name including the extension, e.g. 'report.md', 'budget.csv', 'main.py'.")
        str("content", "The complete content of the file. Do not wrap it in markdown fences.")
        str("mime_type", "Optional MIME type, e.g. text/markdown, text/csv, application/json, text/x-python.")
    }

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val nameRaw = args.str("filename").trim().ifEmpty { "xzo-output.txt" }
        val filename = sanitize(nameRaw)
        var content = args.str("content")
        if (content.isEmpty()) return ToolResult.fail("content is required")
        content = unfence(content)
        val mime = args.str("mime_type").ifBlank { guessMime(filename) }

        ctx.emit("Waiting for you to choose where to save “$filename”…")
        val saved = ctx.files.createDocument(filename, mime, content)
            ?: return ToolResult.fail("The user cancelled the save dialog, or the file could not be written.")

        return ToolResult(
            ok = true,
            output = "Saved \"${saved.name}\" (${saved.bytes} bytes, $mime) to the location chosen by the user. " +
                "Tell the user the file is ready and summarise what is inside it.",
            summary = "Saved ${saved.name} · ${humanBytes(saved.bytes)}",
            artifactUri = saved.uri.toString(),
            artifactName = saved.name
        )
    }

    private fun sanitize(name: String) = name
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(120)

    private fun unfence(s: String): String {
        val t = s.trim()
        if (!t.startsWith("```")) return s
        return t.removePrefix("```").substringAfter('\n', "").removeSuffix("```").trimEnd()
    }

    internal fun guessMime(filename: String): String = when (filename.substringAfterLast('.', "").lowercase()) {
        "md", "markdown" -> "text/markdown"
        "csv" -> "text/csv"
        "json" -> "application/json"
        "html", "htm" -> "text/html"
        "xml" -> "text/xml"
        "js", "ts" -> "text/javascript"
        "py" -> "text/x-python"
        "kt", "java", "c", "cpp", "rs", "go", "sh" -> "text/plain"
        "pdf" -> "application/pdf"
        "txt", "log", "" -> "text/plain"
        else -> "text/plain"
    }

    internal fun humanBytes(b: Int): String = when {
        b < 1024 -> "$b B"
        b < 1024 * 1024 -> "%.1f KB".format(b / 1024.0)
        else -> "%.1f MB".format(b / (1024.0 * 1024))
    }
}

/** Alias so models that were trained to call `write_file` also work. */
object WriteFileTool : AgentTool {
    override val name = "write_file"
    override val description =
        "Alias of create_file: write generated content to a real file on the device via the system save dialog."
    override val interactive = true
    override val parameters: JsonElement = CreateFileTool.parameters
    override suspend fun execute(args: JsonObject, ctx: ToolContext) = CreateFileTool.execute(args, ctx)
}

/** `read_file` – pull a user-picked document into context. */
object ReadFileTool : AgentTool {
    override val name = "read_file"
    override val description =
        "Ask the user to pick a file from their device and read its text content into your context so you can " +
            "analyse, summarise, translate or refactor it. Files already attached to this turn are read automatically."

    override val interactive = true

    override val parameters: JsonElement = schema {
        str("reason", "Short explanation shown to the user of why you need a file.")
        str("mime_filter", "Optional MIME filter such as 'text/*', 'application/json', '*/*'.")
        int("max_chars", "Maximum characters to load (1000-200000).", 60000)
    }

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        // Prefer files the user already attached this turn.
        val attached = ctx.attachments
        if (attached.isNotEmpty()) {
            val body = attached.joinToString("\n\n") { a ->
                "--- FILE: ${a.name} (${a.mime}) ---\n${a.text}"
            }
            return ToolResult.ok(body, "Read ${attached.size} attached file(s)")
        }
        val filter = args.str("mime_filter").ifBlank { "*/*" }
        val max = (args["max_chars"]?.toString()?.trim('"')?.toIntOrNull() ?: 60000).coerceIn(1000, 200_000)
        ctx.emit("Waiting for you to pick a file…")
        val loaded = ctx.files.openDocument(arrayOf(filter), max)
            ?: return ToolResult.fail("The user cancelled the file picker.")
        if (loaded.text.isBlank()) {
            return ToolResult.fail("\"${loaded.name}\" contains no readable text (binary or empty file).")
        }
        return ToolResult(
            ok = true,
            output = "--- FILE: ${loaded.name} (${loaded.mime}, ${loaded.text.length} chars) ---\n${loaded.text}",
            summary = "Read ${loaded.name}",
            artifactUri = loaded.uri.toString(),
            artifactName = loaded.name
        )
    }
}
