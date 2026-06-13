package com.samyak.iptvminepro.download

data class DownloadTask(
    val id: String,
    val title: String,
    val downloadUrl: String,
    val headers: Map<String, String>?,
    val savePath: String,
    var progress: Float,
    var speed: String,
    var status: DownloadStatus,
    var totalBytes: Long,
    var downloadedBytes: Long,
    val addedAt: Long
)

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED
}
