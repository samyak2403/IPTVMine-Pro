package com.samyak.updater

import android.net.Uri

/**
 * Holds information about an available update from GitHub Releases.
 */
data class UpdateInfo(
    /** The latest version tag from GitHub (e.g., "1.0.2") */
    val latestVersion: String,
    /** The currently installed app version (e.g., "1.0.0") */
    val currentVersion: String,
    /** Direct download URL for the APK asset */
    val apkUrl: String,
    /** Size of the APK file in bytes */
    val apkSize: Long,
    /** Release notes / changelog body */
    val releaseNotes: String,
    /** Release title name */
    val releaseName: String,
    /** Whether the latest version is newer than the current version */
    val isUpdateAvailable: Boolean
) {
    /**
     * Returns a human-readable APK file size string.
     */
    val formattedApkSize: String
        get() {
            val kb = apkSize / 1024.0
            val mb = kb / 1024.0
            return when {
                mb >= 1.0 -> String.format("%.1f MB", mb)
                kb >= 1.0 -> String.format("%.1f KB", kb)
                else -> "$apkSize B"
            }
        }
}

/**
 * Represents the current state of an APK download.
 */
sealed class DownloadState {
    /** No download in progress */
    data object Idle : DownloadState()

    /** Download is actively in progress */
    data class Downloading(val progress: Int) : DownloadState()

    /** Download completed successfully */
    data class Downloaded(val uri: Uri) : DownloadState()

    /** Download or installation failed */
    data class Failed(val message: String) : DownloadState()

    /** APK installation has been triggered */
    data object Installing : DownloadState()
}
