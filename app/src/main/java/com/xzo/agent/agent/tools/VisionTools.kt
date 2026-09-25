package com.xzo.agent.agent.tools

import com.xzo.agent.agent.AgentTool
import com.xzo.agent.agent.ToolContext
import com.xzo.agent.agent.ToolResult
import com.xzo.agent.agent.schema
import com.xzo.agent.agent.str
import com.xzo.agent.data.remote.ChatRequest
import com.xzo.agent.data.remote.ModelCatalog
import com.xzo.agent.data.remote.WireMessage
import com.xzo.agent.data.remote.multimodal
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * `analyze_image`
 *
 * Lets *any* selected model work with pictures: the image is routed to a
 * vision-capable model (Qwen 3.8 27B on Groq, Gemma 4 on OpenRouter) and the
 * detailed description is handed back into the agent loop as text.
 */
object AnalyzeImageTool : AgentTool {
    override val name = "analyze_image"
    override val description =
        "Look at an image the user attached and answer a question about it — read the text in it (OCR), " +
            "describe it, identify objects, transcribe a screenshot, check a document photo, or extract " +
            "a table. Call this whenever the user attaches a photo or screenshot."

    override val parameters: JsonElement = schema(required = listOf("question")) {
        str("question", "Exactly what to determine from the image, e.g. 'transcribe all visible text'.")
        str("image_name", "Optional file name if several images are attached; defaults to all of them.")
        str("detail", "brief | detailed", listOf("brief", "detailed"))
    }

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val question = args.str("question").ifBlank { "Describe this image in full detail." }
        val wanted = args.str("image_name").trim()
        val images = ctx.attachments
            .filter { it.isImage }
            .filter { wanted.isEmpty() || it.name.equals(wanted, ignoreCase = true) }
        if (images.isEmpty()) {
            return ToolResult.fail(
                "No image is attached to this message. Ask the user to attach one with the 📎 button."
            )
        }

        val detail = args.str("detail").ifBlank { "detailed" }
        val chain = ModelCatalog.visionChain().filter { ctx.llm.hasKeyFor(it.provider) }
        if (chain.isEmpty()) return ToolResult.fail("No vision-capable model is available with the current keys.")

        ctx.emit("Looking at ${images.size} image(s)…")

        val prompt = buildString {
            appendLine(question)
            appendLine()
            if (detail == "detailed") {
                appendLine("Be exhaustive: transcribe every piece of visible text verbatim (keep the original")
                appendLine("language and layout), describe diagrams/charts including their numbers, and state")
                appendLine("clearly when something is unreadable. Never guess at text you cannot see.")
            } else {
                appendLine("Answer in at most five sentences.")
            }
        }

        var lastError: String? = null
        for (spec in chain) {
            val result = runCatching {
                ctx.llm.complete(
                    spec,
                    ctx.llm.adapt(
                        spec,
                        ChatRequest(
                            model = spec.id,
                            messages = listOf(
                                WireMessage(
                                    "system",
                                    "You are a precise vision analyst. Report only what is actually visible."
                                ),
                                multimodal("user", prompt, images.mapNotNull { it.imageDataUrl })
                            ),
                            temperature = 0.2,
                            maxTokens = 2000,
                            reasoningEffort = "low"
                        ),
                        allowBuiltIns = false
                    )
                )
            }.getOrElse { lastError = it.message; null } ?: continue

            if (result.content.isNotBlank()) {
                return ToolResult.ok(
                    "Image analysis (${spec.label}) of ${images.joinToString { it.name }}:\n\n${result.content}",
                    "Analysed ${images.size} image(s)"
                )
            }
        }
        return ToolResult.fail("Vision models did not return a description. ${lastError.orEmpty()}")
    }
}
