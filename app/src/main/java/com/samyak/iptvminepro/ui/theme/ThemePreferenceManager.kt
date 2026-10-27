package com.samyak.iptvminepro.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ThemePreferenceManager {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_MATERIAL_BLACK_THEME = "theme_material_black"

    private var _blackThemeFlow: MutableStateFlow<Boolean>? = null

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getOrCreateFlow(context: Context): MutableStateFlow<Boolean> {
        val currentFlow = _blackThemeFlow
        if (currentFlow != null) {
            return currentFlow
        }
        val initial = isBlackTheme(context)
        val newFlow = MutableStateFlow(initial)
        _blackThemeFlow = newFlow
        return newFlow
    }

    fun isBlackTheme(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_MATERIAL_BLACK_THEME, false)
    }

    fun setBlackTheme(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_MATERIAL_BLACK_THEME, enabled).apply()
        getOrCreateFlow(context).value = enabled
    }

    fun getBlackThemeFlow(context: Context): StateFlow<Boolean> {
        return getOrCreateFlow(context).asStateFlow()
    }

    @Composable
    fun observeBlackTheme(context: Context): State<Boolean> {
        val flow = remember(context) { getOrCreateFlow(context) }
        return flow.collectAsState()
    }
}
