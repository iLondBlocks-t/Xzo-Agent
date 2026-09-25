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
import com.xzo.agent.data.remote.text
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

enum class AgentMode(val label: String, val hint: String) {
    CHAT("Chat", "Fast, no tools — plain conversation"),
    AGENT("Agent", "Full tool loop: search, code, files, self-verify"),
    DEEP_RESEARCH("Deep Research", "Plans, investigates in parallel, writes and critiques a report")
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
    val enabledTools: Set<String> = emptySet(),
    val mode: AgentMode = AgentMode.AGENT,
    val autoRoute: Boolean = true
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
        com.xzo.agent.agent.tools.AnalyzeImageTool,
        com.xzo.agent.agent.tools.DelegateTool,
        com.xzo.agent.agent.tools.OcrTool,
        com.xzo.agent.agent.tools.OfflineTranslateTool,
        com.xzo.agent.agent.tools.DetectLanguageTool
    )

    private val deepResearch by lazy { DeepResearch(llm) }

    private fun toolsFor(input: AgentInput): List<AgentTool> {
        if (!input.toolsEnabled || input.mode == AgentMode.CHAT) return emptyList()
        if (input.enabledTools.isEmpty()) return allTools
        return allTools.filter { it.name in input.enabledTools }
    }

    suspend fun run(input: AgentInput, onEvent: suspend (AgentEvent) -> Unit): AgentOutcome {
        if (input.mode == AgentMode.DEEP_RESEARCH) return runDeepResearch(input, onEvent)
        val tools = toolsFor(input)
        val toolMap = tools.associateBy { it.name }

        val routed = if (input.autoRoute) {
            AutoRouter.decide(
                userText = input.userText,
                hasImages = input.attachments.any { it.isImage },
                mode = input.mode,
                llm = llm,
                userChoice = input.modelId
            )
        } else null
        val effectiveInput = routed?.let { input.copy(modelId = it.model.id) } ?: input
        val primary = ModelCatalog.byId(effectiveInput.modelId)
        if (routed != null && routed.model.id != ModelCatalog.byId(input.modelId).id) {
            onEvent(AgentEvent.Status("Routing to ${routed.model.label} — ${routed.reason}"))
        }

        @Suppress("NAME_SHADOWING") val input = effectiveInput
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
        var lastReasoning: String? = null

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
            turn.reasoning?.takeIf { it.isNotBlank() }?.let { lastReasoning = it }
            usage = mergeUsage(usage, turn.usage)

            if (turn.toolCalls.isEmpty()) {
                finalText = turn.content.trim()

                // The reply was cut off mid-sentence because it hit the token ceiling —
                // transparently ask for the remainder and stitch it on, so the user never
                // sees a truncated answer.
                if (turn.finishReason == "length" && finalText.isNotBlank()) {
                    finalText = continueTruncated(
                        input = input,
                        convo = convo,
                        soFar = finalText,
                        onEvent = onEvent
                    ) { usage = mergeUsage(usage, it) }
                }

                produced += WireMessage("assistant", finalText)
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
                iterations = iterations,
                reasoning = lastReasoning?.take(4000)
            ),
            usage = usage,
            provider = usedProvider,
            model = usedModel,
            newWireMessages = produced
        )
    }

    /* ------------------------- deep research ------------------------- */

    private suspend fun runDeepResearch(
        input: AgentInput,
        onEvent: suspend (AgentEvent) -> Unit
    ): AgentOutcome {
        val started = System.currentTimeMillis()
        val context = buildString {
            input.history.takeLast(6).forEach { m ->
                appendLine("${m.role}: ${m.text.take(700)}")
            }
            input.attachments.filterNot { it.isImage }.forEach { a ->
                appendLine("--- attached ${a.name} ---")
                appendLine(a.text.take(6000))
            }
        }

        val report = try {
            deepResearch.run(
                question = input.userText,
                conversationContext = context,
                maxSubTasks = input.maxIterations.coerceIn(2, 6),
                onEvent = onEvent
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            val msg = friendlyError(t)
            return AgentOutcome(
                answer = msg,
                trace = AgentTraceLog(emptyList(), null, null, false, null, null, false, 0),
                usage = null,
                provider = Provider.GROQ,
                model = input.modelId,
                error = msg
            )
        }

        val steps = report.findings.map { f ->
            ToolTrace(
                tool = "${f.subTask.role.emoji} ${f.subTask.role.label}",
                argsPreview = f.subTask.task.take(160),
                summary = if (f.ok) "${f.sources.size} sources · ${f.model}" else "failed",
                ok = f.ok,
                durationMs = f.durationMs,
                fullOutput = f.content.take(6000)
            )
        }

        val verified = report.findings.count { it.ok } >= (report.plan.size + 1) / 2
        onEvent(AgentEvent.Verified(verified, if (report.revised) "revised after critique" else "critique passed"))

        return AgentOutcome(
            answer = report.answer,
            trace = AgentTraceLog(
                steps = steps,
                plan = report.plan.joinToString("\n") { "${it.role.emoji} ${it.task}" },
                verdict = report.critique.take(300),
                verified = verified,
                provider = "multi-agent",
                model = "deep-research",
                fallbackUsed = false,
                iterations = report.plan.size
            ),
            usage = null,
            provider = Provider.GROQ,
            model = "deep-research (${report.plan.size} threads, ${(System.currentTimeMillis() - started) / 1000}s)"
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
                    // Surface the primary route server-side tool executions in the trace.
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

    /**
     * Continues an answer that stopped because of `finish_reason: length`.
     * Up to two extra passes, each appended without repeating the overlap.
     */
    private suspend fun continueTruncated(
        input: AgentInput,
        convo: List<WireMessage>,
        soFar: String,
        onEvent: suspend (AgentEvent) -> Unit,
        onUsage: (Usage?) -> Unit
    ): String {
        var text = soFar
        repeat(2) { pass ->
            onEvent(AgentEvent.Status("Continuing the answer…"))
            val spec = ModelCatalog.byId(input.modelId)
            val request = ChatRequest(
                model = spec.id,
                messages = convo + listOf(
                    WireMessage("assistant", text.takeLast(4000)),
                    WireMessage(
                        "user",
                        "Continue exactly where that stopped. Do not repeat anything, do not " +
                            "re-introduce the topic, just carry on from the final character."
                    )
                ),
                temperature = input.temperature,
                maxTokens = input.maxTokens,
                reasoningEffort = "low"
            )
            val more = runCatching {
                llm.complete(spec, llm.adapt(spec, request, allowBuiltIns = false))
            }.getOrNull() ?: return text

            onUsage(more.usage)
            val addition = more.content.trim()
            if (addition.isBlank()) return text
            text = joinWithoutOverlap(text, addition)
            if (more.finishReason != "length") return text
        }
        return text
    }

    /** Glues two chunks together, dropping any duplicated seam the model repeated. */
    internal fun joinWithoutOverlap(head: String, tail: String): String {
        val maxOverlap = minOf(240, head.length, tail.length)
        for (len in maxOverlap downTo 24) {
            if (head.regionMatches(head.length - len, tail, 0, len, ignoreCase = true)) {
                return head + tail.substring(len)
            }
        }
        val sep = if (head.endsWith("\n") || tail.startsWith("\n")) "" else
            if (head.lastOrNull()?.isLetterOrDigit() == true) " " else ""
        return head + sep + tail
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
        t is LlmException && t.missingKey ->
            "🔑 **Xzo is not connected yet.**\n\nNo access key is saved, so there is no cloud brain to think " +
                "with. Open **Settings → Access keys**, paste a key and tap **Test connection**.\n\n" +
                "Everything that runs on the device — reading text from photos, offline translation, the " +
                "calculator and your saved files — keeps working without a key."

        t is LlmException && t.rejectedKey ->
            "🔑 **That access key was refused.**\n\nThree things cause this:\n" +
                "1. the key was only partly copied (they are long — copy all of it),\n" +
                "2. the key was deleted or regenerated,\n" +
                "3. the key was posted somewhere public and got disabled automatically.\n\n" +
                "Create a new key, paste it in **Settings → Access keys**, then tap **Test connection** — " +
                "it will tell you immediately whether the new key works."

        t is LlmException && t.rateLimited ->
            "⏳ **Every route is at capacity right now.**\n\nXzo already retried and switched routes. " +
                "Wait a few seconds and tap retry. Xzo itself never charges or caps you."

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
