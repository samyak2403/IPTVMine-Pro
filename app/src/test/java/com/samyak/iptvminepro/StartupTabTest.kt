package com.samyak.iptvminepro

import com.samyak.television.data.StartupTab
import com.samyak.television.data.StartupTabPreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupTabTest {

    @Test
    fun startupTabFromKey_handlesValidNullAndUnknownKeys() {
        assertEquals(StartupTab.HOME, StartupTab.fromKey("HOME"))
        assertEquals(StartupTab.LIVE_TV, StartupTab.fromKey("LIVE_TV"))
        assertEquals(StartupTab.MOVIES, StartupTab.fromKey("MOVIES"))
        assertEquals(StartupTab.CATEGORIES, StartupTab.fromKey("CATEGORIES"))
        assertEquals(StartupTab.HOME, StartupTab.fromKey(null))
        assertEquals(StartupTab.HOME, StartupTab.fromKey("UNKNOWN_TAB"))
    }

    @Test
    fun resolveMobileStartRoute_withoutProvidersAlwaysGoesToAddProvider() {
        for (tab in StartupTab.entries) {
            val route = StartupTabPreferenceManager.resolveMobileStartRoute(
                startupTab = tab,
                hasAnyProvider = false,
                hasActiveIptvProvider = false,
                homeRoute = "home",
                televisionRoute = "television",
                moviesRoute = "movies",
                categoryRoute = "category",
                addProviderRoute = "add_provider"
            )
            assertEquals("add_provider", route)
        }
    }

    @Test
    fun resolveMobileStartRoute_respectsSelectedStartupTab() {
        assertEquals(
            "home",
            StartupTabPreferenceManager.resolveMobileStartRoute(
                startupTab = StartupTab.HOME,
                hasAnyProvider = true,
                hasActiveIptvProvider = true,
                homeRoute = "home",
                televisionRoute = "television",
                moviesRoute = "movies",
                categoryRoute = "category",
                addProviderRoute = "add_provider"
            )
        )
        assertEquals(
            "television",
            StartupTabPreferenceManager.resolveMobileStartRoute(
                startupTab = StartupTab.LIVE_TV,
                hasAnyProvider = true,
                hasActiveIptvProvider = true,
                homeRoute = "home",
                televisionRoute = "television",
                moviesRoute = "movies",
                categoryRoute = "category",
                addProviderRoute = "add_provider"
            )
        )
        assertEquals(
            "home",
            StartupTabPreferenceManager.resolveMobileStartRoute(
                startupTab = StartupTab.LIVE_TV,
                hasAnyProvider = true,
                hasActiveIptvProvider = false,
                homeRoute = "home",
                televisionRoute = "television",
                moviesRoute = "movies",
                categoryRoute = "category",
                addProviderRoute = "add_provider"
            )
        )
        assertEquals(
            "movies",
            StartupTabPreferenceManager.resolveMobileStartRoute(
                startupTab = StartupTab.MOVIES,
                hasAnyProvider = true,
                hasActiveIptvProvider = false,
                homeRoute = "home",
                televisionRoute = "television",
                moviesRoute = "movies",
                categoryRoute = "category",
                addProviderRoute = "add_provider"
            )
        )
        assertEquals(
            "category",
            StartupTabPreferenceManager.resolveMobileStartRoute(
                startupTab = StartupTab.CATEGORIES,
                hasAnyProvider = true,
                hasActiveIptvProvider = true,
                homeRoute = "home",
                televisionRoute = "television",
                moviesRoute = "movies",
                categoryRoute = "category",
                addProviderRoute = "add_provider"
            )
        )
    }

    @Test
    fun resolveTvScreenIndex_mapsTabsAndSmartDefaultCorrectly() {
        assertEquals(
            StartupTabPreferenceManager.TV_SCREEN_LIVE_TV,
            StartupTabPreferenceManager.resolveTvScreenIndex(
                startupTab = StartupTab.HOME,
                hasActiveIptvProvider = true,
                hasActiveVegaProvider = false
            )
        )
        assertEquals(
            StartupTabPreferenceManager.TV_SCREEN_MOVIES,
            StartupTabPreferenceManager.resolveTvScreenIndex(
                startupTab = StartupTab.HOME,
                hasActiveIptvProvider = true,
                hasActiveVegaProvider = true
            )
        )
        assertEquals(
            StartupTabPreferenceManager.TV_SCREEN_LIVE_TV,
            StartupTabPreferenceManager.resolveTvScreenIndex(
                startupTab = StartupTab.LIVE_TV,
                hasActiveIptvProvider = true,
                hasActiveVegaProvider = true
            )
        )
        assertEquals(
            StartupTabPreferenceManager.TV_SCREEN_MOVIES,
            StartupTabPreferenceManager.resolveTvScreenIndex(
                startupTab = StartupTab.MOVIES,
                hasActiveIptvProvider = true,
                hasActiveVegaProvider = false
            )
        )
        assertEquals(
            StartupTabPreferenceManager.TV_SCREEN_LIVE_TV,
            StartupTabPreferenceManager.resolveTvScreenIndex(
                startupTab = StartupTab.CATEGORIES,
                hasActiveIptvProvider = true,
                hasActiveVegaProvider = true
            )
        )
    }

    @Test
    fun nextTvStartupTab_cyclesThroughTvStartupOptions() {
        assertEquals(StartupTab.LIVE_TV, StartupTabPreferenceManager.nextTvStartupTab(StartupTab.HOME))
        assertEquals(StartupTab.MOVIES, StartupTabPreferenceManager.nextTvStartupTab(StartupTab.LIVE_TV))
        assertEquals(StartupTab.HOME, StartupTabPreferenceManager.nextTvStartupTab(StartupTab.MOVIES))
        assertEquals(StartupTab.HOME, StartupTabPreferenceManager.nextTvStartupTab(StartupTab.CATEGORIES))
    }

    @Test
    fun shouldShowTelevisionTab_keepsTabAliveWhenLiveTvStartupSelected() {
        assertTrue(
            StartupTabPreferenceManager.shouldShowTelevisionTab(
                hasActiveIptvProvider = true,
                hasLoadedChannels = false,
                isLoadingChannels = false,
                hasCachedChannelCount = false,
                startupTab = StartupTab.LIVE_TV
            )
        )
        assertFalse(
            StartupTabPreferenceManager.shouldShowTelevisionTab(
                hasActiveIptvProvider = false,
                hasLoadedChannels = true,
                isLoadingChannels = false,
                hasCachedChannelCount = true,
                startupTab = StartupTab.LIVE_TV
            )
        )
        assertFalse(
            StartupTabPreferenceManager.shouldShowTelevisionTab(
                hasActiveIptvProvider = true,
                hasLoadedChannels = false,
                isLoadingChannels = false,
                hasCachedChannelCount = false,
                startupTab = StartupTab.HOME
            )
        )
    }
}
