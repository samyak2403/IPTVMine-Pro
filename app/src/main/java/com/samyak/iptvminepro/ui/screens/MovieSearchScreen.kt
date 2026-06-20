package com.samyak.iptvminepro.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.samyak.iptvminepro.model.Provider
import com.samyak.iptvminepro.model.ProviderType
import com.samyak.iptvminepro.model.VegaPost
import com.samyak.iptvminepro.model.VegaProvider
import com.samyak.iptvminepro.provider.ProviderRepository
import com.samyak.iptvminepro.provider.VegaProviderRunner
import com.samyak.iptvminepro.ui.components.MovieCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieSearchScreen(
    navController: NavController,
    onMovieClick: (VegaPost, VegaProvider, Provider) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val runner = remember { VegaProviderRunner(context) }
    val providerRepo = remember { ProviderRepository(context) }

    // Providers & Scrapers setup
    val activeProviders = remember { providerRepo.getProviders().filter { it.isActive && it.safeType == ProviderType.VEGA } }
    var selectedProvider by remember { mutableStateOf<Provider?>(activeProviders.firstOrNull()) }
    var selectedScraper by remember { mutableStateOf<VegaProvider?>(null) }
    var isScrapersLoading by remember { mutableStateOf(false) }

    // State
    var movies by remember { mutableStateOf<List<VegaPost>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var page by remember { mutableStateOf(1) }
    var isLoading by remember { mutableStateOf(false) }
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
                selectedScraper = installed.firstOrNull()
            } catch (e: Exception) {
                Log.e("MovieSearchScreen", "Error loading scrapers", e)
            } finally {
                isScrapersLoading = false
            }
        }
    }

    // Fetch movies list
    val loadMovies: (Boolean) -> Unit = { isNextPage ->
        val provider = selectedProvider
        val scraper = selectedScraper
        if (provider != null && scraper != null && !isLoading) {
            isLoading = true
            if (!isNextPage) {
                page = 1
                movies = emptyList()
                hasMore = true
            }
            scope.launch {
                try {
                    val newMovies = if (searchQuery.isNotBlank()) {
                        runner.getSearchPosts(provider.url, scraper.value, searchQuery.trim(), page)
                    } else {
                        runner.getPosts(provider.url, scraper.value, filter = "", page = page)
                    }
                    if (newMovies.isEmpty()) {
                        hasMore = false
                    } else {
                        movies = if (isNextPage) movies + newMovies else newMovies
                        page++
                    }
                } catch (e: Exception) {
                    Log.e("MovieSearchScreen", "Error searching movies", e)
                    if (!isNextPage) {
                        Toast.makeText(context, "Error fetching content: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    isLoading = false
                }
            }
        }
    }

    // Trigger search when query changes
    LaunchedEffect(searchQuery, selectedScraper) {
        if (selectedScraper != null) {
            loadMovies(false)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search movies, TV shows...", color = Color.White.copy(alpha = 0.6f)) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color.White
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { loadMovies(false) }),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear Search"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        content = { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color.White)
            ) {
                val gridState = rememberLazyGridState()

                // Infinite Scroll trigger
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

                if (activeProviders.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Please add a Vega Movies provider in Settings",
                            color = Color(0xFF6B7280),
                            fontSize = 16.sp
                        )
                    }
                } else if (movies.isEmpty() && !isLoading && !isScrapersLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (installedExtensionsState.isEmpty()) "No extensions installed. Please install an extension from Settings." else "No movies or shows found",
                            color = Color(0xFF6B7280),
                            fontSize = 16.sp
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(movies) { _, movie ->
                            val provider = selectedProvider
                            val scraper = selectedScraper
                            MovieCard(
                                movie = movie,
                                onClick = {
                                    if (provider != null && scraper != null) {
                                        onMovieClick(movie, scraper, provider)
                                    }
                                }
                            )
                        }

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

                if ((isLoading || isScrapersLoading) && movies.isEmpty()) {
                    CircularProgressIndicator(
                        color = Color(0xFF26A69A),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    )
}
