package com.samyak.iptvminepro.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.samyak.iptvminepro.model.*
import com.samyak.iptvminepro.provider.ProviderRepository
import com.samyak.iptvminepro.provider.VegaProviderRunner
import com.samyak.iptvminepro.ui.components.MovieCard
import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoviesScreen(
    initialCategoryTitle: String? = null,
    onMovieClick: (VegaPost, VegaProvider, Provider) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Providers & Runner setup
    val repository = remember { ProviderRepository(context) }
    val runner = remember { VegaProviderRunner(context) }
    
    // State
    val vegaProvidersList = remember { repository.getProviders().filter { it.isActive && it.safeType == ProviderType.VEGA } }
    Log.d("MoviesScreen", "Active Vega Providers: ${vegaProvidersList.size}")
    
    var selectedProvider by remember { mutableStateOf<Provider?>(vegaProvidersList.firstOrNull()) }
    
    var scrapers by remember { mutableStateOf<List<VegaProvider>>(emptyList()) }
    var selectedScraper by remember { mutableStateOf<VegaProvider?>(null) }
    
    var categories by remember { mutableStateOf<List<VegaCatalog>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<VegaCatalog?>(null) }
    
    var movies by remember { mutableStateOf<List<VegaPost>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    
    var page by remember { mutableStateOf(1) }
    var isLoading by remember { mutableStateOf(false) }
    var isScrapersLoading by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    
    val extensionRepo = remember { com.samyak.iptvminepro.provider.ExtensionRepository.getInstance(context) }
    val installedExtensionsState by extensionRepo.installedExtensionsFlow.collectAsState()
    
    // Fetch scrapers/extensions for selected provider
    LaunchedEffect(selectedProvider, installedExtensionsState) {
        val provider = selectedProvider
        if (provider != null) {
            isScrapersLoading = true
            try {
                val manifest = runner.fetchManifest(provider.url)
                val installed = manifest.filter { it.value in installedExtensionsState }
                
                // Fallback: If none are explicitly installed, show all non-disabled extensions
                scrapers = if (installed.isNotEmpty()) installed else manifest.filter { !it.disabled }
                
                Log.d("MoviesScreen", "Loaded ${manifest.size} scrapers, using ${scrapers.size} (installed: ${installed.size})")
                
                // Keep selected scraper if it's still in the list, otherwise choose first
                if (selectedScraper == null || !scrapers.any { it.value == selectedScraper?.value }) {
                    selectedScraper = scrapers.firstOrNull()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading scrapers: ${e.message}", Toast.LENGTH_LONG).show()
                scrapers = emptyList()
                selectedScraper = null
            } finally {
                isScrapersLoading = false
            }
        } else {
            scrapers = emptyList()
            selectedScraper = null
        }
    }
    
    // Fetch categories/catalog for selected scraper, then load movies
    LaunchedEffect(selectedProvider, selectedScraper) {
        val provider = selectedProvider
        val scraper = selectedScraper
        if (provider != null && scraper != null) {
            isLoading = true
            movies = emptyList()
            page = 1
            hasMore = true
            try {
                val (catList, _) = runner.getCatalog(provider.url, scraper.value)
                categories = catList
                Log.d("MoviesScreen", "Loaded ${catList.size} categories for ${scraper.display_name}")
                
                // Select category based on initial title if provided
                val targetCat = if (initialCategoryTitle != null) {
                    catList.find { it.title == initialCategoryTitle } ?: catList.firstOrNull()
                } else {
                    catList.firstOrNull()
                }
                
                selectedCategory = targetCat
                
                // Now immediately load movies with the new category
                val filter = targetCat?.filter ?: ""
                val newMovies = runner.getPosts(provider.url, scraper.value, filter, 1)
                Log.d("MoviesScreen", "Loaded ${newMovies.size} movies for category ${targetCat?.title}")
                if (newMovies.isEmpty()) {
                    hasMore = false
                } else {
                    movies = newMovies
                    page = 2
                }
            } catch (e: Exception) {
                Log.e("MoviesScreen", "Error loading catalog/movies: ${e.message}", e)
                categories = emptyList()
                selectedCategory = null
            } finally {
                isLoading = false
            }
        } else {
            categories = emptyList()
            selectedCategory = null
            movies = emptyList()
        }
    }
    
    // Fetch movies list (when category or search changes)
    val loadMovies: (Boolean) -> Unit = { isNextPage ->
        val provider = selectedProvider
        val scraper = selectedScraper
        if (provider != null && scraper != null && !isLoading) {
            scope.launch {
                isLoading = true
                if (!isNextPage) {
                    page = 1
                    movies = emptyList()
                    hasMore = true
                }
                try {
                    val newMovies = if (searchQuery.isNotBlank()) {
                        runner.getSearchPosts(provider.url, scraper.value, searchQuery.trim(), page)
                    } else {
                        val filter = selectedCategory?.filter ?: ""
                        runner.getPosts(provider.url, scraper.value, filter, page)
                    }
                    if (newMovies.isEmpty()) {
                        hasMore = false
                    } else {
                        movies = if (isNextPage) movies + newMovies else newMovies
                        page++
                    }
                } catch (e: Exception) {
                    if (!isNextPage) {
                        Toast.makeText(context, "Error fetching content: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    isLoading = false
                }
            }
        }
    }
    
    // Only trigger on category change (not scraper change, which is handled above)
    LaunchedEffect(selectedCategory) {
        // Skip if still loading from scraper change or if search is active
        if (searchQuery.isBlank() && !isLoading && selectedScraper != null && selectedProvider != null) {
            loadMovies(false)
        }
    }
    
    // Main UI Layout (Premium Sleek Dark Theme)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0E13)) // HSL Tailored premium deep space dark background
    ) {
        if (vegaProvidersList.isEmpty()) {
            // Empty State
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Movie,
                        contentDescription = "No Movies",
                        tint = Color(0xFF6B7280),
                        modifier = Modifier
                            .size(100.dp)
                            .padding(bottom = 16.dp)
                    )
                    Text(
                        text = "VOD Movies & Series",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Please add a Vega Movies provider in Settings to browse and play movies and web series.",
                        fontSize = 15.sp,
                        color = Color(0xFF9CA3AF),
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                }
            }
        } else {
            // Top Section: Provider Selection Dropdown (if multiple providers)
            if (vegaProvidersList.size > 1) {
                var dropdownExpanded by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    OutlinedButton(
                        onClick = { dropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text(selectedProvider?.title ?: "Select Provider")
                    }
                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        vegaProvidersList.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider.title) },
                                onClick = {
                                    selectedProvider = provider
                                    dropdownExpanded = false
                                    searchQuery = ""
                                }
                            )
                        }
                    }
                }
            }
            
            // Search Bar (Premium Sleek Input)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search movies, TV shows...", color = Color(0xFF9CA3AF)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search", tint = Color(0xFF26A69A)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            loadMovies(false)
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear", tint = Color(0xFF9CA3AF))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { loadMovies(false) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF26A69A), // Teal focus
                    unfocusedBorderColor = Color(0xFF2C2A36),
                    focusedContainerColor = Color(0xFF16151D),
                    unfocusedContainerColor = Color(0xFF16151D),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            )
            
            // Scraper Selector Tabs
            if (isScrapersLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = Color(0xFF26A69A)
                )
            } else if (scrapers.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(scrapers) { scraper ->
                        val isSelected = selectedScraper?.value == scraper.value
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedScraper = scraper
                                searchQuery = ""
                            },
                            label = { Text(scraper.display_name, fontSize = 13.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF26A69A),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF1D1B26),
                                labelColor = Color(0xFF9CA3AF)
                            ),
                            border = null,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }
            
            // Categories Selector Tabs (Only if search is empty)
            if (searchQuery.isEmpty() && categories.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { category ->
                        val isSelected = selectedCategory?.filter == category.filter
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = category },
                            label = { Text(category.title, fontSize = 13.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF26A69A).copy(alpha = 0.2f),
                                selectedLabelColor = Color(0xFF26A69A),
                                containerColor = Color.Transparent,
                                labelColor = Color(0xFF9CA3AF)
                            ),
                            border = if (isSelected) BorderStroke(1.dp, Color(0xFF26A69A)) else BorderStroke(1.dp, Color(0xFF2C2A36)),
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
            }
            
            // Movies Grid Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val gridState = rememberLazyGridState()
                
                // Triggers loading next page near end of list
                val shouldLoadMore = remember {
                    derivedStateOf {
                        val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()
                            ?: return@derivedStateOf false
                        lastVisibleItem.index >= gridState.layoutInfo.totalItemsCount - 6
                    }
                }
                
                LaunchedEffect(shouldLoadMore.value) {
                    if (shouldLoadMore.value && hasMore && !isLoading && movies.isNotEmpty()) {
                        loadMovies(true)
                    }
                }
                
                if (movies.isEmpty() && !isLoading) {
                    // No matches state
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (scrapers.isEmpty()) "No extensions installed. Please install an extension from Settings." else "No movies or shows found",
                            color = Color(0xFF6B7280),
                            fontSize = 16.sp
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3), // Standard movie poster column count
                        state = gridState,
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(movies) { index, movie ->
                            MovieCard(
                                movie = movie,
                                onClick = {
                                    val scraper = selectedScraper
                                    val provider = selectedProvider
                                    if (scraper != null && provider != null) {
                                        onMovieClick(movie, scraper, provider)
                                    }
                                }
                            )
                        }
                        
                        // Loading footer item
                        if (isLoading && movies.isNotEmpty()) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF26A69A),
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Overlay spinner for full reload loading
                if (isLoading && movies.isEmpty()) {
                    CircularProgressIndicator(
                        color = Color(0xFF26A69A),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

