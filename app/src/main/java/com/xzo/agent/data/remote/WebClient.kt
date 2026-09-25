package com.xzo.agent.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class SearchHit(
    val title: String,
    val url: String,
    val snippet: String
)

/**
 * Key-less web access used as the fallback path when Groq Compound's built-in
 * search is unavailable (e.g. when the user picked a plain chat model).
 *
 * Sources, tried in order:
 *  1. DuckDuckGo Instant Answer API (json, no key)
 *  2. DuckDuckGo HTML-lite result scrape (no key)
 *  3. Wikipedia OpenSearch API (no key)
 */
class WebClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun get(url: String, accept: String = "*/*"): String =
        http.newCall(
            Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", accept)
                .header("Accept-Language", "en,ar;q=0.8")
                .build()
        ).execute().use { r ->
            if (!r.isSuccessful) throw LlmException("HTTP ${r.code} fetching $url", r.code, retryable = r.code >= 500)
            r.body?.string().orEmpty()
        }

    suspend fun search(query: String, limit: Int = 6): List<SearchHit> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query, "UTF-8")
        val out = LinkedHashMap<String, SearchHit>()

        runCatching {
            val body = get("https://api.duckduckgo.com/?q=$q&format=json&no_html=1&skip_disambig=1")
            val root = json.parseToJsonElement(body).jsonObject
            root["AbstractText"]?.jsonPrimitive?.contentOrNullSafe()?.takeIf { it.isNotBlank() }?.let { abs ->
                val url = root["AbstractURL"]?.jsonPrimitive?.contentOrNullSafe().orEmpty()
                val src = root["AbstractSource"]?.jsonPrimitive?.contentOrNullSafe() ?: "DuckDuckGo"
                out[url.ifBlank { src }] = SearchHit(src, url, abs)
            }
            (root["RelatedTopics"] as? JsonArray)?.forEach { el ->
                collectTopic(el as? JsonObject ?: return@forEach, out)
            }
            root["Answer"]?.jsonPrimitive?.contentOrNullSafe()?.takeIf { it.isNotBlank() }?.let {
                out["answer"] = SearchHit("Instant answer", "", it)
            }
        }

        if (out.size < limit) runCatching { out.putAllHits(scrapeHtml(q)) }
        if (out.isEmpty()) runCatching { out.putAllHits(wikipedia(q)) }

        out.values.take(limit)
    }

    private fun MutableMap<String, SearchHit>.putAllHits(hits: List<SearchHit>) {
        hits.forEach { h -> putIfAbsent(h.url.ifBlank { h.title }, h) }
    }

    private fun collectTopic(obj: JsonObject, out: MutableMap<String, SearchHit>) {
        val text = obj["Text"]?.jsonPrimitive?.contentOrNullSafe()
        val url = obj["FirstURL"]?.jsonPrimitive?.contentOrNullSafe()
        if (!text.isNullOrBlank() && !url.isNullOrBlank()) {
            out.putIfAbsent(url, SearchHit(text.take(90), url, text))
        }
        (obj["Topics"] as? JsonArray)?.forEach { collectTopic(it as? JsonObject ?: return@forEach, out) }
    }

    private fun scrapeHtml(encodedQuery: String): List<SearchHit> {
        val html = get("https://html.duckduckgo.com/html/?q=$encodedQuery", "text/html")
        val hits = mutableListOf<SearchHit>()
        val linkRe = Regex(
            """<a[^>]+class="result__a"[^>]+href="([^"]+)"[^>]*>(.*?)</a>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val snipRe = Regex(
            """<a[^>]+class="result__snippet"[^>]*>(.*?)</a>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val links = linkRe.findAll(html).toList()
        val snips = snipRe.findAll(html).toList()
        links.forEachIndexed { i, m ->
            val rawUrl = m.groupValues[1]
            val url = decodeDdgUrl(rawUrl)
            val title = stripHtml(m.groupValues[2])
            val snippet = snips.getOrNull(i)?.groupValues?.get(1)?.let(::stripHtml).orEmpty()
            if (url.isNotBlank() && title.isNotBlank()) hits += SearchHit(title, url, snippet)
        }
        return hits
    }

    private fun wikipedia(encodedQuery: String): List<SearchHit> {
        val body = get("https://en.wikipedia.org/w/api.php?action=opensearch&limit=5&format=json&search=$encodedQuery")
        val arr = json.parseToJsonElement(body).jsonArray
        val titles = arr.getOrNull(1)?.jsonArray ?: return emptyList()
        val descs = arr.getOrNull(2)?.jsonArray
        val urls = arr.getOrNull(3)?.jsonArray
        return titles.mapIndexed { i, t ->
            SearchHit(
                t.jsonPrimitive.contentOrNullSafe().orEmpty(),
                urls?.getOrNull(i)?.jsonPrimitive?.contentOrNullSafe().orEmpty(),
                descs?.getOrNull(i)?.jsonPrimitive?.contentOrNullSafe().orEmpty()
            )
        }.filter { it.title.isNotBlank() }
    }

    /** Fetch a page and return readable plain text (capped). */
    suspend fun readPage(url: String, maxChars: Int = 8000): String = withContext(Dispatchers.IO) {
        val normalized = if (url.startsWith("http")) url else "https://$url"
        val raw = get(normalized, "text/html,application/xhtml+xml,text/plain")
        val cleaned = raw
            .replace(Regex("(?is)<script.*?</script>"), " ")
            .replace(Regex("(?is)<style.*?</style>"), " ")
            .replace(Regex("(?is)<noscript.*?</noscript>"), " ")
            .let(::stripHtml)
            .replace(Regex("[ \\t\\u00A0]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
        if (cleaned.length > maxChars) cleaned.take(maxChars) + "\n…[truncated]" else cleaned
    }

    private fun decodeDdgUrl(raw: String): String {
        val marker = "uddg="
        val idx = raw.indexOf(marker)
        val candidate = if (idx >= 0) raw.substring(idx + marker.length).substringBefore("&") else raw
        return runCatching { java.net.URLDecoder.decode(candidate, "UTF-8") }.getOrDefault(candidate)
            .let { if (it.startsWith("//")) "https:$it" else it }
    }

    private fun stripHtml(s: String): String = s
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</(p|div|li|h[1-6]|tr)>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .trim()

    companion object {
        const val UA =
            "Mozilla/5.0 (Linux; Android 9; XzoAgent) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"
    }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    runCatching { this.content }.getOrNull()
