package com.samyak.iptvminepro.provider

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.samyak.iptvminepro.model.ProviderType
import com.samyak.iptvminepro.model.VegaProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Detects whether a URL or payload represents a VOD scraper source (base-URL map JSON
 * such as movies.json/urls.json/modflix.json or a Vega manifest JSON), an IPTV playlist
 * (M3U or channel JSON such as LiveTV.json), or a direct video stream.
 */
object ProviderDetectionEngine {

    enum class SourceKind {
        VOD_SCRAPER_MAP,
        VOD_MANIFEST,
        IPTV_M3U,
        IPTV_JSON,
        DIRECT_VIDEO,
        UNKNOWN
    }

    data class ScraperEndpoint(
        val key: String,
        val name: String,
        val url: String
    )

    data class DetectionResult(
        val providerType: ProviderType,
        val sourceKind: SourceKind,
        val scraperEntries: Map<String, ScraperEndpoint> = emptyMap(),
        val manifestEntries: List<VegaProvider> = emptyList()
    )

    data class ParsedQrProvider(
        val title: String,
        val url: String,
        val userAgent: String? = null
    )

    private val VIDEO_EXTENSIONS = setOf(
        ".mp4", ".mkv", ".avi", ".mov", ".wmv", ".flv", ".webm",
        ".mpg", ".mpeg", ".3gp", ".ogv", ".m4v", ".rm", ".rmvb"
    )

    private val STREAM_URL_EXTENSIONS = setOf(".m3u8", ".m3u", ".mpd", ".ts")

    private val IPTV_CONTAINER_KEYS = setOf(
        "channels", "streams", "matches", "playlists", "stations"
    )

    private val IPTV_CHANNEL_FIELDS = setOf(
        "logo", "logo_url", "logoUrl", "tvg-logo", "tvg_logo", "tvgLogo",
        "group", "group-title", "group_title", "groupTitle",
        "source", "stream_url", "streamUrl", "streamurl", "m3u8", "hls",
        "adfree_url", "dai_url", "team_1", "team_2", "event_name", "match_name"
    )

    private val IPTV_URL_KEYS = listOf(
        "url", "link", "stream", "stream_url", "streamurl", "streamUrl",
        "source_url", "adfree_url", "dai_url", "file", "playback_url",
        "manifest", "hls", "m3u8", "playbackUrl", "streamLink"
    )

    private val discoveredEndpoints = ConcurrentHashMap<String, String>()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun getDiscoveredBaseUrls(): Map<String, String> = HashMap(discoveredEndpoints)

    fun recordDiscoveredEndpoints(entries: Map<String, ScraperEndpoint>) {
        for ((key, ep) in entries) {
            if (ep.url.isNotBlank()) {
                discoveredEndpoints[key] = ep.url
                if (ep.name.isNotBlank()) {
                    discoveredEndpoints.putIfAbsent(ep.name, ep.url)
                }
            }
        }
    }

    /**
     * Parses raw QR scanner text into a provider entry (`title`, `url`, optional `userAgent`).
     * Supports plain URLs/identifiers, `Title|URL` or two-line `Title\nURL` pairs, and JSON objects.
     */
    fun parseProviderQrPayload(raw: String): ParsedQrProvider? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val jsonObj = parseJsonSafely(trimmed)?.takeIf { it.isJsonObject }?.asJsonObject
        if (jsonObj != null) {
            val url = listOf("url", "playlist_url", "link", "source")
                .firstNotNullOfOrNull { key -> jsonObj.optString(key).trim().takeIf { it.isNotEmpty() } }
                ?: return null
            if (!isProviderCandidateUrl(url)) return null
            val title = listOf("title", "name", "provider")
                .firstNotNullOfOrNull { key -> jsonObj.optString(key).trim().takeIf { it.isNotEmpty() } }
                ?: deriveProviderTitle(url)
            val ua = listOf("userAgent", "user_agent")
                .firstNotNullOfOrNull { key -> jsonObj.optString(key).trim().takeIf { it.isNotEmpty() } }
            return ParsedQrProvider(title = title, url = url, userAgent = ua)
        }

        // Check two-part "Title|URL" or "Title\nURL" format
        val parts = when {
            trimmed.contains('\n') -> trimmed.lines().map { it.trim() }.filter { it.isNotEmpty() }
            trimmed.contains('|') -> trimmed.split('|', limit = 2).map { it.trim() }
            else -> emptyList()
        }
        if (parts.size == 2 && isProviderCandidateUrl(parts[1])) {
            val cleanTitle = parts[0].ifBlank { deriveProviderTitle(parts[1]) }
            return ParsedQrProvider(title = cleanTitle, url = parts[1])
        }

        if (isProviderCandidateUrl(trimmed)) {
            return ParsedQrProvider(title = deriveProviderTitle(trimmed), url = trimmed)
        }

        return null
    }

    fun deriveProviderTitle(rawUrl: String): String {
        val trimmed = rawUrl.trim().removeSuffix("/")
        if (trimmed.startsWith("@")) return trimmed.removePrefix("@").ifBlank { "Provider" }
        val withoutQuery = trimmed.substringBefore("?").substringBefore("#")
        val afterScheme = withoutQuery.substringAfter("://", withoutQuery)
        val segments = afterScheme.split('/').filter { it.isNotBlank() }
        val last = segments.lastOrNull()
        val genericNames = setOf("index", "playlist", "manifest", "urls", "modflix", "channels")
        if (last != null && segments.size > 1) {
            val baseName = last.substringBeforeLast('.')
            if (baseName.isNotBlank() && baseName.lowercase() !in genericNames) {
                return baseName
            }
            if (segments.size >= 2) {
                val parent = segments[segments.size - 2]
                if (parent.isNotBlank()) return parent
            }
        }
        val host = segments.firstOrNull()?.removePrefix("www.")
        return host?.ifBlank { null } ?: "Provider"
    }

    private fun isProviderCandidateUrl(value: String): Boolean {
        val v = value.trim()
        if (v.isEmpty() || v.any { it.isWhitespace() }) return false
        val lower = v.lowercase()
        return isHttpUrl(v) ||
            v.startsWith("@") ||
            lower.startsWith("github.com/") ||
            lower.startsWith("raw.githubusercontent.com/") ||
            lower.contains(".m3u") ||
            lower.contains(".json")
    }

    /** Fast URL/title heuristic for live form updates before network inspection. */
    fun detectQuick(inputUrl: String, title: String = ""): ProviderType {
        val trimmed = inputUrl.trim()
        val path = trimmed.lowercase().substringBefore("?").substringBefore("#")
        return when {
            isDirectVideoUrl(trimmed) -> ProviderType.VIDEO
            path.endsWith(".m3u") || path.endsWith(".m3u8") -> ProviderType.IPTV
            trimmed.startsWith("@") ||
                trimmed.contains("vega", ignoreCase = true) ||
                title.contains("vega", ignoreCase = true) ||
                looksLikeVegaRepoOrJsonUrl(path) -> ProviderType.VEGA
            else -> ProviderType.IPTV
        }
    }

    fun isDirectVideoUrl(url: String): Boolean {
        val path = url.trim().lowercase().substringBefore("?").substringBefore("#")
        return VIDEO_EXTENSIONS.any { path.endsWith(it) }
    }

    /**
     * Returns true when the URL should be fetched and structurally inspected
     * (e.g. arbitrary `.json` files like `movies.json` or `LiveTV.json`, or URLs
     * without a clear `.m3u`/`.mp4` extension).
     */
    fun needsContentInspection(url: String): Boolean {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return false
        }
        if (isDirectVideoUrl(trimmed)) return false
        val path = trimmed.lowercase().substringBefore("?").substringBefore("#")
        if (path.endsWith(".m3u")) return false
        return true
    }

    private fun looksLikeVegaRepoOrJsonUrl(lowerPath: String): Boolean {
        if (lowerPath.endsWith("/manifest.json") ||
            lowerPath.endsWith("/urls.json") ||
            lowerPath.endsWith("/modflix.json")
        ) {
            return true
        }
        return (lowerPath.startsWith("https://github.com/") ||
            lowerPath.startsWith("http://github.com/")) && !lowerPath.endsWith(".m3u")
    }

    /**
     * Fetches the target URL (with jsDelivr mirror fallback for GitHub raw URLs)
     * and classifies its content structure.
     */
    suspend fun detectFromUrl(url: String, client: OkHttpClient = httpClient): DetectionResult =
        withContext(Dispatchers.IO) {
            val trimmed = url.trim()
            if (!needsContentInspection(trimmed)) {
                val type = detectQuick(trimmed)
                val kind = when (type) {
                    ProviderType.VIDEO -> SourceKind.DIRECT_VIDEO
                    ProviderType.IPTV -> SourceKind.IPTV_M3U
                    ProviderType.VEGA -> SourceKind.VOD_MANIFEST
                }
                return@withContext DetectionResult(type, kind)
            }

            val candidates = buildList {
                add(trimmed)
                VegaProviderRunner.toJsDelivrMirror(trimmed)?.let { add(it) }
                // If it's a GitHub/raw repo root without a trailing .json/.m3u, try manifest.json
                val lowerPath = trimmed.lowercase().substringBefore("?").substringBefore("#").removeSuffix("/")
                if (!lowerPath.endsWith(".json") && !lowerPath.endsWith(".m3u") && !lowerPath.endsWith(".m3u8")) {
                    val resolved = resolveGitHubRepoRoot(trimmed)
                    if (resolved != null) {
                        add("$resolved/manifest.json")
                        VegaProviderRunner.toJsDelivrMirror(resolved)?.let { add("$it/manifest.json") }
                    }
                }
            }.distinct()

            for (candidate in candidates) {
                try {
                    val req = Request.Builder()
                        .url(candidate)
                        .header("User-Agent", "Mozilla/5.0 (Android) IPTVMinePro")
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) continue
                        val body = resp.body?.string().orEmpty()
                        if (body.isNotBlank()) {
                            val result = classifyContent(body, candidate)
                            if (result.sourceKind != SourceKind.UNKNOWN) {
                                if (result.scraperEntries.isNotEmpty()) {
                                    recordDiscoveredEndpoints(result.scraperEntries)
                                }
                                return@withContext result
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Try next candidate
                }
            }

            val fallbackType = detectQuick(trimmed)
            DetectionResult(fallbackType, SourceKind.UNKNOWN)
        }

    /**
     * Pure structural classification algorithm on raw payload text.
     * Distinguishes M3U, HLS video, VOD scraper base-URL maps (`movies.json`),
     * Vega manifests (`manifest.json`), and IPTV JSON (`LiveTV.json`).
     */
    fun classifyContent(content: String, sourceUrl: String = ""): DetectionResult {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            return DetectionResult(detectQuick(sourceUrl), SourceKind.UNKNOWN)
        }

        val hasExtInf = trimmed.contains("#EXTINF", ignoreCase = true)
        val hasExtM3u = trimmed.contains("#EXTM3U", ignoreCase = true)
        val isHlsMedia = !hasExtInf && (
            trimmed.contains("#EXT-X-STREAM-INF") ||
                trimmed.contains("#EXT-X-TARGETDURATION") ||
                trimmed.contains("#EXT-X-MEDIA-SEQUENCE") ||
                trimmed.contains("#EXT-X-PLAYLIST-TYPE")
            )
        if (isHlsMedia) {
            return DetectionResult(ProviderType.VIDEO, SourceKind.DIRECT_VIDEO)
        }
        if (hasExtM3u || hasExtInf) {
            return DetectionResult(ProviderType.IPTV, SourceKind.IPTV_M3U)
        }

        val jsonElement = parseJsonSafely(trimmed)
        if (jsonElement != null) {
            return classifyJsonElement(jsonElement, sourceUrl)
        }

        return DetectionResult(detectQuick(sourceUrl), SourceKind.UNKNOWN)
    }

    /**
     * Returns true when [jsonText] represents a VOD scraper base-URL map
     * (`{ "Key": { "name": "...", "url": "https://..." } }`) rather than an IPTV playlist.
     */
    fun isVodScraperMapJson(jsonText: String): Boolean {
        val el = parseJsonSafely(jsonText) ?: return false
        return classifyJsonElement(el, "").sourceKind == SourceKind.VOD_SCRAPER_MAP
    }

    private fun classifyJsonElement(element: JsonElement, sourceUrl: String): DetectionResult {
        if (element.isJsonArray) {
            val arr = element.asJsonArray
            val manifestEntries = extractManifestEntries(arr, sourceUrl)
            if (manifestEntries.isNotEmpty()) {
                return DetectionResult(
                    providerType = ProviderType.VEGA,
                    sourceKind = SourceKind.VOD_MANIFEST,
                    manifestEntries = manifestEntries
                )
            }
            if (containsIptvChannelObject(arr)) {
                return DetectionResult(ProviderType.IPTV, SourceKind.IPTV_JSON)
            }
            return DetectionResult(detectQuick(sourceUrl), SourceKind.UNKNOWN)
        }

        if (element.isJsonObject) {
            val obj = element.asJsonObject

            // Explicit IPTV container wrapper (e.g. LiveTV.json has "channels", sports JSON has "matches")
            if (IPTV_CONTAINER_KEYS.any { key -> obj.has(key) }) {
                return DetectionResult(ProviderType.IPTV, SourceKind.IPTV_JSON)
            }

            val scraperEntries = extractVodScraperMap(obj)
            if (scraperEntries.isNotEmpty() && !containsIptvChannelObject(obj)) {
                return DetectionResult(
                    providerType = ProviderType.VEGA,
                    sourceKind = SourceKind.VOD_SCRAPER_MAP,
                    scraperEntries = scraperEntries
                )
            }

            if (containsIptvChannelObject(obj)) {
                return DetectionResult(ProviderType.IPTV, SourceKind.IPTV_JSON)
            }
        }

        return DetectionResult(detectQuick(sourceUrl), SourceKind.UNKNOWN)
    }

    /**
     * Extracts `{ "<key>": { "name": "...", "url": "https://..." } }` VOD scraper endpoints.
     */
    fun extractVodScraperMap(root: JsonObject): Map<String, ScraperEndpoint> {
        val result = linkedMapOf<String, ScraperEndpoint>()
        var matchedEntries = 0
        var objectEntries = 0

        for ((key, value) in root.entrySet()) {
            if (!value.isJsonObject) continue
            objectEntries++
            val entryObj = value.asJsonObject

            // Skip objects that carry IPTV channel metadata or stream-specific keys
            if (IPTV_CHANNEL_FIELDS.any { entryObj.has(it) }) continue

            val rawUrl = entryObj.optString("url")
                .ifBlank { entryObj.optString("baseUrl") }
                .ifBlank { entryObj.optString("domain") }
                .trim()
            if (!isHttpUrl(rawUrl)) continue

            val lowerUrlPath = rawUrl.lowercase().substringBefore("?").substringBefore("#")
            if (STREAM_URL_EXTENSIONS.any { lowerUrlPath.endsWith(it) } ||
                VIDEO_EXTENSIONS.any { lowerUrlPath.endsWith(it) } ||
                lowerUrlPath.contains("/stream/channelid/")
            ) {
                continue
            }

            val name = entryObj.optString("name")
                .ifBlank { entryObj.optString("title") }
                .ifBlank { key }
                .trim()
            matchedEntries++
            result[key] = ScraperEndpoint(key = key, name = name, url = rawUrl)
        }

        // Require at least one valid entry and avoid misclassifying random objects with nested IPTV data
        return if (matchedEntries > 0 && matchedEntries == objectEntries) result else emptyMap()
    }

    private fun extractManifestEntries(arr: JsonArray, sourceUrl: String): List<VegaProvider> {
        if (arr.size() == 0) return emptyList()
        val list = mutableListOf<VegaProvider>()
        val repoRoot = sourceUrl.substringBeforeLast("/", "")
        for (item in arr) {
            if (!item.isJsonObject) continue
            val obj = item.asJsonObject
            val value = obj.optString("value").trim()
            val displayName = obj.optString("display_name").trim()
            val version = obj.optString("version").trim()
            if (value.isNotBlank() && (displayName.isNotBlank() || version.isNotBlank())) {
                val rawIcon = obj.optString("icon").trim()
                val resolvedIcon = when {
                    rawIcon.isBlank() -> ""
                    rawIcon.startsWith("http://", true) || rawIcon.startsWith("https://", true) -> rawIcon
                    rawIcon.startsWith("//") -> "https:$rawIcon"
                    rawIcon.startsWith("/") && repoRoot.isNotBlank() -> "$repoRoot$rawIcon"
                    repoRoot.isNotBlank() -> "$repoRoot/$rawIcon"
                    else -> rawIcon
                }
                list.add(
                    VegaProvider(
                        display_name = displayName.ifBlank { value },
                        value = value,
                        version = version,
                        icon = resolvedIcon,
                        type = obj.optString("type").ifBlank { "global" },
                        disabled = obj.optBoolean("disabled")
                    )
                )
            }
        }
        return list
    }

    private fun containsIptvChannelObject(element: JsonElement, depth: Int = 0): Boolean {
        if (depth > 5) return false
        when {
            element.isJsonObject -> {
                val obj = element.asJsonObject
                val hasChannelSignal = IPTV_CHANNEL_FIELDS.any { obj.has(it) }
                val hasStreamUrl = IPTV_URL_KEYS.any { key ->
                    val v = obj.optString(key).trim()
                    isHttpUrl(v) && (
                        STREAM_URL_EXTENSIONS.any { ext -> v.lowercase().substringBefore("?").endsWith(ext) } ||
                            hasChannelSignal
                        )
                }
                if (hasStreamUrl) return true
                for ((_, child) in obj.entrySet()) {
                    if ((child.isJsonObject || child.isJsonArray) &&
                        containsIptvChannelObject(child, depth + 1)
                    ) {
                        return true
                    }
                }
            }
            element.isJsonArray -> {
                val arr = element.asJsonArray
                val sampleLimit = minOf(arr.size(), 10)
                for (i in 0 until sampleLimit) {
                    if (containsIptvChannelObject(arr[i], depth + 1)) return true
                }
            }
        }
        return false
    }

    private fun resolveGitHubRepoRoot(url: String): String? {
        val trimmed = url.trim().removeSuffix("/")
        val lower = trimmed.lowercase()
        if (lower.startsWith("https://github.com/") || lower.startsWith("http://github.com/")) {
            val prefix = if (lower.startsWith("https://")) "https://github.com/".length else "http://github.com/".length
            val parts = trimmed.substring(prefix).split("/")
            if (parts.size >= 2) {
                val author = if (parts[0].equals("vega", true)) "vega-org" else parts[0]
                val branch = if (parts.size >= 4 && parts[2] == "tree") parts[3] else "main"
                return "https://raw.githubusercontent.com/$author/${parts[1]}/refs/heads/$branch"
            }
        }
        if (lower.startsWith("https://raw.githubusercontent.com/") ||
            lower.startsWith("http://raw.githubusercontent.com/")
        ) {
            return trimmed
        }
        return null
    }

    private fun parseJsonSafely(raw: String): JsonElement? {
        val trimmed = raw.trim()
        val firstBrace = trimmed.indexOf('{')
        val firstBracket = trimmed.indexOf('[')
        val start = when {
            firstBrace != -1 && firstBracket != -1 -> minOf(firstBrace, firstBracket)
            firstBrace != -1 -> firstBrace
            else -> firstBracket
        }
        if (start == -1) return null
        return try {
            JsonParser.parseString(trimmed.substring(start))
        } catch (_: Exception) {
            null
        }
    }

    private fun isHttpUrl(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

    private fun JsonObject.optString(key: String): String {
        val el = get(key) ?: return ""
        return if (el.isJsonPrimitive) el.asString else ""
    }

    private fun JsonObject.optBoolean(key: String): Boolean {
        val el = get(key) ?: return false
        return if (el.isJsonPrimitive && el.asJsonPrimitive.isBoolean) el.asBoolean else false
    }
}
