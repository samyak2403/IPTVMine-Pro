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
import com.samyak.iptvminepro.provider.ChannelsProvider.Companion.DEFAULT_LOGO_URL

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

    // Fetch cooldown: prevent repeated network requests
    private var lastFetchTimestamp = 0L
    private var lastFetchedUrlsHash = 0

    /**
     * Per-URL failure backoff. Prevents repeated connection attempts (and wasted mobile data)
     * against sources that are consistently failing. Uses exponential backoff so a broken
     * provider is retried at most once per backoff window instead of on every screen entry.
     */
    private data class FailureState(val failureCount: Int, val nextRetryAt: Long)
    private val sourceFailureStates = java.util.concurrent.ConcurrentHashMap<String, FailureState>()

    private fun isInBackoff(url: String, now: Long): Boolean {
        val state = sourceFailureStates[url] ?: return false
        return now < state.nextRetryAt
    }

    private fun backoffRemainingSeconds(url: String, now: Long): Long {
        val state = sourceFailureStates[url] ?: return 0L
        return ((state.nextRetryAt - now) / 1000L).coerceAtLeast(0L)
    }

    private fun recordSourceSuccess(url: String) {
        sourceFailureStates.remove(url)
    }

    private fun recordSourceFailure(url: String) {
        val newCount = (sourceFailureStates[url]?.failureCount ?: 0) + 1
        // Exponential backoff: BASE * 2^(n-1), capped at MAX_BACKOFF_MS
        val delay = (BASE_BACKOFF_MS shl minOf(newCount - 1, 6)).coerceAtMost(MAX_BACKOFF_MS)
        sourceFailureStates[url] = FailureState(newCount, System.currentTimeMillis() + delay)
        Log.w(TAG, "Source failed ($newCount consecutive) — backing off ${delay / 1000}s: $url")
    }

    /**
     * Decide whether a response Content-Type is a real media/binary file that should be
     * rejected (to avoid downloading videos into memory). M3U/M3U8 playlists are frequently
     * served as audio/x-mpegurl, application/x-mpegurl, or application/octet-stream, so those
     * must be allowed. Only genuine video/image/audio-media types are blocked.
     */
    private fun isBlockedContentType(rawContentType: String?): Boolean {
        val ct = rawContentType?.lowercase()?.substringBefore(";")?.trim().orEmpty()
        if (ct.isEmpty()) return false                       // unknown — allow, size caps protect us
        if (PLAYLIST_CONTENT_TYPES.contains(ct)) return false // explicit playlist types
        if (ct.contains("mpegurl")) return false             // any *mpegurl* variant is a playlist
        if (ct.startsWith("text/")) return false             // text/plain, text/html playlists
        if (ct == "application/json" || ct.endsWith("+json")) return false
        if (ct == "application/xml" || ct.endsWith("+xml")) return false
        // Block genuine media files that would waste data / memory
        return ct.startsWith("video/") || ct.startsWith("image/") || ct.startsWith("audio/")
    }

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
        private const val DEFAULT_LOGO_URL = ""
        private const val CONNECT_TIMEOUT = 30000
        private const val READ_TIMEOUT = 30000
        private const val FETCH_COOLDOWN_MS = 5 * 60 * 1000L // 5 minutes
        private const val MAX_RESPONSE_SIZE = 10 * 1024 * 1024L // 10 MB
        // Per-source exponential backoff bounds (used to stop hammering failing URLs)
        private const val BASE_BACKOFF_MS = 60 * 1000L // 1 minute
        private const val MAX_BACKOFF_MS = 60 * 60 * 1000L // 1 hour cap
        private val VIDEO_EXTENSIONS = setOf(
            ".mp4", ".mkv", ".avi", ".mov", ".wmv", ".flv", ".webm",
            ".mpg", ".mpeg", ".3gp", ".ogv", ".rm", ".rmvb"
        )
        // Content types that ARE valid playlists and must never be rejected.
        // Note: M3U/M3U8 files are commonly served as audio/x-mpegurl or application/x-mpegurl.
        private val PLAYLIST_CONTENT_TYPES = setOf(
            "audio/x-mpegurl", "audio/mpegurl",
            "application/x-mpegurl", "application/mpegurl",
            "application/vnd.apple.mpegurl", "application/vnd.apple.mpegurl.audio",
            "application/octet-stream"
        )

        // Generic JSON field-name candidates used to parse arbitrary IPTV JSON playlists.
        private val JSON_URL_KEYS = listOf(
            "url", "link", "stream", "stream_url", "streamurl", "streamUrl",
            "source_url", "adfree_url", "dai_url", "file", "playback_url",
            "manifest", "hls", "m3u8", "playbackUrl", "streamLink"
        )
        private val JSON_NAME_KEYS = listOf(
            "name", "title", "channel", "channel_name", "channelName",
            "tvg-name", "tvg_name", "tvgName", "display_name"
        )
        private val JSON_LOGO_KEYS = listOf(
            "logo", "logo_url", "logoUrl", "tvg-logo", "tvg_logo", "tvgLogo",
            "icon", "image", "img", "thumbnail", "poster", "src"
        )
        private val JSON_CATEGORY_KEYS = listOf(
            "group", "group-title", "group_title", "groupTitle", "category",
            "genre", "event_category", "categoryName", "type"
        )
        // Container/wrapper keys that should NOT be used as a category name.
        private val JSON_CONTAINER_KEYS = setOf(
            "channels", "streams", "data", "items", "list", "results", "result",
            "playlist", "playlists", "stations", "entries", "categories", "content"
        )
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

    /**
     * Load channels only if needed — skips fetching if data is cached and cooldown hasn't expired.
     * Use this for automatic/screen-entry fetches. Use [fetchM3UFile] for explicit user-triggered refreshes.
     */
    fun loadIfNeeded() {
        val now = System.currentTimeMillis()
        val currentUrlsHash = synchronized(sourceUrls) { sourceUrls.hashCode() }
        val withinCooldown = (now - lastFetchTimestamp) < FETCH_COOLDOWN_MS
        val urlsUnchanged = currentUrlsHash == lastFetchedUrlsHash
        val fetchAttempted = lastFetchTimestamp != 0L

        // Skip if we already attempted a fetch recently for the same set of URLs — regardless of
        // whether it succeeded. This is critical: if every source is failing, _channels stays empty,
        // and re-fetching on every screen entry would hammer the failing URLs and burn mobile data.
        if (fetchAttempted && withinCooldown && urlsUnchanged) {
            Log.d(TAG, "loadIfNeeded: skipping fetch — recent attempt, cooldown active, URLs unchanged")
            return
        }
        fetchM3UFile()
    }

    fun fetchM3UFile(forceRefresh: Boolean = false) {
        refreshProviders()
        fetchJob?.cancel()

        // Explicit user actions (adding/editing/toggling/deleting a provider, manual refresh)
        // clear any accumulated backoff so the affected sources get a fresh attempt.
        if (forceRefresh) {
            sourceFailureStates.clear()
        }

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

        lastFetchTimestamp = System.currentTimeMillis()
        lastFetchedUrlsHash = sourceUrlsSnapshot.hashCode()

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

                    // Skip sources currently in failure backoff to avoid repeated connection
                    // attempts and wasted data. Preserve their last known channel count.
                    val nowMs = System.currentTimeMillis()
                    if (isInBackoff(sourceUrl, nowMs)) {
                        val failures = sourceFailureStates[sourceUrl]?.failureCount ?: 0
                        val waitSec = backoffRemainingSeconds(sourceUrl, nowMs)
                        errorMessages.append("Source ${index + 1}: Temporarily skipped (failed ${failures}×, retry in ${waitSec}s).\n")
                        channelCounts[sourceUrl] = repository.getProviders().find { it.url == sourceUrl }?.channelCount ?: 0
                        Log.d(TAG, "Skipping source ${index + 1} due to backoff (${waitSec}s left): $sourceUrl")
                        continue
                    }

                    // Handle direct video URLs by creating a single virtual channel
                    if (isDirectVideoUrl(sourceUrl)) {
                        val provider = repository.getProviders().find { it.url == sourceUrl }
                        val name = provider?.title ?: sourceUrl.substringAfterLast("/").substringBeforeLast(".")
                        val channels = listOf(
                            Channel(
                                name = name,
                                logoUrl = DEFAULT_LOGO_URL,
                                streamUrl = sourceUrl,
                                category = "Direct Videos"
                            )
                        )
                        allChannels.addAll(channels)
                        channelCounts[sourceUrl] = channels.size
                        successCount++
                        recordSourceSuccess(sourceUrl)
                        if (provider != null) {
                            repository.updateProvider(provider.copy(channelCount = channels.size))
                        }
                        continue
                    }

                    val url = URL(sourceUrl)
                    connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = CONNECT_TIMEOUT
                    connection.readTimeout = READ_TIMEOUT
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("User-Agent", "IPTVmine/1.0 (Android)")
                    connection.setRequestProperty("Accept", "*/*")
                    connection.instanceFollowRedirects = true

                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        // Validate Content-Type: reject genuine video/audio/image media (but allow
                        // M3U playlists served as audio/x-mpegurl, application/x-mpegurl, etc.)
                        val contentType = connection.contentType ?: ""
                        if (isBlockedContentType(contentType)) {
                            errorMessages.append("Source ${index + 1}: Rejected — server returned '$contentType' (not a playlist).\n")
                            channelCounts[sourceUrl] = 0
                            recordSourceFailure(sourceUrl)
                            Log.w(TAG, "Rejected content type '$contentType' for source ${index + 1}")
                            continue
                        }

                        // Validate Content-Length: reject responses larger than 10MB
                        val contentLength = connection.contentLengthLong
                        if (contentLength > MAX_RESPONSE_SIZE) {
                            errorMessages.append("Source ${index + 1}: Rejected — response too large (${contentLength / 1024 / 1024}MB). Max allowed: ${MAX_RESPONSE_SIZE / 1024 / 1024}MB.\n")
                            channelCounts[sourceUrl] = 0
                            recordSourceFailure(sourceUrl)
                            Log.w(TAG, "Rejected oversized response (${contentLength} bytes) for source ${index + 1}")
                            continue
                        }

                        val content = BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                            val stringBuilder = StringBuilder()
                            var line: String?
                            var totalRead = 0L
                            while (reader.readLine().also { line = it } != null) {
                                totalRead += (line?.length ?: 0) + 1
                                if (totalRead > MAX_RESPONSE_SIZE) {
                                    Log.w(TAG, "Aborting read — response exceeded $MAX_RESPONSE_SIZE bytes for source ${index + 1}")
                                    break
                                }
                                stringBuilder.append(line).append("\n")
                            }
                            stringBuilder.toString()
                        }

                        val provider = repository.getProviders().find { it.url == sourceUrl }
                        val channels = parsePlaylistContent(content, sourceUrl, provider?.title ?: "")
                        allChannels.addAll(channels)
                        channelCounts[sourceUrl] = channels.size
                        successCount++
                        recordSourceSuccess(sourceUrl)
                        
                        // Update persisted count in SharedPreferences
                        if (provider != null) {
                            repository.updateProvider(provider.copy(channelCount = channels.size))
                        }
                        
                        Log.d(TAG, "Loaded ${channels.size} channels from source ${index + 1}")
                    } else {
                        errorMessages.append("Source ${index + 1}: HTTP ${connection.responseCode}\n")
                        channelCounts[sourceUrl] = 0
                        recordSourceFailure(sourceUrl)
                    }
                } catch (e: java.net.UnknownHostException) {
                    errorMessages.append("Source ${index + 1}: No internet connection or DNS error\n")
                    channelCounts[sourceUrl] = 0
                    recordSourceFailure(sourceUrl)
                    Log.e(TAG, "Network error for source ${index + 1}", e)
                } catch (e: java.net.SocketTimeoutException) {
                    errorMessages.append("Source ${index + 1}: Connection timeout\n")
                    channelCounts[sourceUrl] = 0
                    recordSourceFailure(sourceUrl)
                    Log.e(TAG, "Timeout for source ${index + 1}", e)
                } catch (e: java.io.IOException) {
                    errorMessages.append("Source ${index + 1}: Network I/O error - ${e.message}\n")
                    channelCounts[sourceUrl] = 0
                    recordSourceFailure(sourceUrl)
                    Log.e(TAG, "I/O error for source ${index + 1}", e)
                } catch (e: Exception) {
                    errorMessages.append("Source ${index + 1}: ${e.localizedMessage ?: e.message ?: "Unknown error"}\n")
                    channelCounts[sourceUrl] = 0
                    recordSourceFailure(sourceUrl)
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
        // Explicit single-source action: clear this URL's backoff so it gets a fresh attempt.
        sourceFailureStates.remove(sourceUrl)

        fetchJob = viewModelScope.launch(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                if (isDirectVideoUrl(sourceUrl)) {
                    val provider = repository.getProviders().find { it.url == sourceUrl }
                    val name = provider?.title ?: sourceUrl.substringAfterLast("/").substringBeforeLast(".")
                    val channels = listOf(
                        Channel(
                            name = name,
                            logoUrl = DEFAULT_LOGO_URL,
                            streamUrl = sourceUrl,
                            category = "Direct Videos"
                        )
                    )
                    recordSourceSuccess(sourceUrl)
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
                        _isLoading.value = false
                    }
                    return@launch
                }

                val url = URL(sourceUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = CONNECT_TIMEOUT
                connection.readTimeout = READ_TIMEOUT
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "IPTVmine/1.0 (Android)")
                connection.setRequestProperty("Accept", "*/*")
                connection.instanceFollowRedirects = true

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    // Validate Content-Type: reject genuine video/audio/image media (but allow
                    // M3U playlists served as audio/x-mpegurl, application/x-mpegurl, etc.)
                    val contentType = connection.contentType ?: ""
                    if (isBlockedContentType(contentType)) {
                        withContext(Dispatchers.Main) {
                            _error.value = "Error: Server returned '$contentType' — this is not a playlist file."
                            _isLoading.value = false
                        }
                        return@launch
                    }

                    // Validate Content-Length: reject responses larger than 10MB
                    val contentLength = connection.contentLengthLong
                    if (contentLength > MAX_RESPONSE_SIZE) {
                        withContext(Dispatchers.Main) {
                            _error.value = "Error: Response too large (${contentLength / 1024 / 1024}MB). This doesn't appear to be a playlist."
                            _isLoading.value = false
                        }
                        return@launch
                    }

                    val content = BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                        val stringBuilder = StringBuilder()
                        var line: String?
                        var totalRead = 0L
                        while (reader.readLine().also { line = it } != null) {
                            totalRead += (line?.length ?: 0) + 1
                            if (totalRead > MAX_RESPONSE_SIZE) {
                                Log.w(TAG, "Aborting read — response exceeded $MAX_RESPONSE_SIZE bytes")
                                break
                            }
                            stringBuilder.append(line).append("\n")
                        }
                        stringBuilder.toString()
                    }

                    val provider = repository.getProviders().find { it.url == sourceUrl }
                    val channels = parsePlaylistContent(content, sourceUrl, provider?.title ?: "")
                    recordSourceSuccess(sourceUrl)

                    // Update persisted count in SharedPreferences
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
                    recordSourceFailure(sourceUrl)
                    withContext(Dispatchers.Main) {
                        val currentMap = _providerChannelCounts.value.orEmpty().toMutableMap()
                        currentMap[sourceUrl] = 0
                        _providerChannelCounts.value = currentMap

                        _error.value = "Error: HTTP ${connection.responseCode}"
                    }
                }
            } catch (e: java.net.UnknownHostException) {
                recordSourceFailure(sourceUrl)
                withContext(Dispatchers.Main) {
                    val currentMap = _providerChannelCounts.value.orEmpty().toMutableMap()
                    currentMap[sourceUrl] = 0
                    _providerChannelCounts.value = currentMap
                    _error.value = "Network Error: No internet connection or unable to resolve host. Please check your connection."
                }
            } catch (e: java.net.SocketTimeoutException) {
                recordSourceFailure(sourceUrl)
                withContext(Dispatchers.Main) {
                    val currentMap = _providerChannelCounts.value.orEmpty().toMutableMap()
                    currentMap[sourceUrl] = 0
                    _providerChannelCounts.value = currentMap
                    _error.value = "Network Error: Connection timeout. Please check your internet speed."
                }
            } catch (e: java.io.IOException) {
                recordSourceFailure(sourceUrl)
                withContext(Dispatchers.Main) {
                    val currentMap = _providerChannelCounts.value.orEmpty().toMutableMap()
                    currentMap[sourceUrl] = 0
                    _providerChannelCounts.value = currentMap
                    _error.value = "Network Error: ${e.message ?: "Unable to connect to server"}"
                }
            } catch (e: Exception) {
                recordSourceFailure(sourceUrl)
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

    private fun parsePlaylistContent(content: String, sourceUrl: String = "", fallbackName: String = ""): List<Channel> {
        val trimmed = content.trim()
        
        // Check if it's M3U format
        val isM3U = trimmed.contains("#EXTM3U") || trimmed.contains("#EXTINF")

        // Detect a direct HLS stream (an actual playable video), NOT an IPTV channel list.
        // HLS master playlists (#EXT-X-STREAM-INF) and media playlists (#EXT-X-TARGETDURATION,
        // #EXT-X-MEDIA-SEQUENCE, #EXT-X-PLAYLIST-TYPE) should play as a single video — exactly
        // like a direct .mp4 URL — by pointing the player straight at the source .m3u8 URL.
        val isHlsStream = trimmed.contains("#EXT-X-STREAM-INF") ||
                          trimmed.contains("#EXT-X-TARGETDURATION") ||
                          trimmed.contains("#EXT-X-MEDIA-SEQUENCE") ||
                          trimmed.contains("#EXT-X-PLAYLIST-TYPE")

        // Check if it's an HLS master playlist (contains stream variant info but no EXTINF channel list)
        val isHlsMaster = trimmed.contains("#EXT-X-STREAM-INF") && !trimmed.contains("#EXTINF")

        // Anything that isn't M3U/HLS but looks like JSON (starts with { or [) is parsed generically.
        val looksLikeJson = !isM3U && (trimmed.startsWith("{") || trimmed.startsWith("["))
        val hasEmbeddedJson = !isM3U && (trimmed.contains("{") || trimmed.contains("["))

        return when {
            isHlsStream && sourceUrl.isNotBlank() -> {
                // Treat the whole .m3u8 as one direct video — the player handles HLS natively.
                val name = fallbackName.ifBlank {
                    sourceUrl.substringAfterLast("/").substringBeforeLast(".").ifBlank { "Stream" }
                }
                listOf(
                    Channel(
                        name = name,
                        logoUrl = DEFAULT_LOGO_URL,
                        streamUrl = sourceUrl,
                        category = "Direct Videos"
                    )
                )
            }
            isHlsMaster -> {
                // HLS master playlist without a known source URL: parse stream variants
                parseHlsMasterPlaylist(content)
            }
            looksLikeJson || hasEmbeddedJson -> {
                // Extract the clean JSON part (from first brace/bracket onward)
                val firstBrace = trimmed.indexOf('{')
                val firstBracket = trimmed.indexOf('[')
                val jsonStart = when {
                    firstBrace != -1 && firstBracket != -1 -> minOf(firstBrace, firstBracket)
                    firstBrace != -1 -> firstBrace
                    else -> firstBracket
                }
                if (jsonStart != -1) {
                    val channels = parseJSONFile(trimmed.substring(jsonStart).trim())
                    // Fall back to M3U parsing if JSON produced nothing (e.g. malformed / not really JSON)
                    if (channels.isNotEmpty()) channels else parseM3UFile(content)
                } else {
                    parseM3UFile(content)
                }
            }
            else -> parseM3UFile(content)
        }
    }

    private fun parseJSONFile(jsonText: String): List<Channel> {
        val channelsList = ArrayList<Channel>()
        try {
            val trimmed = jsonText.trim()
            val root: Any = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed)

            // Detect the legacy sports "matches" format so its team/notification behavior is preserved.
            val isMatchesFormat = when (root) {
                is JSONObject -> root.has("matches")
                is JSONArray -> root.length() > 0 && root.optJSONObject(0)?.let { looksLikeMatch(it) } == true
                else -> false
            }

            if (isMatchesFormat) {
                val matchesArray = if (root is JSONArray) root else (root as JSONObject).getJSONArray("matches")
                parseMatchesArray(matchesArray, channelsList)

                // Enhanced proportional notification logic (matches/events only)
                val totalMatches = channelsList.size
                val newMatchesThreshold = (totalMatches * 0.2).toInt() // 20% threshold
                if (lastMatchCount > 0) {
                    val newMatchesCount = totalMatches - lastMatchCount
                    if (newMatchesCount > 0 && newMatchesCount >= newMatchesThreshold) {
                        channelsList.takeLast(newMatchesCount).forEach { showNewMatchNotification(it) }
                    }
                }
                lastMatchCount = totalMatches
            } else {
                // Generic recursive parser — handles arbitrary IPTV JSON shapes:
                // nested category maps, flat arrays, channels/streams/data wrappers, etc.
                extractChannelsFromJson(root, null, channelsList)
                Log.d(TAG, "Generic JSON parse produced ${channelsList.size} channels")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JSON content", e)
        }
        return channelsList
    }

    /** Heuristic to detect the sports "match" object shape. */
    private fun looksLikeMatch(obj: JSONObject): Boolean {
        return obj.has("team_1") || obj.has("team_2") || obj.has("adfree_url") ||
                obj.has("dai_url") || obj.has("event_name") || obj.has("match_name") ||
                obj.has("event_category")
    }

    /**
     * Recursively walk arbitrary JSON and extract channels. An object is treated as a channel
     * when it contains a valid http(s) URL under any known URL key; otherwise we recurse into
     * its arrays/objects. When recursing through a category map (e.g. {"General": [...]}), the
     * map key is inherited as the category unless the channel object specifies its own group.
     */
    private fun extractChannelsFromJson(element: Any?, inheritedCategory: String?, out: ArrayList<Channel>) {
        when (element) {
            is JSONObject -> {
                val streamUrl = findFirstValidUrl(element)
                if (streamUrl != null) {
                    val name = findFirstString(element, JSON_NAME_KEYS) ?: "Unknown Channel"
                    val logo = findFirstString(element, JSON_LOGO_KEYS) ?: DEFAULT_LOGO_URL
                    val category = findFirstString(element, JSON_CATEGORY_KEYS)
                        ?: inheritedCategory ?: "Uncategorized"
                    if (!ContentFilter.shouldBlockContent(name, category)) {
                        out.add(Channel(name = name, logoUrl = logo, streamUrl = streamUrl, category = category))
                    }
                } else {
                    val keys = element.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = element.get(key)
                        val isContainer = value is JSONArray || value is JSONObject
                        val childCategory = if (isContainer && !JSON_CONTAINER_KEYS.contains(key.lowercase())) {
                            key
                        } else {
                            inheritedCategory
                        }
                        extractChannelsFromJson(value, childCategory, out)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until element.length()) {
                    extractChannelsFromJson(element.opt(i), inheritedCategory, out)
                }
            }
        }
    }

    /** Return the first valid http(s) URL found under any known URL key, or null. */
    private fun findFirstValidUrl(obj: JSONObject): String? {
        for (key in JSON_URL_KEYS) {
            if (obj.has(key) && !obj.isNull(key)) {
                val value = obj.optString(key, "").trim()
                if (isValidUrl(value)) return value
            }
        }
        return null
    }

    /** Return the first non-empty string value found under the given candidate keys, or null. */
    private fun findFirstString(obj: JSONObject, keys: List<String>): String? {
        for (key in keys) {
            if (obj.has(key) && !obj.isNull(key)) {
                val value = obj.optString(key, "").trim()
                if (value.isNotEmpty()) return value
            }
        }
        return null
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

    /**
     * Check if a URL points directly to a video file (not a playlist).
     * These should be rejected as provider URLs since downloading them
     * into memory causes OOM crashes and high data consumption.
     */
    private fun isDirectVideoUrl(url: String): Boolean {
        val lowerUrl = url.lowercase().split("?").first().split("#").first()
        return VIDEO_EXTENSIONS.any { lowerUrl.endsWith(it) }
    }

    /**
     * Parse an HLS master playlist that contains #EXT-X-STREAM-INF entries.
     * Extracts stream variant URLs as individual channels.
     */
    private fun parseHlsMasterPlaylist(content: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        val lines = content.split("\n")
        var streamInfo: String? = null

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("#EXT-X-STREAM-INF:") -> {
                    streamInfo = trimmed
                }
                streamInfo != null && trimmed.isNotEmpty() && !trimmed.startsWith("#") -> {
                    // Extract bandwidth and resolution from stream info
                    val bandwidth = Regex("BANDWIDTH=(\\d+)").find(streamInfo)?.groupValues?.get(1)?.toLongOrNull()
                    val resolution = Regex("RESOLUTION=(\\S+)").find(streamInfo)?.groupValues?.get(1) ?: "Unknown"
                    val bandwidthLabel = if (bandwidth != null) "${bandwidth / 1000}kbps" else "Unknown"
                    val name = "Stream $resolution ($bandwidthLabel)"

                    val streamUrl = if (isValidUrl(trimmed)) trimmed else ""
                    if (streamUrl.isNotEmpty()) {
                        channels.add(
                            Channel(
                                name = name,
                                logoUrl = DEFAULT_LOGO_URL,
                                streamUrl = streamUrl,
                                category = "HLS Streams"
                            )
                        )
                    }
                    streamInfo = null
                }
            }
        }

        Log.d(TAG, "Parsed HLS master playlist: ${channels.size} stream variants")
        return channels
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
