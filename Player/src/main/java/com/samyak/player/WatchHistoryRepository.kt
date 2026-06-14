package com.samyak.player

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object WatchHistoryRepository {
    private const val PREFS_NAME = "iptv_watch_history"
    private const val KEY_HISTORY = "watch_history_entries"
    private val gson = Gson()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun cleanTitle(title: String?): String {
        if (title.isNullOrBlank()) return ""

        val sb = StringBuilder()
        var i = 0
        while (i < title.length) {
            val codePoint = title.codePointAt(i)
            val charCount = Character.charCount(codePoint)

            // Check if codePoint is in Private Use Area or is control character
            val isPua = (codePoint in 0xE000..0xF8FF) || 
                        (codePoint in 0xF0000..0xFFFFD) || 
                        (codePoint in 0x100000..0x10FFFD)

            val isControl = Character.isISOControl(codePoint)

            if (!isPua && !isControl) {
                sb.appendRange(title, i, i + charCount)
            }
            i += charCount
        }

        return sb.toString()
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    @Synchronized
    fun getWatchHistory(context: Context): List<WatchHistoryEntry> {
        val prefs = getPrefs(context)
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        val type = object : TypeToken<List<WatchHistoryEntry>>() {}.type
        return try {
            val list: List<WatchHistoryEntry> = gson.fromJson(json, type) ?: emptyList()
            list.map { it.copy(title = cleanTitle(it.title)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun saveProgress(
        context: Context,
        title: String,
        link: String,
        streamUrl: String,
        imageUrl: String,
        providerUrl: String,
        scraperValue: String,
        position: Long,
        duration: Long
    ) {
        if (link.isBlank()) return
        val currentHistory = getWatchHistory(context).toMutableList()
        
        // Remove existing entry for the same link
        currentHistory.removeAll { it.link == link }

        val cleanedTitle = cleanTitle(title)
        val newEntry = WatchHistoryEntry(
            title = cleanedTitle,
            link = link,
            streamUrl = streamUrl,
            imageUrl = imageUrl,
            providerUrl = providerUrl,
            scraperValue = scraperValue,
            position = position,
            duration = duration,
            lastWatched = System.currentTimeMillis()
        )

        currentHistory.add(0, newEntry) // Add to the top of the list

        // Limit the history size to 100 entries
        val trimmedHistory = if (currentHistory.size > 100) {
            currentHistory.subList(0, 100)
        } else {
            currentHistory
        }

        val prefs = getPrefs(context)
        prefs.edit().putString(KEY_HISTORY, gson.toJson(trimmedHistory)).apply()
    }

    @Synchronized
    fun getProgress(context: Context, link: String): Long {
        if (link.isBlank()) return 0L
        val entry = getWatchHistory(context).find { it.link == link } ?: return 0L
        // If we are close to the end (e.g., watched > 95%), don't resume from the end
        if (entry.duration > 0 && (entry.position.toDouble() / entry.duration.toDouble()) > 0.95) {
            return 0L
        }
        return entry.position
    }

    @Synchronized
    fun clearHistory(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    @Synchronized
    fun deleteEntry(context: Context, link: String) {
        val currentHistory = getWatchHistory(context).toMutableList()
        currentHistory.removeAll { it.link == link }
        val prefs = getPrefs(context)
        prefs.edit().putString(KEY_HISTORY, gson.toJson(currentHistory)).apply()
    }
}
