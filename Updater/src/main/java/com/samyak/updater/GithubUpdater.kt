package com.samyak.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Main entry point for the GitHub in-app update system.
 *
 * Usage:
 * ```kotlin
 * val updater = GithubUpdater("samyak2403", "IPTVMine-Pro")
 * val result = updater.checkForUpdate(context)
 * result.onSuccess { updateInfo ->
 *     if (updateInfo.isUpdateAvailable) {
 *         // Show update dialog
 *     }
 * }
 * ```
 *
 * @param owner GitHub repository owner
 * @param repo GitHub repository name
 */
class GithubUpdater(
    private val owner: String,
    private val repo: String
) {

    companion object {
        /** Session-level cache for the update check result */
        private val cachedResult = AtomicReference<UpdateInfo?>(null)

        /**
         * Clears the cached update check result, forcing a fresh API call
         * on the next [checkForUpdate] invocation.
         */
        fun clearCache() {
            cachedResult.set(null)
        }
    }

    /**
     * Checks for available updates by comparing the installed app version
     * against the latest GitHub release.
     *
     * Results are cached for the app session — subsequent calls return
     * the cached result without hitting the network.
     *
     * @param context Android context (used to read the app's version name)
     * @return Result containing [UpdateInfo] with update availability details
     */
    suspend fun checkForUpdate(context: Context): Result<UpdateInfo> {
        // Return cached result if available
        cachedResult.get()?.let { cached ->
            return Result.success(cached)
        }

        // Get current installed version
        val currentVersion = try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName, PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: "0.0.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "0.0.0"
        }

        // Fetch latest release from GitHub
        val releaseResult = GithubReleaseService.getLatestRelease(owner, repo)

        return releaseResult.map { release ->
            val apkAsset = release.apkAsset
            val latestVersion = release.version

            val updateInfo = UpdateInfo(
                latestVersion = latestVersion,
                currentVersion = currentVersion,
                apkUrl = apkAsset?.browserDownloadUrl ?: "",
                apkSize = apkAsset?.size ?: 0L,
                releaseNotes = release.body ?: "No release notes available.",
                releaseName = release.name ?: "Release $latestVersion",
                isUpdateAvailable = apkAsset != null && isNewerVersion(currentVersion, latestVersion)
            )

            // Cache the result for the session
            cachedResult.set(updateInfo)
            updateInfo
        }
    }

    /**
     * Downloads the APK using Android's DownloadManager and emits progress updates.
     *
     * @param context Android context
     * @param updateInfo The update info containing the APK download URL
     * @return Flow of [DownloadState] representing download progress
     */
    fun downloadApk(context: Context, updateInfo: UpdateInfo): Flow<DownloadState> = callbackFlow {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        // Clean up any previous APK files
        val apkFileName = "${repo}-${updateInfo.latestVersion}.apk"
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val existingFile = File(downloadsDir, apkFileName)
        if (existingFile.exists()) {
            existingFile.delete()
        }

        // Create download request
        val request = DownloadManager.Request(Uri.parse(updateInfo.apkUrl)).apply {
            setTitle("Downloading ${updateInfo.releaseName}")
            setDescription("Downloading update v${updateInfo.latestVersion}")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, apkFileName)
            setMimeType("application/vnd.android.package-archive")
        }

        val downloadId = downloadManager.enqueue(request)

        // Register completion receiver
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = downloadManager.query(query)
                    if (cursor != null && cursor.moveToFirst()) {
                        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val status = cursor.getInt(statusIndex)
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            val file = File(downloadsDir, apkFileName)
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.updater.provider",
                                file
                            )
                            trySend(DownloadState.Downloaded(uri))
                        } else {
                            val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                            val reason = if (reasonIndex >= 0) cursor.getInt(reasonIndex) else -1
                            trySend(DownloadState.Failed("Download failed (reason: $reason)"))
                        }
                        cursor.close()
                    }
                }
            }
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )

        // Poll for download progress
        val progressJob = launch(Dispatchers.IO) {
            var isDownloading = true
            while (isDownloading && isActive) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor: Cursor? = downloadManager.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val status = cursor.getInt(statusIndex)

                    when (status) {
                        DownloadManager.STATUS_RUNNING -> {
                            val totalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                            val downloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                            val total = cursor.getLong(totalIndex)
                            val downloaded = cursor.getLong(downloadedIndex)
                            if (total > 0) {
                                val progress = ((downloaded * 100) / total).toInt()
                                trySend(DownloadState.Downloading(progress))
                            }
                        }
                        DownloadManager.STATUS_SUCCESSFUL,
                        DownloadManager.STATUS_FAILED -> {
                            isDownloading = false
                        }
                        DownloadManager.STATUS_PENDING -> {
                            trySend(DownloadState.Downloading(0))
                        }
                    }
                    cursor.close()
                }
                delay(500) // Poll every 500ms
            }
        }

        awaitClose {
            progressJob.cancel()
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
                // Receiver already unregistered
            }
        }
    }.flowOn(Dispatchers.Main)

    /**
     * Triggers APK installation via an intent.
     *
     * @param context Android context
     * @param uri Content URI of the downloaded APK (from FileProvider)
     */
    fun installApk(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    /**
     * Compares two semantic version strings.
     *
     * Supports versions like "1.0", "1.0.2", "2.1.0.1".
     * Each segment is compared numerically from left to right.
     *
     * @param current The currently installed version
     * @param latest The latest version from GitHub
     * @return true if [latest] is strictly newer than [current]
     */
    internal fun isNewerVersion(current: String, latest: String): Boolean {
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }

        val maxLength = maxOf(currentParts.size, latestParts.size)

        for (i in 0 until maxLength) {
            val currentPart = currentParts.getOrElse(i) { 0 }
            val latestPart = latestParts.getOrElse(i) { 0 }

            if (latestPart > currentPart) return true
            if (latestPart < currentPart) return false
        }

        return false // Versions are equal
    }
}
