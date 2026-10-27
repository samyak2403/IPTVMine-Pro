package com.samyak.iptvminepro.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalIsBlackTheme = staticCompositionLocalOf { false }

private val MaterialBlackColorScheme = darkColorScheme(
    primary = ColorPrimary,
    onPrimary = Color.Black,
    primaryContainer = ColorPrimaryDark,
    onPrimaryContainer = Color.White,
    secondary = ColorAccent,
    onSecondary = Color.Black,
    tertiary = CoolBlue,
    onTertiary = Color.White,
    background = MaterialPureBlack,
    onBackground = Color.White,
    surface = MaterialPureBlack,
    onSurface = MaterialBlackOnSurface,
    surfaceVariant = MaterialBlackSurfaceVariant,
    onSurfaceVariant = MaterialBlackOnSurfaceVariant,
    outline = MaterialBlackOutline,
    outlineVariant = MaterialBlackDivider
)

private val DarkColorScheme = darkColorScheme(
    primary = ColorPrimary,
    secondary = Secondary,
    tertiary = CoolBlue,
    background = BackgroundColor,
    surface = TvCardBackground,
    onPrimary = TextColor,
    onSecondary = TextColor,
    onTertiary = TextColor,
    onBackground = TextColor,
    onSurface = TextColor
)

private val LightColorScheme = lightColorScheme(
    primary = ColorPrimary,
    secondary = Secondary,
    tertiary = CoolBlue,
    background = Color.White,
    surface = Color(0xFFF5F5F5),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onTertiary = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black
)

@Composable
fun IPTVMineProTheme(
    blackTheme: Boolean = ThemePreferenceManager.isBlackTheme(LocalContext.current),
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        blackTheme -> MaterialBlackColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            if (blackTheme) {
                window.statusBarColor = android.graphics.Color.BLACK
                window.navigationBarColor = android.graphics.Color.BLACK
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            } else {
                window.statusBarColor = ColorPrimary.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = false
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalIsBlackTheme provides blackTheme
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}