package com.samyak.television.ui.screens

import android.widget.Toast
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import androidx.compose.material3.CircularProgressIndicator
import coil.compose.AsyncImage
import com.samyak.television.data.VegaProviderRunner
import com.samyak.television.data.VegaStreamFallbackEngine
import com.samyak.television.model.*
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import com.samyak.television.R
import com.samyak.television.ui.components.OttSpecs
import com.samyak.television.ui.components.buildOttImageRequest
import com.samyak.television.ui.components.rememberOttSafeInsets
import com.samyak.television.ui.theme.*
import com.samyak.player.StreamOption
import com.samyak.player.EpisodeItem
import androidx.activity.compose.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged


@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MovieDetailScreen(
    link: String,
    providerUrl: String,
    scraperValue: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val runner = remember { VegaProviderRunner(context) }
    DisposableEffect(Unit) {
        onDispose {
            runner.destroy()
        }
    }
    val cleanLink = remember(link) { link.split('#')[0] }

    var meta by remember { mutableStateOf<VegaMeta?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val backButtonFocusRequester = remember { FocusRequester() }
    val watchNowFocusRequester = remember { FocusRequester() }
    val rightListFocusRequester = remember { FocusRequester() }

    val hasWatchNow = remember(meta) {
        meta?.linkList?.isNotEmpty() == true && meta?.type?.lowercase() == "movie"
    }

    BackHandler(onBack = onBack)

    LaunchedEffect(meta, error, isLoading) {
        if ((meta != null || error != null) && !isLoading) {
            try {
                backButtonFocusRequester.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    // Episodes state
    var episodesMap by remember { mutableStateOf<Map<String, List<VegaLink>>>(emptyMap()) }
    var loadingEpisodesLink by remember { mutableStateOf<String?>(null) }

    // Stream resolution state (Note: streamsToSelect panel is inactive — auto-play uses streams[0])
    var streamsToSelect by remember { mutableStateOf<List<VegaStream>?>(null) }
    var resolvingStream by remember { mutableStateOf(false) }
    var streamStatusText by remember { mutableStateOf("Resolving stream links...") }
    var selectedItemTitle by remember { mutableStateOf("") }
    var streamResolutionJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Expanded states for season categories
    var expandedLinks by remember { mutableStateOf<Set<String>>(emptySet()) }

    val fetchMeta = suspend {
        isLoading = true
        error = null
        try {
            val resolvedMeta = runner.getMeta(providerUrl, scraperValue, cleanLink)
            if (resolvedMeta.title.isBlank() && resolvedMeta.linkList.isEmpty()) {
                error = "Failed to load details. The provider might be experiencing downtime or is blocked."
            } else {
                meta = resolvedMeta
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Failed to load details"
        } finally {
            isLoading = false
        }
    }

    val loadEpisodes: (String) -> Unit = { epLink ->
        scope.launch {
            loadingEpisodesLink = epLink
            try {
                val eps = runner.getEpisodes(providerUrl, scraperValue, epLink)
                episodesMap = episodesMap + (epLink to eps)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load episodes: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                loadingEpisodesLink = null
            }
        }
    }

    LaunchedEffect(link) {
        fetchMeta()
    }

    LaunchedEffect(meta) {
        val firstLink = meta?.linkList?.firstOrNull()
        if (firstLink != null) {
            expandedLinks = setOf(firstLink.title)
            if (firstLink.episodesLink != null && !episodesMap.containsKey(firstLink.episodesLink)) {
                loadEpisodes(firstLink.episodesLink)
            }
        }
    }

    val getResolveType: (String) -> String = { defaultType ->
        val mType = meta?.type?.lowercase() ?: ""
        when {
            mType == "series" || defaultType.equals("series", ignoreCase = true) -> "series"
            mType == "movie" || defaultType.equals("movie", ignoreCase = true) -> "movie"
            defaultType.isNotBlank() -> defaultType
            else -> "movie"
        }
    }

    val onDirectLinkClick: (VegaDirectLink, String) -> Unit = { directLink, title ->
        streamResolutionJob?.cancel()
        streamResolutionJob = scope.launch {
            resolvingStream = true
            streamStatusText = "Resolving stream links..."
            try {
                val resolveType = getResolveType(directLink.type)
                val fallbackRes = VegaStreamFallbackEngine.resolveWithAutoFallback(
                    context = context,
                    runner = runner,
                    initialLink = directLink.link,
                    initialType = resolveType,
                    itemTitle = title,
                    movieTitle = meta?.title ?: title,
                    currentMeta = meta,
                    currentProviderUrl = providerUrl,
                    currentScraperValue = scraperValue,
                    onStatusUpdate = { status -> streamStatusText = status }
                )

                if (fallbackRes == null) {
                    Toast.makeText(context, "No stream links found", Toast.LENGTH_SHORT).show()
                } else {
                    val selectedStream = fallbackRes.selectedStream
                    val streams = fallbackRes.allStreams
                    val fallbacks = ArrayList(streams.map { StreamOption(it.link, HashMap(it.headers ?: emptyMap())) })
                    if (fallbackRes.message != null) {
                        Toast.makeText(context, fallbackRes.message, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Playing: ${selectedStream.server} - ${selectedStream.quality}", Toast.LENGTH_SHORT).show()
                    }
                    val mLink = if (meta?.type?.lowercase() == "series") {
                        "$link#${android.net.Uri.encode(title)}"
                    } else {
                        link
                    }
                    com.samyak.player.PlayerActivity.start(
                        context = context,
                        name = "$title - ${selectedStream.quality}",
                        streamUrl = selectedStream.link,
                        headers = selectedStream.headers,
                        watchHistoryEnabled = true,
                        movieLink = mLink,
                        movieImage = meta?.image ?: "",
                        providerUrl = providerUrl,
                        scraperValue = scraperValue,
                        streamFallbacks = fallbacks
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (directLink.link.startsWith("http://", ignoreCase = true) || directLink.link.startsWith("https://", ignoreCase = true)) {
                    val mLink = if (meta?.type?.lowercase() == "series") "$link#${android.net.Uri.encode(title)}" else link
                    com.samyak.player.PlayerActivity.start(
                        context = context,
                        name = "$title - HD",
                        streamUrl = directLink.link,
                        headers = null,
                        watchHistoryEnabled = true,
                        movieLink = mLink,
                        movieImage = meta?.image ?: "",
                        providerUrl = providerUrl,
                        scraperValue = scraperValue,
                        streamFallbacks = arrayListOf(StreamOption(directLink.link, HashMap()))
                    )
                } else {
                    Toast.makeText(context, "Error resolving stream: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                resolvingStream = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OttBgPrimary)
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = OttAmberStreaming)
            }
        } else if (error != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = error ?: "Unknown error occurred",
                    color = OttWhite,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(450.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = { scope.launch { fetchMeta() } },
                        colors = ButtonDefaults.colors(
                            containerColor = OttWhite,
                            focusedContainerColor = OttAmberStreaming,
                            contentColor = OttBgPrimary,
                            focusedContentColor = OttBgPrimary
                        ),
                        modifier = Modifier
                            .focusRequester(backButtonFocusRequester)
                            .focusProperties {
                                left = backButtonFocusRequester
                                up = backButtonFocusRequester
                            }
                    ) {
                        Text("Retry", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.colors(
                            containerColor = OttSurface,
                            focusedContainerColor = OttWhite,
                            contentColor = OttWhite,
                            focusedContentColor = OttBgPrimary
                        ),
                        modifier = Modifier.focusProperties {
                            up = backButtonFocusRequester
                        }
                    ) {
                        Text("Go Back")
                    }
                }
            }
        } else meta?.let { movieMeta ->
            val resolvedImageUrl = remember(movieMeta.image, cleanLink) {
                val img = movieMeta.image
                if (img.startsWith("//")) {
                    "https:$img"
                } else if (img.startsWith("/")) {
                    try {
                        val uri = cleanLink.toUri()
                        val host = uri.host
                        if (host != null) "https://$host$img" else img
                    } catch (e: Exception) {
                        img
                    }
                } else {
                    img
                }
            }

            val backdropRequest = remember(context, resolvedImageUrl) {
                buildOttImageRequest(context, resolvedImageUrl)
            }

            // Background Backdrop
            AsyncImage(
                model = backdropRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Dark gradient overlays matching OTT Mini UI Kit
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0.0f to OttNavRailBg.copy(alpha = 0.96f),
                            0.45f to OttBgPrimary.copy(alpha = 0.85f),
                            1.0f to OttBgPrimary.copy(alpha = 0.72f)
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.7f to OttBgPrimary.copy(alpha = 0.75f),
                            1.0f to OttBgPrimary
                        )
                    )
            )

            val ottInsets = rememberOttSafeInsets()

            // Two Pane Layout
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = maxOf(ottInsets.railStart + 12.dp, 28.dp),
                        top = ottInsets.top,
                        end = ottInsets.end,
                        bottom = ottInsets.bottom
                    )
            ) {
                val isCompactHeight = maxHeight < 430.dp
                val posterWidth = if (isCompactHeight) 86.dp else 110.dp
                val posterHeight = if (isCompactHeight) 124.dp else 165.dp

                Row(modifier = Modifier.fillMaxSize()) {
                    // Left Column: Movie Info & Poster
                    Column(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                            .background(OttNavRailBg.copy(alpha = 0.78f), RoundedCornerShape(OttSpecs.CardRadius))
                            .border(1.dp, OttBorderSubtle, RoundedCornerShape(OttSpecs.CardRadius))
                            .padding(if (isCompactHeight) 16.dp else 22.dp)
                    ) {
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.colors(
                            containerColor = OttSurfaceElevated,
                            focusedContainerColor = OttWhite,
                            contentColor = OttWhite,
                            focusedContentColor = OttBgPrimary
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
                        modifier = Modifier
                            .focusRequester(backButtonFocusRequester)
                            .focusProperties {
                                right = rightListFocusRequester
                                left = backButtonFocusRequester // Box focus from escaping to the sidebar
                                up = backButtonFocusRequester // Box focus from escaping upwards
                                down = if (hasWatchNow) watchNowFocusRequester else backButtonFocusRequester
                            }
                            .padding(bottom = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_back),
                                contentDescription = "Back",
                                modifier = Modifier.size(16.dp)
                            )
                            Text("Back", fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(
                            modifier = Modifier
                                .width(posterWidth)
                                .height(posterHeight)
                                .clip(RoundedCornerShape(OttSpecs.CardRadius))
                                .border(1.dp, OttBorderSubtle, RoundedCornerShape(OttSpecs.CardRadius))
                        ) {
                            AsyncImage(
                                model = backdropRequest,
                                contentDescription = movieMeta.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = movieMeta.title,
                                fontSize = if (isCompactHeight) 19.sp else 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = OttWhite,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = if (isCompactHeight) 23.sp else 26.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (movieMeta.type.isNotBlank()) "Now Streaming • ${movieMeta.type.uppercase()}" else "Now Streaming",
                                color = OttAmberStreaming,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (movieMeta.imdbId.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .background(OttAmberBrand.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "IMDb: ${movieMeta.imdbId}",
                                        color = OttAmberBrand,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Synopsis",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = OttWhite
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = if (movieMeta.synopsis.isBlank()) "No synopsis description available." else movieMeta.synopsis,
                            fontSize = 12.sp,
                            color = OttTextSecondary,
                            lineHeight = 18.sp
                        )
                    }

                    // OTT Play Now Button (Auto-resolves first option)
                    if (movieMeta.linkList.isNotEmpty() && movieMeta.type.lowercase() == "movie") {
                        Button(
                            onClick = {
                                val firstLink = movieMeta.linkList.firstOrNull()
                                if (firstLink != null) {
                                    val firstDirect = firstLink.directLinks?.firstOrNull()
                                    if (firstDirect != null) {
                                        onDirectLinkClick(firstDirect, "${movieMeta.title} (${firstDirect.title})")
                                    } else {
                                        firstLink.episodesLink?.let { loadEpisodes(it) }
                                    }
                                }
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = OttWhite,
                                contentColor = OttBgPrimary,
                                focusedContainerColor = OttAmberStreaming,
                                focusedContentColor = OttBgPrimary
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
                            modifier = Modifier
                                .focusRequester(watchNowFocusRequester)
                                .focusProperties {
                                    right = rightListFocusRequester
                                    left = watchNowFocusRequester // Box focus from escaping to the sidebar
                                    up = backButtonFocusRequester
                                    down = watchNowFocusRequester // Box focus from escaping downwards
                                }
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text("Play Now", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Spacer panel divider
                Spacer(modifier = Modifier.width(20.dp))

                // Right Column: Playback Options (Seasons / Direct Links)
                Column(
                    modifier = Modifier
                        .weight(1.75f)
                        .fillMaxHeight()
                ) {
                    Text(
                        text = if (resolvingStream) "Resolving Stream Links..." else if (movieMeta.type.lowercase() == "series") "Seasons & Episodes" else "Stream Playback Links",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 14.dp)
                    )

                    // INACTIVE stream selection panel (streamsToSelect is never set — auto-play uses streams[0])
                    val streams = streamsToSelect
                    if (resolvingStream) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = OttAmberStreaming)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = streamStatusText,
                                    fontSize = 14.sp,
                                    color = OttWhite.copy(alpha = 0.8f)
                                )
                            }
                        }
                    } else if (streams != null) {
                        // Display resolved stream links selection list
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(streams) { index, stream ->
                                Surface(
                                    onClick = {
                                        val selected = stream
                                        val remaining = streams.filter { it != selected }
                                        val ordered = listOf(selected) + remaining
                                        val fallbacks = ArrayList(ordered.map { StreamOption(it.link, HashMap(it.headers ?: emptyMap())) })
                                        streamsToSelect = null
                                        com.samyak.player.PlayerActivity.start(
                                            context = context,
                                            name = "$selectedItemTitle - ${stream.quality}",
                                            streamUrl = stream.link,
                                            headers = stream.headers,
                                            watchHistoryEnabled = true,
                                            movieLink = link,
                                            movieImage = movieMeta.image,
                                            providerUrl = providerUrl,
                                            scraperValue = scraperValue,
                                            streamFallbacks = fallbacks
                                        )
                                    },
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = OttSurface,
                                        focusedContainerColor = OttWhite.copy(alpha = 0.22f),
                                        pressedContainerColor = OttWhite.copy(alpha = 0.30f)
                                    ),
                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                                    modifier = Modifier
                                        .then(if (index == 0) Modifier.focusRequester(rightListFocusRequester) else Modifier)
                                        .focusProperties {
                                            left = if (hasWatchNow) watchNowFocusRequester else backButtonFocusRequester
                                            right = FocusRequester.Cancel
                                        }
                                        .fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stream.server,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = OttWhite,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 12.dp)
                                        )
                                        Text(
                                            text = stream.quality,
                                            fontSize = 12.sp,
                                            color = OttAmberStreaming,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { streamsToSelect = null },
                                    colors = ButtonDefaults.colors(
                                        containerColor = OttLiveRed.copy(alpha = 0.14f),
                                        focusedContainerColor = OttLiveRed,
                                        contentColor = OttLiveRed,
                                        focusedContentColor = OttWhite
                                    ),
                                    modifier = Modifier.focusProperties {
                                        left = if (hasWatchNow) watchNowFocusRequester else backButtonFocusRequester
                                        right = FocusRequester.Cancel
                                    }
                                ) {
                                    Text("Cancel")
                                }
                            }
                        }
                    } else {
                        // General catalog list (Seasons or Direct Links)
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(movieMeta.linkList) { index, vegaLink ->
                                val isExpanded = expandedLinks.contains(vegaLink.title)

                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Surface(
                                        onClick = {
                                            if (vegaLink.episodesLink != null) {
                                                expandedLinks = if (isExpanded) {
                                                    expandedLinks - vegaLink.title
                                                } else {
                                                    if (!episodesMap.containsKey(vegaLink.episodesLink)) {
                                                        loadEpisodes(vegaLink.episodesLink)
                                                    }
                                                    expandedLinks + vegaLink.title
                                                }
                                            } else {
                                                expandedLinks = if (isExpanded) {
                                                    expandedLinks - vegaLink.title
                                                } else {
                                                    expandedLinks + vegaLink.title
                                                }
                                            }
                                        },
                                        colors = ClickableSurfaceDefaults.colors(
                                            containerColor = if (isExpanded) OttWhite.copy(alpha = 0.14f) else OttSurface,
                                            focusedContainerColor = OttWhite.copy(alpha = 0.24f),
                                            pressedContainerColor = OttWhite.copy(alpha = 0.30f)
                                        ),
                                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                                        modifier = Modifier
                                            .then(if (index == 0) Modifier.focusRequester(rightListFocusRequester) else Modifier)
                                            .focusProperties {
                                                left = if (hasWatchNow) watchNowFocusRequester else backButtonFocusRequester
                                                right = FocusRequester.Cancel
                                            }
                                            .fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = vegaLink.title,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isExpanded) OttAmberStreaming else OttWhite,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(end = 12.dp)
                                            )
                                            Text(
                                                text = if (isExpanded) "▼" else "▶",
                                                color = OttTextSecondary,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    if (isExpanded) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(OttNavRailBg.copy(alpha = 0.85f))
                                                .padding(horizontal = 10.dp, vertical = 12.dp)
                                        ) {
                                            if (vegaLink.episodesLink != null) {
                                                // TV Show episodes list
                                                if (loadingEpisodesLink == vegaLink.episodesLink) {
                                                    Box(
                                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        CircularProgressIndicator(color = OttAmberStreaming, modifier = Modifier.size(24.dp))
                                                    }
                                                } else {
                                                    val episodes = episodesMap[vegaLink.episodesLink]?.filter { ep ->
                                                        val titleLower = ep.title.lowercase()
                                                        !titleLower.contains("note") &&
                                                        !titleLower.contains("warning") &&
                                                        !titleLower.contains("download") &&
                                                        !titleLower.contains("join") &&
                                                        !titleLower.contains("telegram") &&
                                                        !titleLower.contains("click")
                                                    }
                                                    if (episodes.isNullOrEmpty()) {
                                                        Text("No episodes loaded.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                                                    } else {
                                                    val playlist = buildEpisodePlaylist(episodes, vegaLink.title) { getResolveType(it) }
                                                    episodes.forEach { episode ->
                                                        val firstDirect = episode.directLinks?.firstOrNull()

                                                        Surface(
                                                            onClick = {
                                                                val epTargetLink = firstDirect?.link ?: episode.episodesLink
                                                                val epTargetType = if (firstDirect != null) getResolveType(firstDirect.type) else getResolveType("series")
                                                                val epIndex = playlist.indexOfFirst { it.link == epTargetLink }.coerceAtLeast(0)
                                                                if (epTargetLink != null) {
                                                                    streamResolutionJob?.cancel()
                                                                    streamResolutionJob = scope.launch {
                                                                        resolvingStream = true
                                                                        streamStatusText = "Resolving stream links..."
                                                                        try {
                                                                          val epTitle = "${vegaLink.title} - ${episode.title}"
                                                                          val fallbackRes = VegaStreamFallbackEngine.resolveWithAutoFallback(
                                                                              context = context,
                                                                              runner = runner,
                                                                              initialLink = epTargetLink,
                                                                              initialType = epTargetType,
                                                                              itemTitle = epTitle,
                                                                              movieTitle = movieMeta.title,
                                                                              currentMeta = movieMeta,
                                                                              currentProviderUrl = providerUrl,
                                                                              currentScraperValue = scraperValue,
                                                                              onStatusUpdate = { status -> streamStatusText = status }
                                                                          )
                                                                          if (fallbackRes == null) {
                                                                              Toast.makeText(context, "No stream links found", Toast.LENGTH_SHORT).show()
                                                                          } else {
                                                                              val selectedStream = fallbackRes.selectedStream
                                                                              val streams = fallbackRes.allStreams
                                                                              if (fallbackRes.message != null) {
                                                                                  Toast.makeText(context, fallbackRes.message, Toast.LENGTH_SHORT).show()
                                                                              } else {
                                                                                  Toast.makeText(context, "Playing: ${selectedStream.server} - ${selectedStream.quality}", Toast.LENGTH_SHORT).show()
                                                                              }
                                                                              com.samyak.player.PlayerActivity.start(
                                                                                  context = context,
                                                                                  name = "$epTitle - ${selectedStream.quality}",
                                                                                  streamUrl = selectedStream.link,
                                                                                  headers = selectedStream.headers,
                                                                                  watchHistoryEnabled = true,
                                                                                  movieLink = "$link#${android.net.Uri.encode(epTitle)}",
                                                                                  movieImage = movieMeta.image,
                                                                                  providerUrl = providerUrl,
                                                                                  scraperValue = scraperValue,
                                                                                  episodes = playlist,
                                                                                  episodeIndex = epIndex,
                                                                                  streamFallbacks = ArrayList(streams.map { StreamOption(it.link, HashMap(it.headers ?: emptyMap())) })
                                                                              )
                                                                          }
                                                                        } catch (e: kotlinx.coroutines.CancellationException) {
                                                                            throw e
                                                                        } catch (e: Exception) {
                                                                            if (epTargetLink.startsWith("http://", ignoreCase = true) || epTargetLink.startsWith("https://", ignoreCase = true)) {
                                                                                val epTitle = "${vegaLink.title} - ${episode.title}"
                                                                                com.samyak.player.PlayerActivity.start(
                                                                                    context = context,
                                                                                    name = "$epTitle - HD",
                                                                                    streamUrl = epTargetLink,
                                                                                    headers = null,
                                                                                    watchHistoryEnabled = true,
                                                                                    movieLink = "$link#${android.net.Uri.encode(epTitle)}",
                                                                                    movieImage = movieMeta.image,
                                                                                    providerUrl = providerUrl,
                                                                                    scraperValue = scraperValue,
                                                                                    episodes = playlist,
                                                                                    episodeIndex = epIndex,
                                                                                    streamFallbacks = arrayListOf(StreamOption(epTargetLink, HashMap()))
                                                                                )
                                                                            } else {
                                                                                Toast.makeText(context, "Error resolving stream: ${e.message}", Toast.LENGTH_SHORT).show()
                                                                            }
                                                                        } finally {
                                                                            resolvingStream = false
                                                                        }
                                                                    }
                                                                }
                                                            },
                                                                colors = ClickableSurfaceDefaults.colors(
                                                                    containerColor = OttSurface,
                                                                    focusedContainerColor = OttWhite.copy(alpha = 0.22f),
                                                                    pressedContainerColor = OttWhite.copy(alpha = 0.30f)
                                                                ),
                                                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                                                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                                                                modifier = Modifier
                                                                    .focusProperties {
                                                                        left = if (hasWatchNow) watchNowFocusRequester else backButtonFocusRequester
                                                                        right = FocusRequester.Cancel
                                                                    }
                                                                    .fillMaxWidth()
                                                                    .padding(vertical = 4.dp)
                                                            ) {
                                                                Row(
                                                                    modifier = Modifier.padding(8.dp),
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                                ) {
                                                                    // 16:9 Thumbnail Box
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .width(96.dp)
                                                                            .aspectRatio(16f / 9f)
                                                                            .clip(RoundedCornerShape(8.dp))
                                                                            .background(OttSurfaceElevated),
                                                                        contentAlignment = Alignment.Center
                                                                    ) {
                                                                        val thumbUrl = episode.image?.takeIf { it.isNotBlank() } ?: movieMeta.image
                                                                        if (thumbUrl.isNotBlank()) {
                                                                            val thumbRequest = remember(context, thumbUrl) {
                                                                                buildOttImageRequest(context, thumbUrl)
                                                                            }
                                                                            AsyncImage(
                                                                                model = thumbRequest,
                                                                                contentDescription = episode.title,
                                                                                contentScale = ContentScale.Crop,
                                                                                modifier = Modifier.fillMaxSize()
                                                                            )
                                                                        }
                                                                        Icon(
                                                                            imageVector = Icons.Default.PlayArrow,
                                                                            contentDescription = null,
                                                                            tint = Color.White,
                                                                            modifier = Modifier.size(16.dp)
                                                                        )
                                                                    }
                                                                    Column(modifier = Modifier.weight(1f)) {
                                                                        Text(
                                                                            text = episode.title,
                                                                            fontSize = 13.sp,
                                                                            color = Color.White,
                                                                            fontWeight = FontWeight.Bold,
                                                                            maxLines = 1,
                                                                            overflow = TextOverflow.Ellipsis
                                                                        )
                                                                        val sub = listOfNotNull(
                                                                            episode.episodeNumber?.let { "Ep $it" },
                                                                            episode.date,
                                                                            episode.duration
                                                                        ).joinToString(" • ")
                                                                        if (sub.isNotBlank()) {
                                                                            Spacer(modifier = Modifier.height(2.dp))
                                                                            Text(
                                                                                text = sub,
                                                                                fontSize = 11.sp,
                                                                                color = Color.LightGray,
                                                                                maxLines = 1,
                                                                                overflow = TextOverflow.Ellipsis
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            } else if (!vegaLink.directLinks.isNullOrEmpty()) {
                                                // Movies direct quality links
                                                vegaLink.directLinks.forEach { dl ->
                                                    Surface(
                                                        onClick = {
                                                            onDirectLinkClick(dl, "${movieMeta.title} (${dl.title})")
                                                        },
                                                        colors = ClickableSurfaceDefaults.colors(
                                                            containerColor = Color.Transparent,
                                                            focusedContainerColor = OttWhite.copy(alpha = 0.20f)
                                                        ),
                                                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                                        modifier = Modifier
                                                            .focusProperties {
                                                                left = if (hasWatchNow) watchNowFocusRequester else backButtonFocusRequester
                                                                right = FocusRequester.Cancel
                                                            }
                                                            .fillMaxWidth()
                                                            .padding(vertical = 4.dp)
                                                    ) {
                                                        Text(
                                                            text = dl.title,
                                                            fontSize = 13.sp,
                                                            color = Color.White,
                                                            modifier = Modifier.padding(10.dp)
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
            }
        }
    }
}

fun buildEpisodePlaylist(
    episodes: List<VegaLink>,
    seasonTitle: String,
    resolveType: (String) -> String
): ArrayList<EpisodeItem> {
    val list = ArrayList<EpisodeItem>()
    for (ep in episodes) {
        val dls = ep.directLinks
        if (dls != null && dls.isNotEmpty()) {
            val dl = dls[0]
            list.add(
                EpisodeItem(
                    title = "$seasonTitle - ${ep.title} (${dl.title})",
                    link = dl.link,
                    type = resolveType(dl.type)
                )
            )
        } else if (ep.episodesLink != null) {
            list.add(
                EpisodeItem(
                    title = "$seasonTitle - ${ep.title}",
                    link = ep.episodesLink,
                    type = resolveType("series")
                )
            )
        }
    }
    return list
}

