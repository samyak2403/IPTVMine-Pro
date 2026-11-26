package com.samyak.iptvminepro.provider

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.samyak.iptvminepro.model.Provider
import com.samyak.iptvminepro.model.ProviderType

class ProviderRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("iptv_providers", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_PROVIDERS = "saved_providers"
        private const val KEY_SELECTED_VEGA_PROVIDER_URL = "selected_vega_provider_url"
    }

    fun getSelectedVegaProviderUrl(): String =
        prefs.getString(KEY_SELECTED_VEGA_PROVIDER_URL, "") ?: ""

    fun setSelectedVegaProviderUrl(url: String) {
        prefs.edit().putString(KEY_SELECTED_VEGA_PROVIDER_URL, url).apply()
    }

    fun getPreferredActiveVegaProvider(
        activeProviders: List<Provider> = getProviders().filter { it.isActive && it.safeType == ProviderType.VEGA }
    ): Provider? {
        if (activeProviders.isEmpty()) return null
        val savedUrl = getSelectedVegaProviderUrl()
        return activeProviders.firstOrNull { it.url == savedUrl } ?: activeProviders.firstOrNull()
    }

    fun getProviders(): List<Provider> {
        val json = prefs.getString(KEY_PROVIDERS, null) ?: return emptyList()
        val type = object : TypeToken<List<Provider>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addProvider(provider: Provider) {
        val currentList = getProviders().toMutableList()
        // Prevent exact duplicates
        if (!currentList.any { it.url == provider.url }) {
            currentList.add(provider)
            saveProviders(currentList)
        }
    }

    fun removeProvider(url: String) {
        val currentList = getProviders().toMutableList()
        currentList.removeAll { it.url == url }
        saveProviders(currentList)
    }

    fun updateProvider(provider: Provider) {
        val currentList = getProviders().toMutableList()
        val index = currentList.indexOfFirst { it.url == provider.url }
        if (index != -1) {
            currentList[index] = provider
            saveProviders(currentList)
        }
    }

    fun updateProviderWithUrl(oldUrl: String, newProvider: Provider) {
        val currentList = getProviders().toMutableList()
        val index = currentList.indexOfFirst { it.url == oldUrl }
        if (index != -1) {
            currentList[index] = newProvider
            saveProviders(currentList)
        }
    }

    private fun saveProviders(list: List<Provider>) {
        prefs.edit().putString(KEY_PROVIDERS, gson.toJson(list)).apply()
    }
}
