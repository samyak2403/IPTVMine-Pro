package com.samyak.iptvminepro.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.samyak.iptvminepro.model.Provider
import com.samyak.iptvminepro.model.ProviderType
import com.samyak.iptvminepro.model.VegaCatalog
import com.samyak.iptvminepro.model.VegaPost
import com.samyak.iptvminepro.model.VegaProvider
import com.samyak.iptvminepro.provider.ExtensionRepository
import com.samyak.iptvminepro.provider.ProviderRepository
import com.samyak.iptvminepro.provider.VegaProviderRunner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Activity-scoped ViewModel that caches the Home screen VOD movie data.
 *
 * Survives back-navigation from MovieDetailScreen so the Home screen returns
 * instantly without showing the ProgressBar or making any network request.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val runner = VegaProviderRunner(context)
    private val providerRepo = ProviderRepository(context)

    // ── Exposed state ───────────────────────────────────────────────────────────

    private val _isMoviesLoading = MutableStateFlow(false)
    val isMoviesLoading: StateFlow<Boolean> = _isMoviesLoading.asStateFlow()

    private val _moviesByCategory = MutableStateFlow<Map<VegaCatalog, List<VegaPost>>>(emptyMap())
    val moviesByCategory: StateFlow<Map<VegaCatalog, List<VegaPost>>> = _moviesByCategory.asStateFlow()

    private val _selectedProvider = MutableStateFlow<Provider?>(null)
    val selectedProvider: StateFlow<Provider?> = _selectedProvider.asStateFlow()

    private val _selectedScraper = MutableStateFlow<VegaProvider?>(null)
    val selectedScraper: StateFlow<VegaProvider?> = _selectedScraper.asStateFlow()

    // ── Load guard ──────────────────────────────────────────────────────────────

    /** The extension set that was used for the last successful load. */
    private var lastLoadedExtensions: Set<String>? = null

    /**
     * Loads VOD movies for the Home screen, but ONLY when necessary:
     *
     *  1. [installedExtensions] unchanged AND data already present  →  instant return
     *     (back-navigation from MovieDetailScreen hits this path)
     *  2. Already loading  →  skip to avoid duplicate in-flight requests
     *  3. Otherwise  →  fetch manifest, catalog, and posts; update incrementally
     */
    fun loadMoviesIfNeeded(installedExtensions: Set<String>) {
        // Guard 1: same extensions + data already cached → return instantly
        if (installedExtensions == lastLoadedExtensions && _moviesByCategory.value.isNotEmpty()) return
        // Guard 2: already loading
        if (_isMoviesLoading.value) return

        val activeVegaProviders = providerRepo.getProviders()
            .filter { it.isActive && it.safeType == ProviderType.VEGA }

        if (activeVegaProviders.isEmpty()) {
            _moviesByCategory.value = emptyMap()
            return
        }

        lastLoadedExtensions = installedExtensions

        viewModelScope.launch {
            _isMoviesLoading.value = true
            try {
                val provider = activeVegaProviders.first()
                _selectedProvider.value = provider

                val manifest = runner.fetchManifest(provider.url)
                val installed = manifest.filter { it.value in installedExtensions }
                val firstScraper = installed.firstOrNull()

                if (firstScraper != null) {
                    _selectedScraper.value = firstScraper
                    val (catalogs, _) = runner.getCatalog(provider.url, firstScraper.value)
                    val postsMap = mutableMapOf<VegaCatalog, List<VegaPost>>()

                    val catalogsToFetch = catalogs.take(6)
                    if (catalogsToFetch.isEmpty()) {
                        val posts = runner.getPosts(provider.url, firstScraper.value, filter = "", page = 1)
                        if (posts.isNotEmpty()) {
                            _moviesByCategory.value = mapOf(VegaCatalog("Featured", "") to posts.take(15))
                        }
                    } else {
                        for (cat in catalogsToFetch) {
                            val posts = runner.getPosts(provider.url, firstScraper.value, filter = cat.filter, page = 1)
                            if (posts.isNotEmpty()) {
                                postsMap[cat] = posts.take(15)
                                Log.d("HomeViewModel", "Loaded category: ${cat.title} (${posts.size} items)")
                                // Update incrementally so rows appear as they load
                                _moviesByCategory.value = postsMap.toMap()
                            }
                        }
                    }
                } else {
                    Log.w("HomeViewModel", "No installed scraper found in manifest")
                    _moviesByCategory.value = emptyMap()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error loading home VOD movies: ${e.message}", e)
                _moviesByCategory.value = emptyMap()
            } finally {
                _isMoviesLoading.value = false
            }
        }
    }
}
