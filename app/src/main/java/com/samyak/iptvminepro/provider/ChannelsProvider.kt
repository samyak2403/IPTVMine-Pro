package com.samyak.iptvminepro.provider

import android.util.Log
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.samyak.iptvminepro.model.Channel
import com.samyak.iptvminepro.utils.ContentFilter
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.json.JSONArray
import com.samyak.iptvminepro.notification.NotificationHelper
import com.samyak.iptvminepro.model.Provider

class ChannelsProvider(application: Application) : AndroidViewModel(application) {

    private val _providers = MutableLiveData<List<Provider>>(emptyList())
    val providers: LiveData<List<Provider>> get() = _providers

    private val repository = ProviderRepository(application)

    private val _channels = MutableLiveData<List<Channel>>(emptyList())
    val channels: LiveData<List<Channel>> get() = _channels

    private val _filteredChannels = MutableLiveData<List<Channel>>(emptyList())
    val filteredChannels: LiveData<List<Channel>> get() = _filteredChannels

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> get() = _error

    private val _categories = MutableLiveData<List<String>>(emptyList())
    val categories: LiveData<List<String>> get() = _categories

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _providerChannelCounts = MutableLiveData<Map<String, Int>>(emptyMap())
    val providerChannelCounts: LiveData<Map<String, Int>> get() = _providerChannelCounts

    private val sourceUrls = mutableListOf<String>()

    private var lastMatchCount = 0
    private val notificationHelper = NotificationHelper(application)
    private var notificationCount = 0

    fun showNewMatchNotification(channel: Channel) {
        notificationCount++
        notificationHelper.showMatchNotification(
            title = "New Match Available!",
            message = "${channel.team1} vs ${channel.team2} is now live!",
            matchName = channel.name,
            bannerUrl = channel.logoUrl,
            badgeCount = notificationCount
        )
    }

    fun clearNotifications() {
        notificationCount = 0
        notificationHelper.clearNotifications()
    }

    init {
        refreshProviders()
    }

    fun refreshProviders() {
        val list = repository.getProviders()
        _providers.postValue(list)
        synchronized(sourceUrls) {
            sourceUrls.clear()
            sourceUrls.addAll(list.filter { it.isActive && it.safeType == com.samyak.iptvminepro.model.ProviderType.IPTV }.map { it.url })
        }
    }

    private var fetchJob: Job? = null

    companion object {
        private const val TAG = "ChannelsProvider"
        private const val DEFAULT_LOGO_URL = "assets/images/ic_tv.png"
        private const val CONNECT_TIMEOUT = 30000
        private const val READ_TIMEOUT = 30000
    }

    fun getSourceUrls(): List<String> = synchronized(sourceUrls) {
        sourceUrls.toList()
    }

    fun addSourceUrl(url: String) {
        synchronized(sourceUrls) {
            if (url.isNotBlank() && !sourceUrls.contains(url)) {
                sourceUrls.add(url)
            }
        }
    }

    fun removeSourceUrl(url: String) {
        synchronized(sourceUrls) {
            sourceUrls.remove(url)
        }
    }

    fun setSourceUrls(urls: List<String>) {
        synchronized(sourceUrls) {
            sourceUrls.clear()
            sourceUrls.addAll(urls.filter { it.isNotBlank() })
        }
    }

    fun fetchM3UFile() {
        refreshProviders()
        fetchJob?.cancel()

        val sourceUrlsSnapshot = synchronized(sourceUrls) {
            if (sourceUrls.isEmpty()) {
                _channels.postValue(emptyList())
                _filteredChannels.postValue(emptyList())
                _categories.postValue(emptyList())
                _error.postValue("No source URLs configured")
                _isLoading.postValue(false)
                return
            }
            sourceUrls.toList()
        }

        _isLoading.postValue(true)

        fetchJob = viewModelScope.launch(Dispatchers.IO) {
            val allChannels = mutableListOf<Channel>()
            val errorMessages = StringBuilder()
            var successCount = 0
            val totalSources = sourceUrlsSnapshot.size
            val channelCounts = mutableMapOf<String, Int>()

            for ((index, sourceUrl) in sourceUrlsSnapshot.withIndex()) {
                var connection: HttpURLConnection? = null
                try {
                    Log.d(TAG, "Fetching source ${index + 1}/$totalSources: $sourceUrl")
                    
                    val url = URL(sourceUrl)
                    connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = CONNECT_TIMEOUT
                    connection.readTimeout = READ_TIMEOUT
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("User-Agent", "IPTVmine/1.0 (Android)")
                    connection.setRequestProperty("Accept", "*/*")
                    connection.instanceFollowRedirects = true

                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        val content = BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                            val stringBuilder = StringBuilder()
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                stringBuilder.append(line).append("\n")
                            }
                            stringBuilder.toString()
                        }

                        val channels = parsePlaylistContent(content)
                        allChannels.addAll(channels)
                        channelCounts[sourceUrl] = channels.size
                        successCount++
                        
                        // Update persisted count in SharedPreferences
                        val provider = repository.getProviders().find { it.url == sourceUrl }
                        if (provider != null) {
                            repository.updateProvider(provider.copy(channelCount = channels.size))
                        }
                        
                        Log.d(TAG, "Loaded ${channels.size} channels from source ${index + 1}")
                    } else {
                        errorMessages.append("Source ${index + 1}: HTTP ${connection.responseCode}\n")
                        channelCounts[sourceUrl] = 0
                    }
                } catch (e: java.net.UnknownHostException) {
                    errorMessages.append("Source ${index + 1}: No internet connection or DNS error\n")
                    channelCounts[sourceUrl] = 0
                    Log.e(TAG, "Network error for source ${index + 1}", e)
                } catch (e: java.net.SocketTimeoutException) {
                    errorMessages.append("Source ${index + 1}: Connection timeout\n")
                    channelCounts[sourceUrl] = 0
                    Log.e(TAG, "Timeout for source ${index + 1}", e)
                } catch (e: java.io.IOException) {
                    errorMessages.append("Source ${index + 1}: Network I/O error - ${e.message}\n")
                    channelCounts[sourceUrl] = 0
                    Log.e(TAG, "I/O error for source ${index + 1}", e)
                } catch (e: Exception) {
                    errorMessages.append("Source ${index + 1}: ${e.localizedMessage ?: e.message ?: "Unknown error"}\n")
                    channelCounts[sourceUrl] = 0
                    Log.e(TAG, "Error fetching source ${index + 1}", e)
                } finally {
                    connection?.disconnect()
                }
            }

            withContext(Dispatchers.Main) {
                refreshProviders()
                _providerChannelCounts.value = channelCounts
                if (allChannels.isNotEmpty()) {
                    _channels.value = allChannels
                    _filteredChannels.value = allChannels
                    updateCategories(allChannels)
                    
                    _error.value = if (successCount < totalSources && errorMessages.isNotEmpty()) {
                        "Loaded $successCount/$totalSources sources. Errors:\n$errorMessages"
                    } else null
                    
                    Log.d(TAG, "Total channels: ${allChannels.size}")
                } else {
                    _error.value = if (errorMessages.isNotEmpty()) {
                        "Failed to fetch channels from all sources:\n$errorMessages"
                    } else {
                        "Failed to fetch channels: No data available"
                    }
                }
                _isLoading.value = false
            }
        }
    }

    fun fetchFromSingleSource(sourceUrl: String) {
        fetchJob?.cancel()
        _isLoading.postValue(true)

        fetchJob = viewModelScope.launch(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(sourceUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = CONNECT_TIMEOUT
                connection.readTimeout = READ_TIMEOUT
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "IPTVmine/1.0 (Android)")
                connection.setRequestProperty("Accept", "*/*")
                connection.instanceFollowRedirects = true

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val content = BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                        val stringBuilder = StringBuilder()
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            stringBuilder.append(line).append("\n")
                        }
                        stringBuilder.toString()
                    }

                    val channels = parsePlaylistContent(content)
                    
                    // Update persisted count in SharedPreferences
                    val provider = repository.getProviders().find { it.url == sourceUrl }
                    if (provider != null) {
                        repository.updateProvider(provider.copy(channelCount = channels.size))
                    }

                    withContext(Dispatchers.Main) {
                        refreshProviders()

                        val currentMap = _providerChannelCounts.value.orEmpty().toMutableMap()
                        currentMap[sourceUrl] = channels.size
                        _providerChannelCounts.value = currentMap

                        _channels.value = channels
                        _filteredChannels.value = channels
                        updateCategories(channels)
                        _error.value = null
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        val currentMap = _providerChannelCounts.value.orEmpty().toMutableMap()
                        currentMap[sourceUrl] = 0
                        _providerChannelCounts.value = currentMap

                        _error.value = "Error: HTTP ${connection.responseCode}"
                    }
                }
            } catch (e: java.net.UnknownHostException) {
                withContext(Dispatchers.Main) {
                    val currentMap = _providerChannelCounts.value.orEmpty().toMutableMap()
                    currentMap[sourceUrl] = 0
                    _providerChannelCounts.value = currentMap
                    _error.value = "Network Error: No internet connection or unable to resolve host. Please check your connection."
                }
            } catch (e: java.net.SocketTimeoutException) {
                withContext(Dispatchers.Main) {
                    val currentMap = _providerChannelCounts.value.orEmpty().toMutableMap()
                    currentMap[sourceUrl] = 0
                    _providerChannelCounts.value = currentMap
                    _error.value = "Network Error: Connection timeout. Please check your internet speed."
                }
            } catch (e: java.io.IOException) {
                withContext(Dispatchers.Main) {
                    val currentMap = _providerChannelCounts.value.orEmpty().toMutableMap()
                    currentMap[sourceUrl] = 0
                    _providerChannelCounts.value = currentMap
                    _error.value = "Network Error: ${e.message ?: "Unable to connect to server"}"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val currentMap = _providerChannelCounts.value.orEmpty().toMutableMap()
                    currentMap[sourceUrl] = 0
                    _providerChannelCounts.value = currentMap
                    _error.value = "Error: ${e.localizedMessage ?: e.message ?: "Unknown error occurred"}"
                }
            } finally {
                connection?.disconnect()
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                }
            }
        }
    }

    private fun parseM3UFile(fileText: String): List<Channel> {
        Log.d(TAG, "Starting to parse M3U file, content length: ${fileText.length}")
        
        val lines = fileText.split("\n")
        val channelsList = ArrayList<Channel>(lines.size / 2)
        
        var name: String? = null
        var logoUrl = DEFAULT_LOGO_URL
        var streamUrl: String? = null
        var category: String? = null
        
        var validUrlCount = 0
        var invalidUrlCount = 0

        for (line in lines) {
            when {
                line.startsWith("#EXTINF:") -> {
                    name = extractChannelName(line)
                    logoUrl = extractLogoUrl(line) ?: DEFAULT_LOGO_URL
                    category = extractCategory(line)
                }
                line.trim().isNotEmpty() -> {
                    if (isValidStreamUrl(line)) {
                        streamUrl = line
                        validUrlCount++
                        
                        // Filter out adult content using ContentFilter
                        if (!name.isNullOrEmpty() && !streamUrl.isNullOrEmpty()) {
                            if (!ContentFilter.shouldBlockContent(name, category)) {
                                channelsList.add(
                                    Channel(
                                        name = name,
                                        logoUrl = logoUrl,
                                        streamUrl = streamUrl,
                                        category = category ?: "Uncategorized"
                                    )
                                )
                                Log.d(TAG, "Added channel: $name with URL: ${streamUrl.take(50)}")
                            } else {
                                Log.d(TAG, "Blocked content: $name (Category: $category)")
                            }
                        }
                        
                        name = null
                        logoUrl = DEFAULT_LOGO_URL
                        category = null
                    } else if (line.startsWith("http")) {
                        invalidUrlCount++
                        Log.d(TAG, "Invalid stream URL rejected: ${line.take(50)}")
                    }
                }
            }
        }

        Log.d(TAG, "Parsing complete. Valid URLs: $validUrlCount, Invalid URLs: $invalidUrlCount, Total channels: ${channelsList.size}")
        return channelsList
    }

    private fun parsePlaylistContent(content: String): List<Channel> {
        val trimmed = content.trim()
        
        // Check if it's M3U format
        val isM3U = trimmed.contains("#EXTM3U") || trimmed.contains("#EXTINF")
        
        // Check if it's JSON format (contains matches and starts/contains brace)
        val hasBraces = trimmed.contains("{") || trimmed.contains("[")
        val hasMatches = trimmed.contains("\"matches\"") || trimmed.contains("\"event_category\"") || trimmed.contains("\"channels\"") || trimmed.contains("\"streams\"")
        
        return if (!isM3U && hasBraces && hasMatches) {
            // Extract the clean JSON part (from first brace/bracket to last brace/bracket)
            val firstBrace = trimmed.indexOf('{')
            val firstBracket = trimmed.indexOf('[')
            val jsonStart = when {
                firstBrace != -1 && firstBracket != -1 -> minOf(firstBrace, firstBracket)
                firstBrace != -1 -> firstBrace
                else -> firstBracket
            }
            if (jsonStart != -1) {
                parseJSONFile(trimmed.substring(jsonStart).trim())
            } else {
                parseM3UFile(content)
            }
        } else {
            parseM3UFile(content)
        }
    }

    private fun parseJSONFile(jsonText: String): List<Channel> {
        val channelsList = ArrayList<Channel>()
        try {
            val trimmed = jsonText.trim()
            if (trimmed.startsWith("[")) {
                val array = JSONArray(trimmed)
                parseMatchesArray(array, channelsList)
            } else {
                val jsonObject = JSONObject(trimmed)
                if (jsonObject.has("matches")) {
                    val matchesArray = jsonObject.getJSONArray("matches")
                    parseMatchesArray(matchesArray, channelsList)
                }
            }

            // Enhanced proportional notification logic
            val totalMatches = channelsList.size
            val newMatchesThreshold = (totalMatches * 0.2).toInt() // 20% threshold
            
            if (lastMatchCount > 0) {
                val newMatchesCount = totalMatches - lastMatchCount
                
                // Trigger notification based on proportion of new matches
                if (newMatchesCount > 0 && newMatchesCount >= newMatchesThreshold) {
                    // Show notification for the newest matches
                    val newMatchesToNotify = channelsList.takeLast(newMatchesCount)
                    newMatchesToNotify.forEach { channel ->
                        showNewMatchNotification(channel)
                    }
                }
            }
            
            lastMatchCount = totalMatches
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JSON content", e)
        }
        return channelsList
    }

    private fun parseMatchesArray(matchesArray: JSONArray, channelsList: ArrayList<Channel>) {
        for (i in 0 until matchesArray.length()) {
            val match = matchesArray.getJSONObject(i)
            
            val team1 = match.optString("team_1", "")
            val team2 = match.optString("team_2", "")
            val matchName = match.optString("match_name", "")
            val eventName = match.optString("event_name", "")
            val status = match.optString("status", "UNKNOWN")
            val startTime = match.optString("startTime", "")
            
            val name = when {
                matchName.isNotEmpty() -> matchName
                eventName.isNotEmpty() -> eventName
                team1.isNotEmpty() && team2.isNotEmpty() -> "$team1 vs $team2"
                match.has("title") && !match.isNull("title") -> match.getString("title")
                else -> "Live Event"
            }
            
            val logoUrl = if (match.has("src") && !match.isNull("src")) {
                match.getString("src")
            } else {
                DEFAULT_LOGO_URL
            }
            
            val streamUrl = when {
                match.has("adfree_url") && !match.isNull("adfree_url") -> match.getString("adfree_url")
                match.has("dai_url") && !match.isNull("dai_url") -> match.getString("dai_url")
                else -> ""
            }
            
            val category = if (match.has("event_category") && !match.isNull("event_category")) {
                match.getString("event_category")
            } else {
                "Live Events"
            }
            
            val isValidStream = streamUrl.isEmpty() || isValidUrl(streamUrl)
            if (name.isNotEmpty() && isValidStream) {
                if (!ContentFilter.shouldBlockContent(name, category)) {
                    channelsList.add(
                        Channel(
                            name = name,
                            logoUrl = logoUrl,
                            streamUrl = streamUrl,
                            category = category,
                            team1 = team1,
                            team2 = team2,
                            status = status,
                            startTime = startTime
                        )
                    )
                    Log.d(TAG, "Added event channel: $name with URL: ${streamUrl.take(50)}")
                } else {
                    Log.d(TAG, "Blocked event content: $name (Category: $category)")
                }
            }
        }
    }

    private fun extractChannelName(line: String): String? {
        val commaIndex = line.lastIndexOf(",")
        return if (commaIndex != -1 && commaIndex < line.length - 1) {
            line.substring(commaIndex + 1).trim()
        } else null
    }

    private fun extractLogoUrl(line: String): String? {
        val parts = line.split("\"")
        return parts.firstOrNull { isValidUrl(it) }
    }

    private fun extractCategory(line: String): String? {
        val lowerLine = line.lowercase()
        
        var index = lowerLine.indexOf("group-title=")
        if (index != -1) {
            val startQuote = line.indexOf('"', index)
            if (startQuote != -1) {
                val endQuote = line.indexOf('"', startQuote + 1)
                if (endQuote != -1) {
                    val cat = line.substring(startQuote + 1, endQuote).trim()
                    return if (cat.isEmpty()) "Uncategorized" else cat
                }
            }
        }

        index = lowerLine.indexOf("tvg-group=")
        if (index != -1) {
            val startQuote = line.indexOf('"', index)
            if (startQuote != -1) {
                val endQuote = line.indexOf('"', startQuote + 1)
                if (endQuote != -1) {
                    val cat = line.substring(startQuote + 1, endQuote).trim()
                    return if (cat.isEmpty()) "Uncategorized" else cat
                }
            }
        }

        return "Uncategorized"
    }

    private fun isValidUrl(url: String?): Boolean {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"))
    }

    private fun isValidStreamUrl(url: String): Boolean {
        if (!isValidUrl(url)) return false

        return url.contains(".m3u8") || url.contains(".mp4") || url.contains(".avi") || 
               url.contains(".mkv") || url.contains(".ts") || url.contains(".mpd") ||
               url.contains("stream") || url.contains("live") ||
               (url.startsWith("http") && url.split(":").size >= 3)
    }

    fun filterChannels(query: String) {
        val allChannels = _channels.value ?: emptyList()
        val lowerCaseQuery = query.lowercase()
        val filtered = ArrayList<Channel>(minOf(50, allChannels.size))
        
        for (channel in allChannels) {
            if (channel.name.lowercase().contains(lowerCaseQuery)) {
                filtered.add(channel)
            }
        }
        _filteredChannels.postValue(filtered)
    }

    fun filterChannelsByCategory(category: String?) {
        val allChannels = _channels.value ?: emptyList()
        
        _filteredChannels.postValue(
            if (category.isNullOrEmpty() || category == "All") {
                ArrayList(allChannels)
            } else {
                val filtered = ArrayList<Channel>()
                for (channel in allChannels) {
                    if (category == channel.category) {
                        filtered.add(channel)
                    }
                }
                filtered
            }
        )
    }

    fun filterChannelsByQueryAndCategory(query: String?, category: String?) {
        val allChannels = _channels.value ?: emptyList()
        val lowerCaseQuery = query?.lowercase() ?: ""
        val filtered = ArrayList<Channel>()

        for (channel in allChannels) {
            val categoryMatch = category.isNullOrEmpty() || category == "All" || category == channel.category
            val queryMatch = lowerCaseQuery.isEmpty() || channel.name.lowercase().contains(lowerCaseQuery)

            if (categoryMatch && queryMatch) {
                filtered.add(channel)
            }
        }
        _filteredChannels.postValue(filtered)
    }

    private fun updateCategories(channelList: List<Channel>) {
        if (channelList.isEmpty()) {
            _categories.postValue(emptyList())
            return
        }

        val uniqueCategories = HashSet<String>()
        for (channel in channelList) {
            val cat = channel.category
            // Filter out adult categories using ContentFilter
            if (!cat.isNullOrEmpty() && !ContentFilter.isBlockedCategory(cat)) {
                uniqueCategories.add(cat)
            }
        }

        val categoryList = ArrayList(uniqueCategories).apply {
            sort()
            add(0, "All")
        }
        _categories.postValue(categoryList)
    }

    override fun onCleared() {
        super.onCleared()
        fetchJob?.cancel()
    }
}
