package com.playimdb.mobile

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ImdbRepository(private val client: OkHttpClient = defaultClient) {
    suspend fun suggest(query: String): List<TitleResult> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val url = "$SUGGEST_URL${q[0].lowercaseChar()}/${URLEncoder.encode(q, "UTF-8")}.json"
        val req = Request.Builder().url(url).header("Accept", "application/json").build()

        client.executeCancellable(req).use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
            parseSuggest(JSONObject(resp.body?.string().orEmpty()))
        }
    }

    private fun parseSuggest(root: JSONObject): List<TitleResult> {
        val arr = root.optJSONArray("d") ?: return emptyList()
        val out = ArrayList<TitleResult>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val id = item.optString("id")
            if (!id.startsWith("tt")) continue
            out += TitleResult(
                id = id,
                title = item.optString("l", id),
                year = item.optString("yr", item.optString("y", "")).takeIf { it.isNotBlank() },
                type = item.optString("qid", "").takeIf { it.isNotBlank() },
                posterUrl = item.optJSONObject("i")?.optString("imageUrl")?.takeIf { it.isNotBlank() },
            )
        }
        return out
    }

    companion object {
        private const val SUGGEST_URL = "https://v2.sg.media-imdb.com/suggestion/"
        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
    }
}

class ChartRepository(
    context: Context,
    private val client: OkHttpClient = ImdbRepository.defaultClient,
) {
    private val prefs = context.applicationContext.getSharedPreferences("charts", Context.MODE_PRIVATE)

    suspend fun getChart(kind: ChartKind, forceRefresh: Boolean = false): List<TitleResult> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            readCache(kind)?.takeIf { System.currentTimeMillis() - it.first < CACHE_TTL_MS }?.second?.let {
                return@withContext it
            }
        }

        runCatching { fetch(kind).also { writeCache(kind, it) } }
            .getOrElse { readCache(kind)?.second ?: throw it }
    }

    private suspend fun fetch(kind: ChartKind): List<TitleResult> {
        val body = JSONObject()
            .put("query", CHART_QUERY)
            .put("variables", JSONObject().put("first", kind.limit).put("chartType", kind.imdbType))
            .toString()
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url(GRAPHQL_URL)
            .header("Accept", "application/json")
            .post(body)
            .build()

        client.executeCancellable(req).use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
            return parseChart(JSONObject(resp.body?.string().orEmpty()))
        }
    }

    private fun parseChart(root: JSONObject): List<TitleResult> {
        val edges = root.optJSONObject("data")?.optJSONObject("chartTitles")?.optJSONArray("edges") ?: return emptyList()
        val out = ArrayList<TitleResult>(edges.length())
        for (i in 0 until edges.length()) {
            val node = edges.optJSONObject(i)?.optJSONObject("node") ?: continue
            val id = node.optString("id")
            if (!id.startsWith("tt")) continue
            out += TitleResult(
                id = id,
                title = node.optJSONObject("titleText")?.optString("text")?.takeIf { it.isNotBlank() } ?: id,
                year = node.optJSONObject("releaseYear")?.optInt("year")?.takeIf { it > 0 }?.toString(),
                type = node.optJSONObject("titleType")?.optString("id")?.takeIf { it.isNotBlank() },
                posterUrl = node.optJSONObject("primaryImage")?.optString("url")?.takeIf { it.isNotBlank() },
            )
        }
        return out
    }

    private fun readCache(kind: ChartKind): Pair<Long, List<TitleResult>>? {
        val root = runCatching { JSONObject(prefs.getString(kind.cacheKey, null) ?: return null) }.getOrNull() ?: return null
        return root.optLong("fetchedAtMs") to parseCached(root.optJSONArray("items") ?: return null)
    }

    private fun writeCache(kind: ChartKind, items: List<TitleResult>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().put("id", it.id).put("title", it.title).put("year", it.year).put("type", it.type).put("posterUrl", it.posterUrl))
        }
        prefs.edit().putString(kind.cacheKey, JSONObject().put("fetchedAtMs", System.currentTimeMillis()).put("items", arr).toString()).apply()
    }

    private fun parseCached(arr: JSONArray): List<TitleResult> {
        val out = ArrayList<TitleResult>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            out += TitleResult(
                id = item.optString("id"),
                title = item.optString("title"),
                year = item.optString("year").takeIf { it.isNotBlank() && it != "null" },
                type = item.optString("type").takeIf { it.isNotBlank() && it != "null" },
                posterUrl = item.optString("posterUrl").takeIf { it.isNotBlank() && it != "null" },
            )
        }
        return out
    }

    companion object {
        private const val GRAPHQL_URL = "https://caching.graphql.imdb.com/"
        private const val CACHE_TTL_MS = 24L * 60L * 60L * 1000L
        private const val CHART_QUERY = """
            query Chart(${'$'}first: Int!, ${'$'}chartType: ChartTitleType!) {
              chartTitles(first: ${'$'}first, chart: {chartType: ${'$'}chartType}) {
                edges { node { id titleText { text } releaseYear { year } primaryImage { url } titleType { id text } } }
              }
            }
        """
    }
}

suspend fun OkHttpClient.executeCancellable(request: Request): Response =
    suspendCancellableCoroutine { continuation ->
        val call = newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!continuation.isCancelled) continuation.resume(response)
            }
        })
    }
