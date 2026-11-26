package com.samyak.television.data

import android.content.Context
import android.util.Log
import com.samyak.television.model.VegaDirectLink
import com.samyak.television.model.VegaLink
import com.samyak.television.model.VegaMeta
import com.samyak.television.model.VegaPost
import com.samyak.television.model.VegaStream
import com.samyak.television.model.isPlayableMediaUrl
import com.samyak.television.model.prioritizeStreams
import com.samyak.television.utils.TitleUtils
import kotlinx.coroutines.withTimeoutOrNull

object VegaStreamFallbackEngine {
    private const val TAG = "VegaFallbackEngineTV"

    data class FallbackResult(
        val selectedStream: VegaStream,
        val allStreams: List<VegaStream>,
        val serverTitle: String,
        val providerValue: String,
        val isFallback: Boolean,
        val message: String?
    )

    /**
     * Goal-seeking automatic server & extension resolver for Android TV.
     * Retries mirrors, alternate servers, and cross-extension providers automatically until
     * a playable stream is found (or all fail). Stops the moment the goal is achieved.
     */
    suspend fun resolveWithAutoFallback(
        context: Context,
        runner: VegaProviderRunner,
        initialLink: String,
        initialType: String,
        itemTitle: String,
        movieTitle: String,
        currentMeta: VegaMeta?,
        currentProviderUrl: String,
        currentScraperValue: String,
        onStatusUpdate: ((String) -> Unit)? = null
    ): FallbackResult? {
        val safeInitialType = when {
            initialType.equals("series", ignoreCase = true) -> "series"
            initialType.equals("movie", ignoreCase = true) -> "movie"
            else -> "movie"
        }
        val repoUrl = currentProviderUrl.ifBlank {
            ProviderRepository(context).getProviders()
                .firstOrNull { it.isActive && it.safeType == com.samyak.television.model.ProviderType.VEGA }
                ?.url
                .orEmpty()
        }

        // 1. Direct attempt on chosen link
        onStatusUpdate?.invoke("Resolving stream links...")
        val initialStreams = tryResolveLink(runner, repoUrl, currentScraperValue, initialLink, safeInitialType)
        if (initialStreams.isNotEmpty()) {
            return FallbackResult(
                selectedStream = initialStreams[0],
                allStreams = initialStreams,
                serverTitle = "Direct Link",
                providerValue = currentScraperValue,
                isFallback = false,
                message = null
            )
        }

        // 2. Try other direct links under the same server
        val allServers = currentMeta?.linkList ?: emptyList()
        val currentServer = allServers.firstOrNull { server ->
            server.directLinks?.any { it.link == initialLink } == true
        }

        if (currentServer != null && !currentServer.directLinks.isNullOrEmpty()) {
            for (dLink in currentServer.directLinks) {
                if (dLink.link == initialLink || dLink.link.isBlank()) continue
                val displayTitle = dLink.title.ifBlank { currentServer.title }
                onStatusUpdate?.invoke("Auto-trying mirror: $displayTitle...")
                Log.d(TAG, "Trying mirror directLink: ${dLink.link}")
                val streams = tryResolveLink(runner, repoUrl, currentScraperValue, dLink.link, dLink.type.ifBlank { safeInitialType })
                if (streams.isNotEmpty()) {
                    return FallbackResult(
                        selectedStream = streams[0],
                        allStreams = streams,
                        serverTitle = displayTitle,
                        providerValue = currentScraperValue,
                        isFallback = true,
                        message = "Auto-connected to mirror: $displayTitle"
                    )
                }
            }
        }

        // 3. Try other servers under the same movie/show
        val otherServers = allServers.filter { it != currentServer }
        for (server in otherServers) {
            val dLinks = server.directLinks ?: emptyList()
            for (dLink in dLinks) {
                if (dLink.link.isBlank()) continue
                val displayTitle = server.title
                onStatusUpdate?.invoke("Auto-trying server: $displayTitle...")
                Log.d(TAG, "Trying server: $displayTitle -> ${dLink.link}")
                val streams = tryResolveLink(runner, repoUrl, currentScraperValue, dLink.link, dLink.type.ifBlank { safeInitialType })
                if (streams.isNotEmpty()) {
                    return FallbackResult(
                        selectedStream = streams[0],
                        allStreams = streams,
                        serverTitle = displayTitle,
                        providerValue = currentScraperValue,
                        isFallback = true,
                        message = "Auto-switched to server: $displayTitle"
                    )
                }
            }

            // If series and has episodesLink
            if (!server.episodesLink.isNullOrBlank()) {
                try {
                    onStatusUpdate?.invoke("Checking server episodes: ${server.title}...")
                    val episodes = withTimeoutOrNull(15_000) {
                        runner.getEpisodes(repoUrl, currentScraperValue, server.episodesLink)
                    }
                    val matchedEp = episodes?.firstOrNull { ep ->
                        val epTitle = ep.title.lowercase()
                        val targetTitle = itemTitle.lowercase()
                        epTitle == targetTitle || (targetTitle.contains("episode") && epTitle.contains(targetTitle.substringAfter("episode").trim().take(4)))
                    }
                    if (matchedEp?.directLinks != null) {
                        for (dLink in matchedEp.directLinks) {
                            if (dLink.link.isBlank()) continue
                            val streams = tryResolveLink(runner, repoUrl, currentScraperValue, dLink.link, dLink.type.ifBlank { safeInitialType })
                            if (streams.isNotEmpty()) {
                                return FallbackResult(
                                    selectedStream = streams[0],
                                    allStreams = streams,
                                    serverTitle = "${server.title} - ${matchedEp.title}",
                                    providerValue = currentScraperValue,
                                    isFallback = true,
                                    message = "Auto-switched to server: ${server.title}"
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed resolving server episodes: ${e.message}")
                }
            }
        }

        // 4. Cross-Extension Automatic Fallback (for MovieBox Web and all other extensions!)
        val installedExtensions = ExtensionRepository.getInstance(context).getInstalledExtensions()
            .filter { !it.equals(currentScraperValue, ignoreCase = true) }

        if (installedExtensions.isNotEmpty()) {
            val cleanTitle = TitleUtils.getSearchableTitle(movieTitle).ifBlank { TitleUtils.getSearchableTitle(itemTitle) }
            if (cleanTitle.isNotBlank()) {
                val priorityOrder = listOf("Vega", "Moviesmod", "UhdMovies", "1cinevood", "Katmovie", "hdhub", "TopMovies", "Multi", "movieBoxWeb")
                val sortedExtensions = installedExtensions.sortedBy { ext ->
                    val idx = priorityOrder.indexOfFirst { it.equals(ext, ignoreCase = true) }
                    if (idx >= 0) idx else 999
                }

                for (ext in sortedExtensions) {
                    onStatusUpdate?.invoke("Searching in $ext...")
                    val searchResults: List<VegaPost> = try {
                        withTimeoutOrNull(15_000) {
                            runner.getSearchPosts(repoUrl, ext, cleanTitle, 1)
                        } ?: emptyList()
                    } catch (e: Exception) {
                        Log.w(TAG, "Search failed for $ext: ${e.message}")
                        emptyList()
                    }
                    if (searchResults.isEmpty()) continue

                    val searchWords = cleanTitle.lowercase().split(" ").filter { it.length > 2 }
                    val matchedPost = searchResults.firstOrNull { post ->
                        val pTitle = post.title.lowercase()
                        searchWords.any { word -> pTitle.contains(word) }
                    } ?: searchResults.firstOrNull() ?: continue

                    onStatusUpdate?.invoke("Found in $ext, loading streams...")
                    val extMeta = try {
                        withTimeoutOrNull(15_000) {
                            runner.getMeta(repoUrl, ext, matchedPost.link)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "getMeta failed for $ext: ${e.message}")
                        null
                    } ?: continue

                    for (server in extMeta.linkList) {
                        for (dLink in server.directLinks ?: emptyList()) {
                            if (dLink.link.isBlank()) continue
                            val streams = tryResolveLink(runner, repoUrl, ext, dLink.link, dLink.type.ifBlank { safeInitialType })
                            if (streams.isNotEmpty()) {
                                return FallbackResult(
                                    selectedStream = streams[0],
                                    allStreams = streams,
                                    serverTitle = "$ext - ${server.title}",
                                    providerValue = ext,
                                    isFallback = true,
                                    message = "Auto-connected via $ext (${server.title})"
                                )
                            }
                        }

                        if (!server.episodesLink.isNullOrBlank()) {
                            try {
                                val episodes = withTimeoutOrNull(15_000) {
                                    runner.getEpisodes(repoUrl, ext, server.episodesLink)
                                }
                                val matchedEp = episodes?.firstOrNull { ep ->
                                    val epTitle = ep.title.lowercase()
                                    val targetTitle = itemTitle.lowercase()
                                    epTitle == targetTitle || (targetTitle.contains("episode") && epTitle.contains(targetTitle.substringAfter("episode").trim().take(4)))
                                } ?: episodes?.firstOrNull()
                                if (matchedEp?.directLinks != null) {
                                    for (dLink in matchedEp.directLinks) {
                                        if (dLink.link.isBlank()) continue
                                        val streams = tryResolveLink(runner, repoUrl, ext, dLink.link, dLink.type.ifBlank { safeInitialType })
                                        if (streams.isNotEmpty()) {
                                            return FallbackResult(
                                                selectedStream = streams[0],
                                                allStreams = streams,
                                                serverTitle = "$ext - ${matchedEp.title}",
                                                providerValue = ext,
                                                isFallback = true,
                                                message = "Auto-connected via $ext"
                                            )
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Episodes failed on $ext: ${e.message}")
                            }
                        }
                    }
                }
            }
        }

        // 5. Final fallback if link itself is a direct media URL
        if (isPlayableMediaUrl(initialLink)) {
            val ext = initialLink.substringBefore('?').substringBefore('#').substringAfterLast('.', "mp4").lowercase()
            val directStream = VegaStream("Direct Source", initialLink, ext, "HD")
            return FallbackResult(
                selectedStream = directStream,
                allStreams = listOf(directStream),
                serverTitle = "Direct Source",
                providerValue = currentScraperValue,
                isFallback = false,
                message = null
            )
        }

        return null
    }

    private suspend fun tryResolveLink(
        runner: VegaProviderRunner,
        repoUrl: String,
        scraperValue: String,
        link: String,
        type: String
    ): List<VegaStream> {
        if (link.isBlank()) return emptyList()

        var streams = try {
            withTimeoutOrNull(25_000) {
                prioritizeStreams(runner.getStream(repoUrl, scraperValue, link, type).filter { it.link.isNotBlank() })
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        if (streams.isEmpty()) {
            val altType = if (type.equals("movie", ignoreCase = true)) "series" else "movie"
            streams = try {
                withTimeoutOrNull(20_000) {
                    prioritizeStreams(runner.getStream(repoUrl, scraperValue, link, altType).filter { it.link.isNotBlank() })
                } ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

        if (streams.isEmpty() && isPlayableMediaUrl(link)) {
            val ext = link.substringBefore('?').substringBefore('#').substringAfterLast('.', "mp4").lowercase()
            streams = listOf(VegaStream("Direct Source", link, ext, "HD"))
        }

        return streams
    }
}
