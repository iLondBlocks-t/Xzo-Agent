package com.xzo.agent

import android.app.Application
import com.xzo.agent.agent.AgentEngine
import com.xzo.agent.agent.FileBridge
import com.xzo.agent.agent.MemoryStore
import com.xzo.agent.core.AppSettings
import com.xzo.agent.core.SettingsRepository
import com.xzo.agent.data.db.XzoDatabase
import com.xzo.agent.data.remote.LlmClient
import com.xzo.agent.data.remote.WebClient
import com.xzo.agent.data.repo.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class XzoApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Hand-rolled DI container – no extra libraries, keeps the APK tiny. */
class AppContainer(app: Application) {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsRepo = SettingsRepository(app)

    @Volatile
    var settingsSnapshot: AppSettings = AppSettings()
        private set

    init {
        settingsRepo.flow.onEach { settingsSnapshot = it }.launchIn(scope)
    }

    val db = XzoDatabase.get(app)

    val keyProvider = object : LlmClient.KeyProvider {
        override fun groqKey(): String =
            settingsSnapshot.groqKeyOverride.ifBlank { BuildConfig.GROQ_API_KEY }

        override fun openRouterKey(): String =
            settingsSnapshot.openRouterKeyOverride.ifBlank { BuildConfig.OPENROUTER_API_KEY }
    }

    val llm = LlmClient(keyProvider)
    val web = WebClient()
    val files = FileBridge(app)
    val memory = MemoryStore(db.memories())
    val engine = AgentEngine(app, llm, web, files, memory)
    val chatRepo = ChatRepository(db, engine, settingsRepo, files)
}
