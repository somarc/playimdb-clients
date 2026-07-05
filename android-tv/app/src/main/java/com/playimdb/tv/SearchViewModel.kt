package com.playimdb.tv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.playimdb.tv.model.TitleResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Success(val results: List<TitleResult>) : SearchUiState
    data class Empty(val query: String) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

sealed interface ChartUiState {
    data object Idle : ChartUiState
    data class Loading(val kind: ChartKind) : ChartUiState
    data class Success(val data: ChartData) : ChartUiState
    data class Error(val kind: ChartKind, val message: String) : ChartUiState
}

class SearchViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repo = ImdbRepository()
    private val chartRepo = ChartRepository(application)
    private val debounceMs = 350L

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val _chartState = MutableStateFlow<ChartUiState>(ChartUiState.Idle)
    val chartState: StateFlow<ChartUiState> = _chartState.asStateFlow()

    private val _selectedChart = MutableStateFlow(ChartKind.TopMovies)
    val selectedChart: StateFlow<ChartKind> = _selectedChart.asStateFlow()

    private var debounceJob: Job? = null
    private var searchJob: Job? = null
    private var chartJob: Job? = null

    init {
        prefetchCharts()
    }

    fun prefetchCharts() {
        viewModelScope.launch {
            chartRepo.prefetchAll()
        }
    }

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        debounceJob?.cancel()
        searchJob?.cancel()

        if (newQuery.isBlank()) {
            _state.value = SearchUiState.Idle
            return
        }

        debounceJob = viewModelScope.launch {
            delay(debounceMs)
            runSearch(newQuery)
        }
    }

    private fun runSearch(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.value = SearchUiState.Loading
            try {
                val results = repo.suggest(query)
                _state.value = if (results.isEmpty()) {
                    SearchUiState.Empty(query)
                } else {
                    SearchUiState.Success(results)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val msg = when (error) {
                    is java.io.InterruptedIOException -> "Search timed out. Try again."
                    else -> "Search failed. Try again."
                }
                _state.value = SearchUiState.Error(msg)
            }
        }
    }

    fun loadChart(kind: ChartKind = _selectedChart.value, forceRefresh: Boolean = false) {
        _selectedChart.value = kind
        chartJob?.cancel()
        chartJob = viewModelScope.launch {
            _chartState.value = ChartUiState.Loading(kind)
            try {
                _chartState.value = ChartUiState.Success(chartRepo.getChart(kind, forceRefresh = forceRefresh))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _chartState.value = ChartUiState.Error(kind, "Could not load ${kind.label}. Try again.")
            }
        }
    }
}
