package com.xzo.agent.agent

import android.content.Context
import com.xzo.agent.agent.tools.CalculatorTool
import com.xzo.agent.agent.tools.ClockTool
import com.xzo.agent.agent.tools.CodeExecutionTool
import com.xzo.agent.agent.tools.CreateFileTool
import com.xzo.agent.agent.tools.DeviceInfoTool
import com.xzo.agent.agent.tools.FetchUrlTool
import com.xzo.agent.agent.tools.ReadFileTool
import com.xzo.agent.agent.tools.RecallTool
import com.xzo.agent.agent.tools.RememberTool
import com.xzo.agent.agent.tools.SummarizeTool
import com.xzo.agent.agent.tools.WebSearchTool
import com.xzo.agent.agent.tools.WriteFileTool
import com.xzo.agent.data.remote.ChatRequest
import com.xzo.agent.data.remote.LlmClient
import com.xzo.agent.data.remote.LlmException
import com.xzo.agent.data.remote.ModelCatalog
import com.xzo.agent.data.remote.ModelSpec
import com.xzo.agent.data.remote.Provider
import com.xzo.agent.data.remote.TurnResult
import com.xzo.agent.data.remote.Usage
import com.xzo.agent.data.remote.WebClient
import com.xzo.agent.data.remote.WireJson
import com.xzo.agent.data.remote.WireMessage
import com.xzo.agent.data.remote.multimodal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface AgentEvent {
    data class Status(val text: String) : AgentEvent
    data class Delta(val text: String) : AgentEvent
    data class Plan(val text: String) : AgentEvent
    data class ToolStart(val tool: String, val argsPreview: String) : AgentEvent
    data class ToolEnd(val trace: ToolTrace) : AgentEvent
    data class ProviderSwitch(val from: String, val to: String, val reason: String) : AgentEvent
    data object Verifying : AgentEvent
    data class Verified(val pass: Boolean, val verdict: String) : AgentEvent
    data class Artifact(val name: String, val uri: String) : AgentEvent
}

data class AgentInput(
    val userText: String,
    val history: List<WireMessage>,
    val attachments: List<Attachment> = emptyList(),
    val modelId: String = ModelCatalog.DEFAULT_PRIMARY,
    val fallbackModelId: String = ModelCatalog.DEFAULT_FALLBACK,
    val useBuiltInTools: Boolean = true,
    val reasoningEffort: String = "medium",
    val persona: String = Prompts.DEFAULT_PERSONA,
    val temperature: Double = 0.6,
    val maxTokens: Int = 2048,
    val toolsEnabled: Boolean = true,
    val selfVerify: Boolean = true,
    val streaming: Boolean = true,
    val maxIterations: Int = 6,
    val enabledTools: Set<String> = emptySet()
)

data class AgentOutcome(
    val answer: String,
    val trace: AgentTraceLog,
    val usage: Usage?,
    val provider: Provider,
    val model: String,
    val error: String? = null,
    val newWireMessages: List<WireMessage> = emptyList()
)

class AgentEngine(
    private val appContext: Context,
    private val llm: LlmClient,
    private val web: WebClient,
    private val files: FileBridge,
    private val memory: MemoryStore
) {

    val allTools: List<AgentTool> = listOf(
        WebSearchTool,
        FetchUrlTool,
        CodeExecutionTool,
        CalculatorTool,
        CreateFileTool,
        WriteFileTool,
        ReadFileTool,
        ClockTool,
        DeviceInfoTool,
        RememberTool,
        RecallTool,
        SummarizeTool,
        com.xzo.agent.agent.tools.ListFolderTool,
        com.xzo.agent.agent.tools.ReadFolderFileTool,
        com.xzo.agent.agent.tools.TranslateTool,
        com.xzo.agent.agent.tools.AnalyzeImageTool
    )

    private fun toolsFor(input: AgentInput): List<AgentTool> {
        if (!input.toolsEnabled) return emptyList()
        if (input.enabledTools.isEmpty()) return allTools
        return allTools.filter { it.name in input.enabledTools }
    }

    suspend fun run(input: AgentInput, onEvent: suspend (AgentEvent) -> Unit): AgentOutcome {
        val tools = toolsFor(input)
        val toolMap = tools.associateBy { it.name }
        val primary = ModelCatalog.byId(input.modelId)

        val memoryBlock = runCatching { memory.asPromptBlock() }.getOrDefault("")
        val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())
        val systemPrompt = Prompts.system(
            persona = input.persona,
            toolNames = tools.map { "${it.name}: ${it.description.take(150)}" },
            serverSideTools = primary.serverSideTools,
            memoryBlock = memoryBlock,
            deviceLocale = Locale.getDefault().toString(),
            nowIso = nowIso
        )

        val ctx = ToolContext(
            appContext = appContext,
            web = web,
            llm = llm,
            files = files,
            memory = memory,
            attachments = input.attachments,
            emit = { onEvent(AgentEvent.Status(it)) }
        )

        val convo = mutableListOf<WireMessage>()
        convo += WireMessage("system", systemPrompt)
        convo += input.history
        val textAttachments = input.attachments.filterNot { it.isImage }
        val imageAttachments = input.attachments.filter { it.isImage }

        val attachmentPreamble = if (textAttachments.isEmpty()) "" else buildString {
            appendLine("[Attached files provided by the user for this message]")
            textAttachments.forEach { a ->
                appendLine("--- ${a.name} (${a.mime}) ---")
                appendLine(a.text.take(40_000))
            }
            appendLine("[end of attachments]")
            appendLine()
        }

        val imageNote = if (imageAttachments.isEmpty()) "" else
            "[The user attached ${imageAttachments.size} image(s): " +
                imageAttachments.joinToString { it.name } +
                ". " + (if (primary.vision) "They are included below." else
                "Call analyze_image to look at them.") + "]\n\n"

        convo += if (primary.vision && imageAttachments.isNotEmpty()) {
            multimodal(
                "user",
                attachmentPreamble + input.userText,
                imageAttachments.mapNotNull { it.imageDataUrl }
            )
        } else {
            WireMessage("user", attachmentPreamble + imageNote + input.userText)
        }

        val produced = mutableListOf<WireMessage>()
        val steps = mutableListOf<ToolTrace>()
        val evidence = StringBuilder()
        var usage: Usage? = null
        var fallbackUsed = false
        var finalText = ""
        var usedModel = primary.id
        var usedProvider = primary.provider
        var iterations = 0

        loop@ while (iterations < input.maxIterations) {
            iterations++
            onEvent(AgentEvent.Status(if (iterations == 1) "Planning…" else "Thinking…"))

            val request = ChatRequest(
                model = "",
                messages = convo.toList(),
                temperature = input.temperature,
                maxTokens = input.maxTokens,
                tools = if (tools.isEmpty()) null else tools.map { it.toToolDef() },
                toolChoice = if (tools.isEmpty()) null else "auto",
                reasoningEffort = input.reasoningEffort
            )

            val turn = try {
                callWithFallback(
                    input = input,
                    request = request,
                    streaming = input.streaming,
                    onEvent = onEvent,
                    onFallback = { fallbackUsed = true }
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                val msg = friendlyError(t)
                return AgentOutcome(
                    answer = finalText.ifBlank { msg },
                    trace = AgentTraceLog(steps, null, null, false, usedProvider.label, usedModel, fallbackUsed, iterations),
                    usage = usage,
                    provider = usedProvider,
                    model = usedModel,
                    error = msg
                )
            }

            usedModel = turn.model
            usedProvider = turn.provider
            usage = mergeUsage(usage, turn.usage)

            if (turn.toolCalls.isEmpty()) {
                finalText = turn.content.trim()
                produced += WireMessage("assistant", turn.content)
                break@loop
            }

            // Record the assistant tool-call turn verbatim so the provider stays consistent.
            convo += WireMessage(
                role = "assistant",
                content = turn.content.takeIf { it.isNotBlank() },
                toolCalls = turn.toolCalls
            )

            for (call in turn.toolCalls) {
                val tool = toolMap[call.function.name]
                val argsPreview = call.function.arguments.take(160)
                onEvent(AgentEvent.ToolStart(call.function.name, argsPreview))
                val started = System.currentTimeMillis()
                val result = if (tool == null) {
                    ToolResult.fail("Unknown tool '${call.function.name}'. Available: ${toolMap.keys.joinToString()}")
                } else {
                    try {
                        val args = parseArgs(call.function.arguments)
                        tool.execute(args, ctx)
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (t: Throwable) {
                        ToolResult.fail("${call.function.name} crashed: ${t.message}")
                    }
                }
                val trace = ToolTrace(
                    tool = call.function.name,
                    argsPreview = argsPreview,
                    summary = result.summary,
                    ok = result.ok,
                    durationMs = System.currentTimeMillis() - started,
                    artifactUri = result.artifactUri,
                    artifactName = result.artifactName,
                    fullOutput = result.output.take(6000)
                )
                steps += trace
                onEvent(AgentEvent.ToolEnd(trace))
                result.artifactUri?.let { uri ->
                    onEvent(AgentEvent.Artifact(result.artifactName ?: "file", uri))
                }
                evidence.append("[").append(call.function.name).append("] ")
                    .append(result.output.take(2500)).append("\n\n")

                convo += WireMessage(
                    role = "tool",
                    content = result.output.take(24_000),
                    toolCallId = call.id.ifBlank { call.function.name },
                    name = call.function.name
                )
            }
        }

        if (finalText.isBlank()) {
            finalText = "I hit the tool-iteration limit before finishing. Here is what I gathered:\n\n" +
                steps.joinToString("\n") { "- ${it.tool}: ${it.summary}" }
        }

        var verified = false
        var verdict: String? = null
        if (input.selfVerify && finalText.isNotBlank()) {
            onEvent(AgentEvent.Verifying)
            val check = runCatching {
                selfVerify(input, finalText, evidence.toString(), onEvent)
            }.getOrNull()
            if (check != null) {
                verified = check.pass
                verdict = check.issues
                if (!check.pass && check.fixedAnswer.isNotBlank()) {
                    finalText = check.fixedAnswer
                    verified = true
                    verdict = "Self-corrected: ${check.issues}"
                }
                onEvent(AgentEvent.Verified(verified, verdict ?: ""))
            }
        }

        return AgentOutcome(
            answer = finalText,
            trace = AgentTraceLog(
                steps = steps,
                plan = null,
                verdict = verdict,
                verified = verified,
                provider = usedProvider.label,
                model = usedModel,
                fallbackUsed = fallbackUsed,
                iterations = iterations
            ),
            usage = usage,
            provider = usedProvider,
            model = usedModel,
            newWireMessages = produced
        )
    }

    /* ------------------------- provider routing ------------------------- */

    private suspend fun callWithFallback(
        input: AgentInput,
        request: ChatRequest,
        streaming: Boolean,
        onEvent: suspend (AgentEvent) -> Unit,
        onFallback: () -> Unit
    ): TurnResult {
        val chain = ModelCatalog.fallbackChain(input.modelId, input.fallbackModelId)
            .filter { llm.hasKeyFor(it.provider) }
            .ifEmpty { listOf(ModelCatalog.byId(input.modelId)) }

        var lastError: Throwable? = null
        chain.forEachIndexed { index, spec ->
            var allowBuiltIns = input.useBuiltInTools
            var attempt = 0
            while (attempt < 3) {
                attempt++
                val req = llm.adapt(spec, request, allowBuiltIns)
                try {
                    if (index > 0 && attempt == 1) {
                        onFallback()
                        onEvent(
                            AgentEvent.ProviderSwitch(
                                from = chain[index - 1].label,
                                to = spec.label,
                                reason = lastError?.message?.take(120) ?: "primary unavailable"
                            )
                        )
                    }
                    val result = if (streaming) {
                        llm.stream(spec, req) { piece -> onEvent(AgentEvent.Delta(piece)) }
                    } else {
                        llm.complete(spec, req)
                    }
                    // Surface Groq server-side tool executions in the trace.
                    result.executedTools.forEach { et ->
                        val hits = et.searchResults?.results?.size ?: 0
                        val code = et.codeResults?.firstOrNull()?.text
                        onEvent(
                            AgentEvent.ToolEnd(
                                ToolTrace(
                                    tool = "groq:" + et.name.ifBlank { et.type },
                                    argsPreview = et.arguments.take(160),
                                    summary = when {
                                        hits > 0 -> "$hits web sources browsed"
                                        !code.isNullOrBlank() -> "output: " + code.take(70)
                                        else -> et.output?.take(70) ?: "executed server-side"
                                    },
                                    ok = true,
                                    durationMs = 0,
                                    fullOutput = (code ?: et.output.orEmpty()).take(4000)
                                )
                            )
                        )
                    }
                    return result
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: LlmException) {
                    lastError = e
                    val msg = e.message.orEmpty()
                    // A 400 complaining about tools → retry once without built-in tools.
                    if (e.httpCode == 400 && allowBuiltIns && msg.contains("tool", true)) {
                        allowBuiltIns = false
                        onEvent(AgentEvent.Status("Retrying without server-side tools…"))
                        continue
                    }
                    if (!e.retryable || attempt >= 3) break
                    val backoff = if (e.rateLimited) e.retryAfterMs.coerceAtLeast(1500L) * attempt
                    else 900L * attempt * attempt
                    onEvent(
                        AgentEvent.Status(
                            if (e.rateLimited) "Rate limited — retrying in ${backoff / 1000}s…"
                            else "Retrying (${e.httpCode})…"
                        )
                    )
                    delay(backoff.coerceAtMost(12_000L))
                } catch (t: Throwable) {
                    lastError = t
                    break
                }
            }
        }
        throw lastError ?: LlmException("All providers failed")
    }

    /* ------------------------- self verification ------------------------- */

    data class Verification(val pass: Boolean, val issues: String, val fixedAnswer: String)

    private suspend fun selfVerify(
        input: AgentInput,
        draft: String,
        evidence: String,
        onEvent: suspend (AgentEvent) -> Unit
    ): Verification {
        val reviewerChain = (ModelCatalog.utility() + ModelCatalog.byId(input.fallbackModelId))
            .distinctBy { it.id }
            .filter { llm.hasKeyFor(it.provider) }

        val prompt = Prompts.verification(input.userText, draft, evidence)
        for (spec in reviewerChain) {
            val res = runCatching {
                llm.complete(
                    spec,
                    llm.adapt(
                        spec,
                        ChatRequest(
                            model = spec.id,
                            messages = listOf(
                                WireMessage("system", "You are a meticulous answer reviewer. Follow the output format exactly."),
                                WireMessage("user", prompt)
                            ),
                            temperature = 0.0,
                            maxTokens = 1400,
                            reasoningEffort = "low"
                        ),
                        allowBuiltIns = false
                    )
                )
            }.getOrNull() ?: continue
            return parseVerification(res.content)
        }
        return Verification(pass = false, issues = "verifier unavailable", fixedAnswer = "")
    }

    private fun parseVerification(text: String): Verification {
        val verdictLine = Regex("(?i)verdict\\s*:\\s*(pass|fail)").find(text)?.groupValues?.get(1)?.uppercase()
        val issues = Regex("(?i)issues\\s*:\\s*(.+)").find(text)?.groupValues?.get(1)?.trim()?.lineSequence()
            ?.firstOrNull()?.trim().orEmpty()
        val fixRaw = text.substringAfter("FIX:", "").substringAfter("fix:", "").trim()
        val fix = if (fixRaw.equals("none", true) || fixRaw.length < 12) "" else fixRaw
        val pass = verdictLine == "PASS" || (verdictLine == null && fix.isEmpty())
        return Verification(pass, issues.ifBlank { if (pass) "none" else "unspecified" }, fix)
    }

    /* ------------------------- helpers ------------------------- */

    private fun parseArgs(raw: String): JsonObject {
        val trimmed = raw.trim().ifEmpty { "{}" }
        return runCatching { WireJson.parseToJsonElement(trimmed).jsonObject }
            .getOrElse {
                // Some models emit double-encoded JSON strings.
                runCatching {
                    val inner = WireJson.parseToJsonElement(trimmed).toString().trim('"').replace("\\\"", "\"")
                    WireJson.parseToJsonElement(inner).jsonObject
                }.getOrElse { JsonObject(emptyMap()) }
            }
    }

    private fun mergeUsage(a: Usage?, b: Usage?): Usage? {
        if (a == null) return b
        if (b == null) return a
        return Usage(
            a.promptTokens + b.promptTokens,
            a.completionTokens + b.completionTokens,
            a.totalTokens + b.totalTokens
        )
    }

    private fun friendlyError(t: Throwable): String = when {
        t is LlmException && t.rateLimited ->
            "⚠️ Both providers are rate limited right now (free tier). Wait a few seconds and tap retry — " +
                "the app itself never charges or caps you."
        t is LlmException && (t.httpCode == 401 || t.httpCode == 403) ->
            "⚠️ The API key was rejected. Add a valid key in Settings, or rebuild with the GitHub secrets set."
        t is LlmException -> "⚠️ ${t.message}"
        else -> "⚠️ Unexpected error: ${t.message ?: t::class.java.simpleName}"
    }

    /** Short auto-title for a new conversation. */
    suspend fun titleFor(firstMessage: String): String? {
        val spec = ModelCatalog.utility().firstOrNull { llm.hasKeyFor(it.provider) } ?: return null
        return runCatching {
            llm.complete(
                spec,
                llm.adapt(
                    spec,
                    ChatRequest(
                        model = spec.id,
                        messages = listOf(WireMessage("user", Prompts.titling(firstMessage))),
                        temperature = 0.3,
                        maxTokens = 32,
                        reasoningEffort = "low"
                    ),
                    allowBuiltIns = false
                )
            ).content.trim().trim('"', '.', '،').lines().last().take(48).ifBlank { null }
        }.getOrNull()
    }
}
