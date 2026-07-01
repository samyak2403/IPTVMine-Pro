package com.samyak.iptvminepro.ui.screens.video

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.samyak.iptvminepro.model.Provider
import com.samyak.iptvminepro.model.ProviderType
import com.samyak.iptvminepro.provider.ChannelsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Shows the user's added direct-video URLs (ProviderType.VIDEO) as a playable list.
 * Tapping a row opens the player with that video URL.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoListScreen(
    viewModel: ChannelsProvider = viewModel(),
    onNavigateBack: () -> Unit,
    onAddProviderClick: () -> Unit
) {
    val context = LocalContext.current
    val providers by viewModel.providers.observeAsState(emptyList())
    val videos = remember(providers) {
        providers.filter { it.safeType == ProviderType.VIDEO && it.isActive }
    }

    Scaffold(
        containerColor = Color(0xFFF5F7FA),
        topBar = {
            TopAppBar(
                title = { Text("My Videos") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onAddProviderClick) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Provider",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        if (videos.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No videos added yet",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(videos, key = { it.url }) { video ->
                    VideoRow(
                        video = video,
                        onClick = {
                            com.samyak.player.PlayerActivity.start(
                                context = context,
                                name = video.title,
                                streamUrl = video.url
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoRow(video: Provider, onClick: () -> Unit) {
    val context = LocalContext.current
    // Preview frame + duration, cached per URL so scrolling doesn't re-download media.
    var preview by remember(video.url) { mutableStateOf(VideoPreviewCache.get(video.url)) }

    LaunchedEffect(video.url) {
        if (preview == null) {
            val result = withContext(Dispatchers.IO) {
                extractVideoPreview(context, video.url, video.userAgent)
            }
            // Only cache useful results so a transient failure can be retried later.
            if (result.frame != null || result.durationMs > 0) {
                VideoPreviewCache.put(video.url, result)
            }
            preview = result
        }
    }

    val frame = preview?.frame
    val durationMs = preview?.durationMs ?: 0L

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail: real video frame when available, gradient placeholder otherwise.
            Box(
                modifier = Modifier
                    .size(width = 120.dp, height = 68.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF26A69A), Color(0xFF00695C))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (frame != null) {
                    Image(
                        bitmap = frame.asImageBitmap(),
                        contentDescription = "Video preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Icon(
                    imageVector = Icons.Filled.PlayCircleFilled,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
                // Duration badge (bottom-right), shown once known.
                if (durationMs > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xCC000000))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = formatDuration(durationMs),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F2937),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = fileNameFromUrl(video.url),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B7280),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Grab a representative frame and the total duration from a video URL.
 * Works for direct files (.mp4, .mkv, ...) and HLS (.m3u8) streams. Returns
 * an empty preview if the media can't be read (offline, unsupported, live stream).
 */
private fun extractVideoPreview(
    context: android.content.Context,
    url: String,
    userAgent: String?
): VideoPreview {
    val retriever = MediaMetadataRetriever()
    return try {
        val headers = if (!userAgent.isNullOrBlank()) {
            mapOf("User-Agent" to userAgent)
        } else {
            emptyMap()
        }
        if (url.startsWith("http", ignoreCase = true)) {
            retriever.setDataSource(url, headers)
        } else {
            retriever.setDataSource(context, android.net.Uri.parse(url))
        }
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L
        // Frame at ~1s (or 0 for very short clips) tends to avoid black intro frames.
        val frameTimeUs = if (duration > 2000L) 1_000_000L else 0L
        // Extract a downscaled frame to keep thumbnails light on memory.
        val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            retriever.getScaledFrameAtTime(
                frameTimeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                THUMB_MAX_WIDTH,
                THUMB_MAX_HEIGHT
            )
        } else {
            retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?.let { scaleDown(it, THUMB_MAX_WIDTH, THUMB_MAX_HEIGHT) }
        }
        VideoPreview(frame, duration)
    } catch (e: Exception) {
        VideoPreview(null, 0L)
    } finally {
        try { retriever.release() } catch (_: Exception) {}
    }
}

/** Downscale a bitmap so its longest side fits within the given bounds (for pre-API-27). */
private fun scaleDown(src: Bitmap, maxW: Int, maxH: Int): Bitmap {
    if (src.width <= maxW && src.height <= maxH) return src
    val ratio = minOf(maxW.toFloat() / src.width, maxH.toFloat() / src.height)
    val w = (src.width * ratio).toInt().coerceAtLeast(1)
    val h = (src.height * ratio).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(src, w, h, true)
    if (scaled != src) src.recycle()
    return scaled
}

/** Format milliseconds as mm:ss (or hh:mm:ss for long videos). */
private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = TimeUnit.SECONDS.toHours(totalSeconds)
    val minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private fun fileNameFromUrl(url: String): String {
    val clean = url.substringBefore("?").substringBefore("#")
    return clean.substringAfterLast('/').ifBlank { clean }
}

/** Cached video preview: a downscaled frame plus total duration in ms. */
private data class VideoPreview(val frame: Bitmap?, val durationMs: Long)

// Thumbnail target bounds (px). The row thumbnail is 120x68dp; ~480x270 stays crisp on hi-dpi.
private const val THUMB_MAX_WIDTH = 480
private const val THUMB_MAX_HEIGHT = 270

/**
 * Process-wide LRU cache for extracted previews, keyed by video URL. Prevents re-downloading
 * and re-decoding frames every time a row is recycled while scrolling the list.
 */
private object VideoPreviewCache {
    private const val MAX_ENTRIES = 64
    private val cache = object : LinkedHashMap<String, VideoPreview>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, VideoPreview>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    @Synchronized
    fun get(key: String): VideoPreview? = cache[key]

    @Synchronized
    fun put(key: String, value: VideoPreview) {
        cache[key] = value
    }
}
