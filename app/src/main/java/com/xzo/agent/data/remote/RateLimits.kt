package com.xzo.agent.data.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Response

/**
 * Live free-tier budget, read straight from the provider's own response headers
 * (`x-ratelimit-*`). Because the app is "unlimited" only in the sense that *it*
 * never charges you, showing the real remaining quota is the honest thing to do —
 * and it lets the user see exactly why a 429 happened.
 */
data class RateSnapshot(
    val provider: String,
    val requestsRemaining: Int? = null,
    val requestsLimit: Int? = null,
    val tokensRemaining: Long? = null,
    val tokensLimit: Long? = null,
    val resetRequests: String? = null,
    val resetTokens: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val requestFraction: Float?
        get() = if (requestsLimit != null && requestsLimit > 0 && requestsRemaining != null)
            requestsRemaining.toFloat() / requestsLimit else null

    val tokenFraction: Float?
        get() = if (tokensLimit != null && tokensLimit > 0 && tokensRemaining != null)
            tokensRemaining.toFloat() / tokensLimit else null

    fun summary(): String = buildString {
        append(provider)
        requestsRemaining?.let { r ->
            append(" · ").append(r)
            requestsLimit?.let { append('/').append(it) }
            append(" req")
        }
        tokensRemaining?.let { t ->
            append(" · ").append(compact(t))
            tokensLimit?.let { append('/').append(compact(it)) }
            append(" tok")
        }
        resetRequests?.let { append(" · resets in ").append(it) }
    }

    private fun compact(v: Long): String = when {
        v >= 1_000_000 -> "%.1fM".format(v / 1_000_000.0)
        v >= 1_000 -> "%.0fk".format(v / 1000.0)
        else -> v.toString()
    }
}

object RateLimitTracker {

    private val _groq = MutableStateFlow<RateSnapshot?>(null)
    val groq: StateFlow<RateSnapshot?> = _groq.asStateFlow()

    private val _openRouter = MutableStateFlow<RateSnapshot?>(null)
    val openRouter: StateFlow<RateSnapshot?> = _openRouter.asStateFlow()

    fun record(provider: Provider, response: Response) {
        fun h(name: String): String? = response.header(name)?.takeIf { it.isNotBlank() }

        val snapshot = RateSnapshot(
            provider = provider.label,
            requestsRemaining = h("x-ratelimit-remaining-requests")?.toIntOrNull(),
            requestsLimit = h("x-ratelimit-limit-requests")?.toIntOrNull(),
            tokensRemaining = h("x-ratelimit-remaining-tokens")?.toLongOrNull(),
            tokensLimit = h("x-ratelimit-limit-tokens")?.toLongOrNull(),
            resetRequests = h("x-ratelimit-reset-requests"),
            resetTokens = h("x-ratelimit-reset-tokens")
        )
        // Only publish when the provider actually sent something useful.
        if (snapshot.requestsRemaining == null && snapshot.tokensRemaining == null) return
        when (provider) {
            Provider.GROQ -> _groq.value = snapshot
            Provider.OPENROUTER -> _openRouter.value = snapshot
        }
    }

    fun forProvider(provider: Provider): StateFlow<RateSnapshot?> = when (provider) {
        Provider.GROQ -> groq
        Provider.OPENROUTER -> openRouter
    }
}
