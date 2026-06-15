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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.samyak.television.data.ExtensionRepository
import com.samyak.television.data.ProviderRepository
import com.samyak.television.data.VegaProviderRunner
import com.samyak.television.model.*
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

    Row(modifier = Modifier.fillMaxSize()) {
        // Left Column: Search Form and extension info (Glassmorphism Styled Pane)
        Column(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
                .background(Color(0xFF0C111F).copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Text(
                text = "Search",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Enter movie or show name...", color = Color.Gray) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF26A69A),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                    focusedContainerColor = Color(0xFF0C111F).copy(alpha = 0.8f),
                    unfocusedContainerColor = Color(0xFF0C111F).copy(alpha = 0.5f)
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { loadMovies(false) }),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )

            Button(
                onClick = { loadMovies(false) },
                colors = ButtonDefaults.colors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    focusedContainerColor = Color(0xFF26A69A),
                    focusedContentColor = Color.White
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Search", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // D-pad friendly Popular Searches/Genres list
            Text(
                text = "Popular Categories",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val genres = listOf("Action", "Comedy", "Horror", "Drama", "Sci-Fi")
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
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
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = genre,
                            fontSize = 13.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedScraper != null) {
                Text(
                    text = "Source: ${selectedScraper?.display_name}",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            } else if (activeProviders.isEmpty()) {
                Text(
                    text = "No Vega Providers added.",
                    fontSize = 11.sp,
                    color = Color.Red
                )
            } else if (installedExtensionsState.isEmpty()) {
                Text(
                    text = "No Extensions Installed.",
                    fontSize = 11.sp,
                    color = Color.Red
                )
            }
        }

        // Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.05f))
        )

        // Right Column: Grid of search results with Hotstar Title Header
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 24.dp)
        ) {
            Text(
                text = if (searchQuery.isBlank()) "Trending Searches" else "Results for \"$searchQuery\"",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

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
                        lastVisibleItem.index >= gridState.layoutInfo.totalItemsCount - 4
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
                        Text(
                            text = if (searchQuery.isBlank()) "Type query and press Search" else "No results found",
                            color = Color.Gray,
                            fontSize = 15.sp
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
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
            }
        }
    }
}
