package com.samyak.iptvminepro.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.samyak.iptvminepro.utils.TvUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object UiModePreferenceManager {
    private const val PREFS_NAME = "app_settings"
    const val KEY_UI_MODE = "pref_ui_mode"

    const val MODE_AUTO = "AUTO"
    const val MODE_MOBILE = "MOBILE"
    const val MODE_TV = "TV"

    private var _uiModeFlow: MutableStateFlow<String>? = null

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getOrCreateFlow(context: Context): MutableStateFlow<String> {
        val currentFlow = _uiModeFlow
        if (currentFlow != null) {
            return currentFlow
        }
        val initial = getUiMode(context)
        val newFlow = MutableStateFlow(initial)
        _uiModeFlow = newFlow
        return newFlow
    }

    fun getUiMode(context: Context): String {
        return getPrefs(context).getString(KEY_UI_MODE, MODE_AUTO) ?: MODE_AUTO
    }

    fun isTvMode(context: Context): Boolean {
        return when (getUiMode(context)) {
            MODE_TV -> true
            MODE_MOBILE -> false
            else -> TvUtils.isTvMode(context)
        }
    }

    fun setUiMode(context: Context, mode: String) {
        getPrefs(context).edit().putString(KEY_UI_MODE, mode).apply()
        getOrCreateFlow(context).value = mode
    }

    fun setTvMode(context: Context, enabled: Boolean) {
        setUiMode(context, if (enabled) MODE_TV else MODE_MOBILE)
    }

    fun getUiModeFlow(context: Context): StateFlow<String> {
        return getOrCreateFlow(context).asStateFlow()
    }

    @Composable
    fun observeUiMode(context: Context): State<String> {
        val flow = remember(context) { getOrCreateFlow(context) }
        return flow.collectAsState()
    }
}
