package com.samyak.television

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.samyak.television.data.ProviderRepository
import com.samyak.television.data.TelevisionViewModel
import com.samyak.television.model.Channel
import com.samyak.television.model.Provider
import com.samyak.television.model.ProviderType
import com.samyak.television.model.VegaPost
import com.samyak.television.model.VegaProvider
import com.samyak.television.ui.screens.*
import com.samyak.television.ui.theme.IPTVMineProTheme
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.painter.Painter

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
fun TelevisionApp(viewModel: TelevisionViewModel = viewModel()) {
    val context = LocalContext.current
    val channels by viewModel.channels.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()

    var activeScreenIndex by remember { mutableStateOf(1) } // Default to Movies & Shows for a rich experience!
    // activeScreenIndex == 0: Live TV Grid (categories)
    // activeScreenIndex == 1: VOD Movies & Shows catalog
    // activeScreenIndex == 2: Search Movies Screen
    // activeScreenIndex == 3: Manage Extensions
    // activeScreenIndex == 4: Manage Playlists/Providers

    // Movie Detail Selection States
    var selectedMovieLink by remember { mutableStateOf<String?>(null) }
    var selectedMovieScraper by remember { mutableStateOf<VegaProvider?>(null) }
    var selectedMovieProvider by remember { mutableStateOf<Provider?>(null) }

    // Category View All States
    var selectedCategoryForViewAll by remember { mutableStateOf<com.samyak.television.model.VegaCatalog?>(null) }
    var viewAllScraper by remember { mutableStateOf<VegaProvider?>(null) }
    var viewAllProvider by remember { mutableStateOf<Provider?>(null) }

    var isSidebarExpanded by remember { mutableStateOf(false) }
    val sidebarWidth by animateDpAsState(targetValue = if (isSidebarExpanded) 220.dp else 72.dp)

    // Handle system back navigation (so pressing back on remote returns to previous screen instead of closing app)
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

    BackHandler(enabled = selectedMovieLink == null && selectedCategoryForViewAll == null && activeScreenIndex != 1) {
        activeScreenIndex = 1
    }

    if (selectedMovieLink != null && selectedMovieScraper != null && selectedMovieProvider != null) {
        // Render Movie Detail full screen, hiding the main sidebar
        MovieDetailScreen(
            link = selectedMovieLink!!,
            providerUrl = selectedMovieProvider!!.url,
            scraperValue = selectedMovieScraper!!.value,
            onBack = {
                selectedMovieLink = null
                selectedMovieScraper = null
                selectedMovieProvider = null
            }
        )
    } else if (selectedCategoryForViewAll != null && viewAllScraper != null && viewAllProvider != null) {
        // Render Category Movies Grid full screen, hiding the main sidebar
        CategoryMoviesScreen(
            category = selectedCategoryForViewAll!!,
            scraper = viewAllScraper!!,
            provider = viewAllProvider!!,
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
    } else {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF0F172A), Color(0xFF070B13)),
                        center = androidx.compose.ui.geometry.Offset(500f, 0f),
                        radius = 2800f
                    )
                )
        ) {
            // 1. Sliding Expandable Sidebar Navigation (Left Panel)
            Column(
                modifier = Modifier
                    .width(sidebarWidth)
                    .fillMaxHeight()
                    .background(Color(0xFF080D1A).copy(alpha = 0.96f))
                    .onFocusChanged { state ->
                        isSidebarExpanded = state.hasFocus
                    }
                    .padding(vertical = 24.dp, horizontal = 8.dp),
                horizontalAlignment = if (isSidebarExpanded) Alignment.Start else Alignment.CenterHorizontally
            ) {
                // Brand Logo area
                if (isSidebarExpanded) {
                    Text(
                        text = "IPTV Mine Pro",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF26A69A),
                        modifier = Modifier.padding(bottom = 32.dp, start = 12.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .padding(bottom = 32.dp)
                            .size(36.dp)
                            .background(Color(0xFF26A69A).copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "IP",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF26A69A)
                        )
                    }
                }

                // Nav Items list
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    SidebarItem(
                        painter = rememberVectorPainter(Icons.Default.Search),
                        label = "Search",
                        isSelected = activeScreenIndex == 2,
                        isExpanded = isSidebarExpanded,
                        onClick = { activeScreenIndex = 2 }
                    )
                    SidebarItem(
                        painter = painterResource(id = R.drawable.ic_tv),
                        label = "Live TV",
                        isSelected = activeScreenIndex == 0,
                        isExpanded = isSidebarExpanded,
                        onClick = { activeScreenIndex = 0 }
                    )
                    SidebarItem(
                        painter = rememberVectorPainter(Icons.Default.PlayArrow),
                        label = "Movies & Shows",
                        isSelected = activeScreenIndex == 1,
                        isExpanded = isSidebarExpanded,
                        onClick = { activeScreenIndex = 1 }
                    )
                    SidebarItem(
                        painter = rememberVectorPainter(Icons.Default.Build),
                        label = "Extensions",
                        isSelected = activeScreenIndex == 3,
                        isExpanded = isSidebarExpanded,
                        onClick = { activeScreenIndex = 3 }
                    )
                    SidebarItem(
                        painter = rememberVectorPainter(Icons.Default.List),
                        label = "Playlists",
                        isSelected = activeScreenIndex == 4,
                        isExpanded = isSidebarExpanded,
                        onClick = { activeScreenIndex = 4 }
                    )
                }

                // Bottom profile mock (Disney+ Style)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(Color(0xFFFFD600), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("U", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    if (isSidebarExpanded) {
                        Spacer(modifier = Modifier.width(14.dp))
                        Text("Guest", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                }
            }

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.05f))
            )

            // 2. Right Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(24.dp)
            ) {
                when (activeScreenIndex) {
                    4 -> {
                        // Manage Providers (M3U & Vega)
                        val providerRepository = remember { ProviderRepository(context) }
                        var providerList by remember { mutableStateOf(providerRepository.getProviders()) }
                        
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
                            }
                        )
                    }
                    3 -> {
                        // Extensions Manager screen
                        ExtensionsScreen()
                    }
                    2 -> {
                        // Search Movies screen
                        SearchMoviesScreen(
                            onMovieClick = { movie, scraper, provider ->
                                selectedMovieLink = movie.link
                                selectedMovieScraper = scraper
                                selectedMovieProvider = provider
                            }
                        )
                    }
                    1 -> {
                        // Movies rows display
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
                            }
                        )
                    }
                    0 -> {
                        // Live TV channels list
                        val filteredChannels = remember(channels, selectedCategory) {
                            if (selectedCategory == "All") {
                                channels
                            } else {
                                channels.filter { it.category == selectedCategory }
                            }
                        }

                        Row(modifier = Modifier.fillMaxSize()) {
                            // Left Categories Sub-Panel (within Live TV content)
                            Column(
                                modifier = Modifier
                                    .width(180.dp)
                                    .fillMaxHeight()
                                    .padding(end = 16.dp)
                            ) {
                                Text(
                                    text = "Categories",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.LightGray,
                                    modifier = Modifier.padding(bottom = 12.dp, start = 8.dp)
                                )
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(categories) { categoryName ->
                                        val isCatSelected = selectedCategory == categoryName
                                        Surface(
                                            onClick = { viewModel.selectCategory(categoryName) },
                                            colors = ClickableSurfaceDefaults.colors(
                                                containerColor = if (isCatSelected) Color(0xFF26A69A).copy(alpha = 0.2f) else Color.Transparent,
                                                focusedContainerColor = Color(0xFF26A69A),
                                                pressedContainerColor = Color(0xFF208b80)
                                            ),
                                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = categoryName,
                                                fontSize = 13.sp,
                                                fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // Vertical Divider
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(Color.White.copy(alpha = 0.08f))
                            )
                            
                            // Right Panel: Channels Grid
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(start = 20.dp)
                            ) {
                                Text(
                                    text = selectedCategory,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )

                                if (isLoading) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = Color(0xFF26A69A))
                                    }
                                } else if (errorMessage != null && filteredChannels.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = errorMessage ?: "Unknown Error",
                                            color = Color.Red,
                                            fontSize = 14.sp
                                        )
                                    }
                                } else if (filteredChannels.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No TV channels found",
                                            color = Color.Gray,
                                            fontSize = 14.sp
                                        )
                                    }
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Adaptive(minSize = 130.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(filteredChannels, key = { it.name + "_" + it.streamUrl }) { channel ->
                                            TvChannelCard(channel = channel) {
                                                com.samyak.player.PlayerActivity.start(context, channel.name, channel.streamUrl)
                                            }
                                        }
                                    }
                                }
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
fun SidebarItem(
    painter: Painter,
    label: String,
    isSelected: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.12f),
            pressedContainerColor = Color.White.copy(alpha = 0.08f)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(20.dp)
                    .background(if (isSelected) Color(0xFF26A69A) else Color.Transparent, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                painter = painter,
                contentDescription = label,
                tint = if (isSelected) Color(0xFF26A69A) else Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(22.dp)
            )
            if (isExpanded) {
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvChannelCard(channel: Channel, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF1E1E1E),
            focusedContainerColor = Color(0xFF26A69A),
            pressedContainerColor = Color(0xFF208b80)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = channel.name,
                modifier = Modifier
                    .size(64.dp)
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = channel.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ManageProvidersScreen(
    providerList: List<Provider>,
    onAddProvider: (String, String, ProviderType) -> Unit,
    onDeleteProvider: (String) -> Unit
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
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(providerList) { provider ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
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
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (provider.safeType == ProviderType.VEGA) Color(0xFF26A69A).copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.2f),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (provider.safeType == ProviderType.VEGA) "VEGA VOD" else "IPTV M3U",
                                            color = if (provider.safeType == ProviderType.VEGA) Color(0xFF26A69A) else Color.LightGray,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = provider.url,
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Button(
                                onClick = { onDeleteProvider(provider.url) },
                                colors = ButtonDefaults.colors(
                                    containerColor = Color.Red.copy(alpha = 0.1f),
                                    focusedContainerColor = Color.Red,
                                    contentColor = Color.Red,
                                    focusedContentColor = Color.White
                                ),
                                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp))
                            ) {
                                Text(text = "Delete")
                            }
                        }
                    }
                }
            }
        }

        // Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.1f))
        )

        // Right Column: Add playlist form
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 24.dp)
        ) {
            Text(
                text = "Add Provider Source",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Selector for Type (IPTV vs Vega)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Surface(
                    onClick = { providerType = ProviderType.IPTV },
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (providerType == ProviderType.IPTV) Color(0xFF26A69A) else Color(0xFF1E1E1E),
                        focusedContainerColor = Color(0xFF208b80)
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("IPTV Playlist (M3U)", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                Surface(
                    onClick = { providerType = ProviderType.VEGA },
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (providerType == ProviderType.VEGA) Color(0xFF26A69A) else Color(0xFF1E1E1E),
                        focusedContainerColor = Color(0xFF208b80)
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Vega Provider", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Provider Name", color = Color.Gray) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF26A69A),
                    unfocusedBorderColor = Color.Gray,
                    focusedContainerColor = Color(0xFF1E1E1E),
                    unfocusedContainerColor = Color(0xFF1E1E1E)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(if (providerType == ProviderType.IPTV) "M3U URL" else "Vega URL or ID (e.g. @vega-org)", color = Color.Gray) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF26A69A),
                    unfocusedBorderColor = Color.Gray,
                    focusedContainerColor = Color(0xFF1E1E1E),
                    unfocusedContainerColor = Color(0xFF1E1E1E)
                ),
                modifier = Modifier
                    .fillMaxWidth()
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
                    containerColor = Color(0xFF26A69A),
                    focusedContainerColor = Color(0xFF208b80),
                    disabledContainerColor = Color.Gray.copy(alpha = 0.3f),
                    contentColor = Color.White,
                    disabledContentColor = Color.Gray
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Add Provider")
            }
        }
    }
}