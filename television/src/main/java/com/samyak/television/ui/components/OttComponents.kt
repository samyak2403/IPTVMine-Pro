package com.samyak.television.ui.components

import android.content.Context
import androidx.core.net.toUri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.samyak.television.ui.theme.*

/**
 * Exact 1920x1080 -> 960x540dp proportions derived from the OTT Mini UI Kit Figma specs.
 */
object OttSpecs {
    val RailIconColumnWidth = 56.dp
    val NavRailWidth = 72.dp
    val ContentStartPadding = 90.dp
    val CarouselVerticalGap = 28.dp
    val CarouselHeaderGap = 10.dp
    val CarouselItemGap = 12.dp
    val FocusPadding = 8.dp
    val CardRadius = 12.dp
    val LandscapeCardWidth = 292.dp
    val LandscapeCardHeight = 177.dp
    val TallCardWidth = 194.dp
    val TallCardHeight = 306.dp
    const val TallCardAspectRatio = 388f / 642f
}

/**
 * Combined TV overscan + mobile cutout/system-bar safe padding so every TV resolution
 * (720p, 1080p, 4K, overscan boxes) and landscape phone in TV mode renders unobstructed.
 */
@Immutable
data class OttSafeInsets(
    val railStart: Dp,
    val railVisualWidth: Dp,
    val contentStart: Dp,
    val top: Dp,
    val bottom: Dp,
    val end: Dp
)

@Composable
fun rememberOttSafeInsets(): OttSafeInsets {
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDir = LocalLayoutDirection.current
    val sysStart = if (layoutDir == LayoutDirection.Ltr) safe.calculateLeftPadding(layoutDir) else safe.calculateRightPadding(layoutDir)
    val sysEnd = if (layoutDir == LayoutDirection.Ltr) safe.calculateRightPadding(layoutDir) else safe.calculateLeftPadding(layoutDir)
    val sysTop = safe.calculateTopPadding()
    val sysBottom = safe.calculateBottomPadding()

    // Ensure at least 24dp vertical / 16dp horizontal overscan margin on TVs where system insets are 0dp,
    // and clear system status/cutout/gesture bars when present.
    val railStart = maxOf(sysStart + 6.dp, 16.dp)
    val railVisualWidth = railStart + OttSpecs.RailIconColumnWidth + 18.dp
    val contentStart = railStart + OttSpecs.RailIconColumnWidth + 18.dp
    val top = maxOf(sysTop + 8.dp, 24.dp)
    val bottom = maxOf(sysBottom + 8.dp, 20.dp)
    val end = maxOf(sysEnd + 16.dp, 28.dp)

    return remember(railStart, railVisualWidth, contentStart, top, bottom, end) {
        OttSafeInsets(
            railStart = railStart,
            railVisualWidth = railVisualWidth,
            contentStart = contentStart,
            top = top,
            bottom = bottom,
            end = end
        )
    }
}

private const val OTT_IMAGE_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

/**
 * Builds a Coil [ImageRequest] normalizing protocol-relative URLs and adding browser User-Agent/Referer
 * headers so poster & backdrop CDNs load reliably.
 */
fun buildOttImageRequest(context: Context, rawUrl: String): ImageRequest {
    val resolvedUrl = if (rawUrl.startsWith("//")) "https:$rawUrl" else rawUrl
    val referer = try {
        val host = resolvedUrl.toUri().host
        if (host != null) {
            val parts = host.split(".")
            if (parts.size >= 2) "https://${parts.takeLast(2).joinToString(".")}/" else "https://$host/"
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }

    return ImageRequest.Builder(context)
        .data(resolvedUrl)
        .apply {
            addHeader("User-Agent", OTT_IMAGE_USER_AGENT)
            if (referer != null) {
                addHeader("Referer", referer)
            }
        }
        .crossfade(true)
        .build()
}

/**
 * Hero text & CTA lockup matching Figma's Marquee-Lockup / Live-Marquee-Lockup components.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun OttHeroLockup(
    title: String,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLive: Boolean = false,
    statusText: String = if (isLive) "Live Now" else "Now Streaming",
    ctaText: String = "Play Now",
    ctaFocusRequester: FocusRequester? = null,
    titleFontSize: TextUnit = 38.sp,
    titleLineHeight: TextUnit = 42.sp,
    statusFontSize: TextUnit = 15.sp,
    ctaFontSize: TextUnit = 15.sp
) {
    var isCtaFocused by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = title,
            fontSize = titleFontSize,
            fontWeight = FontWeight.ExtraBold,
            color = OttWhite,
            lineHeight = titleLineHeight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (isLive) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Sensors,
                    contentDescription = null,
                    tint = OttLiveRed,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = statusText,
                    fontSize = statusFontSize,
                    fontWeight = FontWeight.Medium,
                    color = OttLiveRed,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Text(
                text = statusText,
                fontSize = statusFontSize,
                fontWeight = FontWeight.Medium,
                color = OttAmberStreaming,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            onClick = onPlayClick,
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = OttWhite.copy(alpha = 0.16f),
                pressedContainerColor = OttWhite.copy(alpha = 0.24f)
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(24.dp)),
            modifier = Modifier
                .then(if (ctaFocusRequester != null) Modifier.focusRequester(ctaFocusRequester) else Modifier)
                .onFocusChanged { isCtaFocused = it.isFocused }
                .then(
                    if (isCtaFocused) {
                        Modifier.border(1.5.dp, OttWhite, RoundedCornerShape(24.dp))
                    } else {
                        Modifier
                    }
                )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(
                    horizontal = if (isCtaFocused) 14.dp else 2.dp,
                    vertical = 6.dp
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayCircle,
                    contentDescription = null,
                    tint = OttWhite,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = ctaText,
                    fontSize = ctaFontSize,
                    fontWeight = FontWeight.Medium,
                    color = OttWhite
                )
            }
        }
    }
}

/**
 * Contained rounded Mid-banner matching Figma node 9-42 (1670x582 scaled with 24px->12dp corners).
 */
@Composable
fun OttMidBanner(
    title: String,
    imageUrl: String,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLive: Boolean = false,
    statusText: String = if (isLive) "Live Now" else "Now Streaming",
    bannerHeight: Dp = 240.dp,
    titleFontSize: TextUnit = 32.sp,
    titleLineHeight: TextUnit = 36.sp
) {
    val context = LocalContext.current
    val imageRequest = remember(context, imageUrl) { buildOttImageRequest(context, imageUrl) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(bannerHeight)
            .clip(RoundedCornerShape(OttSpecs.CardRadius))
            .background(OttAmberBrand)
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to OttScrimBlack.copy(alpha = 0.90f),
                        0.45f to OttScrimBlack.copy(alpha = 0.65f),
                        0.85f to Color.Transparent
                    )
                )
        )

        OttHeroLockup(
            title = title,
            isLive = isLive,
            statusText = statusText,
            onPlayClick = onPlayClick,
            titleFontSize = titleFontSize,
            titleLineHeight = titleLineHeight,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .widthIn(max = 420.dp)
                .padding(horizontal = 28.dp, vertical = 16.dp)
        )
    }
}

/**
 * Section title matching Figma Carousel Title (36px -> 18sp Medium White) with optional View All chip.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun OttSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = OttWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (actionLabel != null && onActionClick != null) {
            var isFocused by remember { mutableStateOf(false) }
            Surface(
                onClick = onActionClick,
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = OttWhite.copy(alpha = 0.10f),
                    focusedContainerColor = OttWhite,
                    pressedContainerColor = OttWhite.copy(alpha = 0.85f)
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                modifier = Modifier.onFocusChanged { isFocused = it.isFocused }
            ) {
                Text(
                    text = actionLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isFocused) OttBgPrimary else OttWhite.copy(alpha = 0.90f),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * Widescreen landscape card matching Figma Carousel (584x354 -> 292x177dp, 12dp radius, clean artwork).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun OttLandscapeCard(
    title: String,
    imageUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    cardWidth: Dp = OttSpecs.LandscapeCardWidth,
    cardHeight: Dp = OttSpecs.LandscapeCardHeight
) {
    val context = LocalContext.current
    val imageRequest = remember(context, imageUrl) { buildOttImageRequest(context, imageUrl) }
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = OttSurface,
            focusedContainerColor = OttSurfaceElevated,
            pressedContainerColor = OttSurfaceElevated
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(OttSpecs.CardRadius)),
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, OttWhite, RoundedCornerShape(OttSpecs.CardRadius))
                } else {
                    Modifier
                }
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = imageRequest,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(OttSpecs.CardRadius))
            )

            AnimatedVisibility(
                visible = isFocused,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, OttScrimBlack.copy(alpha = 0.88f))
                            )
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = title,
                        color = OttWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Tall portrait card matching Figma Tall-Carousel (388x642 -> 194x306dp, 12dp radius, bottom scrim + bold title).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun OttTallPosterCard(
    title: String,
    imageUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    useFixedCarouselSize: Boolean = true,
    cardWidth: Dp = OttSpecs.TallCardWidth,
    cardHeight: Dp = OttSpecs.TallCardHeight
) {
    val context = LocalContext.current
    val imageRequest = remember(context, imageUrl) { buildOttImageRequest(context, imageUrl) }
    var isFocused by remember { mutableStateOf(false) }

    val sizeModifier = if (useFixedCarouselSize) {
        modifier
            .width(cardWidth)
            .height(cardHeight)
    } else {
        modifier
            .fillMaxWidth()
            .aspectRatio(OttSpecs.TallCardAspectRatio)
    }

    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = OttSurface,
            focusedContainerColor = OttSurfaceElevated,
            pressedContainerColor = OttSurfaceElevated
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(OttSpecs.CardRadius)),
        modifier = sizeModifier
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, OttWhite, RoundedCornerShape(OttSpecs.CardRadius))
                } else {
                    Modifier
                }
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = imageRequest,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(OttSpecs.CardRadius))
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.48f to Color.Transparent,
                            1.0f to OttScrimBlack.copy(alpha = 0.90f)
                        )
                    )
            )

            Text(
                text = title,
                color = OttWhite,
                fontSize = if (useFixedCarouselSize) 17.sp else 14.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = if (useFixedCarouselSize) 21.sp else 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            )
        }
    }
}
