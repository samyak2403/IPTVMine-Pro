package com.samyak.iptvminepro.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Fetches per-episode still thumbnails keyed by (seasonNumber, episodeNumber)
 * using Cinemeta (by IMDb ID or title search) with a TVMaze fallback.
 */
object EpisodeThumbnailResolver {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun fetchEpisodeThumbnails(
        imdbId: String,
        seriesTitle: String
    ): Map<Pair<Int, Int>, String> = withContext(Dispatchers.IO) {
        val cleanImdb = imdbId.trim().takeIf { it.startsWith("tt") }
        val searchableTitle = TitleUtils.getSearchableTitle(seriesTitle)

        // 1. Try Cinemeta directly if we have an IMDb ID, otherwise search Cinemeta by title
        val resolvedImdbId = cleanImdb ?: searchCinemetaImdbId(searchableTitle)
        if (!resolvedImdbId.isNullOrBlank()) {
            val cinemetaThumbs = fetchFromCinemeta(resolvedImdbId)
            if (cinemetaThumbs.isNotEmpty()) {
                return@withContext cinemetaThumbs
            }
        }

        // 2. Fallback to TVMaze single-search with embedded episodes
        if (searchableTitle.isNotBlank()) {
            val tvMazeThumbs = fetchFromTvMaze(searchableTitle)
            if (tvMazeThumbs.isNotEmpty()) {
                return@withContext tvMazeThumbs
            }
        }

        emptyMap()
    }

    private fun searchCinemetaImdbId(title: String): String? {
        if (title.isBlank()) return null
        return try {
            val encoded = URLEncoder.encode(title, "UTF-8")
            val body = getJson("https://v3-cinemeta.strem.io/catalog/series/top/search=$encoded.json")
                ?: return null
            val metas = body.optJSONArray("metas") ?: return null
            for (i in 0 until metas.length()) {
                val item = metas.optJSONObject(i) ?: continue
                val id = item.optString("imdb_id", "").ifBlank { item.optString("id", "") }
                if (id.startsWith("tt")) return id
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchFromCinemeta(imdbId: String): Map<Pair<Int, Int>, String> {
        return try {
            val root = getJson("https://v3-cinemeta.strem.io/meta/series/$imdbId.json")
                ?: return emptyMap()
            val meta = root.optJSONObject("meta") ?: return emptyMap()
            val videos = meta.optJSONArray("videos") ?: return emptyMap()
            val result = HashMap<Pair<Int, Int>, String>(videos.length())
            for (i in 0 until videos.length()) {
                val video = videos.optJSONObject(i) ?: continue
                val season = video.optInt("season", -1)
                val episode = video.optInt("episode", -1).let {
                    if (it > 0) it else video.optInt("number", -1)
                }
                val thumb = video.optString("thumbnail", "").trim()
                if (season > 0 && episode > 0 && thumb.isNotBlank()) {
                    result[season to episode] = thumb
                }
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun fetchFromTvMaze(title: String): Map<Pair<Int, Int>, String> {
        return try {
            val encoded = URLEncoder.encode(title, "UTF-8")
            val root = getJson("https://api.tvmaze.com/singlesearch/shows?q=$encoded&embed=episodes")
                ?: return emptyMap()
            val embedded = root.optJSONObject("_embedded") ?: return emptyMap()
            val episodes = embedded.optJSONArray("episodes") ?: return emptyMap()
            val result = HashMap<Pair<Int, Int>, String>(episodes.length())
            for (i in 0 until episodes.length()) {
                val ep = episodes.optJSONObject(i) ?: continue
                val season = ep.optInt("season", -1)
                val episode = ep.optInt("number", -1)
                val imageObj = ep.optJSONObject("image") ?: continue
                val thumb = imageObj.optString("medium", "")
                    .ifBlank { imageObj.optString("original", "") }
                    .trim()
                if (season > 0 && episode > 0 && thumb.isNotBlank()) {
                    result[season to episode] = thumb
                }
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun getJson(url: String): JSONObject? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android) IPTVMinePro")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string()?.takeIf { it.isNotBlank() } ?: return null
            return JSONObject(body)
        }
    }
}
