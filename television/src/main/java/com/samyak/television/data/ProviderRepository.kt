package com.samyak.television.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.samyak.television.model.Provider
import com.samyak.television.model.ProviderType

class ProviderRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("iptv_providers", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_PROVIDERS = "saved_providers"
        private const val DEFAULT_PROVIDER_URL = "https://iptv-org.github.io/iptv/categories/news.m3u"
    }

    fun getProviders(): List<Provider> {
        val json = prefs.getString(KEY_PROVIDERS, null)
        if (json.isNullOrEmpty()) {
            // Return default provider if empty
            val defaultProvider = Provider(
                title = "Free News Channels",
                url = DEFAULT_PROVIDER_URL,
                isActive = true,
                type = ProviderType.IPTV
            )
            val defaultList = listOf(defaultProvider)
            saveProviders(defaultList)
            return defaultList
        }
        val type = object : TypeToken<List<Provider>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addProvider(provider: Provider) {
        val currentList = getProviders().toMutableList()
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

    private fun saveProviders(list: List<Provider>) {
        prefs.edit().putString(KEY_PROVIDERS, gson.toJson(list)).apply()
    }
}
