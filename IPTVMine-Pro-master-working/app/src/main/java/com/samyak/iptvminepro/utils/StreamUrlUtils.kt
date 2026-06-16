package com.samyak.iptvminepro.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Utility class for stream URL operations and validation
 */
object StreamUrlUtils {
    private const val TAG = "StreamUrlUtils"

    interface StreamUrlCallback {
        fun onResult(isValid: Boolean, message: String)
    }

    /**
     * Validate if a stream URL is accessible (suspend function for coroutines)
     */
    suspend fun validateStreamUrl(url: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (url.isEmpty()) {
            return@withContext Pair(false, "URL is empty")
        }

        try {
            val streamUrl = URL(url)
            val connection = streamUrl.openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "IPTV Mine/1.0")

            val responseCode = connection.responseCode
            connection.disconnect()

            if (responseCode == HttpURLConnection.HTTP_OK) {
                Pair(true, "Stream URL is valid")
            } else {
                Pair(false, "HTTP $responseCode")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error validating stream URL: $url", e)
            Pair(false, "Network error: ${e.message}")
        }
    }

    /**
     * Validate if a stream URL is accessible (callback version for Java compatibility)
     */
    fun validateStreamUrlWithCallback(url: String, callback: StreamUrlCallback) {
        if (url.isEmpty()) {
            callback.onResult(false, "URL is empty")
            return
        }

        Thread {
            try {
                val streamUrl = URL(url)
                val connection = streamUrl.openConnection() as HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.setRequestProperty("User-Agent", "IPTV Mine/1.0")

                val responseCode = connection.responseCode
                connection.disconnect()

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    callback.onResult(true, "Stream URL is valid")
                } else {
                    callback.onResult(false, "HTTP $responseCode")
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error validating stream URL: $url", e)
                callback.onResult(false, "Network error: ${e.message}")
            }
        }.start()
    }

    /**
     * Convert HTTP to HTTPS if possible
     */
    fun upgradeToHttps(url: String?): String? {
        return if (url != null && url.startsWith("http://")) {
            "https://" + url.substring(7)
        } else {
            url
        }
    }

    /**
     * Get stream format from URL
     */
    fun getStreamFormat(url: String?): String {
        if (url == null) return "unknown"
        
        val lowerUrl = url.lowercase()
        return when {
            lowerUrl.contains("m3u8") -> "hls"
            lowerUrl.contains("mpd") -> "dash"
            lowerUrl.contains(".mp4") -> "mp4"
            lowerUrl.contains(".ts") -> "mpeg-ts"
            lowerUrl.contains(".webm") -> "webm"
            lowerUrl.contains(".mkv") -> "mkv"
            lowerUrl.startsWith("rtmp://") -> "rtmp"
            else -> "stream"
        }
    }

    /**
     * Check if URL is likely a live stream
     */
    fun isLiveStream(url: String?): Boolean {
        if (url == null) return false
        
        val lowerUrl = url.lowercase()
        return lowerUrl.contains("live") || 
               lowerUrl.contains("stream") || 
               lowerUrl.contains("24/7") ||
               lowerUrl.contains("m3u8") ||
               lowerUrl.contains("rtmp://")
    }

    /**
     * Generate alternative URLs for fallback
     */
    fun generateFallbackUrls(originalUrl: String?): Array<String?> {
        if (originalUrl == null) return emptyArray()
        
        val alternatives = arrayOfNulls<String>(3)
        alternatives[0] = originalUrl

        // Try HTTPS version
        if (originalUrl.startsWith("http://")) {
            alternatives[1] = upgradeToHttps(originalUrl)
        }

        // Try with different port if applicable
        alternatives[2] = when {
            originalUrl.contains(":8080") -> originalUrl.replace(":8080", ":80")
            originalUrl.contains(":80") -> originalUrl.replace(":80", ":8080")
            else -> null
        }

        return alternatives
    }

    /**
     * Extract quality info from URL if present
     */
    fun extractQualityFromUrl(url: String?): String {
        if (url == null) return "auto"
        
        val lowerUrl = url.lowercase()
        return when {
            lowerUrl.contains("1080p") || lowerUrl.contains("hd") -> "hd"
            lowerUrl.contains("720p") -> "hd"
            lowerUrl.contains("480p") || lowerUrl.contains("sd") -> "sd"
            lowerUrl.contains("360p") || lowerUrl.contains("low") -> "low"
            else -> "auto"
        }
    }

    /**
     * Check if URL requires special headers
     */
    fun requiresSpecialHeaders(url: String?): Boolean {
        if (url == null) return false
        
        val lowerUrl = url.lowercase()
        return lowerUrl.contains("jio") || 
               lowerUrl.contains("hotstar") || 
               lowerUrl.contains("sonyliv") ||
               lowerUrl.contains("zee5")
    }

    /**
     * Get recommended buffer size based on stream type
     */
    fun getRecommendedBufferSize(url: String?): Int {
        return when (getStreamFormat(url)) {
            "hls" -> 3000 // 3 seconds for HLS
            "dash" -> 3000 // 3 seconds for DASH
            "rtmp" -> 1000 // 1 second for RTMP
            else -> 2000 // 2 seconds default
        }
    }

    /**
     * Clean and normalize URL
     */
    fun cleanUrl(url: String?): String? {
        if (url == null) return null
        
        return url.trim()
            .replace(" ", "%20")
            .replace("\n", "")
            .replace("\r", "")
    }

    /**
     * Check if URL is valid format
     */
    fun isValidUrlFormat(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        
        return url.startsWith("http://") || 
               url.startsWith("https://") || 
               url.startsWith("rtmp://") || 
               url.startsWith("rtsp://") ||
               url.startsWith("udp://") ||
               url.startsWith("rtp://")
    }
}
