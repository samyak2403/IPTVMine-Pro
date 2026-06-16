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
    private const val DEFAULT_LOGO_URL = "https://cdn-icons-png.flaticon.com/512/854/854878.png"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchChannels(url: String): List<Channel> {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "IPTVmine/1.0 (Android TV)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Unsuccessful response fetching channels: ${response.code}")
                    return emptyList()
                }

                val body = response.body?.string() ?: return emptyList()
                parseM3U(body)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching channels from $url", e)
            emptyList()
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
}
