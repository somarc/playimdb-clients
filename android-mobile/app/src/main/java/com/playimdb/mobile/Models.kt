package com.playimdb.mobile

data class TitleResult(
    val id: String,
    val title: String,
    val year: String?,
    val type: String?,
    val posterUrl: String?,
)

enum class ChartKind(val label: String, val cacheKey: String, val imdbType: String, val limit: Int) {
    TopMovies("Top Movies", "top250-movies", "TOP_RATED_MOVIES", 250),
    TopTv("Top TV", "top250-tv", "TOP_RATED_TV_SHOWS", 250),
    PopularMovies("Popular Movies", "popular-movies", "MOST_POPULAR_MOVIES", 100),
    PopularTv("Popular TV", "popular-tv", "MOST_POPULAR_TV_SHOWS", 100),
}

sealed interface LoadState {
    data object Idle : LoadState
    data object Loading : LoadState
    data class Success(val results: List<TitleResult>) : LoadState
    data class Empty(val message: String) : LoadState
    data class Error(val message: String) : LoadState
}
