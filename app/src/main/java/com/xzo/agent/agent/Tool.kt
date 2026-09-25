package com.xzo.agent.agent

import android.content.Context
import com.xzo.agent.data.remote.LlmClient
import com.xzo.agent.data.remote.ToolDef
import com.xzo.agent.data.remote.FunctionDef
import com.xzo.agent.data.remote.WebClient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Result handed back to the model (and rendered in the trace UI). */
data class ToolResult(
    val ok: Boolean,
    /** Text fed back to the model as the `tool` message content. */
    val output: String,
    /** Short human summary for the trace chip. */
    val summary: String = output.take(120),
    /** Optional artifact (file uri, url list…) shown in the UI. */
    val artifactUri: String? = null,
    val artifactName: String? = null
) {
    companion object {
        fun ok(output: String, summary: String? = null) =
            ToolResult(true, output, summary ?: output.take(120))

        fun fail(message: String) =
            ToolResult(false, "ERROR: $message", message.take(120))
    }
}

class ToolContext(
    val appContext: Context,
    val web: WebClient,
    val llm: LlmClient,
    val files: FileBridge,
    val memory: MemoryStore,
    /** Text extracted from files the user attached to the current turn. */
    val attachments: List<Attachment>,
    val emit: suspend (String) -> Unit = {}
)

/** Compact, persistable description of what the user attached to a message. */
@kotlinx.serialization.Serializable
data class AttachmentRef(
    val name: String,
    val uri: String,
    val image: Boolean
) {
    companion object {
        private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        fun encode(attachments: List<Attachment>): String? =
            if (attachments.isEmpty()) null
            else json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(serializer()),
                attachments.map { AttachmentRef(it.name, it.uri, it.isImage) }
            )

        fun decode(raw: String?): List<AttachmentRef> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(serializer()), raw)
            }.getOrElse {
                // Older rows stored a plain comma-separated list of names.
                raw.split(",").map { n -> AttachmentRef(n.trim(), "", false) }.filter { it.name.isNotBlank() }
            }
        }
    }
}

data class Attachment(
    val name: String,
    val mime: String,
    val text: String,
    val uri: String,
    /** `data:image/jpeg;base64,…` when the attachment is an image. */
    val imageDataUrl: String? = null
) {
    val isImage: Boolean get() = imageDataUrl != null
}

interface AgentTool {
    val name: String
    val description: String
    val parameters: JsonElement
    /** Whether invoking the tool needs a visible user interaction (SAF picker). */
    val interactive: Boolean get() = false
    suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult

    fun toToolDef(): ToolDef = ToolDef(function = FunctionDef(name, description, parameters))
}

/* ---------- tiny JSON-Schema builders so tool params stay readable ---------- */

internal fun schema(
    required: List<String> = emptyList(),
    props: JsonObjectBuilderScope.() -> Unit
): JsonElement = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") { JsonObjectBuilderScope(this).props() }
    putJsonArray("required") { required.forEach { add(JsonPrimitive(it)) } }
    put("additionalProperties", false)
}

internal class JsonObjectBuilderScope(private val b: kotlinx.serialization.json.JsonObjectBuilder) {
    fun str(name: String, desc: String, enum: List<String>? = null) = b.putJsonObject(name) {
        put("type", "string"); put("description", desc)
        if (enum != null) putJsonArray("enum") { enum.forEach { add(JsonPrimitive(it)) } }
    }

    fun int(name: String, desc: String, default: Int? = null) = b.putJsonObject(name) {
        put("type", "integer"); put("description", desc)
        if (default != null) put("default", default)
    }

    fun bool(name: String, desc: String, default: Boolean? = null) = b.putJsonObject(name) {
        put("type", "boolean"); put("description", desc)
        if (default != null) put("default", default)
    }

    fun arr(name: String, desc: String, itemType: String = "string") = b.putJsonObject(name) {
        put("type", "array"); put("description", desc)
        putJsonObject("items") { put("type", itemType) }
    }
}

/* ---------- argument helpers ---------- */

fun JsonObject.str(key: String, default: String = ""): String =
    runCatching { this[key]?.jsonPrimitive?.content }.getOrNull() ?: default

fun JsonObject.intOr(key: String, default: Int): Int =
    runCatching { this[key]?.jsonPrimitive?.content?.toDouble()?.toInt() }.getOrNull() ?: default

fun JsonObject.boolOr(key: String, default: Boolean): Boolean =
    runCatching { this[key]?.jsonPrimitive?.content?.toBooleanStrictOrNull() }.getOrNull() ?: default

fun JsonObject.strList(key: String): List<String> =
    runCatching { this[key]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } }.getOrNull().orEmpty()
