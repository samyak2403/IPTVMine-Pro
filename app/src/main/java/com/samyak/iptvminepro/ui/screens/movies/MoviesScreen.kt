package com.samyak.iptvminepro.ui.screens.movies

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samyak.iptvminepro.model.*
import com.samyak.iptvminepro.ui.components.MovieCard
import com.samyak.iptvminepro.ui.viewmodel.MoviesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoviesScreen(
    viewModel: MoviesViewModel,          // Passed from Activity scope – never recreated
    initialCategoryTitle: String? = null,
    onMovieClick: (VegaPost, VegaProvider, Provider) -> Unit
) {
    val context = LocalContext.current

    // initIfNeeded is a no-op if data is already loaded (guarded by dataLoaded flag)
    LaunchedEffect(Unit) {
        viewModel.initIfNeeded(initialCategoryTitle)
    }

    // Collect state
    val vegaProvidersList = viewModel.vegaProvidersList
    val selectedProvider by viewModel.selectedProvider.collectAsState()
    val scrapers by viewModel.scrapers.collectAsState()
    val selectedScraper by viewModel.selectedScraper.collectAsState()
    val isScrapersLoading by viewModel.isScrapersLoading.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val movies by viewModel.movies.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val error by viewModel.error.collectAsState()

    // Show error as Toast
    LaunchedEffect(error) {
        error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    // Grid scroll state hoisted to composable level – preserves position across back-navigation
    // (rememberLazyGridState uses rememberSaveable backed by the NavBackStackEntry registry)
    val gridState = rememberLazyGridState()

    // Infinite-scroll trigger – derived at top level so it's stable across recompositions
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()
                ?: return@derivedStateOf false
            lastVisibleItem.index >= gridState.layoutInfo.totalItemsCount - 6
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && hasMore && !isLoading && movies.isNotEmpty()) {
            viewModel.loadMovies(true)
        }
    }

    // Main UI Layout
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (vegaProvidersList.isEmpty()) {
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
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Please add a Vega Movies provider in Settings to browse and play movies and web series.",
                        fontSize = 15.sp,
                        color = Color(0xFF6B7280),
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                }
            }
        } else {
            // Provider dropdown (only when multiple providers)
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
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
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
                                    viewModel.selectProvider(provider)
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search movies...", color = Color(0xFF9CA3AF)) },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = Color(0xFF6B7280))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearSearch() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear", tint = Color(0xFF6B7280))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    viewModel.submitSearch()
                }),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF26A69A),
                    unfocusedBorderColor = Color(0xFFE0E0E0),
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                )
            )

            // Scraper tabs
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

            // Category tabs (only when search is empty)
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
                            onClick = { viewModel.selectCategory(category) },
                            label = { Text(category.title, fontSize = 13.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF26A69A).copy(alpha = 0.2f),
                                selectedLabelColor = Color(0xFF26A69A),
                                containerColor = Color.Transparent,
                                labelColor = Color(0xFF6B7280)
                            ),
                            border = if (isSelected)
                                BorderStroke(1.dp, Color(0xFF26A69A))
                            else
                                BorderStroke(1.dp, Color(0xFFE0E0E0)),
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
            }

            // Movies Grid
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (movies.isEmpty() && !isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (scrapers.isEmpty())
                                "No extensions installed. Please install an extension from Settings."
                            else
                                "No movies or shows found",
                            color = Color(0xFF6B7280),
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
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

                        // Loading footer
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

                // Full-screen spinner for first load
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
