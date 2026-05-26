package com.playimdb.tv

import androidx.lifecycle.ViewModel
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

class SearchViewModel(
    private val repo: ImdbRepository = ImdbRepository(),
    private val debounceMs: Long = 350L,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var debounceJob: Job? = null
    private var searchJob: Job? = null

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
}
