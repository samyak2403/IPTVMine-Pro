package com.samyak.television.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.samyak.television.data.ExtensionRepository
import com.samyak.television.data.ProviderRepository
import com.samyak.television.data.VegaProviderRunner
import com.samyak.television.model.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchMoviesScreen(
    onMovieClick: (VegaPost, VegaProvider, Provider) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val runner = remember { VegaProviderRunner(context) }
    val providerRepo = remember { ProviderRepository(context) }

    val activeProviders = remember { providerRepo.getProviders().filter { it.isActive && it.safeType == ProviderType.VEGA } }
    var selectedProvider by remember { mutableStateOf<Provider?>(activeProviders.firstOrNull()) }
    var selectedScraper by remember { mutableStateOf<VegaProvider?>(null) }
    var isScrapersLoading by remember { mutableStateOf(false) }

    var movies by remember { mutableStateOf<List<VegaPost>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var page by remember { mutableStateOf(1) }
    var isLoading by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }

    val extensionRepo = remember { ExtensionRepository.getInstance(context) }
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
                Log.e("TVSearchMoviesScreen", "Error loading scrapers", e)
            } finally {
                isScrapersLoading = false
            }
        }
    }

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
                    Log.e("TVSearchMoviesScreen", "Error searching movies", e)
                    if (!isNextPage) {
                        Toast.makeText(context, "Error fetching content: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(searchQuery, selectedScraper) {
        if (selectedScraper != null) {
            loadMovies(false)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        // 1. Disney+ Hotstar Pill-Shaped Search Input Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by title, genre, or actor...", color = Color.Gray) },
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF26A69A),
                unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                focusedContainerColor = Color(0xFF0C111F).copy(alpha = 0.85f),
                unfocusedContainerColor = Color(0xFF0C111F).copy(alpha = 0.5f)
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { loadMovies(false) }),
            shape = RoundedCornerShape(30.dp), // Premium pill search input bar
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        // 2. Horizontal Scrollable Popular Categories List
        Text(
            text = "Popular Categories",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val genres = listOf("Action", "Comedy", "Horror", "Drama", "Sci-Fi", "Thriller", "Romance", "Adventure")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        ) {
            items(genres) { genre ->
                Surface(
                    onClick = {
                        searchQuery = genre
                        loadMovies(false)
                    },
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.05f),
                        focusedContainerColor = Color(0xFF26A69A),
                        pressedContainerColor = Color(0xFF208b80)
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)), // Premium rounded pills
                    modifier = Modifier.wrapContentWidth()
                ) {
                    Text(
                        text = genre,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // 3. Grid Title Row (with Extension Source name on the right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (searchQuery.isBlank()) "Trending Searches" else "Results for \"$searchQuery\"",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            if (selectedScraper != null) {
                Text(
                    text = "Source: ${selectedScraper?.display_name}",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 4. Full Width Grid of Results
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val gridState = rememberLazyGridState()

            // Infinite Scroll
            val shouldLoadMore = remember {
                derivedStateOf {
                    val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()
                        ?: return@derivedStateOf false
                    lastVisibleItem.index >= gridState.layoutInfo.totalItemsCount - 5
                }
            }

            LaunchedEffect(shouldLoadMore.value) {
                if (shouldLoadMore.value && hasMore && !isLoading && movies.isNotEmpty()) {
                    loadMovies(true)
                }
            }

            if (isLoading && movies.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF26A69A))
                }
            } else if (movies.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val message = when {
                        activeProviders.isEmpty() -> "No Vega Providers added. Please configure provider in Playlists."
                        installedExtensionsState.isEmpty() -> "No Extensions installed. Please install scrapers in Extensions."
                        searchQuery.isBlank() -> "Type query and press Search"
                        else -> "No results found"
                    }
                    Text(
                        text = message,
                        color = Color.Gray,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5), // 5 columns for premium full-width grid layout
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(movies) { _, movie ->
                        val provider = selectedProvider
                        val scraper = selectedScraper
                        TvMovieCard(
                            movie = movie,
                            onClick = {
                                if (provider != null && scraper != null) {
                                    onMovieClick(movie, scraper, provider)
                                }
                            },
                            showTitle = true
                        )
                    }

                    if (isLoading && movies.isNotEmpty()) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(5) }) {
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
        }
    }
}
