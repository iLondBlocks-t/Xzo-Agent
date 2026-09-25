package com.xzo.agent.agent.tools

import android.net.Uri
import com.xzo.agent.agent.AgentTool
import com.xzo.agent.agent.ToolContext
import com.xzo.agent.agent.ToolResult
import com.xzo.agent.agent.schema
import com.xzo.agent.agent.str
import com.xzo.agent.core.OnDeviceMl
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * `ocr_image` — read text out of an attached picture completely offline, using the
 * ML Kit recogniser bundled in the APK. Works with no API key, no network and no
 * rate limit, and costs nothing, so it is the right first choice for screenshots,
 * receipts, whiteboards and document photos in Latin script.
 */
object OcrTool : AgentTool {
    override val name = "ocr_image"
    override val description =
        "Extract the text from an attached image completely offline (no API, no network). " +
            "Best for screenshots, receipts, forms and documents in Latin script. " +
            "For handwriting, Arabic script, charts or 'describe the picture' use analyze_image instead."

    override val parameters: JsonElement = schema {
        str("image_name", "Optional file name if several images are attached.")
    }

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val wanted = args.str("image_name").trim()
        val images = ctx.attachments
            .filter { it.isImage }
            .filter { wanted.isEmpty() || it.name.equals(wanted, ignoreCase = true) }
        if (images.isEmpty()) return ToolResult.fail("No image is attached to this message.")

        ctx.emit("Reading text from ${images.size} image(s) on-device…")
        val chunks = images.mapNotNull { a ->
            val text = runCatching { OnDeviceMl.ocr(ctx.appContext, Uri.parse(a.uri)) }.getOrNull()
            if (text.isNullOrBlank()) null else "--- ${a.name} ---\n$text"
        }
        if (chunks.isEmpty()) {
            return ToolResult.fail(
                "The on-device recogniser found no Latin-script text. Try analyze_image for " +
                    "handwriting, Arabic script or a visual description."
            )
        }
        val body = chunks.joinToString("\n\n")
        return ToolResult.ok(
            "Offline OCR result:\n\n$body",
            "OCR: ${body.length} chars from ${chunks.size} image(s)"
        )
    }
}

/**
 * `offline_translate` — ML Kit translation that keeps working with no key and no
 * network once the ~30 MB language pair has been fetched a single time.
 */
object OfflineTranslateTool : AgentTool {
    override val name = "offline_translate"
    override val description =
        "Translate text entirely on-device, with no API key and no rate limit. Use it when the " +
            "providers are rate limited or the phone is offline, or for bulk text where quality " +
            "matters less than availability. For nuance and technical text prefer the translate tool."

    override val parameters: JsonElement = schema(required = listOf("text", "target_language_code")) {
        str("text", "The text to translate.")
        str("target_language_code", "BCP-47 code, e.g. 'en', 'ar', 'fr', 'es'.")
        str("source_language_code", "Optional BCP-47 source code; auto-detected when omitted.")
    }

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val text = args.str("text")
        if (text.isBlank()) return ToolResult.fail("text is required")
        val target = args.str("target_language_code").trim().ifBlank { "en" }
        val source = args.str("source_language_code").trim().ifBlank { null }

        ctx.emit("Translating on-device to $target…")
        val result = OnDeviceMl.translate(text, target, source)
            ?: return ToolResult.fail(
                "On-device translation is unavailable for that pair. Supported codes: " +
                    OnDeviceMl.supportedLanguageTags().take(40).joinToString(", ")
            )
        return ToolResult.ok(
            "Offline translation (${result.from} → ${result.to}):\n\n${result.text}",
            "Translated ${result.from} → ${result.to} on-device"
        )
    }
}

/** `detect_language` — instant, offline language identification. */
object DetectLanguageTool : AgentTool {
    override val name = "detect_language"
    override val description =
        "Identify the language of a piece of text instantly and offline. Returns a BCP-47 code."

    override val parameters: JsonElement = schema(required = listOf("text")) {
        str("text", "The text to identify.")
    }

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val text = args.str("text")
        if (text.isBlank()) return ToolResult.fail("text is required")
        val tag = OnDeviceMl.identifyLanguage(text)
        return if (tag == "und") ToolResult.ok("Language could not be determined.", "undetermined")
        else ToolResult.ok("Detected language: $tag", tag)
    }
}
