package com.samyak.iptvminepro.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.samyak.iptvminepro.model.Provider
import com.samyak.iptvminepro.model.VegaPost
import com.samyak.iptvminepro.provider.VegaProviderRunner
import com.samyak.iptvminepro.ui.components.MovieCard
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    val runner = remember { VegaProviderRunner(context) }

    // State
    var movies by remember { mutableStateOf<List<VegaPost>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    var page by remember { mutableStateOf(1) }
    var isLoading by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }

    // Fetch movies list
    val loadMovies: (Boolean) -> Unit = { isNextPage ->
        if (!isLoading) {
            isLoading = true
            if (!isNextPage) {
                page = 1
                movies = emptyList()
                hasMore = true
            }
            scope.launch {
                try {
                    val newMovies = if (isSearchActive && searchQuery.isNotBlank()) {
                        runner.getSearchPosts(providerUrl, scraperValue, searchQuery.trim(), page)
                    } else {
                        runner.getPosts(providerUrl, scraperValue, categoryFilter, page)
                    }
                    if (newMovies.isEmpty()) {
                        hasMore = false
                    } else {
                        movies = if (isNextPage) movies + newMovies else newMovies
                        page++
                    }
                } catch (e: Exception) {
                    Log.e("CategoryMoviesScreen", "Error loading movies", e)
                    if (!isNextPage) {
                        Toast.makeText(context, "Error fetching content: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    isLoading = false
                }
            }
        }
    }

    // Initial load
    LaunchedEffect(Unit) {
        loadMovies(false)
    }

    // Trigger search when query changes or search is closed
    LaunchedEffect(searchQuery, isSearchActive) {
        if (!isSearchActive) {
            // When search is deactivated, reload original category list
            loadMovies(false)
        } else if (searchQuery.isNotBlank()) {
            // Reload with search query
            loadMovies(false)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
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
                            keyboardActions = KeyboardActions(onSearch = { loadMovies(false) }),
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
                                searchQuery = ""
                            } else {
                                isSearchActive = false
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close Search"
                            )
                        }
                    } else {
                        IconButton(onClick = { isSearchActive = true }) {
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
                    .background(Color(0xFF0F0E13)) // HSL Tailored premium deep space dark background
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
