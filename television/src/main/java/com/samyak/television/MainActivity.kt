package com.samyak.television

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.samyak.television.data.ProviderRepository
import com.samyak.television.data.StartupTabPreferenceManager
import com.samyak.television.data.TelevisionViewModel
import com.samyak.television.model.Channel
import com.samyak.television.model.Provider
import com.samyak.television.model.ProviderType
import com.samyak.television.model.VegaProvider
import com.samyak.television.ui.components.OttSpecs
import com.samyak.television.ui.components.buildOttImageRequest
import com.samyak.television.ui.components.rememberOttSafeInsets
import com.samyak.television.ui.screens.*
import com.samyak.television.ui.theme.*
import com.samyak.television.utils.NetworkUtils

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.samyak.player.StreamResolverHolder.resolver =
            com.samyak.television.data.VegaStreamResolver(applicationContext)
        setContent {
            IPTVMineProTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape
                ) {
                    TelevisionApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TelevisionApp(
    viewModel: TelevisionViewModel = viewModel(),
    onSwitchToMobile: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isConnected by remember(context) {
        NetworkUtils.observeConnectivity(context)
    }.collectAsState(initial = NetworkUtils.isNetworkAvailable(context))

    val channels by viewModel.channels.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()

    val providerRepository = remember { ProviderRepository(context) }
    var providerList by remember { mutableStateOf(providerRepository.getProviders()) }
    val startupTab by StartupTabPreferenceManager.observeStartupTab(context)
    val hasActiveIptv = remember(providerList) {
        providerList.any { it.isActive && it.safeType == ProviderType.IPTV }
    }
    val hasActiveVega = remember(providerList) {
        providerList.any { it.isActive && it.safeType == ProviderType.VEGA }
    }
    val defaultScreenIndex = remember(startupTab, hasActiveIptv, hasActiveVega) {
        StartupTabPreferenceManager.resolveTvScreenIndex(
            startupTab = startupTab,
            hasActiveIptvProvider = hasActiveIptv,
            hasActiveVegaProvider = hasActiveVega
        )
    }

    var activeScreenIndex by remember { mutableIntStateOf(defaultScreenIndex) }
    // activeScreenIndex == 0: Live TV Grid (categories)
    // activeScreenIndex == 1: VOD Movies & Shows catalog (Home)
    // activeScreenIndex == 2: Search Movies Screen
    // activeScreenIndex == 3: Manage Extensions
    // activeScreenIndex == 4: Manage Playlists/Providers
    // activeScreenIndex == 5: Speed Test Screen
    // activeScreenIndex == 7: Weather cast screen

    // Movie Detail Selection States
    var selectedMovieLink by remember { mutableStateOf<String?>(null) }
    var selectedMovieScraper by remember { mutableStateOf<VegaProvider?>(null) }
    var selectedMovieProvider by remember { mutableStateOf<Provider?>(null) }

    // Category View All States
    var selectedCategoryForViewAll by remember { mutableStateOf<com.samyak.television.model.VegaCatalog?>(null) }
    var viewAllScraper by remember { mutableStateOf<VegaProvider?>(null) }
    var viewAllProvider by remember { mutableStateOf<Provider?>(null) }

    BackHandler(enabled = selectedMovieLink != null) {
        selectedMovieLink = null
        selectedMovieScraper = null
        selectedMovieProvider = null
    }

    BackHandler(enabled = selectedMovieLink == null && selectedCategoryForViewAll != null) {
        selectedCategoryForViewAll = null
        viewAllScraper = null
        viewAllProvider = null
    }

    BackHandler(enabled = selectedMovieLink == null && selectedCategoryForViewAll == null && activeScreenIndex != defaultScreenIndex) {
        activeScreenIndex = defaultScreenIndex
    }

    val ottInsets = rememberOttSafeInsets()
    val contentFocusRequester = remember { FocusRequester() }
    val density = LocalDensity.current
    var rootTopInWindow by remember { mutableFloatStateOf(0f) }
    var focusedNavTooltip by remember { mutableStateOf<Pair<String, Float>?>(null) }

    val contentScreenPadding = Modifier
        .fillMaxSize()
        .padding(
            start = ottInsets.contentStart,
            top = ottInsets.top,
            end = ottInsets.end,
            bottom = ottInsets.bottom
        )

    val viewAllCategory = selectedCategoryForViewAll
    val viewAllSc = viewAllScraper
    val viewAllPr = viewAllProvider
    val activeMovieLink = selectedMovieLink
    val activeMovieSc = selectedMovieScraper
    val activeMoviePr = selectedMovieProvider
    val hasOverlay = (viewAllCategory != null && viewAllSc != null && viewAllPr != null) ||
        (activeMovieLink != null && activeMovieSc != null && activeMoviePr != null)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OttBgPrimary)
            .onGloballyPositioned { coords ->
                rootTopInWindow = coords.positionInWindow().y
            }
    ) {
        // 1. Background Shell (Content + Clock + Rail) - blocks focus entry while overlay is active
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusProperties {
                    onEnter = {
                        if (hasOverlay) cancelFocusChange()
                    }
                }
        ) {
        // Content Layer (Movies Home manages full-bleed backdrop + safe insets internally)
        Box(modifier = Modifier.fillMaxSize()) {
            when (activeScreenIndex) {
                1 -> {
                    MoviesScreen(
                        onMovieClick = { movie, scraper, provider ->
                            selectedMovieLink = movie.link
                            selectedMovieScraper = scraper
                            selectedMovieProvider = provider
                        },
                        onViewAllClick = { catalog, scraper, provider ->
                            selectedCategoryForViewAll = catalog
                            viewAllScraper = scraper
                            viewAllProvider = provider
                        },
                        primaryFocusRequester = contentFocusRequester
                    )
                }
                0 -> {
                    LiveTvScreen(
                        channels = channels,
                        categories = categories,
                        selectedCategory = selectedCategory,
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        onSelectCategory = { viewModel.selectCategory(it) },
                        onChannelClick = { channel ->
                            com.samyak.player.PlayerActivity.start(context, channel.name, channel.streamUrl)
                        },
                        primaryFocusRequester = contentFocusRequester
                    )
                }
                2 -> {
                    Box(modifier = contentScreenPadding) {
                        SearchMoviesScreen(
                            onMovieClick = { movie, scraper, provider ->
                                selectedMovieLink = movie.link
                                selectedMovieScraper = scraper
                                selectedMovieProvider = provider
                            },
                            primaryFocusRequester = contentFocusRequester
                        )
                    }
                }
                3 -> {
                    Box(modifier = contentScreenPadding) {
                        ExtensionsScreen(primaryFocusRequester = contentFocusRequester)
                    }
                }
                4 -> {
                    Box(modifier = contentScreenPadding) {
                        ManageProvidersScreen(
                            providerList = providerList,
                            onAddProvider = { title, url, type ->
                                val newProvider = Provider(title = title, url = url, type = type)
                                providerRepository.addProvider(newProvider)
                                providerList = providerRepository.getProviders()
                                viewModel.refreshChannels()
                            },
                            onDeleteProvider = { url ->
                                providerRepository.removeProvider(url)
                                providerList = providerRepository.getProviders()
                                viewModel.refreshChannels()
                            },
                            onSwitchToMobile = onSwitchToMobile,
                            primaryFocusRequester = contentFocusRequester
                        )
                    }
                }
                5 -> {
                    Box(modifier = contentScreenPadding) {
                        SpeedTestScreen(primaryFocusRequester = contentFocusRequester)
                    }
                }
                7 -> {
                    Box(modifier = contentScreenPadding) {
                        WeatherScreen(primaryFocusRequester = contentFocusRequester)
                    }
                }
            }
        }

        // Subtle Clock in Top-End clearing overscan & status bar
        ClockView(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = ottInsets.top, end = ottInsets.end)
        )

        // 2. Compact Icon-Only Floating Product_Nav Rail matching Figma (never crushed by cutouts)
        val railBrush = remember {
            Brush.horizontalGradient(
                0.0f to OttNavRailBg,
                0.72f to OttNavRailBg.copy(alpha = 0.82f),
                1.0f to Color.Transparent
            )
        }

        val onRailItemFocusChange: (String, Boolean, Float) -> Unit = { label, focused, centerY ->
            if (focused) {
                focusedNavTooltip = label to centerY
            } else if (focusedNavTooltip?.first == label) {
                focusedNavTooltip = null
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(ottInsets.railVisualWidth)
                .fillMaxHeight()
                .background(railBrush)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .padding(
                        start = ottInsets.railStart,
                        top = ottInsets.top,
                        bottom = ottInsets.bottom
                    )
                    .width(OttSpecs.RailIconColumnWidth)
                    .fillMaxHeight()
            ) {
                val itemButtonSize = when {
                    maxHeight < 370.dp -> 34.dp
                    maxHeight < 450.dp -> 38.dp
                    else -> 44.dp
                }
                val iconSize = when {
                    maxHeight < 370.dp -> 19.dp
                    maxHeight < 450.dp -> 21.dp
                    else -> 24.dp
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    SidebarItem(
                        selectedIcon = Icons.Filled.Home,
                        unselectedIcon = Icons.Outlined.Home,
                        label = "Home",
                        isSelected = activeScreenIndex == 1,
                        buttonSize = itemButtonSize,
                        iconSize = iconSize,
                        contentFocusRequester = contentFocusRequester,
                        onFocusChanged = { focused, y -> onRailItemFocusChange("Home", focused, y) },
                        onClick = { activeScreenIndex = 1 }
                    )
                    SidebarItem(
                        selectedIcon = Icons.Filled.Sensors,
                        unselectedIcon = Icons.Outlined.Sensors,
                        label = "Live TV",
                        isSelected = activeScreenIndex == 0,
                        buttonSize = itemButtonSize,
                        iconSize = iconSize,
                        contentFocusRequester = contentFocusRequester,
                        onFocusChanged = { focused, y -> onRailItemFocusChange("Live TV", focused, y) },
                        onClick = { activeScreenIndex = 0 }
                    )
                    SidebarItem(
                        selectedIcon = Icons.Filled.Search,
                        unselectedIcon = Icons.Outlined.Search,
                        label = "Search",
                        isSelected = activeScreenIndex == 2,
                        buttonSize = itemButtonSize,
                        iconSize = iconSize,
                        contentFocusRequester = contentFocusRequester,
                        onFocusChanged = { focused, y -> onRailItemFocusChange("Search", focused, y) },
                        onClick = { activeScreenIndex = 2 }
                    )
                    SidebarItem(
                        selectedIcon = Icons.Filled.GridView,
                        unselectedIcon = Icons.Outlined.GridView,
                        label = "Extensions",
                        isSelected = activeScreenIndex == 3,
                        buttonSize = itemButtonSize,
                        iconSize = iconSize,
                        contentFocusRequester = contentFocusRequester,
                        onFocusChanged = { focused, y -> onRailItemFocusChange("Extensions", focused, y) },
                        onClick = { activeScreenIndex = 3 }
                    )
                    SidebarItem(
                        selectedIcon = Icons.AutoMirrored.Filled.PlaylistPlay,
                        unselectedIcon = Icons.AutoMirrored.Outlined.PlaylistPlay,
                        label = "Playlists",
                        isSelected = activeScreenIndex == 4,
                        buttonSize = itemButtonSize,
                        iconSize = iconSize,
                        contentFocusRequester = contentFocusRequester,
                        onFocusChanged = { focused, y -> onRailItemFocusChange("Playlists", focused, y) },
                        onClick = { activeScreenIndex = 4 }
                    )
                    SidebarItem(
                        selectedIcon = Icons.Filled.Speed,
                        unselectedIcon = Icons.Outlined.Speed,
                        label = "Speed Test",
                        isSelected = activeScreenIndex == 5,
                        buttonSize = itemButtonSize,
                        iconSize = iconSize,
                        contentFocusRequester = contentFocusRequester,
                        onFocusChanged = { focused, y -> onRailItemFocusChange("Speed Test", focused, y) },
                        onClick = { activeScreenIndex = 5 }
                    )
                    SidebarItem(
                        selectedIcon = Icons.Filled.Cloud,
                        unselectedIcon = Icons.Outlined.Cloud,
                        label = "Weather",
                        isSelected = activeScreenIndex == 7,
                        buttonSize = itemButtonSize,
                        iconSize = iconSize,
                        contentFocusRequester = contentFocusRequester,
                        onFocusChanged = { focused, y -> onRailItemFocusChange("Weather", focused, y) },
                        onClick = { activeScreenIndex = 7 }
                    )

                    if (onSwitchToMobile != null) {
                        val mobileLabel = if (isConnected) "Mobile UI" else "Mobile (Offline)"
                        SidebarItem(
                            selectedIcon = Icons.Filled.PhoneAndroid,
                            unselectedIcon = Icons.Outlined.PhoneAndroid,
                            label = mobileLabel,
                            isSelected = false,
                            buttonSize = itemButtonSize,
                            iconSize = iconSize,
                            contentFocusRequester = contentFocusRequester,
                            onFocusChanged = { focused, y -> onRailItemFocusChange(mobileLabel, focused, y) },
                            onClick = onSwitchToMobile
                        )
                    }
                }
            }
        }

        // Pure Compose non-focusable tooltip overlay beside focused rail icon (no Popup window interference)
        focusedNavTooltip?.let { (label, centerYInWindow) ->
            val tooltipX = with(density) { (ottInsets.railStart + OttSpecs.RailIconColumnWidth + 6.dp).roundToPx() }
            val halfPillPx = with(density) { 16.dp.roundToPx() }
            val offsetY = (centerYInWindow - rootTopInWindow).roundToInt() - halfPillPx
            Box(
                modifier = Modifier
                    .offset { IntOffset(x = tooltipX, y = offsetY.coerceAtLeast(0)) }
                    .background(OttSurfaceElevated, RoundedCornerShape(8.dp))
                    .border(1.dp, OttBorderSubtle, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = label,
                    color = OttWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
        }

        // 3. Overlay Category Movies Screen
        if (viewAllCategory != null && viewAllSc != null && viewAllPr != null) {
            CategoryMoviesScreen(
                category = viewAllCategory,
                scraper = viewAllSc,
                provider = viewAllPr,
                onBack = {
                    selectedCategoryForViewAll = null
                    viewAllScraper = null
                    viewAllProvider = null
                },
                onMovieClick = { movie, scraper, provider ->
                    selectedMovieLink = movie.link
                    selectedMovieScraper = scraper
                    selectedMovieProvider = provider
                }
            )
        }

        // 4. Overlay Movie Detail Screen
        if (activeMovieLink != null && activeMovieSc != null && activeMoviePr != null) {
            MovieDetailScreen(
                link = activeMovieLink,
                providerUrl = activeMoviePr.url,
                scraperValue = activeMovieSc.value,
                onBack = {
                    selectedMovieLink = null
                    selectedMovieScraper = null
                    selectedMovieProvider = null
                }
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SidebarItem(
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    label: String,
    isSelected: Boolean,
    buttonSize: Dp = 44.dp,
    iconSize: Dp = 24.dp,
    contentFocusRequester: FocusRequester? = null,
    onFocusChanged: (Boolean, Float) -> Unit = { _, _ -> },
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    var itemCenterY by remember { mutableFloatStateOf(0f) }

    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = OttWhite.copy(alpha = 0.16f),
            pressedContainerColor = OttWhite.copy(alpha = 0.24f)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        modifier = Modifier
            .size(buttonSize)
            .onGloballyPositioned { coords ->
                itemCenterY = coords.positionInWindow().y + coords.size.height / 2f
            }
            .onFocusChanged {
                isFocused = it.isFocused
                onFocusChanged(it.isFocused, itemCenterY)
            }
            .then(
                if (contentFocusRequester != null) {
                    Modifier
                        .focusProperties {
                            right = contentFocusRequester
                        }
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionRight) {
                                try {
                                    contentFocusRequester.requestFocus()
                                    true
                                } catch (_: Exception) {
                                    false
                                }
                            } else {
                                false
                            }
                        }
                } else {
                    Modifier
                }
            )
            .then(
                if (isFocused) {
                    Modifier.border(1.5.dp, OttWhite, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = if (isSelected) selectedIcon else unselectedIcon,
                contentDescription = label,
                tint = if (isSelected || isFocused) OttWhite else OttWhite.copy(alpha = 0.72f),
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LiveTvScreen(
    channels: List<Channel>,
    categories: List<String>,
    selectedCategory: String,
    isLoading: Boolean,
    errorMessage: String?,
    onSelectCategory: (String) -> Unit,
    onChannelClick: (Channel) -> Unit,
    primaryFocusRequester: FocusRequester? = null
) {
    val ottInsets = rememberOttSafeInsets()
    val filteredChannels = remember(channels, selectedCategory) {
        if (selectedCategory == "All") {
            channels
        } else {
            channels.filter { it.category == selectedCategory }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = ottInsets.contentStart,
                top = ottInsets.top,
                end = ottInsets.end,
                bottom = ottInsets.bottom
            )
    ) {
        // Left Categories List (Borderless OTT Pill Rail)
        Column(
            modifier = Modifier
                .width(172.dp)
                .fillMaxHeight()
                .padding(end = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 12.dp, start = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Sensors,
                    contentDescription = null,
                    tint = OttLiveRed,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Live Now",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OttLiveRed
                )
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(categories) { index, categoryName ->
                    val isCatSelected = selectedCategory == categoryName
                    var isFocused by remember { mutableStateOf(false) }

                    Surface(
                        onClick = { onSelectCategory(categoryName) },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (isCatSelected) OttWhite.copy(alpha = 0.14f) else Color.Transparent,
                            focusedContainerColor = OttWhite,
                            pressedContainerColor = OttWhite.copy(alpha = 0.85f)
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (index == 0 && primaryFocusRequester != null) Modifier.focusRequester(primaryFocusRequester) else Modifier)
                            .onFocusChanged { isFocused = it.isFocused }
                    ) {
                        Text(
                            text = categoryName,
                            fontSize = 13.sp,
                            fontWeight = if (isCatSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                            color = if (isFocused) OttBgPrimary else if (isCatSelected) OttAmberStreaming else OttWhite.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                        )
                    }
                }
            }
        }

        // Right Channels Grid
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 8.dp)
        ) {
            Text(
                text = selectedCategory,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = OttWhite,
                modifier = Modifier.padding(bottom = 14.dp, end = 96.dp)
            )

            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = OttAmberStreaming)
                    }
                }
                errorMessage != null && filteredChannels.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = errorMessage, color = OttLiveRed, fontSize = 14.sp)
                    }
                }
                filteredChannels.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "No TV channels found", color = OttTextSecondary, fontSize = 14.sp)
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 148.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredChannels, key = { it.name + "_" + it.streamUrl }) { channel ->
                            TvChannelCard(channel = channel) {
                                onChannelClick(channel)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvChannelCard(channel: Channel, onClick: () -> Unit) {
    val context = LocalContext.current
    val logoRequest = remember(context, channel.logoUrl) { buildOttImageRequest(context, channel.logoUrl) }
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = OttSurface,
            focusedContainerColor = OttSurfaceElevated,
            pressedContainerColor = OttSurfaceElevated
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(OttSpecs.CardRadius)),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.38f)
            .onFocusChanged { isFocused = it.isFocused }
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, OttWhite, RoundedCornerShape(OttSpecs.CardRadius))
                } else {
                    Modifier.border(1.dp, OttBorderSubtle, RoundedCornerShape(OttSpecs.CardRadius))
                }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            if (isFocused) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(OttLiveRed)
                )
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AsyncImage(
                    model = logoRequest,
                    contentDescription = channel.name,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(OttWhite.copy(alpha = 0.05f)),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = channel.name,
                    fontSize = 13.sp,
                    fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
                    color = OttWhite,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ManageProvidersScreen(
    providerList: List<Provider>,
    onAddProvider: (String, String, ProviderType) -> Unit,
    onDeleteProvider: (String) -> Unit,
    onSwitchToMobile: (() -> Unit)? = null,
    primaryFocusRequester: FocusRequester? = null
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var providerType by remember { mutableStateOf(ProviderType.IPTV) }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left Column: List of existing providers
        Column(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight()
                .padding(end = 24.dp)
        ) {
            Text(
                text = "Configured Providers",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = OttWhite,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(providerList) { index, provider ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(OttSurface, RoundedCornerShape(OttSpecs.CardRadius))
                            .border(1.dp, OttBorderSubtle, RoundedCornerShape(OttSpecs.CardRadius))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = provider.title,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = OttWhite
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (provider.safeType == ProviderType.VEGA) OttAmberStreaming.copy(alpha = 0.18f) else OttWhite.copy(alpha = 0.1f),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (provider.safeType == ProviderType.VEGA) "VEGA VOD" else "IPTV M3U",
                                            color = if (provider.safeType == ProviderType.VEGA) OttAmberStreaming else OttTextSecondary,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = provider.url,
                                    fontSize = 12.sp,
                                    color = OttTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Button(
                                onClick = { onDeleteProvider(provider.url) },
                                colors = ButtonDefaults.colors(
                                    containerColor = OttLiveRed.copy(alpha = 0.14f),
                                    focusedContainerColor = OttLiveRed,
                                    contentColor = OttLiveRed,
                                    focusedContentColor = OttWhite
                                ),
                                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                modifier = if (index == 0 && primaryFocusRequester != null) {
                                    Modifier.focusRequester(primaryFocusRequester)
                                } else {
                                    Modifier
                                }
                            ) {
                                Text(text = "Delete")
                            }
                        }
                    }
                }
            }
        }

        // Right Column: Add playlist form & TV settings
        val context = LocalContext.current
        val focusManager = LocalFocusManager.current
        val startupTab by StartupTabPreferenceManager.observeStartupTab(context)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp)
        ) {
            Text(
                text = "Add Provider Source",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = OttWhite,
                modifier = Modifier.padding(bottom = 16.dp, end = 96.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Surface(
                    onClick = { providerType = ProviderType.IPTV },
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (providerType == ProviderType.IPTV) OttAmberStreaming else OttSurface,
                        focusedContainerColor = OttWhite
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (providerList.isEmpty() && primaryFocusRequester != null) {
                                Modifier.focusRequester(primaryFocusRequester)
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "IPTV Playlist (M3U)",
                            fontSize = 12.sp,
                            color = if (providerType == ProviderType.IPTV) OttBgPrimary else OttWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    onClick = { providerType = ProviderType.VEGA },
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (providerType == ProviderType.VEGA) OttAmberStreaming else OttSurface,
                        focusedContainerColor = OttWhite
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Vega Provider",
                            fontSize = 12.sp,
                            color = if (providerType == ProviderType.VEGA) OttBgPrimary else OttWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Provider Name", color = OttTextSecondary) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = OttWhite,
                    unfocusedTextColor = OttWhite,
                    focusedBorderColor = OttAmberStreaming,
                    unfocusedBorderColor = OttWhite.copy(alpha = 0.2f),
                    focusedContainerColor = OttSurface,
                    unfocusedContainerColor = OttSurface,
                    cursorColor = OttAmberStreaming
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.key) {
                                Key.DirectionUp -> { focusManager.moveFocus(FocusDirection.Up); true }
                                Key.DirectionDown -> { focusManager.moveFocus(FocusDirection.Down); true }
                                Key.DirectionLeft -> if (title.isEmpty()) { focusManager.moveFocus(FocusDirection.Left); true } else false
                                else -> false
                            }
                        } else false
                    }
                    .padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = {
                    Text(
                        if (providerType == ProviderType.IPTV) "M3U URL" else "Vega URL or ID (e.g. @vega-org)",
                        color = OttTextSecondary
                    )
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = OttWhite,
                    unfocusedTextColor = OttWhite,
                    focusedBorderColor = OttAmberStreaming,
                    unfocusedBorderColor = OttWhite.copy(alpha = 0.2f),
                    focusedContainerColor = OttSurface,
                    unfocusedContainerColor = OttSurface,
                    cursorColor = OttAmberStreaming
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.key) {
                                Key.DirectionUp -> { focusManager.moveFocus(FocusDirection.Up); true }
                                Key.DirectionDown -> { focusManager.moveFocus(FocusDirection.Down); true }
                                Key.DirectionLeft -> if (url.isEmpty()) { focusManager.moveFocus(FocusDirection.Left); true } else false
                                else -> false
                            }
                        } else false
                    }
                    .padding(bottom = 24.dp)
            )

            Button(
                onClick = {
                    if (title.isNotBlank() && url.isNotBlank()) {
                        onAddProvider(title, url, providerType)
                        title = ""
                        url = ""
                    }
                },
                enabled = title.isNotBlank() && url.isNotBlank(),
                colors = ButtonDefaults.colors(
                    containerColor = OttWhite,
                    focusedContainerColor = OttAmberStreaming,
                    disabledContainerColor = OttWhite.copy(alpha = 0.12f),
                    contentColor = OttBgPrimary,
                    focusedContentColor = OttBgPrimary,
                    disabledContentColor = OttTextSecondary
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Add Provider", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = {
                    val nextTab = StartupTabPreferenceManager.nextTvStartupTab(startupTab)
                    StartupTabPreferenceManager.setStartupTab(context, nextTab)
                },
                colors = ButtonDefaults.colors(
                    containerColor = OttSurface,
                    focusedContainerColor = OttWhite,
                    contentColor = OttWhite,
                    focusedContentColor = OttBgPrimary
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Startup Tab",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Startup Tab: ${StartupTabPreferenceManager.tvLabelFor(startupTab)}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (onSwitchToMobile != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onSwitchToMobile,
                    colors = ButtonDefaults.colors(
                        containerColor = OttSurfaceElevated,
                        focusedContainerColor = OttWhite,
                        contentColor = OttWhite,
                        focusedContentColor = OttBgPrimary
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = "Switch to Mobile UI",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Switch to Mobile UI")
                }
            }
        }
    }
}

@Composable
fun ClockView(modifier: Modifier = Modifier) {
    var timeText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val formatter = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
        while (true) {
            timeText = formatter.format(java.util.Calendar.getInstance().time)
            kotlinx.coroutines.delay(1000)
        }
    }
    Text(
        text = timeText,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = OttWhite.copy(alpha = 0.65f),
        modifier = modifier
    )
}
