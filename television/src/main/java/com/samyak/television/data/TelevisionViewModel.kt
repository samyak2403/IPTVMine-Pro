package com.samyak.television.data

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.samyak.television.model.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TelevisionViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "TelevisionViewModel"
    private val providerRepository = ProviderRepository(application)

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private val _categories = MutableStateFlow<List<String>>(listOf("All"))
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        refreshChannels()
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun refreshChannels() {
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch(Dispatchers.IO) {
            val providers = providerRepository.getProviders()
            val allChannels = mutableListOf<Channel>()

            for (provider in providers) {
                if (provider.isActive && provider.safeType == com.samyak.television.model.ProviderType.IPTV) {
                    Log.d(TAG, "Loading channels from provider: ${provider.title}")
                    val list = ChannelsLoader.fetchChannels(provider.url, provider.title)
                    allChannels.addAll(list)
                }
            }

            // Extract unique categories
            val uniqueCategories = allChannels.map { it.category }.distinct().sorted()
            val categoryList = mutableListOf("All")
            categoryList.addAll(uniqueCategories)

            withContext(Dispatchers.Main) {
                _channels.value = allChannels
                _categories.value = categoryList
                _isLoading.value = false
                if (allChannels.isEmpty()) {
                    _errorMessage.value = "No channels loaded. Check your Internet connection and playlist URL."
                }
                Log.d(TAG, "Loaded ${allChannels.size} channels total, categories: ${categoryList.size}")
            }
        }
    }
}
