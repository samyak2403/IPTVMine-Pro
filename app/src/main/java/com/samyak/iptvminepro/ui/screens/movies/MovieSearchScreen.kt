package com.samyak.iptvminepro.ui.screens.movies

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.samyak.iptvminepro.model.Provider
import com.samyak.iptvminepro.model.VegaPost
import com.samyak.iptvminepro.model.VegaProvider
import com.samyak.iptvminepro.ui.components.MovieCard
import com.samyak.iptvminepro.ui.theme.LocalIsBlackTheme
import com.samyak.iptvminepro.ui.viewmodel.MovieSearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieSearchScreen(
    navController: NavController,
    viewModel: MovieSearchViewModel = viewModel(),
    onMovieClick: (VegaPost, VegaProvider, Provider) -> Unit
) {
    val isBlackTheme = LocalIsBlackTheme.current
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        viewModel.initIfNeeded()
    }

    val activeProviders = viewModel.activeProviders
    val selectedProvider by viewModel.selectedProvider.collectAsState()
    val scrapers by viewModel.scrapers.collectAsState()
    val selectedScraper by viewModel.selectedScraper.collectAsState()
    val isScrapersLoading by viewModel.isScrapersLoading.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val movies by viewModel.movies.collectAsState()
    val moviesByScraper by viewModel.moviesByScraper.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, "Error fetching content: $it", Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

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
            viewModel.loadMore()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
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
                        keyboardActions = KeyboardActions(onSearch = {
                            keyboardController?.hide()
                            viewModel.submitSearch()
                        }),
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
                        IconButton(onClick = { viewModel.clearSearch() }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear Search"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isBlackTheme) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary,
                    titleContentColor = if (isBlackTheme) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = if (isBlackTheme) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = if (isBlackTheme) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        content = { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Scraper filter chips if multiple extensions
                    if (scrapers.size > 1) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                val isAllSelected = selectedScraper == null
                                FilterChip(
                                    selected = isAllSelected,
                                    onClick = { viewModel.selectAllScrapers() },
                                    label = { Text("All", fontSize = 13.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF26A69A),
                                        selectedLabelColor = Color.White,
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    border = null,
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                            items(scrapers) { scraper ->
                                val isSelected = selectedScraper?.value == scraper.value
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.selectScraper(scraper) },
                                    label = { Text(scraper.display_name, fontSize = 13.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF26A69A),
                                        selectedLabelColor = Color.White,
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    border = null,
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        val emptyMessage = when {
                            activeProviders.isEmpty() -> "Please add a Vega Movies provider in Settings"
                            scrapers.isEmpty() && !isScrapersLoading -> "No extensions installed. Please install an extension from Settings."
                            searchQuery.isBlank() -> "Type in the search bar to find movies and TV shows"
                            else -> "No results found for \"$searchQuery\""
                        }

                        if (activeProviders.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = emptyMessage,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                    fontSize = 16.sp
                                )
                            }
                        } else if (scrapers.isEmpty() && !isScrapersLoading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = emptyMessage,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                    fontSize = 16.sp
                                )
                            }
                        } else if (selectedScraper == null) {
                            // ── MULTI-EXTENSION SECTIONS VIEW (matches second image!) ──────────────────
                            if (moviesByScraper.isEmpty() && !isLoading && !isScrapersLoading) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = emptyMessage,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                        fontSize = 16.sp
                                    )
                                }
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    moviesByScraper.forEach { (scraper, movieList) ->
                                        if (movieList.isNotEmpty()) {
                                            item(key = scraper.value) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(bottom = 16.dp)
                                                ) {
                                                    // Section Header (exact style of Image 2!)
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable { viewModel.selectScraper(scraper) }
                                                            .padding(bottom = 12.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            text = scraper.display_name,
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onBackground
                                                        )
                                                        Icon(
                                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                            contentDescription = "View All",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }

                                                    // Horizontal Carousel of MovieCards (exact style of Image 2!)
                                                    LazyRow(
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        itemsIndexed(
                                                            movieList,
                                                            key = { index, movie ->
                                                                if (movie.link.isNotBlank()) "${movie.link}_$index" else "${scraper.value}_${movie.title}_$index"
                                                            }
                                                        ) { _, movie ->
                                                            MovieCard(
                                                                movie = movie,
                                                                onClick = {
                                                                    val provider = selectedProvider
                                                                    if (provider != null) {
                                                                        onMovieClick(movie, scraper, provider)
                                                                    }
                                                                },
                                                                modifier = Modifier.width(130.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Shimmer placeholder for sections when loading
                            if ((isLoading || isScrapersLoading) && moviesByScraper.isEmpty()) {
                                com.samyak.iptvminepro.ui.components.HomeShimmerPlaceholder(
                                    modifier = Modifier.align(Alignment.TopCenter)
                                )
                            } else if (isLoading && moviesByScraper.isNotEmpty()) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                                    color = Color(0xFF26A69A)
                                )
                            }
                        } else {
                            // ── SINGLE EXTENSION GRID VIEW (with pagination) ────────────────────────
                            if (movies.isEmpty() && !isLoading && !isScrapersLoading) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = emptyMessage,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
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

                            // Only show shimmer placeholder when loading AND no cached/loaded movies in memory
                            if ((isLoading || isScrapersLoading) && movies.isEmpty()) {
                                com.samyak.iptvminepro.ui.components.GridShimmerPlaceholder(
                                    modifier = Modifier.align(Alignment.TopCenter)
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}
