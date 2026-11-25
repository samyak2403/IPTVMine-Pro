package com.samyak.television.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun IPTVMineProTheme(
    isInDarkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = darkColorScheme(
        primary = OttAmberStreaming,
        onPrimary = OttBgPrimary,
        secondary = OttAmberBrand,
        onSecondary = OttBgPrimary,
        tertiary = OttWhite,
        background = OttBgPrimary,
        onBackground = OttWhite,
        surface = OttSurface,
        onSurface = OttWhite,
        surfaceVariant = OttSurfaceElevated,
        onSurfaceVariant = OttTextSecondary
    )
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
