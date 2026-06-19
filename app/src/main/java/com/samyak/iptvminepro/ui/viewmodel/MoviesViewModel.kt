package com.samyak.iptvminepro.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.samyak.iptvminepro.model.*
import com.samyak.iptvminepro.provider.ExtensionRepository
import com.samyak.iptvminepro.provider.ProviderRepository
import com.samyak.iptvminepro.provider.VegaProviderRunner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MoviesViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val repository = ProviderRepository(context)
    private val runner = VegaProviderRunner(context)
    private val extensionRepo = ExtensionRepository.getInstance(context)

    // ── Providers ──────────────────────────────────────────────────────────────
    val vegaProvidersList: List<Provider> =
        repository.getProviders().filter { it.isActive && it.safeType == ProviderType.VEGA }

    private val _selectedProvider = MutableStateFlow<Provider?>(vegaProvidersList.firstOrNull())
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

    // ── Public actions ─────────────────────────────────────────────────────────

    /**
     * Called from the Composable. Triggers the first load only once.
     * Safe to call on every recomposition / back-navigation.
     */
    fun initIfNeeded(initialCategoryTitle: String? = null) {
        if (dataLoaded) return   // Already have data – do nothing
        dataLoaded = true
        viewModelScope.launch {
            loadScrapers(
                installed = extensionRepo.installedExtensionsFlow.value,
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
        if (_selectedScraper.value?.value == scraper.value) return
        _selectedScraper.value = scraper
        _searchQuery.value = ""
        loadCatalogAndMovies()
    }

    fun selectCategory(category: VegaCatalog) {
        if (_selectedCategory.value?.filter == category.filter) return
        _selectedCategory.value = category
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
            _isLoading.value = true
            if (!isNextPage) {
                _page.value = 1
                _movies.value = emptyList()
                _hasMore.value = true
            }
            try {
                val newMovies = if (_searchQuery.value.isNotBlank()) {
                    runner.getSearchPosts(provider.url, scraper.value, _searchQuery.value.trim(), _page.value)
                } else {
                    val filter = _selectedCategory.value?.filter ?: ""
                    runner.getPosts(provider.url, scraper.value, filter, _page.value)
                }
                if (newMovies.isEmpty()) {
                    _hasMore.value = false
                } else {
                    _movies.value = if (isNextPage) _movies.value + newMovies else newMovies
                    _page.value++
                }
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
        val provider = _selectedProvider.value ?: return
        _isScrapersLoading.value = true
        try {
            val manifest = runner.fetchManifest(provider.url)
            val filteredScrapers = manifest.filter { it.value in installed }
            _scrapers.value = filteredScrapers
            Log.d("MoviesViewModel", "Scrapers loaded: ${filteredScrapers.size}")

            val current = _selectedScraper.value
            if (current == null || !filteredScrapers.any { it.value == current.value }) {
                _selectedScraper.value = filteredScrapers.firstOrNull()
                loadCatalogAndMovies(initialCategoryTitle)
            }
        } catch (e: Exception) {
            Log.e("MoviesViewModel", "Error loading scrapers", e)
            _scrapers.value = emptyList()
            _selectedScraper.value = null
        } finally {
            _isScrapersLoading.value = false
        }
    }

    private fun loadCatalogAndMovies(initialCategoryTitle: String? = null) {
        val provider = _selectedProvider.value ?: return
        val scraper = _selectedScraper.value ?: return

        viewModelScope.launch {
            _isLoading.value = true
            _movies.value = emptyList()
            _page.value = 1
            _hasMore.value = true
            try {
                val (catList, _) = runner.getCatalog(provider.url, scraper.value)
                _categories.value = catList
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
                    _hasMore.value = false
                } else {
                    _movies.value = newMovies
                    _page.value = 2
                }
            } catch (e: Exception) {
                Log.e("MoviesViewModel", "Error loading catalog/movies: ${e.message}", e)
                _categories.value = emptyList()
                _selectedCategory.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }
}
