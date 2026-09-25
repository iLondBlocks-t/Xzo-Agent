package com.xzo.agent.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.xzo.agent.agent.Prompts
import com.xzo.agent.data.remote.ModelCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "xzo_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val primaryModel: String = ModelCatalog.DEFAULT_PRIMARY,
    val fallbackModel: String = ModelCatalog.DEFAULT_FALLBACK,
    val temperature: Double = 0.6,
    val maxTokens: Int = 2048,
    val maxIterations: Int = 6,
    val persona: String = Prompts.DEFAULT_PERSONA,
    val toolsEnabled: Boolean = true,
    val selfVerify: Boolean = true,
    val streaming: Boolean = true,
    val showTrace: Boolean = true,
    val haptics: Boolean = true,
    val autoTitle: Boolean = true,
    val enterSends: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val disabledTools: Set<String> = emptySet(),
    val groqKeyOverride: String = "",
    val openRouterKeyOverride: String = "",
    val historyWindow: Int = 24,
    val animatedBackground: Boolean = true,
    val useBuiltInTools: Boolean = true,
    val reasoningEffort: String = "medium",
    val speakReplies: Boolean = false,
    val voiceLanguage: String = ""
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val primaryModel = stringPreferencesKey("primary_model")
        val fallbackModel = stringPreferencesKey("fallback_model")
        val temperature = doublePreferencesKey("temperature")
        val maxTokens = intPreferencesKey("max_tokens")
        val maxIterations = intPreferencesKey("max_iterations")
        val persona = stringPreferencesKey("persona")
        val toolsEnabled = booleanPreferencesKey("tools_enabled")
        val selfVerify = booleanPreferencesKey("self_verify")
        val streaming = booleanPreferencesKey("streaming")
        val showTrace = booleanPreferencesKey("show_trace")
        val haptics = booleanPreferencesKey("haptics")
        val autoTitle = booleanPreferencesKey("auto_title")
        val enterSends = booleanPreferencesKey("enter_sends")
        val themeMode = stringPreferencesKey("theme_mode")
        val disabledTools = stringSetPreferencesKey("disabled_tools")
        val groqKey = stringPreferencesKey("groq_key_override")
        val orKey = stringPreferencesKey("openrouter_key_override")
        val historyWindow = intPreferencesKey("history_window")
        val animatedBackground = booleanPreferencesKey("animated_bg")
        val builtInTools = booleanPreferencesKey("built_in_tools")
        val reasoningEffort = stringPreferencesKey("reasoning_effort")
        val speakReplies = booleanPreferencesKey("speak_replies")
        val voiceLanguage = stringPreferencesKey("voice_language")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            primaryModel = ModelCatalog.migrate(p[Keys.primaryModel] ?: ModelCatalog.DEFAULT_PRIMARY),
            fallbackModel = ModelCatalog.migrate(p[Keys.fallbackModel] ?: ModelCatalog.DEFAULT_FALLBACK),
            temperature = p[Keys.temperature] ?: 0.6,
            maxTokens = p[Keys.maxTokens] ?: 2048,
            maxIterations = p[Keys.maxIterations] ?: 6,
            persona = p[Keys.persona] ?: Prompts.DEFAULT_PERSONA,
            toolsEnabled = p[Keys.toolsEnabled] ?: true,
            selfVerify = p[Keys.selfVerify] ?: true,
            streaming = p[Keys.streaming] ?: true,
            showTrace = p[Keys.showTrace] ?: true,
            haptics = p[Keys.haptics] ?: true,
            autoTitle = p[Keys.autoTitle] ?: true,
            enterSends = p[Keys.enterSends] ?: false,
            themeMode = runCatching { ThemeMode.valueOf(p[Keys.themeMode] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
            disabledTools = p[Keys.disabledTools] ?: emptySet(),
            groqKeyOverride = p[Keys.groqKey].orEmpty(),
            openRouterKeyOverride = p[Keys.orKey].orEmpty(),
            historyWindow = p[Keys.historyWindow] ?: 24,
            animatedBackground = p[Keys.animatedBackground] ?: true,
            useBuiltInTools = p[Keys.builtInTools] ?: true,
            reasoningEffort = p[Keys.reasoningEffort] ?: "medium",
            speakReplies = p[Keys.speakReplies] ?: false,
            voiceLanguage = p[Keys.voiceLanguage].orEmpty()
        )
    }

    suspend fun setPrimaryModel(v: String) = edit { it[Keys.primaryModel] = v }
    suspend fun setFallbackModel(v: String) = edit { it[Keys.fallbackModel] = v }
    suspend fun setTemperature(v: Double) = edit { it[Keys.temperature] = v }
    suspend fun setMaxTokens(v: Int) = edit { it[Keys.maxTokens] = v }
    suspend fun setMaxIterations(v: Int) = edit { it[Keys.maxIterations] = v }
    suspend fun setPersona(v: String) = edit { it[Keys.persona] = v }
    suspend fun setToolsEnabled(v: Boolean) = edit { it[Keys.toolsEnabled] = v }
    suspend fun setSelfVerify(v: Boolean) = edit { it[Keys.selfVerify] = v }
    suspend fun setStreaming(v: Boolean) = edit { it[Keys.streaming] = v }
    suspend fun setShowTrace(v: Boolean) = edit { it[Keys.showTrace] = v }
    suspend fun setHaptics(v: Boolean) = edit { it[Keys.haptics] = v }
    suspend fun setAutoTitle(v: Boolean) = edit { it[Keys.autoTitle] = v }
    suspend fun setEnterSends(v: Boolean) = edit { it[Keys.enterSends] = v }
    suspend fun setThemeMode(v: ThemeMode) = edit { it[Keys.themeMode] = v.name }
    suspend fun setHistoryWindow(v: Int) = edit { it[Keys.historyWindow] = v }
    suspend fun setAnimatedBackground(v: Boolean) = edit { it[Keys.animatedBackground] = v }
    suspend fun setBuiltInTools(v: Boolean) = edit { it[Keys.builtInTools] = v }
    suspend fun setReasoningEffort(v: String) = edit { it[Keys.reasoningEffort] = v }
    suspend fun setSpeakReplies(v: Boolean) = edit { it[Keys.speakReplies] = v }
    suspend fun setVoiceLanguage(v: String) = edit { it[Keys.voiceLanguage] = v.trim() }
    suspend fun setGroqKey(v: String) = edit { it[Keys.groqKey] = v.trim() }
    suspend fun setOpenRouterKey(v: String) = edit { it[Keys.orKey] = v.trim() }

    suspend fun toggleTool(name: String, enabled: Boolean) = edit { p ->
        val cur = (p[Keys.disabledTools] ?: emptySet()).toMutableSet()
        if (enabled) cur.remove(name) else cur.add(name)
        p[Keys.disabledTools] = cur
    }

    suspend fun resetAll() = context.dataStore.edit { it.clear() }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
