package com.playimdb.tv

import android.content.Context
import com.playimdb.tv.model.TitleResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class ChartKind(
    val label: String,
    val cacheKey: String,
    val imdbType: String,
    val limit: Int,
) {
    TopMovies("Top Movies", "top250-movies", "TOP_RATED_MOVIES", 250),
    TopTv("Top TV", "top250-tv", "TOP_RATED_TV_SHOWS", 250),
    PopularMovies("Popular Movies", "popular-movies", "MOST_POPULAR_MOVIES", 100),
    PopularTv("Popular TV", "popular-tv", "MOST_POPULAR_TV_SHOWS", 100),
}

data class ChartData(
    val kind: ChartKind,
    val titles: List<TitleResult>,
    val fetchedAtMs: Long,
)

class ChartRepository(
    context: Context,
    private val client: OkHttpClient = defaultClient,
) {
    private val prefs = context.applicationContext.getSharedPreferences("charts", Context.MODE_PRIVATE)

    suspend fun prefetchAll(maxAgeMs: Long = CACHE_TTL_MS): Unit = coroutineScope {
        ChartKind.entries.map { kind ->
            async {
                runCatching { getChart(kind, maxAgeMs, forceRefresh = false) }
            }
        }.awaitAll()
    }

    suspend fun getChart(
        kind: ChartKind,
        maxAgeMs: Long = CACHE_TTL_MS,
        forceRefresh: Boolean = false,
    ): ChartData = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            readCache(kind)?.takeIf { System.currentTimeMillis() - it.fetchedAtMs < maxAgeMs }?.let {
                return@withContext it
            }
        }

        runCatching {
            fetchChart(kind).also { writeCache(it) }
        }.getOrElse { error ->
            readCache(kind) ?: throw error
        }
    }

    private suspend fun fetchChart(kind: ChartKind): ChartData {
        val body = JSONObject()
            .put("query", CHART_QUERY)
            .put(
                "variables",
                JSONObject()
                    .put("first", kind.limit)
                    .put("chartType", kind.imdbType),
            )
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        val req = Request.Builder()
            .url(GRAPHQL_URL)
            .header("Accept", "application/json")
            .post(body)
            .build()

        client.executeCancellable(req).use { resp ->
            if (!resp.isSuccessful) {
                throw RuntimeException("HTTP ${resp.code}")
            }
            val json = JSONObject(resp.body?.string().orEmpty())
            return ChartData(
                kind = kind,
                titles = parseTitles(json),
                fetchedAtMs = System.currentTimeMillis(),
            )
        }
    }

    private fun parseTitles(root: JSONObject): List<TitleResult> {
        val edges = root
            .optJSONObject("data")
            ?.optJSONObject("chartTitles")
            ?.optJSONArray("edges")
            ?: return emptyList()

        val out = ArrayList<TitleResult>(edges.length())
        for (i in 0 until edges.length()) {
            val node = edges.optJSONObject(i)?.optJSONObject("node") ?: continue
            val id = node.optString("id", "")
            if (!id.startsWith("tt")) continue

            val title = node.optJSONObject("titleText")?.optString("text")?.takeIf { it.isNotBlank() } ?: id
            val year = node.optJSONObject("releaseYear")?.optInt("year")?.takeIf { it > 0 }?.toString()
            val type = node.optJSONObject("titleType")?.optString("id")?.takeIf { it.isNotBlank() }
            val posterUrl = node.optJSONObject("primaryImage")?.optString("url")?.takeIf { it.isNotBlank() }

            out += TitleResult(
                id = id,
                title = title,
                year = year,
                type = type,
                posterUrl = posterUrl,
            )
        }
        return out
    }

    private fun readCache(kind: ChartKind): ChartData? {
        val raw = prefs.getString(kind.cacheKey, null) ?: return null
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val titles = root.optJSONArray("titles") ?: return null
        return ChartData(
            kind = kind,
            titles = parseCachedTitles(titles),
            fetchedAtMs = root.optLong("fetchedAtMs", 0L),
        )
    }

    private fun writeCache(data: ChartData) {
        val titles = JSONArray()
        data.titles.forEach { title ->
            titles.put(
                JSONObject()
                    .put("id", title.id)
                    .put("title", title.title)
                    .put("year", title.year)
                    .put("type", title.type)
                    .put("posterUrl", title.posterUrl),
            )
        }

        val root = JSONObject()
            .put("fetchedAtMs", data.fetchedAtMs)
            .put("titles", titles)

        prefs.edit().putString(data.kind.cacheKey, root.toString()).apply()
    }

    private fun parseCachedTitles(arr: JSONArray): List<TitleResult> {
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

        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        private val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()

        private const val CHART_QUERY = """
            query Chart(${'$'}first: Int!, ${'$'}chartType: ChartTitleType!) {
              chartTitles(first: ${'$'}first, chart: {chartType: ${'$'}chartType}) {
                edges {
                  node {
                    id
                    titleText { text }
                    releaseYear { year }
                    ratingsSummary { aggregateRating voteCount }
                    primaryImage { url }
                    titleType { text id }
                  }
                }
              }
            }
        """
    }
}
