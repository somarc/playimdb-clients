package com.playimdb.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.UnknownHostException

object PlayUrlResolver {
    const val PRIMARY_HOST = "playimdb.com"
    const val FALLBACK_HOST = "streamimdb.ru"

    private val TV_TYPES = setOf("tv", "tvSeries", "tvMiniSeries", "tvSpecial", "podcastSeries")

    @Volatile
    private var resolvedHost: String? = null

    suspend fun ensureResolved() {
        if (resolvedHost != null) return
        resolvedHost = withContext(Dispatchers.IO) {
            try {
                InetAddress.getByName(PRIMARY_HOST)
                PRIMARY_HOST
            } catch (_: UnknownHostException) {
                FALLBACK_HOST
            }
        }
    }

    suspend fun titleUrl(id: String, type: String?): String {
        ensureResolved()
        return buildUrl(resolvedHost!!, id, type)
    }

    fun displayPath(id: String, type: String?): String {
        val host = resolvedHost ?: PRIMARY_HOST
        return buildPath(host, id, type)
    }

    private fun buildUrl(host: String, id: String, type: String?): String {
        return "https://${buildPath(host, id, type)}"
    }

    private fun buildPath(host: String, id: String, type: String?): String {
        return if (host == FALLBACK_HOST) {
            "$host/embed/${embedKind(type)}/$id"
        } else {
            "$host/title/$id"
        }
    }

    private fun embedKind(type: String?): String {
        return if (type != null && type in TV_TYPES) "tv" else "movie"
    }
}