package com.playimdb.tv

import com.playimdb.tv.model.TitleResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ImdbRepository(
    private val client: OkHttpClient = defaultClient,
) {

    suspend fun suggest(query: String): List<TitleResult> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()

        val firstChar = q[0].lowercaseChar()
        val encoded = URLEncoder.encode(q, "UTF-8")
        val url = "$BASE_URL$firstChar/$encoded.json"

        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()

        client.executeCancellable(req).use { resp ->
            if (!resp.isSuccessful) {
                throw RuntimeException("HTTP ${resp.code}")
            }
            val body = resp.body?.string().orEmpty()
            parse(body)
        }
    }

    private fun parse(body: String): List<TitleResult> {
        if (body.isBlank()) return emptyList()
        val root = JSONObject(body)
        val arr = root.optJSONArray("d") ?: return emptyList()
        val out = ArrayList<TitleResult>(arr.length())

        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id", "")
            if (!id.startsWith("tt")) continue

            val label = o.optString("l", id)
            val year = when {
                o.has("yr") && !o.isNull("yr") -> o.optString("yr").takeIf { it.isNotBlank() }
                o.has("y") && !o.isNull("y") -> o.optString("y").takeIf { it.isNotBlank() }
                else -> null
            }
            val qid = o.optString("qid", "").takeIf { it.isNotBlank() }
            val image = o.optJSONObject("i")?.optString("imageUrl")?.takeIf { it.isNotBlank() }

            out += TitleResult(
                id = id,
                title = label,
                year = year,
                type = qid,
                posterUrl = image,
            )
        }
        return out
    }

    companion object {
        private const val BASE_URL = "https://v2.sg.media-imdb.com/suggestion/"

        private val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
    }
}

suspend fun OkHttpClient.executeCancellable(request: Request): Response =
    suspendCancellableCoroutine { continuation ->
        val call = newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) {
                    continuation.resumeWithException(e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!continuation.isCancelled) {
                    continuation.resume(response)
                }
            }
        })
    }
