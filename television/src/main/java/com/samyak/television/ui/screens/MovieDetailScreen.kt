package com.samyak.television.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import coil.request.ImageRequest
import com.samyak.television.data.VegaProviderRunner
import com.samyak.television.model.*
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import com.samyak.television.R


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
    val cleanLink = remember(link) { link.split('#')[0] }

    var meta by remember { mutableStateOf<VegaMeta?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Episodes state
    var episodesMap by remember { mutableStateOf<Map<String, List<VegaLink>>>(emptyMap()) }
    var loadingEpisodesLink by remember { mutableStateOf<String?>(null) }

    // Stream resolution state
    var streamsToSelect by remember { mutableStateOf<List<VegaStream>?>(null) }
    var resolvingStream by remember { mutableStateOf(false) }
    var selectedItemTitle by remember { mutableStateOf("") }

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
        } catch (e: Exception) {
            error = e.message ?: "Failed to load details"
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(link) {
        fetchMeta()
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
                    val mLink = if (meta?.type?.lowercase() == "series") {
                        "$link#${android.net.Uri.encode(title)}"
                    } else {
                        link
                    }
                    com.samyak.player.PlayerActivity.start(
                        context = context,
                        name = "$title - ${streams[0].quality}",
                        streamUrl = streams[0].link,
                        headers = streams[0].headers,
                        watchHistoryEnabled = true,
                        movieLink = mLink,
                        movieImage = meta?.image ?: "",
                        providerUrl = providerUrl,
                        scraperValue = scraperValue
                    )
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070B13)) // HSL Tailored premium deep space dark background
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF26A69A))
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
                    color = Color.White,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(450.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = { scope.launch { fetchMeta() } },
                        colors = ButtonDefaults.colors(containerColor = Color(0xFF26A69A))
                    ) {
                        Text("Retry")
                    }
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.colors(containerColor = Color(0xFF1E1E1E))
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
                        val uri = android.net.Uri.parse(cleanLink)
                        val host = uri.host
                        if (host != null) "https://$host$img" else img
                    } catch (e: Exception) {
                        img
                    }
                } else {
                    img
                }
            }

            // Background Backdrop
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(resolvedImageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Dark gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color.Transparent, Color(0xFF070B13).copy(alpha = 0.95f), Color(0xFF070B13)),
                            radius = 2000f
                        )
                    )
            )

            // Two Pane Layout
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Left Column: Movie Info & Poster (Glassmorphism Styled Pane)
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .background(Color(0xFF0C111F).copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            focusedContainerColor = Color(0xFF26A69A),
                            contentColor = Color.White
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_back),
                                contentDescription = "Back",
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                            Text("Back")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(
                            modifier = Modifier
                                .width(110.dp)
                                .height(155.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        ) {
                            AsyncImage(
                                model = resolvedImageUrl,
                                contentDescription = movieMeta.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Column {
                            Text(
                                text = movieMeta.title,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 24.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF26A69A).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = movieMeta.type.uppercase(),
                                        color = Color(0xFF26A69A),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (movieMeta.imdbId.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFFFFD600).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "IMDb: ${movieMeta.imdbId}",
                                            color = Color(0xFFFFD600),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Synopsis",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (movieMeta.synopsis.isBlank()) "No synopsis description available." else movieMeta.synopsis,
                        fontSize = 12.sp,
                        color = Color.LightGray,
                        lineHeight = 18.sp
                    )

                    // Cinematic Hotstar Play Button (Auto-resolves first option)
                    if (movieMeta.linkList.isNotEmpty() && movieMeta.type.lowercase() == "movie") {
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                val firstLink = movieMeta.linkList.firstOrNull()
                                if (firstLink != null) {
                                    if (!firstLink.directLinks.isNullOrEmpty()) {
                                        onDirectLinkClick(firstLink.directLinks!![0], "${movieMeta.title} (${firstLink.directLinks!![0].title})")
                                    } else {
                                        firstLink.episodesLink?.let { loadEpisodes(it) }
                                    }
                                }
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = Color.White,
                                contentColor = Color.Black,
                                focusedContainerColor = Color(0xFF26A69A),
                                focusedContentColor = Color.White
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text("Watch Now", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Spacer panel divider
                Spacer(modifier = Modifier.width(24.dp))


                // Right Column: Playback Options (Seasons / Direct Links)
                Column(
                    modifier = Modifier
                        .weight(1.8f)
                        .fillMaxHeight()
                        .padding(start = 24.dp)
                ) {
                    Text(
                        text = if (resolvingStream) "Resolving Stream Links..." else if (streamsToSelect != null) "Select Stream Link:" else if (movieMeta.type.lowercase() == "series") "Seasons & Episodes" else "Stream Playback Links",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    val streams = streamsToSelect
                    if (resolvingStream) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFF26A69A))
                        }
                    } else if (streams != null) {
                        // Display resolved stream links selection list
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(streams) { stream ->
                                Surface(
                                    onClick = {
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
                                            scraperValue = scraperValue
                                        )
                                    },
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = Color(0xFF1E1E1E),
                                        focusedContainerColor = Color(0xFF26A69A),
                                        pressedContainerColor = Color(0xFF208b80)
                                    ),
                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stream.server,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = stream.quality,
                                            fontSize = 12.sp,
                                            color = Color(0xFF26A69A),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { streamsToSelect = null },
                                    colors = ButtonDefaults.colors(containerColor = Color.Red.copy(alpha = 0.1f), focusedContainerColor = Color.Red, contentColor = Color.Red, focusedContentColor = Color.White)
                                ) {
                                    Text("Cancel")
                                }
                            }
                        }
                    } else {
                        // General catalog list (Seasons or Direct Links)
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(movieMeta.linkList) { vegaLink ->
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
                                            containerColor = if (isExpanded) Color(0xFF26A69A).copy(alpha = 0.15f) else Color(0xFF1E1E1E),
                                            focusedContainerColor = Color(0xFF26A69A),
                                            pressedContainerColor = Color(0xFF208b80)
                                        ),
                                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
                                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = vegaLink.title,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = if (isExpanded) "▼" else "▶",
                                                color = Color.Gray,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    if (isExpanded) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFF16151D))
                                                .padding(horizontal = 8.dp, vertical = 12.dp)
                                        ) {
                                            if (vegaLink.episodesLink != null) {
                                                // TV Show episodes list
                                                if (loadingEpisodesLink == vegaLink.episodesLink) {
                                                    Box(
                                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        CircularProgressIndicator(color = Color(0xFF26A69A), modifier = Modifier.size(24.dp))
                                                    }
                                                } else {
                                                    val episodes = episodesMap[vegaLink.episodesLink]
                                                    if (episodes.isNullOrEmpty()) {
                                                        Text("No episodes loaded.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                                                    } else {
                                                        episodes.forEach { episode ->
                                                            val hasEpLink = episode.episodesLink != null
                                                            val hasDirectLinks = !episode.directLinks.isNullOrEmpty()
                                                            
                                                            Surface(
                                                                onClick = {
                                                                    if (hasDirectLinks) {
                                                                        onDirectLinkClick(episode.directLinks!![0], "${vegaLink.title} - ${episode.title}")
                                                                    } else if (hasEpLink) {
                                                                        scope.launch {
                                                                            resolvingStream = true
                                                                            try {
                                                                                val streams = runner.getStream(providerUrl, scraperValue, episode.episodesLink!!, getResolveType("series")).filter { it.link.isNotBlank() }
                                                                                if (streams.isEmpty()) {
                                                                                    Toast.makeText(context, "No stream links found", Toast.LENGTH_SHORT).show()
                                                                                } else if (streams.size == 1) {
                                                                                    val epTitle = "${vegaLink.title} - ${episode.title}"
                                                                                    com.samyak.player.PlayerActivity.start(
                                                                                        context = context,
                                                                                        name = "$epTitle - ${streams[0].quality}",
                                                                                        streamUrl = streams[0].link,
                                                                                        headers = streams[0].headers,
                                                                                        watchHistoryEnabled = true,
                                                                                        movieLink = "$link#${android.net.Uri.encode(epTitle)}",
                                                                                        movieImage = movieMeta.image,
                                                                                        providerUrl = providerUrl,
                                                                                        scraperValue = scraperValue
                                                                                    )
                                                                                } else {
                                                                                    selectedItemTitle = "${vegaLink.title} - ${episode.title}"
                                                                                    streamsToSelect = streams
                                                                                }
                                                                            } catch (e: Exception) {
                                                                                Toast.makeText(context, "Error resolving stream: ${e.message}", Toast.LENGTH_SHORT).show()
                                                                            } finally {
                                                                                resolvingStream = false
                                                                            }
                                                                        }
                                                                    }
                                                                },
                                                                colors = ClickableSurfaceDefaults.colors(
                                                                    containerColor = Color.Transparent,
                                                                    focusedContainerColor = Color(0xFF26A69A).copy(alpha = 0.2f)
                                                                ),
                                                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
                                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                                            ) {
                                                                Text(
                                                                    text = episode.title,
                                                                    fontSize = 13.sp,
                                                                    color = Color(0xFF26A69A),
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    modifier = Modifier.padding(10.dp)
                                                                )
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
                                                            focusedContainerColor = Color(0xFF26A69A).copy(alpha = 0.2f)
                                                        ),
                                                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
                                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
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
