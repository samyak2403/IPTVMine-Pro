package com.samyak.iptvminepro.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
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
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.samyak.iptvminepro.model.*
import com.samyak.iptvminepro.provider.VegaProviderRunner
import com.samyak.player.PlayerActivity
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
    
    val storagePermissions = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arrayOf(android.Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (!granted) {
            Toast.makeText(context, "Storage permission is required to download videos.", Toast.LENGTH_LONG).show()
        }
    }

    val downloadWithPermissionCheck: (String, String, Map<String, String>?) -> Unit = { dlUrl, dlTitle, dlHeaders ->
        val hasPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_MEDIA_VIDEO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (hasPermission) {
            com.samyak.iptvminepro.download.DownloadManager.download(
                title = dlTitle,
                downloadUrl = dlUrl,
                headers = dlHeaders
            )
            Toast.makeText(context, "Added to downloads queue", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Storage permissions required to download", Toast.LENGTH_SHORT).show()
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
    var selectedItemTitle by remember { mutableStateOf("") }

    // Expanded states for season categories
    var expandedLinks by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(link) {
        isLoading = true
        error = null
        try {
            val resolvedMeta = runner.getMeta(providerUrl, scraperValue, link)
            if (resolvedMeta.title.isBlank() && resolvedMeta.linkList.isEmpty()) {
                error = "Failed to load details. The provider might be blocked by your ISP or experiencing downtime. Please try enabling a VPN or Cloudflare WARP, or configure Private DNS (Settings > Network > Private DNS: one.one.one.one) and try again."
            } else {
                meta = resolvedMeta
            }
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
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load episodes: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                loadingEpisodesLink = null
            }
        }
    }

    val getResolveType: (String) -> String = { defaultType ->
        if (scraperValue.equals("vega", ignoreCase = true)) {
            "movie"
        } else {
            if (meta?.type?.lowercase() == "movie") "movie" else if (meta?.type?.lowercase() == "series") "series" else defaultType
        }
    }

    val onDirectLinkClick: (VegaDirectLink, String) -> Unit = { directLink, title ->
        scope.launch {
            resolvingStream = true
            try {
                val resolveType = getResolveType(directLink.type)
                val streams = runner.getStream(providerUrl, scraperValue, directLink.link, resolveType).filter { it.link.isNotBlank() }
                if (streams.isEmpty()) {
                    Toast.makeText(context, "No stream links found", Toast.LENGTH_SHORT).show()
                } else if (streams.size == 1) {
                    PlayerActivity.start(context, "$title - ${streams[0].quality}", streams[0].link, streams[0].headers)
                } else {
                    selectedItemTitle = title
                    streamsToSelect = streams
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error resolving stream: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                resolvingStream = false
            }
        }
    }

    val onDownloadLinkClick: (VegaDirectLink, String) -> Unit = { directLink, title ->
        scope.launch {
            resolvingStream = true
            try {
                val resolveType = getResolveType(directLink.type)
                val streams = runner.getStream(providerUrl, scraperValue, directLink.link, resolveType).filter { it.link.isNotBlank() }
                if (streams.isEmpty()) {
                    Toast.makeText(context, "No stream links found", Toast.LENGTH_SHORT).show()
                } else if (streams.size == 1) {
                    downloadWithPermissionCheck(
                        streams[0].link,
                        "$title - ${streams[0].quality}",
                        streams[0].headers
                    )
                } else {
                    selectedItemTitle = title
                    streamsToDownload = streams
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error resolving stream: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                resolvingStream = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0E13)) // HSL Tailored premium deep space dark background
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
                                val resolvedMeta = runner.getMeta(providerUrl, scraperValue, link)
                                if (resolvedMeta.title.isBlank() && resolvedMeta.linkList.isEmpty()) {
                                    error = "Failed to load details. The provider might be blocked by your ISP or experiencing downtime. Please try enabling a VPN or Cloudflare WARP, or configure Private DNS (Settings > Network > Private DNS: one.one.one.one) and try again."
                                } else {
                                    meta = resolvedMeta
                                }
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

            val resolvedImageUrl = remember(movieMeta.image, link) {
                val img = movieMeta.image
                if (img.startsWith("//")) {
                    "https:$img"
                } else if (img.startsWith("/")) {
                    try {
                        val uri = android.net.Uri.parse(link)
                        val host = uri.host
                        if (host != null) {
                            "https://$host$img"
                        } else {
                            img
                        }
                    } catch (e: Exception) {
                        img
                    }
                } else {
                    img
                }
            }

            val refererHeader = remember(resolvedImageUrl) {
                try {
                    val uri = android.net.Uri.parse(resolvedImageUrl)
                    val host = uri.host
                    if (host != null) {
                        val parts = host.split(".")
                        if (parts.size >= 2) {
                            val domain = parts.takeLast(2).joinToString(".")
                            "https://$domain/"
                        } else {
                            "https://$host/"
                        }
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // Top Backdrop section with blurred/gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
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
                    
                    // Dark and colored gradient overlays for premium look
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.3f),
                                        Color(0xFF0F0E13).copy(alpha = 0.8f),
                                        Color(0xFF0F0E13)
                                    )
                                )
                            )
                    )

                    // Back navigation button
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier
                            .padding(top = 12.dp, start = 12.dp)
                            .align(Alignment.TopStart)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                }

                // Info Section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .offset(y = (-60).dp),
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
                            .offset(y = (-40).dp)
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
                        .offset(y = (-20).dp)
                ) {
                    Text(
                        text = if (movieMeta.type.lowercase() == "series") "Seasons & Episodes" else "Stream / Download Options",
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
                            
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF16151D)
                                ),
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
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = vegaLink.title,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            modifier = Modifier.weight(1f)
                                        )
                                        
                                        if (vegaLink.episodesLink != null || (vegaLink.directLinks != null && vegaLink.directLinks.isNotEmpty())) {
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                                tint = Color(0xFF26A69A)
                                            )
                                        }
                                    }

                                    // Expanded content (Direct Links or Episodes)
                                    AnimatedVisibility(visible = isExpanded) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFF1B1A24))
                                                .padding(16.dp)
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
                                                    if (episodes == null || episodes.isEmpty()) {
                                                        Text("No episodes loaded.", color = Color.Gray, fontSize = 13.sp)
                                                    } else {
                                                        episodes.forEach { episodeLink ->
                                                            val hasEpLink = episodeLink.episodesLink != null
                                                            val hasDirectLinks = episodeLink.directLinks != null && episodeLink.directLinks.isNotEmpty()
                                                            Column(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(bottom = 12.dp)
                                                            ) {
                                                                Row(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .clickable(enabled = hasEpLink || hasDirectLinks) {
                                                                            if (hasDirectLinks) {
                                                                                val dLinks = episodeLink.directLinks!!
                                                                                onDirectLinkClick(dLinks[0], "${vegaLink.title} - ${episodeLink.title} (${dLinks[0].title})")
                                                                            } else if (hasEpLink) {
                                                                                scope.launch {
                                                                                    resolvingStream = true
                                                                                    try {
                                                                                        val streams = runner.getStream(providerUrl, scraperValue, episodeLink.episodesLink!!, getResolveType("series")).filter { it.link.isNotBlank() }
                                                                                        if (streams.isEmpty()) {
                                                                                            Toast.makeText(context, "No stream links found", Toast.LENGTH_SHORT).show()
                                                                                        } else if (streams.size == 1) {
                                                                                            PlayerActivity.start(context, "${vegaLink.title} - ${episodeLink.title} - ${streams[0].quality}", streams[0].link, streams[0].headers)
                                                                                        } else {
                                                                                            selectedItemTitle = "${vegaLink.title} - ${episodeLink.title}"
                                                                                            streamsToSelect = streams
                                                                                        }
                                                                                    } catch (e: Exception) {
                                                                                        Toast.makeText(context, "Error resolving stream: ${e.message}", Toast.LENGTH_SHORT).show()
                                                                                    } finally {
                                                                                        resolvingStream = false
                                                                                    }
                                                                                }
                                                                            }
                                                                        }
                                                                        .padding(vertical = 6.dp),
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                                ) {
                                                                    Text(
                                                                        text = episodeLink.title,
                                                                        fontSize = 13.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = Color(0xFF26A69A),
                                                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                                                    )
                                                                    
                                                                    if (hasEpLink || hasDirectLinks) {
                                                                        Row(
                                                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                            verticalAlignment = Alignment.CenterVertically
                                                                        ) {
                                                                            Box(
                                                                                contentAlignment = Alignment.Center,
                                                                                modifier = Modifier
                                                                                    .size(32.dp)
                                                                                    .background(Color(0xFF26A69A).copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                                                                    .clip(RoundedCornerShape(16.dp))
                                                                                    .clickable {
                                                                                        if (hasDirectLinks) {
                                                                                            val dLinks = episodeLink.directLinks!!
                                                                                            onDirectLinkClick(dLinks[0], "${vegaLink.title} - ${episodeLink.title} (${dLinks[0].title})")
                                                                                        } else if (hasEpLink) {
                                                                                            scope.launch {
                                                                                                resolvingStream = true
                                                                                                try {
                                                                                                    val streams = runner.getStream(providerUrl, scraperValue, episodeLink.episodesLink!!, getResolveType("series")).filter { it.link.isNotBlank() }
                                                                                                    if (streams.isEmpty()) {
                                                                                                        Toast.makeText(context, "No stream links found", Toast.LENGTH_SHORT).show()
                                                                                                    } else if (streams.size == 1) {
                                                                                                        PlayerActivity.start(context, "${vegaLink.title} - ${episodeLink.title} - ${streams[0].quality}", streams[0].link, streams[0].headers)
                                                                                                    } else {
                                                                                                        selectedItemTitle = "${vegaLink.title} - ${episodeLink.title}"
                                                                                                        streamsToSelect = streams
                                                                                                    }
                                                                                                } catch (e: Exception) {
                                                                                                    Toast.makeText(context, "Error resolving stream: ${e.message}", Toast.LENGTH_SHORT).show()
                                                                                                } finally {
                                                                                                    resolvingStream = false
                                                                                                }
                                                                                            }
                                                                                        }
                                                                                    }
                                                                            ) {
                                                                                Icon(
                                                                                    imageVector = Icons.Filled.PlayArrow,
                                                                                    contentDescription = "Play Episode",
                                                                                    tint = Color(0xFF26A69A),
                                                                                    modifier = Modifier.size(18.dp)
                                                                                )
                                                                            }

                                                                            Box(
                                                                                contentAlignment = Alignment.Center,
                                                                                modifier = Modifier
                                                                                    .size(32.dp)
                                                                                    .background(Color(0xFF26A69A).copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                                                                    .clip(RoundedCornerShape(16.dp))
                                                                                    .clickable {
                                                                                        if (hasDirectLinks) {
                                                                                            val dLinks = episodeLink.directLinks!!
                                                                                            onDownloadLinkClick(dLinks[0], "${vegaLink.title} - ${episodeLink.title} (${dLinks[0].title})")
                                                                                        } else if (hasEpLink) {
                                                                                            scope.launch {
                                                                                                resolvingStream = true
                                                                                                try {
                                                                                                    val streams = runner.getStream(providerUrl, scraperValue, episodeLink.episodesLink!!, getResolveType("series")).filter { it.link.isNotBlank() }
                                                                                                    if (streams.isEmpty()) {
                                                                                                        Toast.makeText(context, "No stream links found", Toast.LENGTH_SHORT).show()
                                                                                                    } else if (streams.size == 1) {
                                                                                                         downloadWithPermissionCheck(
                                                                                                             streams[0].link,
                                                                                                             "${vegaLink.title} - ${episodeLink.title} - ${streams[0].quality}",
                                                                                                             streams[0].headers
                                                                                                         )
                                                                                                    } else {
                                                                                                        selectedItemTitle = "${vegaLink.title} - ${episodeLink.title}"
                                                                                                        streamsToDownload = streams
                                                                                                    }
                                                                                                } catch (e: Exception) {
                                                                                                    Toast.makeText(context, "Error resolving stream: ${e.message}", Toast.LENGTH_SHORT).show()
                                                                                                } finally {
                                                                                                    resolvingStream = false
                                                                                                }
                                                                                            }
                                                                                        }
                                                                                    }
                                                                            ) {
                                                                                Icon(
                                                                                    imageVector = Icons.Filled.Download,
                                                                                    contentDescription = "Download Episode",
                                                                                    tint = Color(0xFF26A69A),
                                                                                    modifier = Modifier.size(18.dp)
                                                                                )
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                                
                                                                if (episodeLink.directLinks != null && episodeLink.directLinks.isNotEmpty()) {
                                                                    Spacer(modifier = Modifier.height(6.dp))
                                                                    FlowRow(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                                                    ) {
                                                                        episodeLink.directLinks.forEach { directLink ->
                                                                            Row(
                                                                                verticalAlignment = Alignment.CenterVertically,
                                                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                                            ) {
                                                                                Button(
                                                                                    onClick = { onDirectLinkClick(directLink, "${vegaLink.title} - ${episodeLink.title} (${directLink.title})") },
                                                                                    colors = ButtonDefaults.buttonColors(
                                                                                        containerColor = Color(0xFF2C2A36),
                                                                                        contentColor = Color.White
                                                                                    ),
                                                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                                                                    shape = RoundedCornerShape(6.dp),
                                                                                    modifier = Modifier.height(32.dp)
                                                                                ) {
                                                                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                                    Text(directLink.title, fontSize = 11.sp)
                                                                                }
                                                                                Box(
                                                                                    contentAlignment = Alignment.Center,
                                                                                    modifier = Modifier
                                                                                        .size(32.dp)
                                                                                        .background(Color(0xFF2C2A36).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                                                                        .clip(RoundedCornerShape(6.dp))
                                                                                        .clickable { onDownloadLinkClick(directLink, "${vegaLink.title} - ${episodeLink.title} (${directLink.title})") }
                                                                                ) {
                                                                                    Icon(
                                                                                        imageVector = Icons.Filled.Download,
                                                                                        contentDescription = "Download",
                                                                                        tint = Color(0xFF26A69A),
                                                                                        modifier = Modifier.size(16.dp)
                                                                                    )
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                } else if (episodeLink.episodesLink != null) {
                                                                    Spacer(modifier = Modifier.height(4.dp))
                                                                    Text("Stream available. Tap here or the play button to resolve & play.", color = Color(0xFF26A69A).copy(alpha = 0.8f), fontSize = 12.sp)
                                                                } else {
                                                                    Spacer(modifier = Modifier.height(4.dp))
                                                                    Text("No streaming links found for this episode", color = Color.Gray, fontSize = 12.sp)
                                                                }
                                                                Spacer(modifier = Modifier.height(8.dp))
                                                                HorizontalDivider(color = Color(0xFF2C2A36))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            // 2. Direct links (Movie resolutions/mirrors)
                                            else if (vegaLink.directLinks != null && vegaLink.directLinks.isNotEmpty()) {
                                                FlowRow(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    vegaLink.directLinks.forEach { directLink ->
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Button(
                                                                onClick = { onDirectLinkClick(directLink, "${movieMeta.title} - ${vegaLink.title} (${directLink.title})") },
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
                                                                Text(directLink.title, fontSize = 12.sp)
                                                            }
                                                            Box(
                                                                contentAlignment = Alignment.Center,
                                                                modifier = Modifier
                                                                    .size(36.dp)
                                                                    .background(Color(0xFF26A69A).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                                                    .clip(RoundedCornerShape(8.dp))
                                                                    .clickable { onDownloadLinkClick(directLink, "${movieMeta.title} - ${vegaLink.title} (${directLink.title})") }
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
                onDismissRequest = { streamsToSelect = null },
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
                                        streamsToSelect = null
                                        PlayerActivity.start(context, "$selectedItemTitle - ${stream.quality}", stream.link, stream.headers)
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
                    TextButton(onClick = { streamsToSelect = null }) {
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
                        text = "Resolving video link...",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

// Inline helper extension
private fun <T> List<T>?.isNullOrEmpty(): Boolean = this == null || this.isEmpty()
