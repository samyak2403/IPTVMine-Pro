package com.samyak.updater

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Service for fetching the latest release from GitHub Releases API.
 * Uses OkHttp for networking and Gson for JSON parsing.
 */
object GithubReleaseService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Fetches the latest release from a GitHub repository.
     *
     * @param owner The GitHub repository owner (e.g., "samyak2403")
     * @param repo The GitHub repository name (e.g., "IPTVMine-Pro")
     * @return Result containing the parsed GithubRelease or an error
     */
    suspend fun getLatestRelease(owner: String, repo: String): Result<GithubRelease> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/vnd.github.v3+json")
                    .get()
                    .build()

                val response = client.newCall(request).await()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("GitHub API error: ${response.code} ${response.message}")
                    )
                }

                val body = response.body?.string()
                    ?: return@withContext Result.failure(IOException("Empty response body"))

                val release = gson.fromJson(body, GithubRelease::class.java)
                    ?: return@withContext Result.failure(IOException("Failed to parse release JSON"))

                Result.success(release)
            } catch (e: IOException) {
                Result.failure(IOException("Network error: ${e.message}", e))
            } catch (e: Exception) {
                Result.failure(Exception("Unexpected error: ${e.message}", e))
            }
        }
    }

    /**
     * Extension function to make OkHttp calls suspendable.
     */
    private suspend fun Call.await(): Response {
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                cancel()
            }
            enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isCancelled) {
                        continuation.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }
            })
        }
    }
}

/**
 * Data class representing a GitHub Release API response.
 */
data class GithubRelease(
    @SerializedName("tag_name")
    val tagName: String,

    @SerializedName("name")
    val name: String?,

    @SerializedName("body")
    val body: String?,

    @SerializedName("prerelease")
    val prerelease: Boolean,

    @SerializedName("assets")
    val assets: List<GithubAsset>?
) {
    /**
     * Returns the version string without a leading 'v' prefix.
     */
    val version: String
        get() = tagName.removePrefix("v").removePrefix("V").trim()

    /**
     * Finds the first APK asset from the release.
     */
    val apkAsset: GithubAsset?
        get() = assets?.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
}

/**
 * Data class representing a GitHub Release Asset.
 */
data class GithubAsset(
    @SerializedName("name")
    val name: String,

    @SerializedName("browser_download_url")
    val browserDownloadUrl: String,

    @SerializedName("size")
    val size: Long,

    @SerializedName("content_type")
    val contentType: String?
)
