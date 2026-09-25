package com.xzo.agent.data.remote

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * OpenAI-compatible wire types shared by Groq and OpenRouter.
 * Kept deliberately lenient: unknown keys are ignored so a provider adding
 * fields never crashes the app.
 */

val WireJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    isLenient = true
    coerceInputValues = true
    prettyPrint = false
}

val PrettyJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    prettyPrint = true
    isLenient = true
}

@Serializable
data class FunctionCall(
    val name: String = "",
    val arguments: String = "{}"
)

@Serializable
data class ToolCall(
    val id: String = "",
    val type: String = "function",
    val function: FunctionCall = FunctionCall()
)

/**
 * A chat message.
 *
 * `content` is a raw [JsonElement] because the OpenAI-compatible schema allows either
 * a plain string **or** an array of typed parts (text + image_url) for vision models.
 * Use the [WireMessage] factory for plain text and [multimodal] for text + images.
 */
@Serializable
data class WireMessage(
    val role: String,
    val content: JsonElement? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null,
    /** GPT-OSS reasoning trace (Groq). Never sent back up. */
    val reasoning: String? = null,
    /** Groq server-side tool executions (browser_search / code_interpreter). */
    @SerialName("executed_tools") val executedTools: List<ExecutedTool>? = null
)

/** Plain-text convenience factory; keeps every existing call-site unchanged. */
@Suppress("FunctionName")
fun WireMessage(
    role: String,
    content: String?,
    toolCalls: List<ToolCall>? = null,
    toolCallId: String? = null,
    name: String? = null
): WireMessage = WireMessage(
    role = role,
    content = content?.let { JsonPrimitive(it) },
    toolCalls = toolCalls,
    toolCallId = toolCallId,
    name = name
)

/** Builds a vision message: text plus one or more images (data: or https: URLs). */
fun multimodal(role: String, text: String, imageUrls: List<String>): WireMessage {
    val parts = buildJsonArray {
        if (text.isNotBlank()) {
            add(buildJsonObject {
                put("type", "text")
                put("text", text)
            })
        }
        imageUrls.forEach { url ->
            add(buildJsonObject {
                put("type", "image_url")
                put("image_url", buildJsonObject { put("url", url) })
            })
        }
    }
    return WireMessage(role = role, content = parts)
}

/** Flattens any content shape back to readable text (for history, export, TTS). */
val WireMessage.text: String
    get() = when (val c = content) {
        null -> ""
        is JsonPrimitive -> c.contentOrNull.orEmpty()
        is JsonArray -> c.mapNotNull { el ->
            (el as? JsonObject)?.get("text")?.let { (it as? JsonPrimitive)?.contentOrNull }
        }.joinToString("\n")
        else -> c.toString()
    }

@Serializable
data class ExecutedTool(
    val index: Int = 0,
    val type: String = "function",
    val name: String = "",
    val arguments: String = "",
    val output: String? = null,
    @SerialName("code_results") val codeResults: List<CodeResult>? = null,
    @SerialName("search_results") val searchResults: SearchResults? = null
)

@Serializable
data class CodeResult(val text: String = "")

@Serializable
data class SearchResults(val results: List<SearchResultItem>? = null)

@Serializable
data class SearchResultItem(
    val title: String = "",
    val url: String = "",
    val content: String = "",
    val score: Double = 0.0
)

@Serializable
data class FunctionDef(
    val name: String,
    val description: String,
    val parameters: JsonElement
)

@Serializable
data class ToolDef(
    val type: String = "function",
    /** Null for Groq built-in tools such as {"type":"browser_search"}. */
    val function: FunctionDef? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<WireMessage>,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("max_completion_tokens") val maxCompletionTokens: Int? = null,
    @SerialName("reasoning_effort") val reasoningEffort: String? = null,
    @SerialName("top_p") val topP: Double? = null,
    val stream: Boolean = false,
    val tools: List<ToolDef>? = null,
    @SerialName("tool_choice") val toolChoice: String? = null,
    val stop: List<String>? = null
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0
)

@Serializable
data class Choice(
    val index: Int = 0,
    val message: WireMessage? = null,
    val delta: WireMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class ChatResponse(
    val id: String = "",
    val model: String = "",
    val choices: List<Choice> = emptyList(),
    val usage: Usage? = null,
    val error: ApiError? = null
)

@Serializable
data class ApiError(
    val message: String = "",
    val type: String? = null,
    val code: JsonElement? = null
)

/** Streaming delta for a partially-received tool call. */
@Serializable
data class DeltaToolCall(
    val index: Int = 0,
    val id: String? = null,
    val type: String? = null,
    val function: DeltaFunction? = null
)

@Serializable
data class DeltaFunction(
    val name: String? = null,
    val arguments: String? = null
)

@Serializable
data class DeltaMessage(
    val role: String? = null,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<DeltaToolCall>? = null
)

@Serializable
data class StreamChoice(
    val index: Int = 0,
    val delta: DeltaMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class ModelListEntry(
    val id: String = "",
    @SerialName("owned_by") val ownedBy: String? = null,
    val active: Boolean = true,
    @SerialName("context_window") val contextWindow: Int? = null,
    val name: String? = null,
    val description: String? = null,
    val pricing: ModelPricing? = null,
    @SerialName("context_length") val contextLength: Int? = null
)

@Serializable
data class ModelPricing(
    val prompt: String? = null,
    val completion: String? = null
)

@Serializable
data class ModelListResponse(
    val data: List<ModelListEntry> = emptyList()
)

@Serializable
data class StreamChunk(
    val id: String = "",
    val model: String = "",
    val choices: List<StreamChoice> = emptyList(),
    val usage: Usage? = null,
    @SerialName("x_groq") val xGroq: JsonObject? = null
)
