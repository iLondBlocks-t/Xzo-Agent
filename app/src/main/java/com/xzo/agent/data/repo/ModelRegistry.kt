package com.xzo.agent.data.repo

import com.xzo.agent.data.remote.LlmClient
import com.xzo.agent.data.remote.ModelCatalog
import com.xzo.agent.data.remote.ModelSpec
import com.xzo.agent.data.remote.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Keeps the model list fresh.
 *
 * Provider catalogues churn fast (Groq retired `groq/compound` on 2026-09-21 and the
 * Llama 3.x line on 2026-08-16), so the app never relies solely on hard-coded IDs:
 * it queries each provider's `/models` endpoint and merges the result with the
 * curated catalogue.
 */
class ModelRegistry(
    private val llm: LlmClient,
    private val scope: CoroutineScope
) {
    private val _models = MutableStateFlow(ModelCatalog.known())
    val models: StateFlow<List<ModelSpec>> = _models.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun refresh(onDone: (String) -> Unit = {}) {
        if (_refreshing.value) return
        scope.launch {
            _refreshing.value = true
            _lastError.value = null
            val found = mutableListOf<ModelSpec>()
            var error: String? = null
            for (p in Provider.entries) {
                runCatching { llm.listModels(p) }
                    .onSuccess { found += it }
                    .onFailure { error = it.message }
            }
            if (found.isNotEmpty()) {
                ModelCatalog.discovered = found
                _models.value = ModelCatalog.known()
            }
            _lastError.value = error
            _refreshing.value = false
            onDone(
                when {
                    found.isNotEmpty() -> "Found ${found.size} live models"
                    error != null -> "Refresh failed: $error"
                    else -> "No models returned"
                }
            )
        }
    }

    /** True when the currently selected id is known to be retired. */
    fun isRetired(id: String): Boolean = ModelCatalog.RETIRED.containsKey(id)
}
