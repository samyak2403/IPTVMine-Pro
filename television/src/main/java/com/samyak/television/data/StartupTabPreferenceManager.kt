package com.samyak.television.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class StartupTab(val key: String, val label: String) {
    HOME("HOME", "Home"),
    LIVE_TV("LIVE_TV", "Live TV"),
    MOVIES("MOVIES", "Movies"),
    CATEGORIES("CATEGORIES", "Categories");

    companion object {
        fun fromKey(key: String?): StartupTab {
            return entries.firstOrNull { it.key == key } ?: HOME
        }
    }
}

object StartupTabPreferenceManager {
    private const val PREFS_NAME = "app_settings"
    const val KEY_STARTUP_TAB = "pref_startup_tab"

    const val TV_SCREEN_LIVE_TV = 0
    const val TV_SCREEN_MOVIES = 1

    private var _startupTabFlow: MutableStateFlow<StartupTab>? = null

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getOrCreateFlow(context: Context): MutableStateFlow<StartupTab> {
        val currentFlow = _startupTabFlow
        if (currentFlow != null) {
            return currentFlow
        }
        val initial = getStartupTab(context)
        val newFlow = MutableStateFlow(initial)
        _startupTabFlow = newFlow
        return newFlow
    }

    fun getStartupTab(context: Context): StartupTab {
        val raw = getPrefs(context).getString(KEY_STARTUP_TAB, StartupTab.HOME.key)
        return StartupTab.fromKey(raw)
    }

    fun setStartupTab(context: Context, tab: StartupTab) {
        getPrefs(context).edit().putString(KEY_STARTUP_TAB, tab.key).apply()
        getOrCreateFlow(context).value = tab
    }

    fun getStartupTabFlow(context: Context): StateFlow<StartupTab> {
        return getOrCreateFlow(context).asStateFlow()
    }

    @Composable
    fun observeStartupTab(context: Context): State<StartupTab> {
        val flow = remember(context) { getOrCreateFlow(context) }
        return flow.collectAsState()
    }

    fun resolveMobileStartRoute(
        startupTab: StartupTab,
        hasAnyProvider: Boolean,
        hasActiveIptvProvider: Boolean,
        homeRoute: String,
        televisionRoute: String,
        moviesRoute: String,
        categoryRoute: String,
        addProviderRoute: String
    ): String {
        if (!hasAnyProvider) return addProviderRoute
        return when (startupTab) {
            StartupTab.HOME -> homeRoute
            StartupTab.LIVE_TV -> if (hasActiveIptvProvider) televisionRoute else homeRoute
            StartupTab.MOVIES -> moviesRoute
            StartupTab.CATEGORIES -> categoryRoute
        }
    }

    fun resolveTvScreenIndex(
        startupTab: StartupTab,
        hasActiveIptvProvider: Boolean,
        hasActiveVegaProvider: Boolean
    ): Int {
        return when (startupTab) {
            StartupTab.LIVE_TV, StartupTab.CATEGORIES -> TV_SCREEN_LIVE_TV
            StartupTab.MOVIES -> TV_SCREEN_MOVIES
            StartupTab.HOME -> {
                if (hasActiveIptvProvider && !hasActiveVegaProvider) TV_SCREEN_LIVE_TV
                else TV_SCREEN_MOVIES
            }
        }
    }

    fun nextTvStartupTab(current: StartupTab): StartupTab {
        return when (current) {
            StartupTab.HOME -> StartupTab.LIVE_TV
            StartupTab.LIVE_TV -> StartupTab.MOVIES
            StartupTab.MOVIES, StartupTab.CATEGORIES -> StartupTab.HOME
        }
    }

    fun tvLabelFor(tab: StartupTab): String {
        return when (tab) {
            StartupTab.HOME -> "Auto (Smart Default)"
            StartupTab.LIVE_TV, StartupTab.CATEGORIES -> "Live TV"
            StartupTab.MOVIES -> "Movies & Shows"
        }
    }

    fun shouldShowTelevisionTab(
        hasActiveIptvProvider: Boolean,
        hasLoadedChannels: Boolean,
        isLoadingChannels: Boolean,
        hasCachedChannelCount: Boolean,
        startupTab: StartupTab
    ): Boolean {
        if (!hasActiveIptvProvider) return false
        if (hasLoadedChannels || startupTab == StartupTab.LIVE_TV) return true
        return isLoadingChannels && hasCachedChannelCount
    }
}
