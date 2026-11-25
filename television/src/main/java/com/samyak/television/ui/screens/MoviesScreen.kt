package com.samyak.television.ui.screens

import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.samyak.television.data.ExtensionRepository
import com.samyak.television.data.MovieCacheStore
import com.samyak.television.data.ProviderRepository
import com.samyak.television.data.VegaProviderRunner
import com.samyak.television.model.*
import com.samyak.television.ui.components.*
import com.samyak.television.ui.theme.*
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MoviesScreen(
    onMovieClick: (VegaPost, VegaProvider, Provider) -> Unit,
    onViewAllClick: (VegaCatalog, VegaProvider, Provider) -> Unit,
    primaryFocusRequester: FocusRequester? = null
) {
    val context = LocalContext.current

    val providerRepository = remember { ProviderRepository(context) }
    val extensionRepository = remember { ExtensionRepository.getInstance(context) }
    val runner = remember { VegaProviderRunner(context) }
    DisposableEffect(Unit) {
        onDispose {
            runner.destroy()
        }
    }
    val cacheStore = remember { MovieCacheStore.getInstance(context) }

    val vegaProviders = remember {
        providerRepository.getProviders().filter { it.isActive && it.safeType == ProviderType.VEGA }
    }

    var selectedProvider by remember { mutableStateOf<Provider?>(vegaProviders.firstOrNull()) }
    var scrapers by remember { mutableStateOf<List<VegaProvider>>(emptyList()) }
    var selectedScraper by remember { mutableStateOf<VegaProvider?>(null) }

    var moviesByCategory by remember { mutableStateOf<Map<VegaCatalog, List<VegaPost>>>(emptyMap()) }
    var isScrapersLoading by remember { mutableStateOf(false) }
    var isMoviesLoading by remember { mutableStateOf(false) }

    val installedExtensionsState by extensionRepository.installedExtensionsFlow.collectAsState()

    // 1. Fetch scrapers/extensions for selected provider with cache memory
    LaunchedEffect(selectedProvider, installedExtensionsState) {
        val provider = selectedProvider
        if (provider != null) {
            val cachedScrapers = cacheStore.getScrapers(provider.url)?.filter { it.value in installedExtensionsState }
            if (!cachedScrapers.isNullOrEmpty() && scrapers.isEmpty()) {
                scrapers = cachedScrapers
                if (selectedScraper == null) {
                    selectedScraper = cachedScrapers.firstOrNull()
                }
            } else if (scrapers.isEmpty()) {
                isScrapersLoading = true
            }
            try {
                val manifest = runner.fetchManifest(provider.url)
                val installed = manifest.filter { it.value in installedExtensionsState }
                scrapers = installed
                cacheStore.putScrapers(provider.url, manifest)

                if (selectedScraper == null || !scrapers.any { it.value == selectedScraper?.value }) {
                    selectedScraper = scrapers.firstOrNull()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("TVMoviesScreen", "Error loading scrapers", e)
                if (scrapers.isEmpty()) {
                    scrapers = emptyList()
                    selectedScraper = null
                }
            } finally {
                isScrapersLoading = false
            }
        } else {
            scrapers = emptyList()
            selectedScraper = null
        }
    }

    // 2. Fetch catalog and posts for selected scraper with cache memory
    LaunchedEffect(selectedProvider, selectedScraper) {
        val provider = selectedProvider
        val scraper = selectedScraper
        if (provider != null && scraper != null) {
            val cachedCats = cacheStore.getCategories(provider.url, scraper.value)
            if (!cachedCats.isNullOrEmpty()) {
                val cachedMap = mutableMapOf<VegaCatalog, List<VegaPost>>()
                for (cat in cachedCats.take(6)) {
                    val cachedPosts = cacheStore.getMovies(provider.url, scraper.value, cat.filter)
                    if (!cachedPosts.isNullOrEmpty()) {
                        cachedMap[cat] = cachedPosts.take(15)
                    }
                }
                if (cachedMap.isNotEmpty()) {
                    moviesByCategory = cachedMap
                    isMoviesLoading = false
                }
            }

            if (moviesByCategory.isEmpty()) {
                isMoviesLoading = true
            }
            try {
                val (catalogs, _) = runner.getCatalog(provider.url, scraper.value)
                if (catalogs.isNotEmpty()) {
                    cacheStore.putCategories(provider.url, scraper.value, catalogs)
                }
                val postsMap = mutableMapOf<VegaCatalog, List<VegaPost>>()

                val catalogsToFetch = catalogs.take(6)
                if (catalogsToFetch.isEmpty()) {
                    val posts = runner.getPosts(provider.url, scraper.value, filter = "", page = 1)
                    if (posts.isNotEmpty()) {
                        moviesByCategory = mapOf(VegaCatalog("Featured", "") to posts.take(15))
                        cacheStore.putMovies(provider.url, scraper.value, "", posts)
                    }
                } else {
                    for (cat in catalogsToFetch) {
                        val posts = runner.getPosts(provider.url, scraper.value, filter = cat.filter, page = 1)
                        if (posts.isNotEmpty()) {
                            postsMap[cat] = posts.take(15)
                            moviesByCategory = postsMap.toMap()
                            cacheStore.putMovies(provider.url, scraper.value, cat.filter, posts)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("TVMoviesScreen", "Error loading catalog/movies", e)
                if (moviesByCategory.isEmpty()) {
                    moviesByCategory = emptyMap()
                }
            } finally {
                isMoviesLoading = false
            }
        } else {
            moviesByCategory = emptyMap()
            isMoviesLoading = false
        }
    }

    val ottInsets = rememberOttSafeInsets()

    if (vegaProviders.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OttBgPrimary)
                .padding(
                    start = ottInsets.contentStart,
                    top = ottInsets.top,
                    end = ottInsets.end,
                    bottom = ottInsets.bottom
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "VOD Movies & Series",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = OttWhite,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Add a Vega Provider source in Playlists to browse movies and web series.",
                    fontSize = 14.sp,
                    color = OttTextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    var activeHeroMovie by remember { mutableStateOf<VegaPost?>(null) }
    var focusedMovie by remember { mutableStateOf<VegaPost?>(null) }
    val fallbackFocusRequester = remember { FocusRequester() }
    val heroCtaFocusRequester = primaryFocusRequester ?: fallbackFocusRequester
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }

    LaunchedEffect(moviesByCategory) {
        val firstMovie = moviesByCategory.values.firstOrNull()?.firstOrNull()
        if (firstMovie != null && (activeHeroMovie == null || moviesByCategory.values.flatten().none { it.link == activeHeroMovie?.link })) {
            activeHeroMovie = firstMovie
        }
    }

    LaunchedEffect(activeHeroMovie) {
        if (activeHeroMovie != null && !hasRequestedInitialFocus) {
            hasRequestedInitialFocus = true
            try {
                heroCtaFocusRequester.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(focusedMovie) {
        val movie = focusedMovie
        if (movie != null) {
            kotlinx.coroutines.delay(400)
            activeHeroMovie = movie
        }
    }

    val listState = rememberLazyListState()
    val heroBackdropAlpha by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 0f
            else (1f - (listState.firstVisibleItemScrollOffset / 300f)).coerceIn(0f, 1f)
        }
    }

    val catalogEntries = remember(moviesByCategory) { moviesByCategory.entries.toList() }
    val contentStartPadding = ottInsets.contentStart
    val safeTopPadding = ottInsets.top
    val safeBottomPadding = ottInsets.bottom
    val horizontalEndPadding = ottInsets.end

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(OttBgPrimary)
    ) {
        val usableHeight = (maxHeight - safeTopPadding - safeBottomPadding).coerceAtLeast(260.dp)
        val usableWidth = (maxWidth - contentStartPadding - horizontalEndPadding).coerceAtLeast(360.dp)
        val isCompactHeight = usableHeight < 460.dp
        val isCompactWidth = usableWidth < 680.dp

        val heroLockupHeight = if (isCompactHeight) (usableHeight * 0.44f).coerceIn(156.dp, 210.dp) else 292.dp
        val heroBackdropHeight = heroLockupHeight + safeTopPadding + 150.dp
        val heroTitleSize = if (isCompactHeight) 26.sp else 38.sp
        val heroTitleLineHeight = if (isCompactHeight) 30.sp else 42.sp
        val heroStatusSize = if (isCompactHeight) 13.sp else 15.sp
        val heroCtaSize = if (isCompactHeight) 13.sp else 15.sp

        val landscapeCardWidth = when {
            isCompactWidth -> 196.dp
            isCompactHeight -> 216.dp
            else -> OttSpecs.LandscapeCardWidth
        }
        val landscapeCardHeight = when {
            isCompactWidth -> 120.dp
            isCompactHeight -> 130.dp
            else -> OttSpecs.LandscapeCardHeight
        }
        val tallCardWidth = when {
            isCompactWidth -> 136.dp
            isCompactHeight -> 148.dp
            else -> OttSpecs.TallCardWidth
        }
        val tallCardHeight = when {
            isCompactWidth -> 212.dp
            isCompactHeight -> 226.dp
            else -> OttSpecs.TallCardHeight
        }
        val midBannerHeight = if (isCompactHeight) 182.dp else 236.dp
        val carouselGap = if (isCompactHeight) 18.dp else OttSpecs.CarouselVerticalGap

        // 1. Full-Bleed Home-Marquee Backdrop extending seamlessly under nav & top carousel
        activeHeroMovie?.let { hero ->
            val heroImageRequest = remember(context, hero.image) {
                buildOttImageRequest(context, hero.image)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroBackdropHeight)
                    .graphicsLayer { alpha = heroBackdropAlpha }
            ) {
                Crossfade(
                    targetState = heroImageRequest,
                    animationSpec = tween(durationMillis = 380),
                    label = "HeroBackdropCrossfade"
                ) { request ->
                    AsyncImage(
                        model = request,
                        contentDescription = hero.title,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Figma Rectangle 2: Left horizontal dark scrim behind lockup & Product_Nav
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                0.0f to OttNavRailBg.copy(alpha = 0.94f),
                                0.36f to OttNavRailBg.copy(alpha = 0.72f),
                                0.72f to Color.Transparent
                            )
                        )
                )

                // Figma Rectangle 1: Bottom vertical dark scrim melting into #121212
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.42f to OttBgPrimary.copy(alpha = 0.30f),
                                0.80f to OttBgPrimary.copy(alpha = 0.92f),
                                1.0f to OttBgPrimary
                            )
                        )
                )
            }
        }

        // 2. Main Scrollable OTT Feed
        if (isMoviesLoading && catalogEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = contentStartPadding, top = safeTopPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = OttAmberStreaming)
            }
        } else if (catalogEntries.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = contentStartPadding,
                        top = safeTopPadding,
                        end = horizontalEndPadding,
                        bottom = safeBottomPadding
                    ),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (scrapers.size > 1 || vegaProviders.size > 1) {
                    ScraperAndProviderBar(
                        scrapers = scrapers,
                        selectedScraper = selectedScraper,
                        onSelectScraper = { selectedScraper = it },
                        vegaProviders = vegaProviders,
                        selectedProvider = selectedProvider,
                        onSelectProvider = { selectedProvider = it },
                        firstScraperFocusRequester = primaryFocusRequester,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                }
                Text(
                    text = if (!isScrapersLoading && scrapers.isEmpty()) {
                        "No Extensions Installed. Go to Extensions to install scraper plug-ins."
                    } else {
                        "No content available"
                    },
                    color = OttTextSecondary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = safeBottomPadding + 16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Hero Lockup Area (responsive so Row 0 peeks cleanly on both TV and compact landscape)
                item(key = "hero_lockup") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(heroLockupHeight + safeTopPadding)
                            .padding(
                                start = contentStartPadding,
                                end = 88.dp + horizontalEndPadding,
                                top = safeTopPadding,
                                bottom = if (isCompactHeight) 10.dp else 18.dp
                            )
                    ) {
                        if (scrapers.size > 1 || vegaProviders.size > 1) {
                            ScraperAndProviderBar(
                                scrapers = scrapers,
                                selectedScraper = selectedScraper,
                                onSelectScraper = { selectedScraper = it },
                                vegaProviders = vegaProviders,
                                selectedProvider = selectedProvider,
                                onSelectProvider = { selectedProvider = it },
                                firstScraperFocusRequester = if (activeHeroMovie == null) primaryFocusRequester else null,
                                modifier = Modifier.align(Alignment.TopStart)
                            )
                        }

                        activeHeroMovie?.let { hero ->
                            OttHeroLockup(
                                title = hero.title,
                                isLive = false,
                                statusText = "Now Streaming",
                                onPlayClick = {
                                    val scraper = selectedScraper
                                    val provider = selectedProvider
                                    if (scraper != null && provider != null) {
                                        onMovieClick(hero, scraper, provider)
                                    }
                                },
                                ctaFocusRequester = heroCtaFocusRequester,
                                titleFontSize = heroTitleSize,
                                titleLineHeight = heroTitleLineHeight,
                                statusFontSize = heroStatusSize,
                                ctaFontSize = heroCtaSize,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .widthIn(max = if (isCompactHeight) 380.dp else 460.dp)
                            )
                        }
                    }
                }

                // Catalog Carousels + Mid-Banner
                catalogEntries.forEachIndexed { index, (catalog, movies) ->
                    val useTallCards = index == 1 || (index > 1 && index % 2 == 1)

                    item(key = "catalog_${catalog.title}_$index") {
                        if (index > 0) {
                            Spacer(modifier = Modifier.height(carouselGap))
                        }

                        Column(modifier = Modifier.fillMaxWidth()) {
                            OttSectionHeader(
                                title = catalog.title,
                                actionLabel = "View All",
                                onActionClick = {
                                    val scraper = selectedScraper
                                    val provider = selectedProvider
                                    if (scraper != null && provider != null) {
                                        onViewAllClick(catalog, scraper, provider)
                                    }
                                },
                                modifier = Modifier.padding(
                                    start = contentStartPadding,
                                    end = horizontalEndPadding,
                                    bottom = OttSpecs.CarouselHeaderGap
                                )
                            )

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(OttSpecs.CarouselItemGap),
                                contentPadding = PaddingValues(
                                    start = contentStartPadding,
                                    end = horizontalEndPadding,
                                    top = OttSpecs.FocusPadding,
                                    bottom = OttSpecs.FocusPadding
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(movies, key = { it.link }) { movie ->
                                    val openMovie = {
                                        val scraper = selectedScraper
                                        val provider = selectedProvider
                                        if (scraper != null && provider != null) {
                                            onMovieClick(movie, scraper, provider)
                                        }
                                    }
                                    if (useTallCards) {
                                        OttTallPosterCard(
                                            title = movie.title,
                                            imageUrl = movie.image,
                                            onClick = openMovie,
                                            onFocused = { focusedMovie = movie },
                                            useFixedCarouselSize = true,
                                            cardWidth = tallCardWidth,
                                            cardHeight = tallCardHeight
                                        )
                                    } else {
                                        OttLandscapeCard(
                                            title = movie.title,
                                            imageUrl = movie.image,
                                            onClick = openMovie,
                                            onFocused = { focusedMovie = movie },
                                            cardWidth = landscapeCardWidth,
                                            cardHeight = landscapeCardHeight
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Contained Rounded Mid-Banner after the 2nd carousel (matching Figma node 9-42)
                    if (index == 1 && catalogEntries.size >= 3) {
                        val bannerMovie = catalogEntries[2].value.firstOrNull() ?: movies.getOrNull(1)
                        if (bannerMovie != null) {
                            item(key = "mid_banner") {
                                Spacer(modifier = Modifier.height(carouselGap))
                                OttMidBanner(
                                    title = bannerMovie.title,
                                    imageUrl = bannerMovie.image,
                                    isLive = false,
                                    statusText = "Now Streaming",
                                    bannerHeight = midBannerHeight,
                                    titleFontSize = if (isCompactHeight) 24.sp else 32.sp,
                                    titleLineHeight = if (isCompactHeight) 28.sp else 36.sp,
                                    onPlayClick = {
                                        val scraper = selectedScraper
                                        val provider = selectedProvider
                                        if (scraper != null && provider != null) {
                                            onMovieClick(bannerMovie, scraper, provider)
                                        }
                                    },
                                    modifier = Modifier.padding(
                                        start = contentStartPadding,
                                        end = horizontalEndPadding
                                    )
                                )
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
private fun ScraperAndProviderBar(
    scrapers: List<VegaProvider>,
    selectedScraper: VegaProvider?,
    onSelectScraper: (VegaProvider) -> Unit,
    vegaProviders: List<Provider>,
    selectedProvider: Provider?,
    onSelectProvider: (Provider) -> Unit,
    modifier: Modifier = Modifier,
    firstScraperFocusRequester: FocusRequester? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (scrapers.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(scrapers) { index, scraper ->
                    val isSelected = selectedScraper?.value == scraper.value
                    var isFocused by remember { mutableStateOf(false) }

                    Surface(
                        onClick = { onSelectScraper(scraper) },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (isSelected) OttWhite.copy(alpha = 0.22f) else OttNavRailBg.copy(alpha = 0.72f),
                            focusedContainerColor = OttWhite,
                            pressedContainerColor = OttWhite.copy(alpha = 0.85f)
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                        modifier = Modifier
                            .then(
                                if (index == 0 && firstScraperFocusRequester != null) {
                                    Modifier.focusRequester(firstScraperFocusRequester)
                                } else {
                                    Modifier
                                }
                            )
                            .onFocusChanged { isFocused = it.isFocused }
                            .border(
                                width = 1.dp,
                                color = if (isSelected) OttAmberStreaming.copy(alpha = 0.8f) else OttBorderSubtle,
                                shape = RoundedCornerShape(16.dp)
                            )
                    ) {
                        Text(
                            text = scraper.display_name,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                            color = if (isFocused) OttBgPrimary else if (isSelected) OttAmberStreaming else OttWhite.copy(alpha = 0.90f),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        if (vegaProviders.size > 1) {
            var isFocused by remember { mutableStateOf(false) }
            Surface(
                onClick = {
                    val idx = vegaProviders.indexOfFirst { it.url == selectedProvider?.url }
                    val next = vegaProviders[(idx + 1) % vegaProviders.size]
                    onSelectProvider(next)
                },
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = OttNavRailBg.copy(alpha = 0.72f),
                    focusedContainerColor = OttWhite,
                    pressedContainerColor = OttWhite.copy(alpha = 0.85f)
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                modifier = Modifier
                    .onFocusChanged { isFocused = it.isFocused }
                    .border(1.dp, OttBorderSubtle, RoundedCornerShape(16.dp))
            ) {
                Text(
                    text = selectedProvider?.title ?: "Source",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isFocused) OttBgPrimary else OttWhite,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvMovieCard(
    movie: VegaPost,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    showTitle: Boolean = false
) {
    OttTallPosterCard(
        title = movie.title,
        imageUrl = movie.image,
        onClick = onClick,
        onFocused = onFocused,
        modifier = modifier,
        useFixedCarouselSize = false
    )
}
