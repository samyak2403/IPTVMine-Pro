package com.samyak.television.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.samyak.television.data.ExtensionRepository
import com.samyak.television.data.MovieCacheStore
import com.samyak.television.data.ProviderRepository
import com.samyak.television.data.VegaProviderRunner
import com.samyak.television.model.*
import com.samyak.television.ui.components.OttSpecs
import com.samyak.television.ui.components.OttTallPosterCard
import com.samyak.television.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchMoviesScreen(
    onMovieClick: (VegaPost, VegaProvider, Provider) -> Unit,
    primaryFocusRequester: FocusRequester? = null
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val runner = remember { VegaProviderRunner(context) }
    val providerRepo = remember { ProviderRepository(context) }
    val cacheStore = remember { MovieCacheStore.getInstance(context) }

    DisposableEffect(Unit) {
        onDispose {
            runner.destroy()
        }
    }

    val activeProviders = remember { providerRepo.getProviders().filter { it.isActive && it.safeType == ProviderType.VEGA } }
    var selectedProvider by remember { mutableStateOf<Provider?>(activeProviders.firstOrNull()) }
    var scrapers by remember { mutableStateOf<List<VegaProvider>>(emptyList()) }
    var selectedScraper by remember { mutableStateOf<VegaProvider?>(null) }
    var isScrapersLoading by remember { mutableStateOf(false) }

    var movies by remember { mutableStateOf<List<VegaPost>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(1) }
    var isLoading by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    val extensionRepo = remember { ExtensionRepository.getInstance(context) }
    val installedExtensionsState by extensionRepo.installedExtensionsFlow.collectAsState()

    LaunchedEffect(selectedProvider, installedExtensionsState) {
        val provider = selectedProvider
        if (provider != null) {
            val cachedScrapers = cacheStore.getScrapers(provider.url)?.filter { it.value in installedExtensionsState }
            if (!cachedScrapers.isNullOrEmpty() && scrapers.isEmpty()) {
                scrapers = cachedScrapers
                if (selectedScraper == null) {
                    selectedScraper = cachedScrapers.firstOrNull()
                }
            } else if (scrapers.isEmpty()) {
                isScrapersLoading = true
            }
            try {
                val manifest = runner.fetchManifest(provider.url)
                val installed = manifest.filter { it.value in installedExtensionsState }
                scrapers = installed
                cacheStore.putScrapers(provider.url, manifest)
                if (selectedScraper == null || !installed.any { it.value == selectedScraper?.value }) {
                    selectedScraper = installed.firstOrNull()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("TVSearchMoviesScreen", "Error loading scrapers", e)
                if (scrapers.isEmpty()) {
                    scrapers = emptyList()
                    selectedScraper = null
                }
            } finally {
                isScrapersLoading = false
            }
        }
    }

    val performSearch: (Boolean, Boolean) -> Unit = { isNextPage, isImmediate ->
        val provider = selectedProvider
        val scraper = selectedScraper
        if (provider != null && scraper != null) {
            searchJob?.cancel()
            val targetPage = if (isNextPage) page else 1
            val trimmedQuery = searchQuery.trim()

            if (!isNextPage && trimmedQuery.isBlank()) {
                val cached = cacheStore.getMovies(provider.url, scraper.value, "")
                if (!cached.isNullOrEmpty() && movies.isEmpty()) {
                    movies = cached
                    page = 2
                    hasMore = true
                }
            }

            searchJob = scope.launch {
                if (!isImmediate && !isNextPage && trimmedQuery.isNotBlank()) {
                    delay(350)
                }
                isLoading = true
                if (!isNextPage) {
                    page = 1
                    hasMore = true
                }
                try {
                    val newMovies = if (trimmedQuery.isNotBlank()) {
                        runner.getSearchPosts(provider.url, scraper.value, trimmedQuery, targetPage)
                    } else {
                        runner.getPosts(provider.url, scraper.value, filter = "", page = targetPage)
                    }

                    if (!isNextPage && trimmedQuery.isBlank() && newMovies.isNotEmpty()) {
                        cacheStore.putMovies(provider.url, scraper.value, "", newMovies)
                    }

                    if (newMovies.isEmpty()) {
                        hasMore = false
                        if (!isNextPage) {
                            movies = emptyList()
                        }
                    } else {
                        movies = if (isNextPage) movies + newMovies else newMovies
                        page = targetPage + 1
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
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
            performSearch(false, false)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        // 1. Pill-Shaped Search Input Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by title, genre, or actor...", color = OttTextSecondary) },
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = OttTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = OttWhite,
                unfocusedTextColor = OttWhite,
                focusedBorderColor = OttAmberStreaming,
                unfocusedBorderColor = OttBorderSubtle,
                focusedContainerColor = OttSurface,
                unfocusedContainerColor = OttSurface,
                cursorColor = OttAmberStreaming
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { performSearch(false, true) }),
            shape = RoundedCornerShape(30.dp),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (primaryFocusRequester != null) Modifier.focusRequester(primaryFocusRequester) else Modifier)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.DirectionDown -> { focusManager.moveFocus(FocusDirection.Down); true }
                            Key.DirectionUp -> { focusManager.moveFocus(FocusDirection.Up); true }
                            Key.DirectionLeft -> if (searchQuery.isEmpty()) { focusManager.moveFocus(FocusDirection.Left); true } else false
                            else -> false
                        }
                    } else false
                }
                .padding(end = 88.dp, bottom = 12.dp)
        )

        // 2. Scraper selector chips if multiple extensions are installed
        if (scrapers.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                items(scrapers) { scraper ->
                    val isSelected = selectedScraper?.value == scraper.value
                    var isFocused by remember { mutableStateOf(false) }

                    Surface(
                        onClick = {
                            selectedScraper = scraper
                            movies = emptyList()
                            performSearch(false, true)
                        },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (isSelected) OttAmberStreaming else OttSurface,
                            focusedContainerColor = OttWhite,
                            pressedContainerColor = OttWhite.copy(alpha = 0.85f)
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                        modifier = Modifier
                            .wrapContentWidth()
                            .onFocusChanged { isFocused = it.isFocused }
                    ) {
                        Text(
                            text = scraper.display_name,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isFocused || isSelected) OttBgPrimary else OttWhite,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // 3. Popular Categories Chips
        Text(
            text = "Popular Categories",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = OttTextSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val genres = listOf("Action", "Comedy", "Horror", "Drama", "Sci-Fi", "Thriller", "Romance", "Adventure")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
        ) {
            items(genres) { genre ->
                var isFocused by remember { mutableStateOf(false) }
                Surface(
                    onClick = {
                        searchQuery = genre
                        performSearch(false, true)
                    },
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = OttSurface,
                        focusedContainerColor = OttWhite,
                        pressedContainerColor = OttWhite.copy(alpha = 0.85f)
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
                    modifier = Modifier
                        .wrapContentWidth()
                        .onFocusChanged { isFocused = it.isFocused }
                        .border(1.dp, OttBorderSubtle, RoundedCornerShape(20.dp))
                ) {
                    Text(
                        text = genre,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isFocused) OttBgPrimary else OttWhite,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                    )
                }
            }
        }

        // 4. Grid Title Row
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
                fontWeight = FontWeight.Medium,
                color = OttWhite
            )
            if (selectedScraper != null) {
                Text(
                    text = "Source: ${selectedScraper?.display_name}",
                    fontSize = 12.sp,
                    color = OttAmberStreaming,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 5. Full Width Grid of Results
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val gridState = rememberLazyGridState()

            val shouldLoadMore = remember {
                derivedStateOf {
                    val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()
                        ?: return@derivedStateOf false
                    lastVisibleItem.index >= gridState.layoutInfo.totalItemsCount - 5
                }
            }

            LaunchedEffect(shouldLoadMore.value) {
                if (shouldLoadMore.value && hasMore && !isLoading && movies.isNotEmpty()) {
                    performSearch(true, true)
                }
            }

            if (isLoading && movies.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = OttAmberStreaming)
                }
            } else if (movies.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val message = when {
                        activeProviders.isEmpty() -> "No Vega Providers added. Please configure provider in Playlists."
                        installedExtensionsState.isEmpty() -> "No Extensions installed. Please install scrapers in Extensions."
                        searchQuery.isBlank() -> "Type in search bar or select a category above"
                        else -> "No results found for \"$searchQuery\""
                    }
                    Text(
                        text = message,
                        color = OttTextSecondary,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 138.dp),
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(OttSpecs.CarouselItemGap),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(movies) { _, movie ->
                        val provider = selectedProvider
                        val scraper = selectedScraper
                        OttTallPosterCard(
                            title = movie.title,
                            imageUrl = movie.image,
                            onClick = {
                                if (provider != null && scraper != null) {
                                    onMovieClick(movie, scraper, provider)
                                }
                            },
                            useFixedCarouselSize = false
                        )
                    }

                    if (isLoading && movies.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = OttAmberStreaming,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (isLoading && movies.isNotEmpty()) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    color = OttAmberStreaming
                )
            }
        }
    }
}
