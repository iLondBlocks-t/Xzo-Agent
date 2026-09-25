package com.xzo.agent.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ToolTrace(
    val tool: String,
    val argsPreview: String,
    val summary: String,
    val ok: Boolean,
    val durationMs: Long,
    val artifactUri: String? = null,
    val artifactName: String? = null,
    val fullOutput: String = ""
)

@Serializable
data class AgentTraceLog(
    val steps: List<ToolTrace> = emptyList(),
    val plan: String? = null,
    val verdict: String? = null,
    val verified: Boolean = false,
    val provider: String? = null,
    val model: String? = null,
    val fallbackUsed: Boolean = false,
    val iterations: Int = 0,
    val reasoning: String? = null
) {
    fun encode(): String = TraceJson.encodeToString(serializer(), this)

    companion object {
        val TraceJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun decode(s: String?): AgentTraceLog =
            if (s.isNullOrBlank()) AgentTraceLog()
            else runCatching { TraceJson.decodeFromString(serializer(), s) }.getOrDefault(AgentTraceLog())
    }
}

private val TraceJson = AgentTraceLog.TraceJson
