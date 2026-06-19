package com.samyak.television.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.samyak.television.data.ExtensionRepository
import com.samyak.television.data.ProviderRepository
import com.samyak.television.data.VegaProviderRunner
import com.samyak.television.model.*
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MoviesScreen(
    onMovieClick: (VegaPost, VegaProvider, Provider) -> Unit,
    onViewAllClick: (VegaCatalog, VegaProvider, Provider) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val providerRepository = remember { ProviderRepository(context) }
    val extensionRepository = remember { ExtensionRepository.getInstance(context) }
    val runner = remember { VegaProviderRunner(context) }
    
    val vegaProviders = remember { providerRepository.getProviders().filter { it.isActive && it.safeType == ProviderType.VEGA } }
    
    var selectedProvider by remember { mutableStateOf<Provider?>(vegaProviders.firstOrNull()) }
    var scrapers by remember { mutableStateOf<List<VegaProvider>>(emptyList()) }
    var selectedScraper by remember { mutableStateOf<VegaProvider?>(null) }
    
    var moviesByCategory by remember { mutableStateOf<Map<VegaCatalog, List<VegaPost>>>(emptyMap()) }
    var isScrapersLoading by remember { mutableStateOf(false) }
    var isMoviesLoading by remember { mutableStateOf(false) }
    
    val installedExtensionsState by extensionRepository.installedExtensionsFlow.collectAsState()

    // 1. Fetch scrapers/extensions for selected provider
    LaunchedEffect(selectedProvider, installedExtensionsState) {
        val provider = selectedProvider
        if (provider != null) {
            isScrapersLoading = true
            try {
                val manifest = runner.fetchManifest(provider.url)
                val installed = manifest.filter { it.value in installedExtensionsState }
                scrapers = installed
                
                if (selectedScraper == null || !scrapers.any { it.value == selectedScraper?.value }) {
                    selectedScraper = scrapers.firstOrNull()
                }
            } catch (e: Exception) {
                Log.e("TVMoviesScreen", "Error loading scrapers", e)
                scrapers = emptyList()
                selectedScraper = null
            } finally {
                isScrapersLoading = false
            }
        } else {
            scrapers = emptyList()
            selectedScraper = null
        }
    }

    // 2. Fetch catalog and posts for selected scraper
    LaunchedEffect(selectedProvider, selectedScraper) {
        val provider = selectedProvider
        val scraper = selectedScraper
        if (provider != null && scraper != null) {
            isMoviesLoading = true
            moviesByCategory = emptyMap()
            try {
                val (catalogs, _) = runner.getCatalog(provider.url, scraper.value)
                val postsMap = mutableMapOf<VegaCatalog, List<VegaPost>>()
                
                val catalogsToFetch = catalogs.take(6)
                if (catalogsToFetch.isEmpty()) {
                    val posts = runner.getPosts(provider.url, scraper.value, filter = "", page = 1)
                    if (posts.isNotEmpty()) {
                        moviesByCategory = mapOf(VegaCatalog("Featured", "") to posts.take(15))
                    }
                } else {
                    for (cat in catalogsToFetch) {
                        val posts = runner.getPosts(provider.url, scraper.value, filter = cat.filter, page = 1)
                        if (posts.isNotEmpty()) {
                            postsMap[cat] = posts.take(15)
                            moviesByCategory = postsMap.toMap() // Update state incrementally
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("TVMoviesScreen", "Error loading catalog/movies", e)
                moviesByCategory = emptyMap()
            } finally {
                isMoviesLoading = false
            }
        } else {
            moviesByCategory = emptyMap()
            isMoviesLoading = false
        }
    }

    if (vegaProviders.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    text = "VOD Movies & Series",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Please add a Vega Provider source in 'Manage Playlists' to browse movies and web series.",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        var activeHeroMovie by remember { mutableStateOf<VegaPost?>(null) }
        var focusedMovie by remember { mutableStateOf<VegaPost?>(null) }

        LaunchedEffect(moviesByCategory) {
            if (activeHeroMovie == null) {
                activeHeroMovie = moviesByCategory.values.firstOrNull()?.firstOrNull()
            }
        }

        LaunchedEffect(focusedMovie) {
            val movie = focusedMovie
            if (movie != null) {
                kotlinx.coroutines.delay(400) // Debounce focus changes to avoid lag during fast D-pad navigation
                activeHeroMovie = movie
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
            // Header: Source and Scraper Selectors
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp, end = 100.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Scraper row
                if (scrapers.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(scrapers) { scraper ->
                            val isSelected = selectedScraper?.value == scraper.value
                            Surface(
                                onClick = { selectedScraper = scraper },
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isSelected) Color(0xFF26A69A).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                                    focusedContainerColor = Color(0xFF26A69A),
                                    pressedContainerColor = Color(0xFF208b80)
                                ),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp))
                            ) {
                                Text(
                                    text = scraper.display_name,
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                } else if (!isScrapersLoading) {
                    Text(
                        text = "No Extensions Installed. Go to 'Manage Extensions' to install scraper plug-ins.",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Provider Source Selector
                if (vegaProviders.size > 1) {
                    var isExpanded by remember { mutableStateOf(false) }
                    Box {
                        Button(
                            onClick = { isExpanded = !isExpanded },
                            colors = ButtonDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.05f),
                                contentColor = Color.White
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp))
                        ) {
                            Text(text = selectedProvider?.title ?: "Select Source")
                        }
                    }
                }
            }

            // Movies rows
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (isMoviesLoading && moviesByCategory.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF26A69A))
                    }
                } else if (moviesByCategory.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (scrapers.isEmpty()) "Install extensions to browse content" else "No content available",
                            color = Color.Gray,
                            fontSize = 15.sp
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // 1. Featured Hero Banner
                        activeHeroMovie?.let { hero ->
                            item {
                                FeaturedHeroBanner(
                                    movie = hero,
                                    onClick = {
                                        val scraper = selectedScraper
                                        val provider = selectedProvider
                                        if (scraper != null && provider != null) {
                                            onMovieClick(hero, scraper, provider)
                                        }
                                    }
                                )
                            }
                        }


                        // 3. Movie Categories Rows
                        moviesByCategory.forEach { (catalog, movies) ->
                            item {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 12.dp, end = 24.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = catalog.title,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        
                                        Surface(
                                            onClick = {
                                                val scraper = selectedScraper
                                                val provider = selectedProvider
                                                if (scraper != null && provider != null) {
                                                    onViewAllClick(catalog, scraper, provider)
                                                }
                                            },
                                            colors = ClickableSurfaceDefaults.colors(
                                                containerColor = Color.White.copy(alpha = 0.05f),
                                                focusedContainerColor = Color(0xFF26A69A),
                                                pressedContainerColor = Color(0xFF208b80)
                                            ),
                                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp))
                                        ) {
                                            Text(
                                                text = "View All",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(movies) { movie ->
                                            TvMovieCard(
                                                movie = movie,
                                                onClick = {
                                                    val scraper = selectedScraper
                                                    val provider = selectedProvider
                                                    if (scraper != null && provider != null) {
                                                        onMovieClick(movie, scraper, provider)
                                                    }
                                                },
                                                onFocused = {
                                                    focusedMovie = movie
                                                }
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
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FeaturedHeroBanner(
    movie: VegaPost,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF080D1A).copy(alpha = 0.5f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
    ) {
        // Widescreen Backdrop Image on the right
        AsyncImage(
            model = movie.image,
            contentDescription = movie.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.65f)
                .align(Alignment.CenterEnd)
        )
        
        // Dark gradient vignette overlay from left to right
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF080D1A),
                            Color(0xFF080D1A).copy(alpha = 0.95f),
                            Color(0xFF080D1A).copy(alpha = 0.6f),
                            Color.Transparent
                        ),
                        startX = 0f,
                        endX = 1200f
                    )
                )
        )

        // Movie Info Panel on the left
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.45f)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .background(Color(0xFF26A69A).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "FEATURED",
                    color = Color(0xFF26A69A),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Text(
                text = movie.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 24.sp
            )
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Text(
                text = "Discover premium high quality streams. Select this item to view detailed link resolution options.",
                fontSize = 12.sp,
                color = Color.LightGray,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.colors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        focusedContainerColor = Color(0xFF26A69A),
                        focusedContentColor = Color.White
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("Watch Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvMovieCard(
    movie: VegaPost,
    onClick: () -> Unit,
    onFocused: () -> Unit = {},
    showTitle: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.width(130.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Surface(
            onClick = onClick,
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFF1E1E1E),
                focusedContainerColor = Color(0xFF26A69A),
                pressedContainerColor = Color(0xFF208b80)
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .onFocusChanged {
                    isFocused = it.isFocused
                    if (it.isFocused) {
                        onFocused()
                    }
                }
        ) {
            AsyncImage(
                model = movie.image,
                contentDescription = movie.title,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = if (isFocused) 2.dp else 1.dp,
                        color = if (isFocused) Color(0xFF26A69A) else Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentScale = ContentScale.Crop
            )
        }
        
        if (showTitle) {
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = movie.title,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun Modifier.fillTargetGradient(): Modifier {
    return this
        .fillMaxWidth()
        .fillMaxHeight(0.45f)
        .background(
            Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))
            )
        )
}
