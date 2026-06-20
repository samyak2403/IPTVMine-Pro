package com.samyak.iptvminepro.ui.screens.movies

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.samyak.iptvminepro.model.VegaPost
import com.samyak.iptvminepro.ui.components.MovieCard
import com.samyak.iptvminepro.ui.viewmodel.CategoryMoviesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryMoviesScreen(
    categoryName: String,
    categoryFilter: String,
    providerUrl: String,
    scraperValue: String,
    navController: NavController,
    onMovieClick: (VegaPost) -> Unit
) {
    val context = LocalContext.current

    // ViewModel scoped to this NavBackStackEntry — survives back-nav from MovieDetail
    val viewModel: CategoryMoviesViewModel = viewModel()

    // Trigger initial load; no-op on back-navigation when data is already cached
    LaunchedEffect(Unit) {
        viewModel.initIfNeeded()
    }

    // Collect state from ViewModel
    val movies by viewModel.movies.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearchActive by viewModel.isSearchActive.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    // Show error as Toast
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    // Grid scroll state at composable top level — preserves scroll position across back-nav
    val gridState = rememberLazyGridState()

    // Infinite scroll trigger
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search in $categoryName...", color = Color.White.copy(alpha = 0.6f)) },
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
                            keyboardActions = KeyboardActions(onSearch = { viewModel.submitSearch() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(categoryName, fontWeight = FontWeight.Bold)
                    }
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
                    if (isSearchActive) {
                        IconButton(onClick = {
                            if (searchQuery.isNotEmpty()) {
                                viewModel.clearSearchQuery()
                            } else {
                                viewModel.deactivateSearch()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close Search"
                            )
                        }
                    } else {
                        IconButton(onClick = { viewModel.activateSearch() }) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Search"
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
                if (movies.isEmpty() && !isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No movies or shows found",
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
                            MovieCard(
                                movie = movie,
                                onClick = { onMovieClick(movie) }
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

                if (isLoading && movies.isEmpty()) {
                    CircularProgressIndicator(
                        color = Color(0xFF26A69A),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    )
}
