package com.xzo.agent.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xzo.agent.AppContainer
import com.xzo.agent.XzoApp
import com.xzo.agent.agent.AgentEvent
import com.xzo.agent.agent.AgentInput
import com.xzo.agent.agent.AgentTraceLog
import com.xzo.agent.agent.Attachment
import com.xzo.agent.agent.ToolTrace
import com.xzo.agent.core.AppSettings
import com.xzo.agent.data.db.ArtifactEntity
import com.xzo.agent.data.db.ConversationEntity
import com.xzo.agent.data.db.MessageEntity
import com.xzo.agent.data.remote.ModelCatalog
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val conversationId: Long = 0,
    val title: String = "New chat",
    val messages: List<MessageEntity> = emptyList(),
    val conversations: List<ConversationEntity> = emptyList(),
    val artifacts: List<ArtifactEntity> = emptyList(),
    val input: String = "",
    val attachments: List<Attachment> = emptyList(),
    val busy: Boolean = false,
    val verifying: Boolean = false,
    val status: String? = null,
    val streamingText: String = "",
    val liveTraces: List<ToolTrace> = emptyList(),
    val banner: String? = null,
    val settings: AppSettings = AppSettings(),
    val searchQuery: String = "",
    val searchResults: List<MessageEntity> = emptyList(),
    val voice: VoiceState = VoiceState.IDLE,
    val speakingMessageId: Long = -1,
    val models: List<com.xzo.agent.data.remote.ModelSpec> = com.xzo.agent.data.remote.ModelCatalog.known(),
    val refreshingModels: Boolean = false,
    val mode: com.xzo.agent.agent.AgentMode = com.xzo.agent.agent.AgentMode.AGENT,
    val plan: String? = null,
    val lastError: String? = null,
    val automations: List<com.xzo.agent.data.db.AutomationEntity> = emptyList(),
    val rateLimit: com.xzo.agent.data.remote.RateSnapshot? = null,
    val keyChecks: Map<com.xzo.agent.data.remote.Provider, com.xzo.agent.data.remote.LlmClient.KeyCheck> = emptyMap(),
    val checkingKey: com.xzo.agent.data.remote.Provider? = null,
    val needsSetup: Boolean = false,
    val followUps: List<String> = emptyList()
)

enum class VoiceState { IDLE, RECORDING, TRANSCRIBING }

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val container: AppContainer = (app as XzoApp).container
    private val repo = container.chatRepo

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val currentId = MutableStateFlow(0L)
    private var runJob: Job? = null

    val fileRequests = container.files.requests

    /** Emitted when the mic is tapped without RECORD_AUDIO permission. */
    val micPermissionRequests = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 2)

    init {
        viewModelScope.launch {
            container.settingsRepo.flow.collectLatest { s -> _state.update { it.copy(settings = s) } }
        }
        viewModelScope.launch {
            repo.conversations().collectLatest { list -> _state.update { it.copy(conversations = list) } }
        }
        viewModelScope.launch {
            repo.artifacts().collectLatest { list -> _state.update { it.copy(artifacts = list) } }
        }
        viewModelScope.launch {
            repo.automations().collectLatest { list -> _state.update { it.copy(automations = list) } }
        }
        viewModelScope.launch {
            @Suppress("OPT_IN_USAGE")
            currentId.flatMapLatest { id -> repo.messages(id) }
                .collectLatest { msgs -> _state.update { it.copy(messages = msgs) } }
        }
        viewModelScope.launch {
            @Suppress("OPT_IN_USAGE")
            currentId.flatMapLatest { id -> repo.conversation(id) }
                .collectLatest { c -> _state.update { it.copy(title = c?.title ?: "New chat") } }
        }
        viewModelScope.launch {
            container.modelRegistry.models.collectLatest { m -> _state.update { it.copy(models = m) } }
        }
        viewModelScope.launch {
            com.xzo.agent.data.remote.RateLimitTracker.groq.collectLatest { snap ->
                _state.update { it.copy(rateLimit = snap) }
            }
        }
        viewModelScope.launch {
            container.modelRegistry.refreshing.collectLatest { r -> _state.update { it.copy(refreshingModels = r) } }
        }
        viewModelScope.launch {
            // Wait for the stored overrides to load before deciding setup is needed.
            kotlinx.coroutines.delay(250)
            if (!container.llm.hasAnyKey()) _state.update { it.copy(needsSetup = true) }
        }
        viewModelScope.launch {
            val first = repo.conversations().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList()).value
            val id = first.firstOrNull()?.id ?: repo.newConversation(container.settingsSnapshot.primaryModel)
            select(id)
        }
    }

    /* ------------------------------ navigation ------------------------------ */

    fun select(id: Long) {
        currentId.value = id
        _state.update { it.copy(conversationId = id, streamingText = "", liveTraces = emptyList(), status = null) }
    }

    fun newChat() = viewModelScope.launch {
        val id = repo.newConversation(_state.value.settings.primaryModel)
        select(id)
    }

    fun deleteChat(id: Long) = viewModelScope.launch {
        repo.deleteConversation(id)
        if (_state.value.conversationId == id) {
            val next = _state.value.conversations.firstOrNull { it.id != id }?.id
                ?: repo.newConversation(_state.value.settings.primaryModel)
            select(next)
        }
    }

    fun renameChat(id: Long, title: String) = viewModelScope.launch { repo.rename(id, title) }
    fun pinChat(id: Long, pinned: Boolean) = viewModelScope.launch { repo.setPinned(id, pinned) }
    fun clearCurrent() = viewModelScope.launch { repo.clearConversation(_state.value.conversationId) }

    fun exportPdf() = viewModelScope.launch {
        val s = _state.value
        if (s.messages.isEmpty()) { banner("Nothing to export yet"); return@launch }
        banner("Building PDF…")
        val saved = com.xzo.agent.core.PdfExporter.export(s.title, s.messages, container.files)
        banner(if (saved != null) "Saved ${saved.name}" else "PDF export cancelled")
    }

    fun exportCurrent() = viewModelScope.launch {
        val saved = repo.exportConversation(_state.value.conversationId)
        banner(if (saved != null) "Exported to ${saved.name}" else "Export cancelled")
    }

    fun search(q: String) = viewModelScope.launch {
        _state.update { it.copy(searchQuery = q) }
        val res = if (q.isBlank()) emptyList() else repo.searchMessages(q)
        _state.update { it.copy(searchResults = res) }
    }

    /* ------------------------------ composing ------------------------------ */

    fun onInputChange(v: String) = _state.update { it.copy(input = v) }

    fun useFollowUp(text: String) {
        _state.update { it.copy(followUps = emptyList()) }
        send(text)
    }

    fun setMode(mode: com.xzo.agent.agent.AgentMode) = _state.update { it.copy(mode = mode) }

    fun banner(text: String?) = _state.update { it.copy(banner = text) }

    fun attachFile(imagesOnly: Boolean = false) = viewModelScope.launch {
        val filter = if (imagesOnly) arrayOf("image/*") else arrayOf("*/*")
        val loaded = container.files.openDocument(filter)
        when {
            loaded == null -> banner("No file selected")

            loaded.imageDataUrl != null -> {
                _state.update {
                    it.copy(
                        attachments = it.attachments + Attachment(
                            name = loaded.name,
                            mime = loaded.mime,
                            text = loaded.text,
                            uri = loaded.uri.toString(),
                            imageDataUrl = loaded.imageDataUrl
                        )
                    )
                }
                banner("Attached image ${loaded.name} (${com.xzo.agent.util.Images.approxKb(loaded.imageDataUrl)} KB)")
            }

            loaded.text.isBlank() -> banner("“${loaded.name}” has no readable text")

            else -> {
                _state.update {
                    it.copy(
                        attachments = it.attachments + Attachment(
                            loaded.name, loaded.mime, loaded.text, loaded.uri.toString()
                        )
                    )
                }
                banner("Attached ${loaded.name} (${loaded.text.length} chars)")
            }
        }
    }

    fun capturePhoto() = viewModelScope.launch {
        val loaded = container.files.capturePhoto()
        if (loaded?.imageDataUrl == null) {
            banner("No photo captured")
            return@launch
        }
        _state.update {
            it.copy(
                attachments = it.attachments + Attachment(
                    name = loaded.name,
                    mime = loaded.mime,
                    text = loaded.text,
                    uri = loaded.uri.toString(),
                    imageDataUrl = loaded.imageDataUrl
                )
            )
        }
        banner("Photo attached — ask about it, or say “read the text in this”")
    }

    fun removeAttachment(uri: String) =
        _state.update { it.copy(attachments = it.attachments.filterNot { a -> a.uri == uri }) }

    fun stop() {
        runJob?.cancel()
        container.files.cancelAll()
        _state.update { it.copy(busy = false, status = null, verifying = false) }
    }

    /** Pull a user message back into the composer and drop everything after it. */
    fun editMessage(m: MessageEntity) = viewModelScope.launch {
        if (m.role != "user") return@launch
        repo.truncateFrom(m.conversationId, m.id)
        _state.update { it.copy(input = m.content) }
        banner("Edit and send again")
    }

    /** Fork the conversation at this message so you can explore another direction. */
    fun branchFrom(m: MessageEntity) = viewModelScope.launch {
        val newId = repo.branch(m.conversationId, m.id)
        select(newId)
        banner("Branched into a new chat")
    }

    /** Re-answer the last question, optionally forcing a specific model. */
    fun regenerate(modelId: String? = null) {
        val msgs = _state.value.messages
        val lastUser = msgs.lastOrNull { it.role == "user" } ?: return
        viewModelScope.launch {
            repo.truncateFrom(lastUser.conversationId, lastUser.id + 1)
            modelId?.let { container.settingsRepo.setPrimaryModel(it) }
            send(lastUser.content, reuseText = true)
        }
    }

    fun retryLast() {
        val last = _state.value.messages.lastOrNull { it.role == "user" } ?: return
        viewModelScope.launch {
            _state.value.messages.lastOrNull()?.takeIf { it.role == "assistant" }?.let { repo.deleteMessage(it.id) }
            send(last.content, reuseText = true)
        }
    }

    fun send(text: String = _state.value.input, reuseText: Boolean = false) {
        val prompt = text.trim()
        if (prompt.isEmpty() || _state.value.busy) return
        val s = _state.value
        val attachments = s.attachments

        runJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    input = if (reuseText) it.input else "",
                    attachments = emptyList(),
                    busy = true,
                    status = "Planning…",
                    streamingText = "",
                    liveTraces = emptyList(),
                    verifying = false,
                    plan = null,
                    banner = null,
                    lastError = null,
                    followUps = emptyList()
                )
            }

            val cid = repo.ensureConversation(s.conversationId.takeIf { it > 0 }, s.settings.primaryModel)
            if (cid != s.conversationId) select(cid)

            val history = repo.wireHistory(cid, s.settings.historyWindow)

            repo.addMessage(
                MessageEntity(
                    conversationId = cid,
                    role = "user",
                    content = prompt,
                    attachmentsJson = if (attachments.isEmpty()) null
                    else attachments.joinToString(", ") { it.name }
                )
            )

            val started = System.currentTimeMillis()
            val input = AgentInput(
                userText = prompt,
                history = history,
                attachments = attachments,
                modelId = s.settings.primaryModel,
                fallbackModelId = s.settings.fallbackModel,
                persona = s.settings.persona,
                temperature = s.settings.temperature,
                maxTokens = s.settings.maxTokens,
                toolsEnabled = s.settings.toolsEnabled,
                selfVerify = s.settings.selfVerify,
                streaming = s.settings.streaming,
                maxIterations = s.settings.maxIterations,
                useBuiltInTools = s.settings.useBuiltInTools,
                mode = s.mode,
                autoRoute = s.settings.autoRoute,
                reasoningEffort = s.settings.reasoningEffort,
                enabledTools = container.engine.allTools
                    .map { it.name }
                    .filterNot { it in s.settings.disabledTools }
                    .toSet()
            )

            val outcome = try {
                container.engine.run(input) { event -> handleEvent(event, cid) }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) {
                    _state.update { it.copy(busy = false, status = null, streamingText = "") }
                    return@launch
                }
                null
            }

            if (outcome == null) {
                repo.addMessage(
                    MessageEntity(
                        conversationId = cid,
                        role = "assistant",
                        content = "The request failed unexpectedly. Tap retry.",
                        error = true
                    )
                )
            } else {
                if (outcome.error != null) _state.update { it.copy(lastError = outcome.error) }
                repo.addMessage(
                    MessageEntity(
                        conversationId = cid,
                        role = "assistant",
                        content = outcome.answer,
                        model = outcome.model,
                        provider = outcome.provider.label,
                        verified = outcome.trace.verified,
                        verdict = outcome.trace.verdict,
                        error = outcome.error != null,
                        traceJson = outcome.trace.encode(),
                        promptTokens = outcome.usage?.promptTokens ?: 0,
                        completionTokens = outcome.usage?.completionTokens ?: 0,
                        latencyMs = System.currentTimeMillis() - started
                    )
                )
                outcome.trace.steps.forEach { st ->
                    if (st.artifactUri != null && st.tool.contains("file")) {
                        repo.recordArtifact(
                            ArtifactEntity(
                                conversationId = cid,
                                name = st.artifactName ?: "file",
                                uri = st.artifactUri,
                                mime = "*/*",
                                bytes = 0
                            )
                        )
                    }
                }
            }

            if (_state.value.settings.autoTitle) repo.maybeAutoTitle(cid, prompt)

            // After the answer is on screen: learn durable facts and propose next steps.
            if (outcome != null && outcome.answer.isNotBlank() && outcome.error == null) {
                launch {
                    runCatching {
                        com.xzo.agent.agent.MemoryHarvester.harvest(prompt, container.llm, container.memory)
                    }
                }
                launch {
                    val ups = runCatching {
                        com.xzo.agent.agent.FollowUps.suggest(prompt, outcome.answer, container.llm)
                    }.getOrDefault(emptyList())
                    _state.update { it.copy(followUps = ups) }
                }
            }
            if (_state.value.settings.speakReplies && outcome != null && outcome.answer.isNotBlank()) {
                container.speaker.speak(outcome.answer)
            }
            if (!container.appInForeground && outcome != null && outcome.answer.isNotBlank()) {
                container.notifier.notifyResult(
                    title = _state.value.title.ifBlank { "Xzo finished" },
                    answer = outcome.answer
                )
            }

            _state.update {
                it.copy(
                    busy = false, status = null, streamingText = "",
                    verifying = false, liveTraces = emptyList(), plan = null
                )
            }
        }
    }

    private fun handleEvent(event: AgentEvent, cid: Long) {
        when (event) {
            is AgentEvent.Status -> _state.update { it.copy(status = event.text) }
            is AgentEvent.Delta -> _state.update {
                it.copy(streamingText = it.streamingText + event.text, status = null)
            }
            is AgentEvent.Plan -> _state.update { it.copy(plan = event.text) }
            is AgentEvent.ToolStart -> _state.update {
                it.copy(status = "Using ${event.tool}…")
            }
            is AgentEvent.ToolEnd -> _state.update {
                it.copy(liveTraces = it.liveTraces + event.trace, status = null)
            }
            is AgentEvent.ProviderSwitch -> _state.update {
                it.copy(status = "Switching to ${event.to} (${event.reason})")
            }
            AgentEvent.Verifying -> _state.update { it.copy(verifying = true, status = "Verifying answer…") }
            is AgentEvent.Verified -> _state.update { it.copy(verifying = false, status = null) }
            is AgentEvent.Artifact -> _state.update { it.copy(banner = "Saved ${event.name}") }
        }
    }

    /* ------------------------------ voice ------------------------------ */

    fun refreshModels() = container.modelRegistry.refresh { msg -> banner(msg) }

    /* ------------------------------ access keys ------------------------------ */

    /** Saves a key and immediately proves whether it actually works. */
    fun saveAndTestKey(provider: com.xzo.agent.data.remote.Provider, key: String) =
        viewModelScope.launch {
            when (provider) {
                com.xzo.agent.data.remote.Provider.GROQ -> container.settingsRepo.setGroqKey(key)
                com.xzo.agent.data.remote.Provider.OPENROUTER -> container.settingsRepo.setOpenRouterKey(key)
            }
            // Give the settings flow a moment to publish before probing.
            kotlinx.coroutines.delay(120)
            testKey(provider)
        }

    fun testKey(provider: com.xzo.agent.data.remote.Provider) = viewModelScope.launch {
        _state.update { it.copy(checkingKey = provider) }
        val result = container.llm.verifyKey(provider)
        _state.update {
            it.copy(
                checkingKey = null,
                keyChecks = it.keyChecks + (provider to result),
                needsSetup = if (result.ok) false else it.needsSetup
            )
        }
        banner(if (result.ok) "Connected" else result.detail.take(120))
    }

    fun dismissSetup() = _state.update { it.copy(needsSetup = false) }

    fun onMicTap(hasPermission: Boolean) {
        if (!hasPermission) {
            viewModelScope.launch { micPermissionRequests.emit(Unit) }
            return
        }
        when (_state.value.voice) {
            VoiceState.RECORDING -> finishRecording()
            VoiceState.TRANSCRIBING -> Unit
            VoiceState.IDLE -> {
                val started = container.recorder.start()
                if (started) {
                    _state.update { it.copy(voice = VoiceState.RECORDING) }
                    banner("Listening… tap the mic again to stop")
                } else {
                    banner("Could not start the microphone")
                }
            }
        }
    }

    private fun finishRecording() {
        val file = container.recorder.stop()
        if (file == null) {
            _state.update { it.copy(voice = VoiceState.IDLE) }
            banner("Recording too short")
            return
        }
        _state.update { it.copy(voice = VoiceState.TRANSCRIBING) }
        viewModelScope.launch {
            val lang = _state.value.settings.voiceLanguage.takeIf { it.isNotBlank() }
            val text = runCatching { container.llm.transcribe(file, lang) }
                .getOrElse { err ->
                    banner("Transcription failed: ${err.message}")
                    ""
                }
            runCatching { file.delete() }
            _state.update {
                it.copy(
                    voice = VoiceState.IDLE,
                    input = (it.input.trim() + " " + text.trim()).trim()
                )
            }
            if (text.isNotBlank()) banner(null)
        }
    }

    fun cancelRecording() {
        container.recorder.cancel()
        _state.update { it.copy(voice = VoiceState.IDLE) }
    }

    fun speak(message: MessageEntity) {
        if (_state.value.speakingMessageId == message.id) {
            container.speaker.stop()
            _state.update { it.copy(speakingMessageId = -1) }
            return
        }
        _state.update { it.copy(speakingMessageId = message.id) }
        viewModelScope.launch { container.speaker.speak(message.content) }
    }

    fun stopSpeaking() {
        container.speaker.stop()
        _state.update { it.copy(speakingMessageId = -1) }
    }

    override fun onCleared() {
        super.onCleared()
        container.recorder.cancel()
        container.speaker.stop()
    }

    /* ------------------------------ settings ------------------------------ */

    val settingsRepo get() = container.settingsRepo
    val toolNames get() = container.engine.allTools.map { it.name to it.description }

    fun modelLabel(id: String) = ModelCatalog.byId(id).label

    fun keyStatus(): Pair<Boolean, Boolean> =
        container.keyProvider.groqKey().isNotBlank() to container.keyProvider.openRouterKey().isNotBlank()

    suspend fun stats() = repo.stats()

    suspend fun listMemories(): List<Pair<String, String>> = container.memory.all()

    fun forgetMemory(key: String) = viewModelScope.launch { container.memory.forget(key) }

    fun deleteArtifact(id: Long) = viewModelScope.launch { repo.deleteArtifact(id) }

    /* ------------------------------ automations ------------------------------ */

    fun saveAutomation(a: com.xzo.agent.data.db.AutomationEntity) = viewModelScope.launch {
        val id = repo.upsertAutomation(a)
        repo.automationById(id)?.let {
            com.xzo.agent.core.AutomationScheduler.schedule(getApplication<Application>(), it)
        }
        banner(
            if (a.enabled) "Scheduled “${a.title}” daily at %02d:%02d".format(a.hour, a.minute)
            else "Saved “${a.title}” (disabled)"
        )
    }

    fun toggleAutomation(a: com.xzo.agent.data.db.AutomationEntity, enabled: Boolean) =
        viewModelScope.launch {
            repo.setAutomationEnabled(a.id, enabled)
            val updated = a.copy(enabled = enabled)
            if (enabled) com.xzo.agent.core.AutomationScheduler.schedule(getApplication<Application>(), updated)
            else com.xzo.agent.core.AutomationScheduler.cancel(getApplication<Application>(), a.id)
        }

    fun runAutomationNow(a: com.xzo.agent.data.db.AutomationEntity) {
        com.xzo.agent.core.AutomationScheduler.runNow(getApplication<Application>(), a.id)
        banner("Running “${a.title}” now — you'll get a notification")
    }

    fun deleteAutomation(a: com.xzo.agent.data.db.AutomationEntity) = viewModelScope.launch {
        com.xzo.agent.core.AutomationScheduler.cancel(getApplication<Application>(), a.id)
        repo.deleteAutomation(a.id)
    }

    fun openAutomationChat(a: com.xzo.agent.data.db.AutomationEntity) {
        if (a.conversationId > 0) select(a.conversationId)
    }

    fun exportBackup() = viewModelScope.launch {
        val saved = container.backup.export()
        banner(if (saved != null) "Backup saved as ${saved.name}" else "Backup cancelled")
    }

    fun importBackup() = viewModelScope.launch {
        val r = container.backup.import()
        banner(
            when {
                r.error == "cancelled" -> "Import cancelled"
                r.error != null -> "Import failed: ${r.error}"
                else -> "Imported ${r.conversations} chats, ${r.messages} messages, ${r.memories} memories"
            }
        )
    }

    fun traceOf(m: MessageEntity): AgentTraceLog = AgentTraceLog.decode(m.traceJson)
}
