package com.playimdb.tv

object PlayUrlResolver {
    const val HOST = "streamimdb.ru"

    private val TV_TYPES = setOf("tv", "tvSeries", "tvMiniSeries", "tvSpecial", "podcastSeries")

    fun titleUrl(id: String, type: String?): String = "https://${displayPath(id, type)}"

    fun displayPath(id: String, type: String?): String =
        "$HOST/embed/${embedKind(type)}/$id"

    private fun embedKind(type: String?): String =
        if (type != null && type in TV_TYPES) "tv" else "movie"
}