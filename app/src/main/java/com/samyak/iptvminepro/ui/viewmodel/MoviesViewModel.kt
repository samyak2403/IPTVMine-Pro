package com.samyak.iptvminepro.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.samyak.iptvminepro.model.*
import com.samyak.iptvminepro.provider.ExtensionRepository
import com.samyak.iptvminepro.provider.MovieCacheStore
import com.samyak.iptvminepro.provider.ProviderRepository
import com.samyak.iptvminepro.provider.VegaProviderRunner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MoviesViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val repository = ProviderRepository(context)
    private val runner = VegaProviderRunner(context)
    private val extensionRepo = ExtensionRepository.getInstance(context)
    private val cacheStore = MovieCacheStore.getInstance(context)

    override fun onCleared() {
        super.onCleared()
        runner.destroy()
    }

    // ── Providers ──────────────────────────────────────────────────────────────
    val vegaProvidersList: List<Provider>
        get() = repository.getProviders().filter { it.isActive && it.safeType == ProviderType.VEGA }

    private val _selectedProvider = MutableStateFlow<Provider?>(repository.getProviders().firstOrNull { it.isActive && it.safeType == ProviderType.VEGA })
    val selectedProvider: StateFlow<Provider?> = _selectedProvider.asStateFlow()

    // ── Scrapers ───────────────────────────────────────────────────────────────
    private val _scrapers = MutableStateFlow<List<VegaProvider>>(emptyList())
    val scrapers: StateFlow<List<VegaProvider>> = _scrapers.asStateFlow()

    private val _selectedScraper = MutableStateFlow<VegaProvider?>(null)
    val selectedScraper: StateFlow<VegaProvider?> = _selectedScraper.asStateFlow()

    private val _isScrapersLoading = MutableStateFlow(false)
    val isScrapersLoading: StateFlow<Boolean> = _isScrapersLoading.asStateFlow()

    // ── Categories ─────────────────────────────────────────────────────────────
    private val _categories = MutableStateFlow<List<VegaCatalog>>(emptyList())
    val categories: StateFlow<List<VegaCatalog>> = _categories.asStateFlow()

    private val _selectedCategory = MutableStateFlow<VegaCatalog?>(null)
    val selectedCategory: StateFlow<VegaCatalog?> = _selectedCategory.asStateFlow()

    // ── Movies ─────────────────────────────────────────────────────────────────
    private val _movies = MutableStateFlow<List<VegaPost>>(emptyList())
    val movies: StateFlow<List<VegaPost>> = _movies.asStateFlow()

    private val _page = MutableStateFlow(1)
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    // ── Search ─────────────────────────────────────────────────────────────────
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // ── Error ──────────────────────────────────────────────────────────────────
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Guard: true once the very first load has been triggered
    private var dataLoaded = false

    // Track which extension set was used for the last load.
    // When extensions change (install/uninstall), we reload scrapers automatically.
    private var lastInstalledExtensions: Set<String>? = null
    private var catalogLoadJob: Job? = null

    init {
        // Observe installed extensions for real-time updates.
        // When the user installs or uninstalls an extension from Settings,
        // the scraper tabs and movies update immediately — no manual refresh needed.
        viewModelScope.launch {
            extensionRepo.installedExtensionsFlow.collect { installed ->
                // Only react to CHANGES after the initial load has completed.
                // lastInstalledExtensions == null means initIfNeeded hasn't run yet.
                val previous = lastInstalledExtensions
                if (previous != null && previous != installed) {
                    lastInstalledExtensions = installed
                    loadScrapers(installed = installed)
                }
            }
        }
    }

    // ── Public actions ─────────────────────────────────────────────────────────

    /**
     * Refreshes providers from repo and triggers init if categories or scrapers haven't been loaded yet.
     */
    fun refreshProvidersAndInit() {
        val activeProviders = vegaProvidersList
        if (_selectedProvider.value == null && activeProviders.isNotEmpty()) {
            _selectedProvider.value = activeProviders.firstOrNull()
            dataLoaded = false
        }
        if (_categories.value.isEmpty()) {
            dataLoaded = false
        }
        initIfNeeded()
    }

    /**
     * Called from the Composable. Triggers the first load only once.
     * Safe to call on every recomposition / back-navigation.
     *
     * Guard priority:
     *  1. [Primary]   Movies already cached → return instantly (back-navigation from MovieDetail).
     *                 No API call, no spinner, scroll position preserved.
     *  2. [Secondary] Load already in-flight / completed → skip duplicate call.
     */
    fun initIfNeeded(initialCategoryTitle: String? = null) {
        val activeProviders = vegaProvidersList
        if (_selectedProvider.value == null && activeProviders.isNotEmpty()) {
            _selectedProvider.value = activeProviders.firstOrNull()
        }
        val provider = _selectedProvider.value
        val installed = extensionRepo.installedExtensionsFlow.value

        // Immediate Cache Memory Store Restore (0ms!)
        if (provider != null) {
            val cachedScrapers = cacheStore.getScrapers(provider.url)?.filter { it.value in installed }
            if (!cachedScrapers.isNullOrEmpty() && _scrapers.value.isEmpty()) {
                _scrapers.value = cachedScrapers
                val firstScraper = cachedScrapers.first()
                if (_selectedScraper.value == null) {
                    _selectedScraper.value = firstScraper
                }
                val cachedCats = cacheStore.getCategories(provider.url, firstScraper.value)
                if (!cachedCats.isNullOrEmpty() && _categories.value.isEmpty()) {
                    _categories.value = cachedCats
                    val targetCat = if (initialCategoryTitle != null) {
                        cachedCats.find { it.title == initialCategoryTitle } ?: cachedCats.first()
                    } else {
                        cachedCats.first()
                    }
                    _selectedCategory.value = targetCat
                    val cachedPosts = cacheStore.getMovies(provider.url, firstScraper.value, targetCat.filter)
                    if (!cachedPosts.isNullOrEmpty() && _movies.value.isEmpty()) {
                        _movies.value = cachedPosts
                        _isLoading.value = false
                    }
                }
            }
        }

        if (_movies.value.isNotEmpty() && _categories.value.isNotEmpty()) {
            val extensionsChanged = lastInstalledExtensions != installed
            lastInstalledExtensions = installed
            if (!dataLoaded || extensionsChanged) {
                dataLoaded = true
                viewModelScope.launch {
                    loadScrapers(installed = installed, initialCategoryTitle = initialCategoryTitle)
                }
            }
            return
        }
        val needsReloadForNewExtensions = installed.isNotEmpty() &&
            (_scrapers.value.isEmpty() || lastInstalledExtensions != installed)
        if (dataLoaded && !needsReloadForNewExtensions) return
        dataLoaded = true
        lastInstalledExtensions = installed
        viewModelScope.launch {
            loadScrapers(
                installed = installed,
                initialCategoryTitle = initialCategoryTitle
            )
        }
    }

    fun selectProvider(provider: Provider) {
        if (_selectedProvider.value?.url == provider.url) return
        _selectedProvider.value = provider
        _searchQuery.value = ""
        dataLoaded = false       // Allow re-init for new provider
        initIfNeeded()
    }

    fun selectScraper(scraper: VegaProvider) {
        if (_selectedScraper.value?.value == scraper.value) {
            if (_movies.value.isEmpty() && !_isLoading.value) {
                loadCatalogAndMovies()
            }
            return
        }
        _selectedScraper.value = scraper
        _searchQuery.value = ""

        // Instant Cache Memory lookup: populate categories and movies immediately,
        // or clear stale data from the previous scraper while the new one loads.
        val provider = _selectedProvider.value
        val cachedCats = provider?.let { cacheStore.getCategories(it.url, scraper.value) }
        if (!cachedCats.isNullOrEmpty()) {
            _categories.value = cachedCats
            val targetCat = cachedCats.firstOrNull()
            _selectedCategory.value = targetCat
            val cachedMovies = if (targetCat != null) {
                cacheStore.getMovies(provider.url, scraper.value, targetCat.filter)
            } else null
            if (!cachedMovies.isNullOrEmpty()) {
                _movies.value = cachedMovies
                _isLoading.value = false
            } else {
                _movies.value = emptyList()
            }
        } else {
            _categories.value = emptyList()
            _selectedCategory.value = null
            _movies.value = emptyList()
        }
        loadCatalogAndMovies()
    }

    fun retryCurrentScraper() {
        val installed = extensionRepo.installedExtensionsFlow.value
        lastInstalledExtensions = installed
        if (_selectedScraper.value == null) {
            viewModelScope.launch {
                loadScrapers(installed = installed)
            }
        } else {
            loadCatalogAndMovies()
        }
    }

    fun selectCategory(category: VegaCatalog) {
        if (_selectedCategory.value?.filter == category.filter) return
        _selectedCategory.value = category

        // Instant Cache Memory lookup for category filter
        val provider = _selectedProvider.value
        val scraper = _selectedScraper.value
        if (provider != null && scraper != null) {
            val cachedMovies = cacheStore.getMovies(provider.url, scraper.value, category.filter)
            if (!cachedMovies.isNullOrEmpty()) {
                _movies.value = cachedMovies
                _isLoading.value = false
            }
        }
        loadMovies(isNextPage = false)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun submitSearch() {
        loadMovies(isNextPage = false)
    }

    fun clearSearch() {
        _searchQuery.value = ""
        loadMovies(isNextPage = false)
    }

    fun loadMovies(isNextPage: Boolean) {
        val provider = _selectedProvider.value ?: return
        val scraper = _selectedScraper.value ?: return
        if (_isLoading.value) return

        viewModelScope.launch {
            val filter = _selectedCategory.value?.filter ?: ""
            if (!isNextPage) {
                _page.value = 1
                val cached = if (_searchQuery.value.isBlank()) cacheStore.getMovies(provider.url, scraper.value, filter) else null
                if (!cached.isNullOrEmpty()) {
                    _movies.value = cached
                    _isLoading.value = false
                } else if (_movies.value.isEmpty()) {
                    _isLoading.value = true
                }
                _hasMore.value = true
            } else {
                _isLoading.value = true
            }
            try {
                val newMovies = if (_searchQuery.value.isNotBlank()) {
                    runner.getSearchPosts(provider.url, scraper.value, _searchQuery.value.trim(), _page.value)
                } else {
                    runner.getPosts(provider.url, scraper.value, filter, _page.value)
                }
                if (newMovies.isEmpty()) {
                    if (!isNextPage && _movies.value.isEmpty()) {
                        _movies.value = emptyList()
                    }
                    _hasMore.value = false
                } else {
                    _movies.value = if (isNextPage) _movies.value + newMovies else newMovies
                    _page.value++
                    if (!isNextPage && _searchQuery.value.isBlank()) {
                        cacheStore.putMovies(provider.url, scraper.value, filter, newMovies)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MoviesViewModel", "Error loading movies", e)
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private suspend fun loadScrapers(
        installed: Set<String>,
        initialCategoryTitle: String? = null
    ) {
        val provider = _selectedProvider.value
            ?: vegaProvidersList.firstOrNull()?.also { _selectedProvider.value = it }
            ?: return
        if (_scrapers.value.isEmpty()) {
            _isScrapersLoading.value = true
        }
        try {
            val manifest = runner.fetchManifest(provider.url)
            val filteredScrapers = manifest.filter { it.value in installed }
            _scrapers.value = filteredScrapers
            if (filteredScrapers.isNotEmpty()) {
                cacheStore.putScrapers(provider.url, manifest)
            }
            Log.d("MoviesViewModel", "Scrapers loaded: ${filteredScrapers.size}")

            // No installed scrapers → clear all data so UI shows empty state
            if (filteredScrapers.isEmpty()) {
                _selectedScraper.value = null
                _movies.value = emptyList()
                _categories.value = emptyList()
                _selectedCategory.value = null
                _hasMore.value = false
                return
            }

            val current = _selectedScraper.value
            if (current == null || !filteredScrapers.any { it.value == current.value }) {
                _selectedScraper.value = filteredScrapers.firstOrNull()
                _categories.value = emptyList()
                _selectedCategory.value = null
                _movies.value = emptyList()
                loadCatalogAndMovies(initialCategoryTitle)
            } else if (_movies.value.isEmpty() && !_isLoading.value) {
                loadCatalogAndMovies(initialCategoryTitle)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("MoviesViewModel", "Error loading scrapers", e)
            _error.value = e.message ?: "Failed to load extensions"
            if (_scrapers.value.isEmpty()) {
                _scrapers.value = emptyList()
                _selectedScraper.value = null
            }
        } finally {
            _isScrapersLoading.value = false
        }
    }

    private fun loadCatalogAndMovies(initialCategoryTitle: String? = null) {
        val provider = _selectedProvider.value ?: return
        val scraper = _selectedScraper.value ?: return

        catalogLoadJob?.cancel()
        catalogLoadJob = viewModelScope.launch {
            if (_movies.value.isEmpty()) {
                _isLoading.value = true
            }
            _page.value = 1
            _hasMore.value = true
            try {
                val (catList, _) = runner.getCatalog(provider.url, scraper.value)
                if (catList.isNotEmpty()) {
                    _categories.value = catList
                    cacheStore.putCategories(provider.url, scraper.value, catList)
                }
                Log.d("MoviesViewModel", "Categories loaded: ${catList.size} for ${scraper.display_name}")

                val targetCat = if (initialCategoryTitle != null) {
                    catList.find { it.title == initialCategoryTitle } ?: catList.firstOrNull()
                } else {
                    _selectedCategory.value?.let { cur ->
                        catList.find { it.filter == cur.filter }
                    } ?: catList.firstOrNull()
                }
                _selectedCategory.value = targetCat

                val filter = targetCat?.filter ?: ""
                val newMovies = runner.getPosts(provider.url, scraper.value, filter, 1)
                Log.d("MoviesViewModel", "Movies loaded: ${newMovies.size}")
                if (newMovies.isEmpty()) {
                    if (_movies.value.isEmpty()) {
                        _hasMore.value = false
                    }
                } else {
                    _movies.value = newMovies
                    _page.value = 2
                    cacheStore.putMovies(provider.url, scraper.value, filter, newMovies)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MoviesViewModel", "Error loading catalog/movies: ${e.message}", e)
                _error.value = e.message ?: "Failed to load ${scraper.display_name}"
                if (_categories.value.isEmpty()) {
                    _categories.value = emptyList()
                    _selectedCategory.value = null
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
}
