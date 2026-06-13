package com.samyak.iptvminepro.utils

import android.util.Log

/**
 * Content Filter - Manages blocked categories and content filtering
 * Prevents adult and inappropriate content from being displayed
 */
object ContentFilter {

    private const val TAG = "ContentFilter"

    // Blocked category keywords (case-insensitive)
    private val BLOCKED_CATEGORIES = setOf(
        // Adult content
        "adult",
        "xxx",
        "18+",
        "18 +",
        "porn",
        "erotic",
        "sex",
        "sexy",
        
        // Explicit variations
        "adults only",
        "mature",
        "nsfw",
        "x-rated",
        "r-rated",
        
        // International variations
        "adulto",      // Spanish/Portuguese
        "adulte",      // French
        "erwachsene",  // German
        "成人",         // Chinese
        "大人",         // Japanese
        "성인"          // Korean
    )

    // Blocked channel name keywords
    private val BLOCKED_CHANNEL_KEYWORDS = setOf(
        "xxx",
        "porn",
        "adult",
        "sex",
        "erotic",
        "playboy",
        "hustler",
        "brazzers",
        "bangbros"
    )

    /**
     * Check if a category should be blocked
     * @param category The category name to check
     * @return true if the category should be blocked
     */
    fun isBlockedCategory(category: String?): Boolean {
        if (category.isNullOrBlank()) return false
        
        val lowerCategory = category.lowercase().trim()
        
        // Check exact matches and contains
        val isBlocked = BLOCKED_CATEGORIES.any { blocked ->
            lowerCategory == blocked || lowerCategory.contains(blocked)
        }
        
        if (isBlocked) {
            Log.d(TAG, "Blocked category detected: $category")
        }
        
        return isBlocked
    }

    /**
     * Check if a channel name contains blocked keywords
     * @param channelName The channel name to check
     * @return true if the channel name contains blocked keywords
     */
    fun isBlockedChannelName(channelName: String?): Boolean {
        if (channelName.isNullOrBlank()) return false
        
        val lowerName = channelName.lowercase().trim()
        
        val isBlocked = BLOCKED_CHANNEL_KEYWORDS.any { keyword ->
            lowerName.contains(keyword)
        }
        
        if (isBlocked) {
            Log.d(TAG, "Blocked channel name detected: $channelName")
        }
        
        return isBlocked
    }

    /**
     * Check if content should be blocked (checks both category and channel name)
     * @param channelName The channel name
     * @param category The category name
     * @return true if the content should be blocked
     */
    fun shouldBlockContent(channelName: String?, category: String?): Boolean {
        return isBlockedCategory(category) || isBlockedChannelName(channelName)
    }

    /**
     * Get all blocked categories (for debugging/admin purposes)
     */
    fun getBlockedCategories(): Set<String> = BLOCKED_CATEGORIES

    /**
     * Get all blocked channel keywords (for debugging/admin purposes)
     */
    fun getBlockedChannelKeywords(): Set<String> = BLOCKED_CHANNEL_KEYWORDS

    /**
     * Add custom blocked category at runtime (optional feature)
     */
    private val customBlockedCategories = mutableSetOf<String>()

    fun addCustomBlockedCategory(category: String) {
        customBlockedCategories.add(category.lowercase().trim())
        Log.d(TAG, "Added custom blocked category: $category")
    }

    fun removeCustomBlockedCategory(category: String) {
        customBlockedCategories.remove(category.lowercase().trim())
        Log.d(TAG, "Removed custom blocked category: $category")
    }

    fun isCustomBlockedCategory(category: String?): Boolean {
        if (category.isNullOrBlank()) return false
        return customBlockedCategories.contains(category.lowercase().trim())
    }

    /**
     * Clear all custom blocked categories
     */
    fun clearCustomBlockedCategories() {
        customBlockedCategories.clear()
        Log.d(TAG, "Cleared all custom blocked categories")
    }
}
