package com.playimdb.mobile

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.AndroidViewModel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MobileApp(
                onOpenTitle = { result ->
                    startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(PlayUrlResolver.titleUrl(result.id, result.type))),
                    )
                },
            )
        }
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val imdb = ImdbRepository()
    private val charts = ChartRepository(application)
    private var searchJob: Job? = null

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _searchState = MutableStateFlow<LoadState>(LoadState.Idle)
    val searchState: StateFlow<LoadState> = _searchState.asStateFlow()

    private val _selectedChart = MutableStateFlow(ChartKind.TopMovies)
    val selectedChart: StateFlow<ChartKind> = _selectedChart.asStateFlow()

    private val _chartState = MutableStateFlow<LoadState>(LoadState.Idle)
    val chartState: StateFlow<LoadState> = _chartState.asStateFlow()

    init {
        loadChart(ChartKind.TopMovies)
    }

    fun onQueryChange(next: String) {
        _query.value = next
        searchJob?.cancel()
        if (next.isBlank()) {
            _searchState.value = LoadState.Idle
            return
        }

        searchJob = viewModelScope.launch {
            delay(350)
            _searchState.value = LoadState.Loading
            try {
                val results = imdb.suggest(next)
                _searchState.value = if (results.isEmpty()) LoadState.Empty("No titles found.") else LoadState.Success(results)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _searchState.value = LoadState.Error("Search failed. Try again.")
            }
        }
    }

    fun loadChart(kind: ChartKind, forceRefresh: Boolean = false) {
        _selectedChart.value = kind
        viewModelScope.launch {
            _chartState.value = LoadState.Loading
            try {
                val results = charts.getChart(kind, forceRefresh)
                _chartState.value = if (results.isEmpty()) LoadState.Empty("No titles found.") else LoadState.Success(results)
            } catch (_: Throwable) {
                _chartState.value = LoadState.Error("Could not load ${kind.label}.")
            }
        }
    }
}
