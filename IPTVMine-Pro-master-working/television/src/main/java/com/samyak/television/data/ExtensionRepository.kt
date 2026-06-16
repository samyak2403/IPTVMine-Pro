package com.samyak.television.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ExtensionRepository private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("iptv_extensions", Context.MODE_PRIVATE)

    private val _installedExtensionsFlow = MutableStateFlow(getInstalledExtensions())
    val installedExtensionsFlow: StateFlow<Set<String>> = _installedExtensionsFlow.asStateFlow()

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_INSTALLED_EXTENSIONS) {
            _installedExtensionsFlow.value = getInstalledExtensions()
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    companion object {
        private const val KEY_INSTALLED_EXTENSIONS = "installed_extensions"
        
        @Volatile
        private var instance: ExtensionRepository? = null

        fun getInstance(context: Context): ExtensionRepository {
            return instance ?: synchronized(this) {
                instance ?: ExtensionRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    fun getInstalledExtensions(): Set<String> {
        return try {
            prefs.getStringSet(KEY_INSTALLED_EXTENSIONS, emptySet())?.toSet() ?: emptySet()
        } catch (e: ClassCastException) {
            val str = try { prefs.getString(KEY_INSTALLED_EXTENSIONS, "") } catch (e: Exception) { "" } ?: ""
            if (str.isNullOrBlank()) emptySet() else str.split(",").filter { it.isNotBlank() }.toSet()
        }
    }

    fun setExtensionInstalled(value: String, installed: Boolean) {
        val installedSet = getInstalledExtensions().toMutableSet()
        if (installed) {
            installedSet.add(value)
        } else {
            installedSet.remove(value)
        }
        prefs.edit().putStringSet(KEY_INSTALLED_EXTENSIONS, installedSet).apply()
        _installedExtensionsFlow.value = installedSet
    }

    fun isInstalled(value: String): Boolean {
        return getInstalledExtensions().contains(value)
    }
}
