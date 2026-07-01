package com.samyak.television.data

import android.util.Log
import com.samyak.television.model.Channel
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.StringReader
import java.util.concurrent.TimeUnit

object ChannelsLoader {
    private const val TAG = "ChannelsLoader"
    private const val DEFAULT_LOGO_URL = ""
    private const val MAX_RESPONSE_SIZE = 10L * 1024 * 1024 // 10 MB

    private val VIDEO_EXTENSIONS = setOf(
        ".mp4", ".mkv", ".avi", ".mov", ".wmv", ".flv", ".webm",
        ".mpg", ".mpeg", ".3gp", ".ogv", ".rm", ".rmvb"
    )
    private val BLOCKED_CONTENT_TYPES = setOf(
        "video/", "audio/", "image/", "application/octet-stream"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchChannels(url: String, providerTitle: String? = null): List<Channel> {
        return try {
            // Pre-flight check: handle direct video file URLs
            val lowerUrl = url.lowercase().split("?").first().split("#").first()
            if (VIDEO_EXTENSIONS.any { lowerUrl.endsWith(it) }) {
                val name = providerTitle ?: lowerUrl.substringAfterLast("/").substringBeforeLast(".")
                return listOf(
                    Channel(
                        name = name,
                        logoUrl = DEFAULT_LOGO_URL,
                        streamUrl = url,
                        category = "Direct Videos"
                    )
                )
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "IPTVmine/1.0 (Android TV)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Unsuccessful response fetching channels: ${response.code}")
                    return emptyList()
                }

                // Validate Content-Type: reject video/audio/image responses
                val contentType = response.header("Content-Type")?.lowercase() ?: ""
                if (BLOCKED_CONTENT_TYPES.any { contentType.startsWith(it) }) {
                    Log.w(TAG, "Rejected content type '$contentType' for URL: $url")
                    return emptyList()
                }

                // Validate Content-Length: reject responses larger than 10MB
                val contentLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
                if (contentLength > MAX_RESPONSE_SIZE) {
                    Log.w(TAG, "Rejected oversized response (${contentLength} bytes) for URL: $url")
                    return emptyList()
                }

                val body = response.body?.string() ?: return emptyList()
                parseContent(body)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching channels from $url", e)
            emptyList()
        }
    }

    private fun parseContent(content: String): List<Channel> {
        val trimmed = content.trim()
        val isHlsMaster = trimmed.contains("#EXT-X-STREAM-INF") && !trimmed.contains("#EXTINF")
        return if (isHlsMaster) {
            parseHlsMasterPlaylist(content)
        } else {
            parseM3U(content)
        }
    }

    private fun parseM3U(content: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        try {
            val reader = BufferedReader(StringReader(content))
            var line: String? = reader.readLine()

            var name: String? = null
            var logoUrl: String = DEFAULT_LOGO_URL
            var category = "Uncategorized"

            while (line != null) {
                val trimmed = line.trim()
                if (trimmed.startsWith("#EXTINF:")) {
                    name = extractChannelName(trimmed)
                    logoUrl = extractLogoUrl(trimmed) ?: DEFAULT_LOGO_URL
                    category = extractCategory(trimmed) ?: "Uncategorized"
                } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    if (isValidStreamUrl(trimmed)) {
                        val finalName = name ?: "Unknown Channel"
                        channels.add(
                            Channel(
                                name = finalName,
                                logoUrl = logoUrl,
                                streamUrl = trimmed,
                                category = category
                            )
                        )
                    }
                    // Reset variables for next channel
                    name = null
                    logoUrl = DEFAULT_LOGO_URL
                    category = "Uncategorized"
                }
                line = reader.readLine()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing M3U", e)
        }
        return channels
    }

    private fun extractChannelName(line: String): String? {
        val commaIndex = line.lastIndexOf(",")
        return if (commaIndex != -1 && commaIndex < line.length - 1) {
            line.substring(commaIndex + 1).trim()
        } else null
    }

    private fun extractLogoUrl(line: String): String? {
        val logoTag = "tvg-logo="
        val index = line.indexOf(logoTag)
        if (index != -1) {
            val startQuote = line.indexOf('"', index + logoTag.length)
            if (startQuote != -1) {
                val endQuote = line.indexOf('"', startQuote + 1)
                if (endQuote != -1) {
                    return line.substring(startQuote + 1, endQuote).trim()
                }
            }
        }
        return null
    }

    private fun extractCategory(line: String): String? {
        val groupTag = "group-title="
        var index = line.indexOf(groupTag)
        if (index != -1) {
            val startQuote = line.indexOf('"', index + groupTag.length)
            if (startQuote != -1) {
                val endQuote = line.indexOf('"', startQuote + 1)
                if (endQuote != -1) {
                    return line.substring(startQuote + 1, endQuote).trim()
                }
            }
        }
        return null
    }

    private fun isValidStreamUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }

    /**
     * Parse an HLS master playlist that contains #EXT-X-STREAM-INF entries.
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
                    val bandwidth = Regex("BANDWIDTH=(\\d+)").find(streamInfo)?.groupValues?.get(1)?.toLongOrNull()
                    val resolution = Regex("RESOLUTION=(\\S+)").find(streamInfo)?.groupValues?.get(1) ?: "Unknown"
                    val bandwidthLabel = if (bandwidth != null) "${bandwidth / 1000}kbps" else "Unknown"
                    val name = "Stream $resolution ($bandwidthLabel)"

                    if (isValidStreamUrl(trimmed)) {
                        channels.add(
                            Channel(
                                name = name,
                                logoUrl = DEFAULT_LOGO_URL,
                                streamUrl = trimmed,
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
}
