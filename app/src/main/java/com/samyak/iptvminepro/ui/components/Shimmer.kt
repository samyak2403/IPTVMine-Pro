package com.samyak.iptvminepro.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * An animated shimmer (skeleton) gradient used as a loading placeholder, replacing the
 * plain spinner with a Netflix/Disney+ style content skeleton.
 */
@Composable
fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    val shimmerColors = listOf(
        Color(0xFFE2E2E2),
        Color(0xFFF6F6F6),
        Color(0xFFE2E2E2)
    )
    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translate - 400f, translate - 400f),
        end = Offset(translate, translate)
    )
}

/** Applies a shimmering placeholder background, clipped to [shape]. */
fun Modifier.shimmerBackground(brush: Brush, shape: Shape = RoundedCornerShape(8.dp)): Modifier =
    this.clip(shape).background(brush)

/**
 * Full-screen skeleton that mimics the home layout: a few category rows, each with a
 * title bar and a row of poster cards, all shimmering while content loads.
 */
@Composable
fun HomeShimmerPlaceholder(modifier: Modifier = Modifier) {
    val brush = rememberShimmerBrush()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        repeat(4) {
            // Category title placeholder
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .height(20.dp)
                    .fillMaxWidth(0.45f)
                    .shimmerBackground(brush)
            )
            // Row of poster card placeholders
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .width(110.dp)
                            .height(160.dp)
                            .shimmerBackground(brush, RoundedCornerShape(12.dp))
                    )
                }
            }
        }
    }
}

/**
 * Grid skeleton that mimics a poster grid (Movies / Search results): rows of poster
 * card placeholders shimmering while content loads.
 */
@Composable
fun GridShimmerPlaceholder(
    modifier: Modifier = Modifier,
    columns: Int = 3,
    rows: Int = 4
) {
    val brush = rememberShimmerBrush()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(columns) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .shimmerBackground(brush, RoundedCornerShape(12.dp))
                        )
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(12.dp)
                                .shimmerBackground(brush)
                        )
                    }
                }
            }
        }
    }
}
