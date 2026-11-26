package com.samyak.iptvminepro.ui.screens.movies

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.samyak.iptvminepro.model.*
import com.samyak.iptvminepro.provider.VegaProviderRunner
import com.samyak.iptvminepro.provider.VegaStreamFallbackEngine
import com.samyak.player.PlayerActivity
import com.samyak.player.EpisodeItem
import com.samyak.player.StreamOption
import com.samyak.iptvminepro.ui.theme.ColorPrimary
import com.samyak.iptvminepro.ui.theme.ColorPrimaryDark
import com.samyak.iptvminepro.ui.theme.LocalIsBlackTheme
import com.samyak.iptvminepro.utils.EpisodeBadgeMeta
import com.samyak.iptvminepro.utils.EpisodeThumbnailResolver
import com.samyak.iptvminepro.utils.TitleUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailScreen(
    link: String,
    providerUrl: String,
    scraperValue: String,
    navController: NavController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val runner = remember { VegaProviderRunner(context) }
    DisposableEffect(Unit) { onDispose { runner.destroy() } }
    val cleanLink = remember(link) { link.split('#')[0] }

    // Downloads write to app-specific storage + MediaStore (scoped storage), so no
    // runtime permission is needed on Android 10+ (API 29+). Only legacy devices
    // (API <= 28) require WRITE_EXTERNAL_STORAGE. Requiring it on Android 11+ would be a
    // permanent denial (the permission is capped at API 29 in the manifest).
    val storagePermissions = remember { arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) }

    var pendingDownload by remember {
        mutableStateOf<Triple<String, String, Map<String, String>?>?>(null)
    }

    val performDownload: (String, String, Map<String, String>?) -> Unit = { dlUrl, dlTitle, dlHeaders ->
        com.samyak.iptvminepro.download.DownloadManager.download(
            title = dlTitle,
            downloadUrl = dlUrl,
            headers = dlHeaders
        )
        Toast.makeText(context, "Added to downloads queue", Toast.LENGTH_SHORT).show()
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            pendingDownload?.let { performDownload(it.first, it.second, it.third) }
        } else {
            Toast.makeText(context, "Storage permission is required to download videos.", Toast.LENGTH_LONG).show()
        }
        pendingDownload = null
    }

    val downloadWithPermissionCheck: (String, String, Map<String, String>?) -> Unit = { dlUrl, dlTitle, dlHeaders ->
        val needsLegacyWrite = android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P
        val hasPermission = !needsLegacyWrite ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            performDownload(dlUrl, dlTitle, dlHeaders)
        } else {
            pendingDownload = Triple(dlUrl, dlTitle, dlHeaders)
            permissionLauncher.launch(storagePermissions)
        }
    }

    var meta by remember { mutableStateOf<VegaMeta?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Episodes state
    var episodesMap by remember { mutableStateOf<Map<String, List<VegaLink>>>(emptyMap()) }
    var loadingEpisodesLink by remember { mutableStateOf<String?>(null) }

    // Stream resolution state
    var streamsToSelect by remember { mutableStateOf<List<VegaStream>?>(null) }
    var streamsToDownload by remember { mutableStateOf<List<VegaStream>?>(null) }
    var resolvingStream by remember { mutableStateOf(false) }
    var streamStatusText by remember { mutableStateOf("Resolving stream links...") }
    var selectedItemTitle by remember { mutableStateOf("") }
    var selectedEpisodeContext by remember { mutableStateOf<Pair<ArrayList<EpisodeItem>, Int>?>(null) }
    var streamResolutionJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Expanded states for season categories
    var expandedLinks by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(link) {
        isLoading = true
        error = null
        try {
            val resolvedMeta = runner.getMeta(providerUrl, scraperValue, cleanLink)
            if (resolvedMeta.title.isBlank() && resolvedMeta.linkList.isEmpty()) {
                error = "Failed to load details. The provider might be blocked by your ISP or experiencing downtime. Please try enabling a VPN or Cloudflare WARP, or configure Private DNS (Settings > Network > Private DNS: one.one.one.one) and try again."
            } else {
                meta = resolvedMeta
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Failed to load details"
            android.util.Log.e("MovieDetailScreen", "Error getting meta", e)
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
                    PlayerActivity.start(
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
                if (isPlayableMediaUrl(directLink.link)) {
                    val mLink = if (meta?.type?.lowercase() == "series") "$link#${android.net.Uri.encode(title)}" else link
                    PlayerActivity.start(
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
                    Toast.makeText(context, "No stream links found: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                resolvingStream = false
            }
        }
    }

    val onDownloadLinkClick: (VegaDirectLink, String) -> Unit = { directLink, title ->
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
                    Toast.makeText(context, "No stream links found for download", Toast.LENGTH_SHORT).show()
                } else {
                    val streams = fallbackRes.allStreams
                    if (fallbackRes.message != null) {
                        Toast.makeText(context, fallbackRes.message, Toast.LENGTH_SHORT).show()
                    }
                    if (streams.size == 1) {
                        downloadWithPermissionCheck(
                            streams[0].link,
                            "$title - ${streams[0].quality}",
                            streams[0].headers
                        )
                    } else {
                        selectedItemTitle = title
                        streamsToDownload = streams
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isPlayableMediaUrl(directLink.link) &&
                    !directLink.link.contains("pixeldrain", ignoreCase = true) && !directLink.link.contains("pdrain", ignoreCase = true)) {
                    downloadWithPermissionCheck(directLink.link, "$title - HD", null)
                } else {
                    Toast.makeText(context, "No stream links found for download", Toast.LENGTH_SHORT).show()
                }
            } finally {
                resolvingStream = false
            }
        }
    }

    // Resolves and plays an episode from a playlist, passing the whole playlist + index
    // to the player so its "Next Episode" button can advance through the season.
    val playEpisodeFromPlaylist: (ArrayList<EpisodeItem>, Int) -> Unit = { playlist, index ->
        val item = playlist.getOrNull(index)
        if (item != null) {
            streamResolutionJob?.cancel()
            streamResolutionJob = scope.launch {
                resolvingStream = true
                streamStatusText = "Resolving stream links..."
                try {
                    val fallbackRes = VegaStreamFallbackEngine.resolveWithAutoFallback(
                        context = context,
                        runner = runner,
                        initialLink = item.link,
                        initialType = item.type,
                        itemTitle = item.title,
                        movieTitle = meta?.title ?: item.title,
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
                        PlayerActivity.start(
                            context = context,
                            name = "${item.title} - ${selectedStream.quality}",
                            streamUrl = selectedStream.link,
                            headers = selectedStream.headers,
                            watchHistoryEnabled = true,
                            movieLink = "$link#${android.net.Uri.encode(item.title)}",
                            movieImage = meta?.image ?: "",
                            providerUrl = providerUrl,
                            scraperValue = scraperValue,
                            episodes = playlist,
                            episodeIndex = index,
                            streamFallbacks = fallbacks
                        )
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (isPlayableMediaUrl(item.link) &&
                        !item.link.contains("pixeldrain", ignoreCase = true) && !item.link.contains("pdrain", ignoreCase = true)) {
                        PlayerActivity.start(
                            context = context,
                            name = "${item.title} - HD",
                            streamUrl = item.link,
                            headers = null,
                            watchHistoryEnabled = true,
                            movieLink = "$link#${android.net.Uri.encode(item.title)}",
                            movieImage = meta?.image ?: "",
                            providerUrl = providerUrl,
                            scraperValue = scraperValue,
                            episodes = playlist,
                            episodeIndex = index,
                            streamFallbacks = arrayListOf(StreamOption(item.link, HashMap()))
                        )
                    } else {
                        Toast.makeText(context, "No stream links found for this episode", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    resolvingStream = false
                }
            }
        }
    }

    val onSelectQualityForTarget: (
        targetLink: String,
        targetType: String,
        title: String,
        playlistContext: Pair<ArrayList<EpisodeItem>, Int>?
    ) -> Unit = { targetLink, targetType, title, playlistContext ->
        streamResolutionJob?.cancel()
        streamResolutionJob = scope.launch {
            resolvingStream = true
            streamStatusText = "Loading available servers..."
            try {
                val fallbackRes = VegaStreamFallbackEngine.resolveWithAutoFallback(
                    context = context,
                    runner = runner,
                    initialLink = targetLink,
                    initialType = getResolveType(targetType),
                    itemTitle = title,
                    movieTitle = meta?.title ?: title,
                    currentMeta = meta,
                    currentProviderUrl = providerUrl,
                    currentScraperValue = scraperValue,
                    onStatusUpdate = { status -> streamStatusText = status }
                )
                val streams = fallbackRes?.allStreams.orEmpty()
                if (streams.isEmpty()) {
                    Toast.makeText(context, "No stream servers found", Toast.LENGTH_SHORT).show()
                } else {
                    selectedItemTitle = title
                    selectedEpisodeContext = playlistContext
                    streamsToSelect = streams
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading servers: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                resolvingStream = false
            }
        }
    }


    val isBlackTheme = LocalIsBlackTheme.current

    // Force white status bar icons so they're visible over the dark movie backdrop
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    // Restore icons appropriately when leaving this screen
    DisposableEffect(isBlackTheme) {
        onDispose {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isBlackTheme
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isBlackTheme) Color(0xFF000000) else Color(0xFF0F0E13))
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xFF26A69A)
            )
        } else if (error != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = "Error",
                    tint = Color.Red,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = error ?: "Unknown error occurred",
                    color = Color.White,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        isLoading = true
                        error = null
                        scope.launch {
                            try {
                                val resolvedMeta = runner.getMeta(providerUrl, scraperValue, cleanLink)
                                if (resolvedMeta.title.isBlank() && resolvedMeta.linkList.isEmpty()) {
                                    error = "Failed to load details. The provider might be blocked by your ISP or experiencing downtime. Please try enabling a VPN or Cloudflare WARP, or configure Private DNS (Settings > Network > Private DNS: one.one.one.one) and try again."
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
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26A69A))
                ) {
                    Text("Retry")
                }
            }
        } else meta?.let { movieMeta ->
            val scrollState = rememberScrollState()

            val resolvedImageUrl = remember(movieMeta.image, cleanLink) {
                resolveMediaImageUrl(movieMeta.image, cleanLink)
            }

            val refererHeader = remember(resolvedImageUrl) {
                extractImageReferer(resolvedImageUrl)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // Top Backdrop – full cinematic hero image behind status bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)   // Tall hero backdrop for cinematic look
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(resolvedImageUrl)
                            .apply {
                                addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                                if (refererHeader != null) {
                                    addHeader("Referer", refererHeader)
                                }
                            }
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // Subtle gradient – mostly transparent at top, dark at bottom to blend into content
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Black.copy(alpha = 0.05f),   // Nearly invisible at very top
                                        0.35f to Color.Black.copy(alpha = 0.10f),  // Light scrim in middle
                                        0.72f to Color(0xFF0F0E13).copy(alpha = 0.55f),
                                        1.0f to Color(0xFF0F0E13)                  // Fully opaque at bottom
                                    )
                                )
                            )
                    )

                    // Back navigation button – statusBarsPadding keeps it below the clock/icons
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(top = 4.dp, start = 12.dp)
                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                }


                // Info Section - overlaps upward into the hero backdrop
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .offset(y = (-90).dp),   // Deeper overlap into tall backdrop
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Small poster card
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .width(110.dp)
                            .height(160.dp)
                            .border(1.dp, Color(0xFF2C2A36), RoundedCornerShape(12.dp)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(resolvedImageUrl)
                                .apply {
                                    addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                                    if (refererHeader != null) {
                                        addHeader("Referer", refererHeader)
                                    }
                                }
                                .crossfade(true)
                                .build(),
                            contentDescription = movieMeta.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Metadata details
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 64.dp)
                    ) {
                        Text(
                            text = movieMeta.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Badge for type (Movie / Series)
                            SuggestionChip(
                                onClick = {},
                                label = { Text(movieMeta.type.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    labelColor = Color(0xFF26A69A),
                                    containerColor = Color(0xFF26A69A).copy(alpha = 0.15f)
                                ),
                                border = null,
                                modifier = Modifier.height(28.dp)
                            )
                            
                            if (movieMeta.imdbId.isNotEmpty()) {
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("IMDb: ${movieMeta.imdbId}", fontSize = 11.sp) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        labelColor = Color(0xFFFFD600),
                                        containerColor = Color(0xFFFFD600).copy(alpha = 0.1f)
                                    ),
                                    border = null,
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }
                    }
                }

                // Synopsis/Description
                if (movieMeta.synopsis.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .offset(y = (-64).dp)
                    ) {
                        Text(
                            text = "Synopsis",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = movieMeta.synopsis,
                            fontSize = 14.sp,
                            color = Color(0xFF9CA3AF),
                            lineHeight = 20.sp
                        )
                    }
                }

                // Links / Season Packages
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .offset(y = (-44).dp)
                ) {
                    val isSeries = movieMeta.type.equals("series", ignoreCase = true) ||
                        movieMeta.linkList.any { it.episodesLink != null } ||
                        movieMeta.linkList.any { link ->
                            link.directLinks?.any { dl ->
                                dl.title.contains("episode", ignoreCase = true) ||
                                dl.title.contains("ep ", ignoreCase = true) ||
                                Regex("(?i)\\bep?\\s*\\d{1,3}\\b").containsMatchIn(dl.title)
                            } == true
                        }

                    var episodeThumbnails by remember(cleanLink) {
                        mutableStateOf<Map<Pair<Int, Int>, String>>(emptyMap())
                    }
                    LaunchedEffect(movieMeta.title, movieMeta.imdbId, isSeries) {
                        if (isSeries) {
                            episodeThumbnails = EpisodeThumbnailResolver.fetchEpisodeThumbnails(
                                imdbId = movieMeta.imdbId,
                                seriesTitle = movieMeta.title
                            )
                        }
                    }

                    Text(
                        text = if (isSeries) "Seasons & Episodes" else "Stream / Download Options",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (movieMeta.linkList.isEmpty()) {
                        Text(
                            text = "No links available for this item.",
                            color = Color(0xFF6B7280),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        movieMeta.linkList.forEach { vegaLink ->
                            val isExpanded = expandedLinks.contains(vegaLink.title)
                            val chevronRotation by animateFloatAsState(
                                targetValue = if (isExpanded) 180f else 0f,
                                animationSpec = tween(durationMillis = 180, easing = EmilEaseOut),
                                label = "seasonChevron"
                            )

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isBlackTheme) Color(0xFF0E0E11) else Color(0xFF16151D)
                                ),
                                border = BorderStroke(0.75.dp, Color.White.copy(alpha = 0.08f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    // Header of the link item
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
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
                                            }
                                            .padding(horizontal = 14.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = TitleUtils.cleanTitle(vegaLink.title),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFF3F4F6),
                                            modifier = Modifier.weight(1f)
                                        )

                                        if (vegaLink.episodesLink != null || (vegaLink.directLinks != null && vegaLink.directLinks.isNotEmpty())) {
                                            Icon(
                                                imageVector = Icons.Filled.ArrowDropDown,
                                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                                tint = ColorPrimary,
                                                modifier = Modifier.graphicsLayer {
                                                    rotationZ = chevronRotation
                                                }
                                            )
                                        }
                                    }

                                    // Expanded content (Direct Links or Episodes)
                                    AnimatedVisibility(
                                        visible = isExpanded,
                                        enter = expandVertically(tween(200, easing = EmilEaseOut)) +
                                            fadeIn(tween(180, easing = EmilEaseOut)),
                                        exit = shrinkVertically(tween(150, easing = EmilEaseOut)) +
                                            fadeOut(tween(120, easing = EmilEaseOut))
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(if (isBlackTheme) Color(0xFF08080A) else Color(0xFF111116))
                                                .padding(10.dp)
                                        ) {
                                            // 1. If it's a TV show with episode link
                                            if (vegaLink.episodesLink != null) {
                                                if (loadingEpisodesLink == vegaLink.episodesLink) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(16.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        CircularProgressIndicator(
                                                            color = Color(0xFF26A69A),
                                                            modifier = Modifier.size(24.dp)
                                                        )
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
                                                        Text("No episodes loaded.", color = Color.Gray, fontSize = 13.sp)
                                                    } else {
                                                        val cleanSeasonTitle = TitleUtils.cleanTitle(vegaLink.title)
                                                        val episodePlaylist = buildEpisodePlaylist(episodes, cleanSeasonTitle, getResolveType)
                                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            episodes.forEach { episodeLink ->
                                                                val firstDirect = episodeLink.directLinks?.firstOrNull()
                                                                val hasEpLink = episodeLink.episodesLink != null
                                                                val hasDirectLinks = firstDirect != null
                                                                val episodeTargetLink = firstDirect?.link ?: episodeLink.episodesLink
                                                                val episodeTargetType = firstDirect?.type ?: "series"
                                                                val epIdx = episodePlaylist.indexOfFirst { it.link == episodeTargetLink }.coerceAtLeast(0)
                                                                val playEpisode = {
                                                                    playEpisodeFromPlaylist(episodePlaylist, epIdx)
                                                                }
                                                                val cleanEpTitle = TitleUtils.cleanTitle(episodeLink.title)
                                                                val epActionTitle = "$cleanSeasonTitle - $cleanEpTitle"
                                                                val badgeMeta = TitleUtils.extractEpisodeBadgeMeta(
                                                                    episodeTitle = episodeLink.title,
                                                                    seasonTitle = cleanSeasonTitle,
                                                                    rawSeasonNumber = episodeLink.seasonNumber ?: vegaLink.seasonNumber,
                                                                    rawEpisodeNumber = episodeLink.episodeNumber,
                                                                    rawDuration = episodeLink.duration,
                                                                    fallbackQualitySource = firstDirect?.title ?: ""
                                                                )
                                                                val matchedEpisodeThumb = if (badgeMeta.seasonNumber != null && badgeMeta.episodeNumber != null) {
                                                                    episodeThumbnails[badgeMeta.seasonNumber to badgeMeta.episodeNumber]
                                                                } else null
                                                                val thumbUrl = resolveMediaImageUrl(
                                                                    episodeLink.image?.takeIf { it.isNotBlank() }
                                                                        ?: firstDirect?.image?.takeIf { it.isNotBlank() }
                                                                        ?: matchedEpisodeThumb?.takeIf { it.isNotBlank() }
                                                                        ?: movieMeta.image,
                                                                    cleanLink
                                                                )

                                                                MobileEpisodeCard(
                                                                    meta = badgeMeta,
                                                                    dateText = episodeLink.date,
                                                                    thumbnailUrl = thumbUrl,
                                                                    isBlackTheme = isBlackTheme,
                                                                    canPlay = hasEpLink || hasDirectLinks,
                                                                    canDownload = hasEpLink || hasDirectLinks,
                                                                    extraDirectLinks = episodeLink.directLinks.orEmpty(),
                                                                    onPlay = playEpisode,
                                                                    onDownload = {
                                                                        if (firstDirect != null) {
                                                                            onDownloadLinkClick(firstDirect, "$epActionTitle (${TitleUtils.cleanTitle(firstDirect.title)})")
                                                                        } else if (episodeLink.episodesLink != null) {
                                                                            val epLinkForDownload = episodeLink.episodesLink
                                                                            streamResolutionJob?.cancel()
                                                                            streamResolutionJob = scope.launch {
                                                                                resolvingStream = true
                                                                                streamStatusText = "Resolving stream links..."
                                                                                try {
                                                                                    val fallbackRes = VegaStreamFallbackEngine.resolveWithAutoFallback(
                                                                                        context = context,
                                                                                        runner = runner,
                                                                                        initialLink = epLinkForDownload,
                                                                                        initialType = getResolveType("series"),
                                                                                        itemTitle = epActionTitle,
                                                                                        movieTitle = meta?.title ?: cleanSeasonTitle,
                                                                                        currentMeta = meta,
                                                                                        currentProviderUrl = providerUrl,
                                                                                        currentScraperValue = scraperValue,
                                                                                        onStatusUpdate = { status -> streamStatusText = status }
                                                                                    )
                                                                                    val streams = fallbackRes?.allStreams.orEmpty()
                                                                                    if (streams.isEmpty()) {
                                                                                        Toast.makeText(context, "No stream links found for download", Toast.LENGTH_SHORT).show()
                                                                                    } else if (streams.size == 1) {
                                                                                        downloadWithPermissionCheck(
                                                                                            streams[0].link,
                                                                                            "$epActionTitle - ${streams[0].quality}",
                                                                                            streams[0].headers
                                                                                        )
                                                                                    } else {
                                                                                        selectedItemTitle = epActionTitle
                                                                                        streamsToDownload = streams
                                                                                    }
                                                                                } catch (e: kotlinx.coroutines.CancellationException) {
                                                                                    throw e
                                                                                } catch (e: Exception) {
                                                                                    Toast.makeText(context, "Error resolving stream: ${e.message}", Toast.LENGTH_SHORT).show()
                                                                                } finally {
                                                                                    resolvingStream = false
                                                                                }
                                                                            }
                                                                        }
                                                                    },
                                                                    onSelectServer = episodeTargetLink?.let { target ->
                                                                        {
                                                                            onSelectQualityForTarget(
                                                                                target,
                                                                                episodeTargetType,
                                                                                epActionTitle,
                                                                                Pair(episodePlaylist, epIdx)
                                                                            )
                                                                        }
                                                                    },
                                                                    onPlayDirectLink = { directLink ->
                                                                        onDirectLinkClick(directLink, "$epActionTitle (${TitleUtils.cleanTitle(directLink.title)})")
                                                                    },
                                                                    onDownloadDirectLink = { directLink ->
                                                                        onDownloadLinkClick(directLink, "$epActionTitle (${TitleUtils.cleanTitle(directLink.title)})")
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            // 2. Direct links (Series direct episodes OR Movie resolutions/mirrors)
                                            else if (!vegaLink.directLinks.isNullOrEmpty()) {
                                                val cleanSeasonTitle = TitleUtils.cleanTitle(vegaLink.title)
                                                val isSeriesLinks = movieMeta.type.equals("series", ignoreCase = true) ||
                                                    (vegaLink.directLinks.size > 1 && vegaLink.directLinks.any {
                                                        it.title.contains("episode", ignoreCase = true) ||
                                                        it.title.contains("ep ", ignoreCase = true) ||
                                                        Regex("(?i)\\bep?\\s*\\d{1,3}\\b").containsMatchIn(it.title)
                                                    })
                                                if (isSeriesLinks) {
                                                    val seasonPlaylist = ArrayList(
                                                        vegaLink.directLinks.map { dl ->
                                                            EpisodeItem(
                                                                title = "${movieMeta.title} - $cleanSeasonTitle (${TitleUtils.cleanTitle(dl.title)})",
                                                                link = dl.link,
                                                                type = getResolveType(dl.type)
                                                            )
                                                        }
                                                    )
                                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        vegaLink.directLinks.forEachIndexed { dlIndex, directLink ->
                                                            val cleanDlTitle = TitleUtils.cleanTitle(directLink.title)
                                                            val epActionTitle = "${movieMeta.title} - $cleanSeasonTitle ($cleanDlTitle)"
                                                            val badgeMeta = TitleUtils.extractEpisodeBadgeMeta(
                                                                episodeTitle = directLink.title,
                                                                seasonTitle = cleanSeasonTitle,
                                                                rawSeasonNumber = directLink.seasonNumber ?: vegaLink.seasonNumber,
                                                                rawEpisodeNumber = directLink.episodeNumber ?: vegaLink.episodeNumber,
                                                                rawDuration = directLink.duration ?: vegaLink.duration
                                                            )
                                                            val matchedEpisodeThumb = if (badgeMeta.seasonNumber != null && badgeMeta.episodeNumber != null) {
                                                                episodeThumbnails[badgeMeta.seasonNumber to badgeMeta.episodeNumber]
                                                            } else null
                                                            val thumbUrl = resolveMediaImageUrl(
                                                                directLink.image?.takeIf { it.isNotBlank() }
                                                                    ?: vegaLink.image?.takeIf { it.isNotBlank() }
                                                                    ?: matchedEpisodeThumb?.takeIf { it.isNotBlank() }
                                                                    ?: movieMeta.image,
                                                                cleanLink
                                                            )

                                                            MobileEpisodeCard(
                                                                meta = badgeMeta,
                                                                dateText = directLink.date ?: vegaLink.date,
                                                                thumbnailUrl = thumbUrl,
                                                                isBlackTheme = isBlackTheme,
                                                                canPlay = true,
                                                                canDownload = true,
                                                                onPlay = { playEpisodeFromPlaylist(seasonPlaylist, dlIndex) },
                                                                onDownload = { onDownloadLinkClick(directLink, epActionTitle) },
                                                                onSelectServer = {
                                                                    onSelectQualityForTarget(
                                                                        directLink.link,
                                                                        directLink.type,
                                                                        epActionTitle,
                                                                        Pair(seasonPlaylist, dlIndex)
                                                                    )
                                                                }
                                                            )
                                                        }
                                                    }
                                                } else {
                                                    FlowRow(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        vegaLink.directLinks.forEach { directLink ->
                                                            val cleanDlTitle = TitleUtils.cleanTitle(directLink.title)
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                            ) {
                                                                Button(
                                                                    onClick = {
                                                                        onDirectLinkClick(directLink, "${movieMeta.title} - $cleanSeasonTitle ($cleanDlTitle)")
                                                                    },
                                                                    colors = ButtonDefaults.buttonColors(
                                                                        containerColor = Color(0xFF26A69A),
                                                                        contentColor = Color.White
                                                                    ),
                                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                                                    shape = RoundedCornerShape(8.dp),
                                                                    modifier = Modifier.height(36.dp)
                                                                ) {
                                                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                    Text(cleanDlTitle, fontSize = 12.sp)
                                                                }
                                                                Box(
                                                                    contentAlignment = Alignment.Center,
                                                                    modifier = Modifier
                                                                        .size(36.dp)
                                                                        .background(Color(0xFF26A69A).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                                                        .clip(RoundedCornerShape(8.dp))
                                                                        .clickable { onDownloadLinkClick(directLink, "${movieMeta.title} - $cleanSeasonTitle ($cleanDlTitle)") }
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Filled.Download,
                                                                        contentDescription = "Download",
                                                                        tint = Color(0xFF26A69A),
                                                                        modifier = Modifier.size(18.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            } else {
                                                Text("No streaming sources available.", color = Color.Gray, fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        // Dialog for Stream Selection
        streamsToSelect?.let { streams ->
            AlertDialog(
                onDismissRequest = {
                    streamsToSelect = null
                    selectedEpisodeContext = null
                },
                title = { Text("Select Stream Quality / Server", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        streams.forEach { stream ->
                            val displayName = buildString {
                                append(stream.server)
                                if (stream.quality.isNotEmpty()) {
                                    append(" - ")
                                    append(stream.quality)
                                }
                                if (stream.type.isNotEmpty()) {
                                    append(" (")
                                    append(stream.type)
                                    append(")")
                                }
                            }
                            
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val selected = stream
                                        val allStreams = streamsToSelect ?: listOf(selected)
                                        val reordered = listOf(selected) + allStreams.filter { it != selected }
                                        val fallbacks = ArrayList(reordered.map { StreamOption(it.link, HashMap(it.headers ?: emptyMap())) })
                                        val epCtx = selectedEpisodeContext
                                        streamsToSelect = null
                                        selectedEpisodeContext = null
                                        val mLink = if (meta?.type?.lowercase() == "series") {
                                            "$link#${android.net.Uri.encode(selectedItemTitle)}"
                                        } else {
                                            link
                                        }
                                        PlayerActivity.start(
                                            context = context,
                                            name = "$selectedItemTitle - ${stream.quality}",
                                            streamUrl = stream.link,
                                            headers = stream.headers,
                                            watchHistoryEnabled = true,
                                            movieLink = mLink,
                                            movieImage = meta?.image ?: "",
                                            providerUrl = providerUrl,
                                            scraperValue = scraperValue,
                                            episodes = epCtx?.first ?: ArrayList(),
                                            episodeIndex = epCtx?.second ?: 0,
                                            streamFallbacks = fallbacks
                                        )
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF2C2A36)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = displayName,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = "Play",
                                        tint = Color(0xFF26A69A)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        streamsToSelect = null
                        selectedEpisodeContext = null
                    }) {
                        Text("Cancel", color = Color(0xFF26A69A))
                    }
                },
                containerColor = Color(0xFF16151D),
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Dialog for Download Selection
        streamsToDownload?.let { streams ->
            AlertDialog(
                onDismissRequest = { streamsToDownload = null },
                title = { Text("Select Download Quality / Server", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        streams.forEach { stream ->
                            val displayName = buildString {
                                append(stream.server)
                                if (stream.quality.isNotEmpty()) {
                                    append(" - ")
                                    append(stream.quality)
                                }
                                if (stream.type.isNotEmpty()) {
                                    append(" (")
                                    append(stream.type)
                                    append(")")
                                }
                            }
                            
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        streamsToDownload = null
                                        downloadWithPermissionCheck(
                                            stream.link,
                                            "$selectedItemTitle - ${stream.quality}",
                                            stream.headers
                                        )
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF2C2A36)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = displayName,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.Download,
                                        contentDescription = "Download",
                                        tint = Color(0xFF26A69A)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { streamsToDownload = null }) {
                        Text("Cancel", color = Color(0xFF26A69A))
                    }
                },
                containerColor = Color(0xFF16151D),
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Overlay spinner while resolving stream URL
        if (resolvingStream) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF26A69A))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = streamStatusText,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}


private val EmilEaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private val EpisodeTealSoftBg = ColorPrimary.copy(alpha = 0.14f)
private val EpisodeTealSoftBorder = ColorPrimary.copy(alpha = 0.34f)
private val EpisodeTealText = Color(0xFF4FD1C5)

@Composable
private fun MobileEpisodeCard(
    meta: EpisodeBadgeMeta,
    dateText: String?,
    thumbnailUrl: String,
    isBlackTheme: Boolean,
    canPlay: Boolean,
    canDownload: Boolean,
    extraDirectLinks: List<VegaDirectLink> = emptyList(),
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onSelectServer: (() -> Unit)? = null,
    onPlayDirectLink: ((VegaDirectLink) -> Unit)? = null,
    onDownloadDirectLink: ((VegaDirectLink) -> Unit)? = null
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    val cardInteraction = remember { MutableInteractionSource() }
    val isCardPressed by cardInteraction.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isCardPressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = 140, easing = EmilEaseOut),
        label = "episodeCardScale"
    )

    val downloadInteraction = remember { MutableInteractionSource() }
    val isDownloadPressed by downloadInteraction.collectIsPressedAsState()
    val downloadScale by animateFloatAsState(
        targetValue = if (isDownloadPressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = 120, easing = EmilEaseOut),
        label = "episodeDownloadScale"
    )

    val cardShape = RoundedCornerShape(14.dp)
    val cardBg = if (isBlackTheme) {
        if (isCardPressed) Color(0xFF16161A) else Color(0xFF111215)
    } else {
        if (isCardPressed) Color(0xFF1D1D26) else Color(0xFF17171F)
    }
    val thumbShape = RoundedCornerShape(8.dp)
    val thumbReferer = remember(thumbnailUrl) { extractImageReferer(thumbnailUrl) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .clip(cardShape)
            .background(cardBg)
            .border(0.75.dp, Color.White.copy(alpha = 0.08f), cardShape)
            .clickable(
                interactionSource = cardInteraction,
                indication = ripple(color = Color.White.copy(alpha = 0.08f)),
                enabled = canPlay,
                onClick = onPlay
            )
            .padding(9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(104.dp)
                    .aspectRatio(16f / 9f)
                    .clip(thumbShape)
                    .background(if (isBlackTheme) Color(0xFF18181C) else Color(0xFF21212B))
                    .border(0.5.dp, Color.White.copy(alpha = 0.12f), thumbShape)
            ) {
                if (thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(thumbnailUrl)
                            .apply {
                                addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                                if (thumbReferer != null) {
                                    addHeader("Referer", thumbReferer)
                                }
                            }
                            .crossfade(180)
                            .build(),
                        contentDescription = meta.displayTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.05f),
                                    Color.Black.copy(alpha = 0.38f)
                                )
                            )
                        )
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.52f), CircleShape)
                        .border(0.75.dp, Color.White.copy(alpha = 0.24f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = meta.displayTitle,
                        color = Color(0xFFF3F4F6),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp)
                    )
                    if (canPlay || canDownload || onSelectServer != null) {
                        Box {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .clickable { menuExpanded = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "More options",
                                    tint = Color(0xFF9CA3AF),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                containerColor = if (isBlackTheme) Color(0xFF121215) else Color(0xFF1B1B22)
                            ) {
                                if (canPlay) {
                                    DropdownMenuItem(
                                        text = { Text("Play Episode", color = Color.White, fontSize = 13.sp) },
                                        onClick = {
                                            menuExpanded = false
                                            onPlay()
                                        }
                                    )
                                }
                                if (extraDirectLinks.size > 1 && onPlayDirectLink != null) {
                                    extraDirectLinks.forEach { dl ->
                                        val cleanDlTitle = TitleUtils.cleanTitle(dl.title)
                                        DropdownMenuItem(
                                            text = { Text("Play: $cleanDlTitle", color = Color.White, fontSize = 13.sp) },
                                            onClick = {
                                                menuExpanded = false
                                                onPlayDirectLink(dl)
                                            }
                                        )
                                        if (onDownloadDirectLink != null) {
                                            DropdownMenuItem(
                                                text = { Text("Download: $cleanDlTitle", color = Color(0xFF9CA3AF), fontSize = 12.sp) },
                                                onClick = {
                                                    menuExpanded = false
                                                    onDownloadDirectLink(dl)
                                                }
                                            )
                                        }
                                    }
                                } else if (onSelectServer != null) {
                                    DropdownMenuItem(
                                        text = { Text("Select Quality / Server", color = Color.White, fontSize = 13.sp) },
                                        onClick = {
                                            menuExpanded = false
                                            onSelectServer()
                                        }
                                    )
                                }
                                if (canDownload) {
                                    DropdownMenuItem(
                                        text = { Text("Download", color = Color.White, fontSize = 13.sp) },
                                        onClick = {
                                            menuExpanded = false
                                            onDownload()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                val hasBadgeMeta = meta.seasonLabel != null ||
                    meta.episodeLabel != null ||
                    meta.qualityBadge.isNotBlank() ||
                    !meta.bottomRightTag.isNullOrBlank() ||
                    !dateText.isNullOrBlank()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (hasBadgeMeta) {
                        FlowRow(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            itemVerticalAlignment = Alignment.CenterVertically
                        ) {
                            if (meta.episodeLabel != null) {
                                Box(
                                    modifier = Modifier
                                        .background(EpisodeTealSoftBg, RoundedCornerShape(6.dp))
                                        .border(0.5.dp, EpisodeTealSoftBorder, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = meta.episodeLabel,
                                        color = EpisodeTealText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }
                            } else if (!dateText.isNullOrBlank()) {
                                Text(
                                    text = dateText,
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 10.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (meta.seasonLabel != null) {
                                Text(
                                    text = meta.seasonLabel,
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (meta.qualityBadge.isNotBlank()) {
                                Text(
                                    text = "\u2022",
                                    color = Color(0xFF4B5563),
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = meta.qualityBadge,
                                    color = Color(0xFFD1D5DB),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                            if (!meta.bottomRightTag.isNullOrBlank()) {
                                Text(
                                    text = "\u2022",
                                    color = Color(0xFF4B5563),
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = meta.bottomRightTag,
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                        }
                    } else if (!canPlay && !canDownload) {
                        Text(
                            text = "No streaming links",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    if (canDownload) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(30.dp)
                                .graphicsLayer {
                                    scaleX = downloadScale
                                    scaleY = downloadScale
                                }
                                .clip(CircleShape)
                                .background(EpisodeTealSoftBg)
                                .border(0.75.dp, EpisodeTealSoftBorder, CircleShape)
                                .clickable(
                                    interactionSource = downloadInteraction,
                                    indication = ripple(color = ColorPrimary.copy(alpha = 0.25f)),
                                    onClick = onDownload
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = "Download Episode",
                                tint = EpisodeTealText,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun resolveMediaImageUrl(rawUrl: String, pageLink: String): String {
    if (rawUrl.startsWith("//")) return "https:$rawUrl"
    if (rawUrl.startsWith("/")) {
        return try {
            val host = pageLink.toUri().host
            if (host != null) "https://$host$rawUrl" else rawUrl
        } catch (_: Exception) {
            rawUrl
        }
    }
    return rawUrl
}

private fun extractImageReferer(resolvedUrl: String): String? {
    if (resolvedUrl.isBlank()) return null
    return try {
        val host = resolvedUrl.toUri().host ?: return null
        val parts = host.split(".")
        if (parts.size >= 2) {
            "https://${parts.takeLast(2).joinToString(".")}/"
        } else {
            "https://$host/"
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * Builds an ordered playlist of episodes (within a season) so the player can offer
 * "Next Episode" playback. Each entry carries the unresolved provider link and the
 * resolve type; the player resolves it to a stream on demand via the registered
 * StreamResolver. Episodes that expose neither a direct link nor an episodes link are
 * skipped so indices stay consistent with what is actually playable.
 */
private fun buildEpisodePlaylist(
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
