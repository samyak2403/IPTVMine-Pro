package com.samyak.iptvminepro.ui.screens.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.samyak.iptvminepro.R
import com.samyak.iptvminepro.model.Channel
import com.samyak.iptvminepro.model.Provider
import com.samyak.iptvminepro.model.VegaCatalog
import com.samyak.iptvminepro.model.VegaProvider
import com.samyak.iptvminepro.provider.ChannelsProvider
import com.samyak.iptvminepro.ui.viewmodel.MoviesViewModel
import kotlin.math.absoluteValue

enum class CategoryTabType {
    MOVIES,
    LIVE_TV
}

@Composable
fun CategoryScreen(
    channelsViewModel: ChannelsProvider = viewModel(),
    moviesViewModel: MoviesViewModel = viewModel(),
    onTvCategoryClick: (String) -> Unit = {},
    onMovieCategoryClick: (VegaCatalog, Provider, VegaProvider) -> Unit = { _, _, _ -> }
) {
    // Live TV states
    val channels by channelsViewModel.channels.observeAsState(emptyList())
    val isTvLoading by channelsViewModel.isLoading.observeAsState(false)
    val tvErrorMessage by channelsViewModel.error.observeAsState(null)

    LaunchedEffect(Unit) {
        channelsViewModel.loadIfNeeded()
        moviesViewModel.refreshProvidersAndInit()
    }

    val tvCategoriesWithCount = remember(channels) {
        channels.groupBy { it.category }
            .mapValues { it.value.size }
            .toList()
            .sortedBy { it.first }
    }

    // Movies states
    val vegaProviders = moviesViewModel.vegaProvidersList
    val selectedProvider by moviesViewModel.selectedProvider.collectAsState()
    val scrapers by moviesViewModel.scrapers.collectAsState()
    val selectedScraper by moviesViewModel.selectedScraper.collectAsState()
    val isScrapersLoading by moviesViewModel.isScrapersLoading.collectAsState()
    val movieCategories by moviesViewModel.categories.collectAsState()
    val isMoviesLoading by moviesViewModel.isLoading.collectAsState()

    // Determine initial tab:
    // If no TV source URLs configured or channels empty, default to MOVIES.
    // Otherwise default to LIVE_TV.
    var userManuallySelectedTab by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable {
        mutableStateOf(
            if (channels.isNotEmpty()) CategoryTabType.LIVE_TV else CategoryTabType.MOVIES
        )
    }

    // Auto-switch to MOVIES if Live TV has no source URLs configured and user hasn't explicitly tapped Live TV
    LaunchedEffect(channels, tvErrorMessage) {
        if (!userManuallySelectedTab && channels.isEmpty() && tvErrorMessage == "No source URLs configured") {
            selectedTab = CategoryTabType.MOVIES
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top TabRow to toggle between Movies and Live TV
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = Color.White,
            contentColor = Color(0xFF26A69A),
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                    color = Color(0xFF26A69A)
                )
            },
            divider = {
                HorizontalDivider(color = Color(0xFFE5E7EB))
            }
        ) {
            // Tab 0: Movies
            Tab(
                selected = selectedTab == CategoryTabType.MOVIES,
                onClick = {
                    userManuallySelectedTab = true
                    selectedTab = CategoryTabType.MOVIES
                    moviesViewModel.refreshProvidersAndInit()
                },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Movie,
                            contentDescription = null,
                            tint = if (selectedTab == CategoryTabType.MOVIES) Color(0xFF26A69A) else Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.tab_movies),
                            color = if (selectedTab == CategoryTabType.MOVIES) Color(0xFF26A69A) else Color.Gray,
                            fontWeight = if (selectedTab == CategoryTabType.MOVIES) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 15.sp
                        )
                        if (movieCategories.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Badge(
                                containerColor = if (selectedTab == CategoryTabType.MOVIES) Color(0xFF26A69A) else Color.Gray.copy(alpha = 0.15f),
                                contentColor = if (selectedTab == CategoryTabType.MOVIES) Color.White else Color.Gray
                            ) {
                                Text("${movieCategories.size}", fontSize = 11.sp)
                            }
                        }
                    }
                }
            )

            // Tab 1: Live TV
            Tab(
                selected = selectedTab == CategoryTabType.LIVE_TV,
                onClick = {
                    userManuallySelectedTab = true
                    selectedTab = CategoryTabType.LIVE_TV
                },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Tv,
                            contentDescription = null,
                            tint = if (selectedTab == CategoryTabType.LIVE_TV) Color(0xFF26A69A) else Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.tab_live_tv),
                            color = if (selectedTab == CategoryTabType.LIVE_TV) Color(0xFF26A69A) else Color.Gray,
                            fontWeight = if (selectedTab == CategoryTabType.LIVE_TV) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 15.sp
                        )
                        if (tvCategoriesWithCount.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Badge(
                                containerColor = if (selectedTab == CategoryTabType.LIVE_TV) Color(0xFF26A69A) else Color.Gray.copy(alpha = 0.15f),
                                contentColor = if (selectedTab == CategoryTabType.LIVE_TV) Color.White else Color.Gray
                            ) {
                                Text("${tvCategoriesWithCount.size}", fontSize = 11.sp)
                            }
                        }
                    }
                }
            )
        }

        // Tab Content
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                CategoryTabType.MOVIES -> {
                    MovieCategoriesContent(
                        vegaProviders = vegaProviders,
                        selectedProvider = selectedProvider,
                        scrapers = scrapers,
                        selectedScraper = selectedScraper,
                        isScrapersLoading = isScrapersLoading,
                        movieCategories = movieCategories,
                        isMoviesLoading = isMoviesLoading,
                        onSelectScraper = { moviesViewModel.selectScraper(it) },
                        onMovieCategoryClick = { catalog, provider, scraper ->
                            onMovieCategoryClick(catalog, provider, scraper)
                        }
                    )
                }

                CategoryTabType.LIVE_TV -> {
                    LiveTvCategoriesContent(
                        channels = channels,
                        isTvLoading = isTvLoading,
                        tvErrorMessage = tvErrorMessage,
                        categoriesWithCount = tvCategoriesWithCount,
                        onTvCategoryClick = onTvCategoryClick
                    )
                }
            }
        }
    }
}

@Composable
private fun MovieCategoriesContent(
    vegaProviders: List<Provider>,
    selectedProvider: Provider?,
    scrapers: List<VegaProvider>,
    selectedScraper: VegaProvider?,
    isScrapersLoading: Boolean,
    movieCategories: List<VegaCatalog>,
    isMoviesLoading: Boolean,
    onSelectScraper: (VegaProvider) -> Unit,
    onMovieCategoryClick: (VegaCatalog, Provider, VegaProvider) -> Unit
) {
    if (vegaProviders.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Movie,
                contentDescription = null,
                tint = Color(0xFF9E9E9E),
                modifier = Modifier
                    .size(80.dp)
                    .padding(bottom = 16.dp)
            )
            Text(
                text = "No Movie Providers Configured",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(id = R.string.desc_movie_providers_empty),
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    } else if (isScrapersLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF26A69A))
        }
    } else if (scrapers.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Extension,
                contentDescription = null,
                tint = Color(0xFF9E9E9E),
                modifier = Modifier
                    .size(80.dp)
                    .padding(bottom = 16.dp)
            )
            Text(
                text = "No Extensions Installed",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(id = R.string.desc_extensions_empty),
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            // Scraper chips (if multiple extensions installed)
            if (scrapers.size > 1) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(scrapers) { scraper ->
                        val isSelected = selectedScraper?.value == scraper.value
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectScraper(scraper) },
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
                if (isMoviesLoading && movieCategories.isEmpty()) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color(0xFF26A69A)
                    )
                } else if (movieCategories.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(id = R.string.label_no_movie_categories),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(movieCategories, key = { it.filter.ifEmpty { it.title } }) { catalog ->
                            MovieCategoryCard(
                                name = catalog.title,
                                onClick = {
                                    val provider = selectedProvider ?: vegaProviders.firstOrNull()
                                    val scraper = selectedScraper ?: scrapers.firstOrNull()
                                    if (provider != null && scraper != null) {
                                        onMovieCategoryClick(catalog, provider, scraper)
                                    }
                                }
                            )
                        }
                    }
                }

                if (isMoviesLoading && movieCategories.isNotEmpty()) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                        color = Color(0xFF26A69A)
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveTvCategoriesContent(
    channels: List<Channel>,
    isTvLoading: Boolean,
    tvErrorMessage: String?,
    categoriesWithCount: List<Pair<String, Int>>,
    onTvCategoryClick: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (isTvLoading && channels.isEmpty()) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        } else if (!tvErrorMessage.isNullOrEmpty() && channels.isEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.undraw_files_missing_ntwe),
                    contentDescription = stringResource(id = R.string.desc_error),
                    modifier = Modifier
                        .size(200.dp)
                        .padding(bottom = 16.dp)
                )
                Text(
                    text = tvErrorMessage,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        } else if (channels.isEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.undraw_files_missing_ntwe),
                    contentDescription = stringResource(id = R.string.desc_no_channels),
                    modifier = Modifier
                        .size(200.dp)
                        .padding(bottom = 16.dp)
                )
                Text(
                    text = stringResource(id = R.string.label_no_categories),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(categoriesWithCount, key = { it.first }) { (categoryName, count) ->
                    CategoryCard(
                        name = categoryName,
                        count = count,
                        onClick = { onTvCategoryClick(categoryName) }
                    )
                }
            }
        }

        if (isTvLoading && channels.isNotEmpty()) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun MovieCategoryCard(
    name: String,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1.0f, label = "MovieCategoryScale")

    val gradients = remember {
        listOf(
            Brush.linearGradient(listOf(Color(0xFFE0F2F1), Color(0xFFB2DFDB))), // Teal
            Brush.linearGradient(listOf(Color(0xFFE3F2FD), Color(0xFFBBDEFB))), // Blue
            Brush.linearGradient(listOf(Color(0xFFEDE7F6), Color(0xFFD1C4E9))), // Purple
            Brush.linearGradient(listOf(Color(0xFFFCE4EC), Color(0xFFF8BBD0))), // Pink
            Brush.linearGradient(listOf(Color(0xFFFFF8E1), Color(0xFFFFECB3))), // Amber
            Brush.linearGradient(listOf(Color(0xFFE8F5E9), Color(0xFFC8E6C9))), // Green
            Brush.linearGradient(listOf(Color(0xFFF3E5F5), Color(0xFFE1BEE7))), // Deep Purple
            Brush.linearGradient(listOf(Color(0xFFFFF3E0), Color(0xFFFFE0B2)))  // Orange
        )
    }

    val cardGradient = remember(name) {
        val index = name.hashCode().absoluteValue % gradients.size
        gradients[index]
    }

    val primaryColor = remember(name) {
        val hash = name.hashCode().absoluteValue
        when (hash % 8) {
            0 -> Color(0xFF00796B)
            1 -> Color(0xFF1976D2)
            2 -> Color(0xFF512DA8)
            3 -> Color(0xFFC2185B)
            4 -> Color(0xFFF57C00)
            5 -> Color(0xFF388E3C)
            6 -> Color(0xFF7B1FA2)
            else -> Color(0xFFE65100)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isFocused) 8.dp else 2.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(cardGradient)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(primaryColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Movie,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(primaryColor.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Movies",
                            color = primaryColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun CategoryCard(
    name: String,
    count: Int,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1.0f, label = "TvCategoryScale")

    val gradients = remember {
        listOf(
            Brush.linearGradient(listOf(Color(0xFFE0F2F1), Color(0xFFB2DFDB))), // Teal
            Brush.linearGradient(listOf(Color(0xFFE3F2FD), Color(0xFFBBDEFB))), // Blue
            Brush.linearGradient(listOf(Color(0xFFEDE7F6), Color(0xFFD1C4E9))), // Purple
            Brush.linearGradient(listOf(Color(0xFFFCE4EC), Color(0xFFF8BBD0))), // Pink
            Brush.linearGradient(listOf(Color(0xFFFFF8E1), Color(0xFFFFECB3))), // Amber
            Brush.linearGradient(listOf(Color(0xFFE8F5E9), Color(0xFFC8E6C9))), // Green
            Brush.linearGradient(listOf(Color(0xFFF3E5F5), Color(0xFFE1BEE7))), // Deep Purple
            Brush.linearGradient(listOf(Color(0xFFFFF3E0), Color(0xFFFFE0B2)))  // Orange
        )
    }

    val cardGradient = remember(name) {
        val index = name.hashCode().absoluteValue % gradients.size
        gradients[index]
    }

    val primaryColor = remember(name) {
        val hash = name.hashCode().absoluteValue
        when (hash % 8) {
            0 -> Color(0xFF00796B)
            1 -> Color(0xFF1976D2)
            2 -> Color(0xFF512DA8)
            3 -> Color(0xFFC2185B)
            4 -> Color(0xFFF57C00)
            5 -> Color(0xFF388E3C)
            6 -> Color(0xFF7B1FA2)
            else -> Color(0xFFE65100)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isFocused) 8.dp else 2.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(cardGradient)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(primaryColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(primaryColor.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (count == 1) stringResource(id = R.string.item_count_single) else stringResource(id = R.string.item_count_plural, count),
                            color = primaryColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121), // High contrast text for light colored card backgrounds
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
