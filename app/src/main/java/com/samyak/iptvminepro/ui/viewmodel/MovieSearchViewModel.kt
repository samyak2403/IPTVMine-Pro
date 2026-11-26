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
import com.samyak.iptvminepro.provider.MovieCacheStore
import com.samyak.iptvminepro.provider.ProviderRepository
import com.samyak.iptvminepro.provider.VegaProviderRunner
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope

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
    private val cacheStore = MovieCacheStore.getInstance(context)

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

    private val _selectedProvider = MutableStateFlow<Provider?>(providerRepo.getPreferredActiveVegaProvider())
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

    private val _moviesByScraper = MutableStateFlow<Map<VegaProvider, List<VegaPost>>>(emptyMap())
    val moviesByScraper: StateFlow<Map<VegaProvider, List<VegaPost>>> = _moviesByScraper.asStateFlow()

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
        val preferred = providerRepo.getPreferredActiveVegaProvider(providers)
        if (preferred != null && _selectedProvider.value?.url != preferred.url) {
            _selectedProvider.value = preferred
            dataLoaded = false
        }

        // If movies or sections are already cached and loaded in memory, do NOT re-fetch unless missing scrapers!
        if (_movies.value.isNotEmpty() || _moviesByScraper.value.isNotEmpty()) {
            if (_selectedScraper.value == null && _scrapers.value.isNotEmpty()) {
                val loadedScraperValues = _moviesByScraper.value.keys.map { it.value }.toSet()
                val missing = _scrapers.value.filter { it.value !in loadedScraperValues }
                if (missing.isNotEmpty() && !_isLoading.value) {
                    performSearch(query = _searchQuery.value.trim(), isImmediate = true, isNextPage = false)
                }
            }
            return
        }
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
                } else if (filtered.size == 1) {
                    filtered.first()
                } else {
                    null // "All" mode when multiple extensions installed
                }
                _selectedScraper.value = targetScraper
                performSearch(query = _searchQuery.value, isImmediate = true, isNextPage = false)
            } else {
                _selectedScraper.value = null
                _movies.value = emptyList()
                _moviesByScraper.value = emptyMap()
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
        _moviesByScraper.value = emptyMap()
        dataLoaded = false
        initIfNeeded()
    }

    fun selectAllScrapers() {
        if (_selectedScraper.value == null) return
        _selectedScraper.value = null
        searchJob?.cancel()
        performSearch(query = _searchQuery.value.trim(), isImmediate = true, isNextPage = false)
    }

    fun selectScraper(scraper: VegaProvider) {
        if (_selectedScraper.value?.value == scraper.value) return
        _selectedScraper.value = scraper
        searchJob?.cancel()
        performSearch(query = _searchQuery.value.trim(), isImmediate = true, isNextPage = false)
    }

    fun onSearchQueryChanged(newQuery: String) {
        if (_searchQuery.value == newQuery) return
        _searchQuery.value = newQuery

        searchJob?.cancel()

        val provider = _selectedProvider.value ?: return
        val trimmedQuery = newQuery.trim()

        val scraper = _selectedScraper.value
        if (scraper != null) {
            val cacheKey = getCacheKey(provider.url, scraper.value, trimmedQuery, 1)
            val cachedResult = synchronized(memoryCache) { memoryCache[cacheKey] }
                ?: (if (trimmedQuery.isEmpty()) cacheStore.getMovies(provider.url, scraper.value, "") else null)
            if (!cachedResult.isNullOrEmpty()) {
                synchronized(memoryCache) {
                    memoryCache[cacheKey] = cachedResult
                }
                _movies.value = cachedResult
                _isLoading.value = false
                _hasMore.value = true
                page = 2
                return
            }
        } else {
            val allScrapers = _scrapers.value
            val cachedMap = LinkedHashMap<VegaProvider, List<VegaPost>>()
            var allCached = allScrapers.isNotEmpty()
            for (s in allScrapers) {
                val key = getCacheKey(provider.url, s.value, trimmedQuery, 1)
                val cached = synchronized(memoryCache) { memoryCache[key] }
                    ?: (if (trimmedQuery.isEmpty()) cacheStore.getMovies(provider.url, s.value, "") else null)
                if (!cached.isNullOrEmpty()) {
                    cachedMap[s] = cached
                    synchronized(memoryCache) {
                        memoryCache[key] = cached
                    }
                } else {
                    allCached = false
                }
            }
            if (allCached) {
                _moviesByScraper.value = cachedMap
                _isLoading.value = false
                return
            }
        }

        // If not in cache, debounce 350ms then perform search directly within searchJob
        searchJob = viewModelScope.launch {
            if (trimmedQuery.isNotEmpty()) {
                delay(350)
            }
            val currentScraper = _selectedScraper.value
            if (currentScraper == null) {
                performSearchAll(trimmedQuery)
            } else {
                performSearchSingle(trimmedQuery, isImmediate = false, isNextPage = false)
            }
        }
    }

    fun submitSearch() {
        searchJob?.cancel()
        performSearch(query = _searchQuery.value.trim(), isImmediate = true, isNextPage = false)
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchQuery.value = ""
        performSearch(query = "", isImmediate = true, isNextPage = false)
    }

    fun loadMore() {
        if (_selectedScraper.value == null) return
        if (_isLoading.value || !_hasMore.value || _movies.value.isEmpty()) return
        performSearch(query = _searchQuery.value.trim(), isImmediate = true, isNextPage = true)
    }

    private fun performSearch(query: String, isImmediate: Boolean, isNextPage: Boolean) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val scraper = _selectedScraper.value
            if (scraper == null) {
                performSearchAll(query)
            } else {
                performSearchSingle(query, isImmediate, isNextPage)
            }
        }
    }

    private suspend fun performSearchAll(query: String) {
        val provider = _selectedProvider.value ?: return
        val allScrapers = _scrapers.value
        if (allScrapers.isEmpty()) {
            _moviesByScraper.value = emptyMap()
            _isLoading.value = false
            return
        }

        _isLoading.value = true
        _errorMessage.value = null

        val initialCachedMap = LinkedHashMap<VegaProvider, List<VegaPost>>()
        val uncachedScrapers = mutableListOf<VegaProvider>()

        for (scraper in allScrapers) {
            val cacheKey = getCacheKey(provider.url, scraper.value, query, 1)
            val cached = synchronized(memoryCache) { memoryCache[cacheKey] }
                ?: (if (query.isBlank()) cacheStore.getMovies(provider.url, scraper.value, "") else null)
            if (!cached.isNullOrEmpty()) {
                initialCachedMap[scraper] = cached
                synchronized(memoryCache) {
                    memoryCache[cacheKey] = cached
                }
            } else {
                uncachedScrapers.add(scraper)
            }
        }

        _moviesByScraper.value = initialCachedMap

        if (uncachedScrapers.isEmpty()) {
            _isLoading.value = false
            return
        }

        val resultMap = LinkedHashMap(initialCachedMap)

        try {
            coroutineScope {
                uncachedScrapers.forEach { scraper ->
                    launch(Dispatchers.IO) {
                        try {
                            val results = if (query.isNotBlank()) {
                                runner.getSearchPosts(provider.url, scraper.value, query, 1)
                            } else {
                                runner.getPosts(provider.url, scraper.value, filter = "", page = 1)
                            }
                            if (results.isNotEmpty()) {
                                val cacheKey = getCacheKey(provider.url, scraper.value, query, 1)
                                synchronized(memoryCache) {
                                    memoryCache[cacheKey] = results
                                }
                                if (query.isBlank()) {
                                    cacheStore.putMovies(provider.url, scraper.value, "", results)
                                }
                                synchronized(resultMap) {
                                    resultMap[scraper] = results
                                    val sortedMap = LinkedHashMap<VegaProvider, List<VegaPost>>()
                                    allScrapers.forEach { s ->
                                        resultMap[s]?.let { posts ->
                                            if (posts.isNotEmpty()) {
                                                sortedMap[s] = posts
                                            }
                                        }
                                    }
                                    _moviesByScraper.value = sortedMap
                                }
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e("MovieSearchViewModel", "Search failed for ${scraper.display_name}", e)
                        }
                    }
                }
            }
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun performSearchSingle(query: String, isImmediate: Boolean, isNextPage: Boolean) {
        val provider = _selectedProvider.value ?: return
        val scraper = _selectedScraper.value ?: return

        val targetPage = if (isNextPage) page else 1
        val cacheKey = getCacheKey(provider.url, scraper.value, query, targetPage)

        // Check in-memory cache and persistent cache first
        val cached = synchronized(memoryCache) { memoryCache[cacheKey] }
            ?: (if (!isNextPage && query.isBlank()) cacheStore.getMovies(provider.url, scraper.value, "") else null)

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

            if (results.isNotEmpty()) {
                // Store in memory cache
                synchronized(memoryCache) {
                    memoryCache[cacheKey] = results
                }
                if (!isNextPage && query.isBlank()) {
                    cacheStore.putMovies(provider.url, scraper.value, "", results)
                }
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

    fun clearError() {
        _errorMessage.value = null
    }
}
