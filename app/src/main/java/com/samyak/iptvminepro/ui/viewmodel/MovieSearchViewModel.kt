package com.samyak.iptvminepro.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.samyak.iptvminepro.model.Provider
import com.samyak.iptvminepro.model.ProviderType
import com.samyak.iptvminepro.model.VegaPost
import com.samyak.iptvminepro.model.VegaProvider
import com.samyak.iptvminepro.provider.ExtensionRepository
import com.samyak.iptvminepro.provider.ProviderRepository
import com.samyak.iptvminepro.provider.VegaProviderRunner
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Activity-scoped ViewModel for MovieSearchScreen with an in-memory cache store.
 *
 * Keeps search queries and results in memory across navigation (e.g. opening
 * MovieDetail and pressing back) so the user returns instantly without
 * re-fetching or showing loading shimmer placeholders.
 */
class MovieSearchViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val runner = VegaProviderRunner(context)
    private val providerRepo = ProviderRepository(context)
    private val extensionRepo = ExtensionRepository.getInstance(context)

    override fun onCleared() {
        super.onCleared()
        runner.destroy()
    }

    // ── In-Memory Cache Store ───────────────────────────────────────────────────
    // LRU cache storing search query results by key: "$providerUrl|$scraperValue|$query|$page"
    private val memoryCache = object : LinkedHashMap<String, List<VegaPost>>(30, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<VegaPost>>?): Boolean {
            return size > 100 // retain up to 100 cached query/page results in memory
        }
    }

    private fun getCacheKey(providerUrl: String, scraperValue: String, query: String, page: Int): String {
        return "$providerUrl|$scraperValue|${query.trim().lowercase()}|$page"
    }

    // ── Providers & Scrapers State ──────────────────────────────────────────────
    val activeProviders: List<Provider>
        get() = providerRepo.getProviders().filter { it.isActive && it.safeType == ProviderType.VEGA }

    private val _selectedProvider = MutableStateFlow<Provider?>(providerRepo.getProviders().firstOrNull { it.isActive && it.safeType == ProviderType.VEGA })
    val selectedProvider: StateFlow<Provider?> = _selectedProvider.asStateFlow()

    private val _scrapers = MutableStateFlow<List<VegaProvider>>(emptyList())
    val scrapers: StateFlow<List<VegaProvider>> = _scrapers.asStateFlow()

    private val _selectedScraper = MutableStateFlow<VegaProvider?>(null)
    val selectedScraper: StateFlow<VegaProvider?> = _selectedScraper.asStateFlow()

    private val _isScrapersLoading = MutableStateFlow(false)
    val isScrapersLoading: StateFlow<Boolean> = _isScrapersLoading.asStateFlow()

    // ── Search State ────────────────────────────────────────────────────────────
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _movies = MutableStateFlow<List<VegaPost>>(emptyList())
    val movies: StateFlow<List<VegaPost>> = _movies.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var page = 1
    private var searchJob: Job? = null
    private var dataLoaded = false
    private var lastInstalledExtensions: Set<String>? = null

    init {
        viewModelScope.launch {
            extensionRepo.installedExtensionsFlow.collect { installed ->
                val prev = lastInstalledExtensions
                if (prev != null && prev != installed) {
                    lastInstalledExtensions = installed
                    loadScrapers(installed)
                }
            }
        }
    }

    fun initIfNeeded() {
        val providers = activeProviders
        if (_selectedProvider.value == null && providers.isNotEmpty()) {
            _selectedProvider.value = providers.firstOrNull()
        }

        // If movies are already cached and loaded in memory, do NOT re-fetch!
        if (_movies.value.isNotEmpty()) return
        if (dataLoaded) return
        dataLoaded = true

        val installed = extensionRepo.installedExtensionsFlow.value
        lastInstalledExtensions = installed
        viewModelScope.launch {
            loadScrapers(installed)
        }
    }

    private suspend fun loadScrapers(installed: Set<String>) {
        val provider = _selectedProvider.value ?: return
        _isScrapersLoading.value = true
        try {
            val manifest = runner.fetchManifest(provider.url)
            val filtered = manifest.filter { it.value in installed }
            _scrapers.value = filtered
            if (filtered.isNotEmpty()) {
                val current = _selectedScraper.value
                val targetScraper = if (current != null && filtered.any { it.value == current.value }) {
                    current
                } else {
                    filtered.firstOrNull()
                }
                _selectedScraper.value = targetScraper
                if (targetScraper != null) {
                    performSearch(query = _searchQuery.value, isImmediate = true, isNextPage = false)
                }
            } else {
                _selectedScraper.value = null
                _movies.value = emptyList()
            }
        } catch (e: Exception) {
            Log.e("MovieSearchViewModel", "Error loading scrapers", e)
        } finally {
            _isScrapersLoading.value = false
        }
    }

    fun selectProvider(provider: Provider) {
        if (_selectedProvider.value?.url == provider.url) return
        _selectedProvider.value = provider
        _movies.value = emptyList()
        dataLoaded = false
        initIfNeeded()
    }

    fun selectScraper(scraper: VegaProvider) {
        if (_selectedScraper.value?.value == scraper.value) return
        _selectedScraper.value = scraper
        performSearch(query = _searchQuery.value, isImmediate = true, isNextPage = false)
    }

    fun onSearchQueryChanged(newQuery: String) {
        if (_searchQuery.value == newQuery) return
        _searchQuery.value = newQuery

        searchJob?.cancel()

        val provider = _selectedProvider.value ?: return
        val scraper = _selectedScraper.value ?: return

        val trimmedQuery = newQuery.trim()
        val cacheKey = getCacheKey(provider.url, scraper.value, trimmedQuery, 1)

        // Instant In-Memory Cache Lookup:
        // If we already have the result in memory, show it instantly without delay or shimmer!
        synchronized(memoryCache) {
            val cachedResult = memoryCache[cacheKey]
            if (cachedResult != null) {
                _movies.value = cachedResult
                _isLoading.value = false
                _hasMore.value = cachedResult.isNotEmpty()
                page = 2
                return
            }
        }

        // If not in cache, debounce 400ms then perform search
        searchJob = viewModelScope.launch {
            if (trimmedQuery.isNotEmpty()) {
                delay(400)
            }
            performSearch(query = trimmedQuery, isImmediate = false, isNextPage = false)
        }
    }

    fun submitSearch() {
        searchJob?.cancel()
        performSearch(query = _searchQuery.value.trim(), isImmediate = true, isNextPage = false)
    }

    fun clearSearch() {
        _searchQuery.value = ""
        performSearch(query = "", isImmediate = true, isNextPage = false)
    }

    fun loadMore() {
        if (_isLoading.value || !_hasMore.value || _movies.value.isEmpty()) return
        performSearch(query = _searchQuery.value.trim(), isImmediate = true, isNextPage = true)
    }

    private fun performSearch(query: String, isImmediate: Boolean, isNextPage: Boolean) {
        val provider = _selectedProvider.value ?: return
        val scraper = _selectedScraper.value ?: return

        val targetPage = if (isNextPage) page else 1
        val cacheKey = getCacheKey(provider.url, scraper.value, query, targetPage)

        // Check in-memory cache first
        synchronized(memoryCache) {
            val cached = memoryCache[cacheKey]
            if (cached != null) {
                if (isNextPage) {
                    _movies.value = _movies.value + cached
                    page = targetPage + 1
                    _hasMore.value = cached.isNotEmpty()
                } else {
                    _movies.value = cached
                    page = 2
                    _hasMore.value = cached.isNotEmpty()
                }
                _isLoading.value = false
                return
            }
        }

        viewModelScope.launch {
            _isLoading.value = true
            if (!isNextPage) {
                _movies.value = emptyList()
                _hasMore.value = true
                page = 1
            }
            try {
                val results = if (query.isNotBlank()) {
                    runner.getSearchPosts(provider.url, scraper.value, query, targetPage)
                } else {
                    runner.getPosts(provider.url, scraper.value, filter = "", page = targetPage)
                }

                // Store in memory cache
                synchronized(memoryCache) {
                    memoryCache[cacheKey] = results
                }

                if (results.isEmpty()) {
                    _hasMore.value = false
                } else {
                    _movies.value = if (isNextPage) _movies.value + results else results
                    page = targetPage + 1
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MovieSearchViewModel", "Error fetching search posts", e)
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
