package com.samyak.player

data class WatchHistoryEntry(
    val title: String,
    val link: String,
    val streamUrl: String,
    val imageUrl: String,
    val providerUrl: String,
    val scraperValue: String,
    val position: Long,
    val duration: Long,
    val lastWatched: Long
)
