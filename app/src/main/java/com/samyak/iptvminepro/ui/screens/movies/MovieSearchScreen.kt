package com.samyak.iptvminepro.ui.screens.movies

import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.samyak.iptvminepro.model.Provider
import com.samyak.iptvminepro.model.VegaPost
import com.samyak.iptvminepro.model.VegaProvider
import com.samyak.iptvminepro.ui.components.MovieCard
import com.samyak.iptvminepro.ui.viewmodel.MovieSearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieSearchScreen(
    navController: NavController,
    viewModel: MovieSearchViewModel = viewModel(),
    onMovieClick: (VegaPost, VegaProvider, Provider) -> Unit
) {
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
                Column(modifier = Modifier.fillMaxSize()) {
                    // Scraper filter chips if multiple extensions
                    if (scrapers.size > 1) {
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
                                    onClick = { viewModel.selectScraper(scraper) },
                                    label = { Text(scraper.display_name, fontSize = 13.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF26A69A),
                                        selectedLabelColor = Color.White,
                                        containerColor = Color(0xFFF5F5F5),
                                        labelColor = Color(0xFF6B7280)
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
                                    text = if (scrapers.isEmpty()) "No extensions installed. Please install an extension from Settings." else "No movies or shows found",
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
    )
}
