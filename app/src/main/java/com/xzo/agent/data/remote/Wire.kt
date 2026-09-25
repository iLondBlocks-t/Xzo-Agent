package com.xzo.agent.data.remote

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

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

@Serializable
data class WireMessage(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null
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
    val function: FunctionDef
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<WireMessage>,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
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
data class StreamChunk(
    val id: String = "",
    val model: String = "",
    val choices: List<StreamChoice> = emptyList(),
    val usage: Usage? = null,
    @SerialName("x_groq") val xGroq: JsonObject? = null
)
