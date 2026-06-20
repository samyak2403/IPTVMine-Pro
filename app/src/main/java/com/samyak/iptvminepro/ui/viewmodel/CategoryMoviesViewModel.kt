package com.samyak.iptvminepro.ui.viewmodel

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.samyak.iptvminepro.model.VegaPost
import com.samyak.iptvminepro.provider.VegaProviderRunner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for CategoryMoviesScreen, scoped to the NavBackStackEntry.
 *
 * Survives back-navigation from MovieDetailScreen so the user returns
 * instantly to their previous scroll position with cached data — no
 * API call, no spinner.
 *
 * Navigation arguments are extracted from [SavedStateHandle] so this
 * ViewModel can be created by the default ViewModelProvider.Factory.
 */
class CategoryMoviesViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val runner = VegaProviderRunner(context)

    // Navigation arguments
    val categoryName: String = savedStateHandle["categoryName"] ?: ""
    val categoryFilter: String = savedStateHandle["categoryFilter"] ?: ""
    val providerUrl: String = savedStateHandle["providerUrl"] ?: ""
    val scraperValue: String = savedStateHandle["scraperValue"] ?: ""

    // ── State ────────────────────────────────────────────────────────────────────

    private val _movies = MutableStateFlow<List<VegaPost>>(emptyList())
    val movies: StateFlow<List<VegaPost>> = _movies.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    private var page = 1
    private var dataLoaded = false

    // ── Public actions ───────────────────────────────────────────────────────────

    /**
     * Called once from the composable. On back-navigation from MovieDetail,
     * _movies is already populated so the guard exits immediately.
     */
    fun initIfNeeded() {
        if (_movies.value.isNotEmpty()) return   // Cached → no reload on back-nav
        if (dataLoaded) return                    // Already in-flight
        dataLoaded = true
        loadMovies(isNextPage = false)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun activateSearch() {
        _isSearchActive.value = true
    }

    fun deactivateSearch() {
        _isSearchActive.value = false
        _searchQuery.value = ""
        loadMovies(isNextPage = false)
    }

    fun clearSearchQuery() {
        _searchQuery.value = ""
    }

    fun submitSearch() {
        if (_searchQuery.value.isNotBlank()) {
            loadMovies(isNextPage = false)
        }
    }

    fun loadMovies(isNextPage: Boolean) {
        if (_isLoading.value) return

        viewModelScope.launch {
            _isLoading.value = true
            if (!isNextPage) {
                page = 1
                _movies.value = emptyList()
                _hasMore.value = true
            }
            try {
                val newMovies = if (_isSearchActive.value && _searchQuery.value.isNotBlank()) {
                    runner.getSearchPosts(providerUrl, scraperValue, _searchQuery.value.trim(), page)
                } else {
                    runner.getPosts(providerUrl, scraperValue, categoryFilter, page)
                }
                if (newMovies.isEmpty()) {
                    _hasMore.value = false
                } else {
                    _movies.value = if (isNextPage) _movies.value + newMovies else newMovies
                    page++
                }
            } catch (e: Exception) {
                Log.e("CategoryMoviesViewModel", "Error loading movies", e)
                if (!isNextPage) {
                    _errorMessage.value = "Error fetching content: ${e.message}"
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ── Error ────────────────────────────────────────────────────────────────────

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun clearError() {
        _errorMessage.value = null
    }
}
