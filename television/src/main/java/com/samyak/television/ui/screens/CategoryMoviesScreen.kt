package com.samyak.television.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.samyak.television.R
import com.samyak.television.data.VegaProviderRunner
import com.samyak.television.model.Provider
import com.samyak.television.model.VegaCatalog
import com.samyak.television.model.VegaPost
import com.samyak.television.model.VegaProvider
import com.samyak.television.ui.components.OttSpecs
import com.samyak.television.ui.components.OttTallPosterCard
import com.samyak.television.ui.components.rememberOttSafeInsets
import com.samyak.television.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CategoryMoviesScreen(
    category: VegaCatalog,
    scraper: VegaProvider,
    provider: Provider,
    onBack: () -> Unit,
    onMovieClick: (VegaPost, VegaProvider, Provider) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val runner = remember { VegaProviderRunner(context) }
    DisposableEffect(Unit) {
        onDispose {
            runner.destroy()
        }
    }

    var movies by remember { mutableStateOf<List<VegaPost>>(emptyList()) }
    var page by remember { mutableIntStateOf(1) }
    var isLoading by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }

    val backButtonFocusRequester = remember { FocusRequester() }
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    LaunchedEffect(movies, isLoading) {
        if (movies.isNotEmpty() && !isLoading && !hasRequestedInitialFocus) {
            try {
                backButtonFocusRequester.requestFocus()
                hasRequestedInitialFocus = true
            } catch (_: Exception) {
            }
        }
    }

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
                    val newMovies = runner.getPosts(provider.url, scraper.value, filter = category.filter, page = page)
                    if (newMovies.isEmpty()) {
                        hasMore = false
                    } else {
                        movies = if (isNextPage) movies + newMovies else newMovies
                        page++
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("TVCategoryMovies", "Error loading category movies", e)
                    if (!isNextPage) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(category, scraper, provider) {
        hasRequestedInitialFocus = false
        loadMovies(false)
    }

    val ottInsets = rememberOttSafeInsets()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OttBgPrimary)
            .padding(
                start = maxOf(ottInsets.railStart + 12.dp, 28.dp),
                top = ottInsets.top,
                end = ottInsets.end,
                bottom = ottInsets.bottom
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.colors(
                        containerColor = OttSurface,
                        focusedContainerColor = OttWhite,
                        contentColor = OttWhite,
                        focusedContentColor = OttBgPrimary
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
                    modifier = Modifier
                        .focusRequester(backButtonFocusRequester)
                        .focusProperties {
                            left = backButtonFocusRequester
                            up = backButtonFocusRequester
                        }
                        .padding(end = 18.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_back),
                        contentDescription = "Back",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Back", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = category.title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = OttWhite
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "•",
                        fontSize = 14.sp,
                        color = OttTextSecondary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = scraper.display_name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = OttAmberStreaming
                    )
                }
            }

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
                        loadMovies(true)
                    }
                }

                if (isLoading && movies.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = OttAmberStreaming)
                    }
                } else if (movies.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No content found in this category",
                            color = OttTextSecondary,
                            fontSize = 15.sp
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
                            OttTallPosterCard(
                                title = movie.title,
                                imageUrl = movie.image,
                                onClick = { onMovieClick(movie, scraper, provider) },
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
            }
        }
    }
}
