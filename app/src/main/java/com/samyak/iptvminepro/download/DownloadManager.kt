package com.samyak.iptvminepro.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

object DownloadManager {
    private val gson = Gson()
    private const val FILE_NAME = "downloads.json"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val semaphore = Semaphore(5) // Max 5 downloads at a time
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _downloadTasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val downloadTasks: StateFlow<List<DownloadTask>> = _downloadTasks

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        loadTasks()
        
        // Reset any DOWNLOADING or PENDING tasks back to PENDING on startup to auto-resume
        val unfinished = _downloadTasks.value.filter { 
            it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING 
        }
        
        if (unfinished.isNotEmpty()) {
            _downloadTasks.value = _downloadTasks.value.map {
                if (it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING) {
                    it.copy(status = DownloadStatus.PENDING, speed = "")
                } else it
            }
            saveTasks()
            startServiceIfRunning()
            
            // Queue up all unfinished tasks
            _downloadTasks.value.filter { it.status == DownloadStatus.PENDING }.forEach { task ->
                scope.launch {
                    runTask(task)
                }
            }
        }
    }

    private fun loadTasks() {
        val file = File(appContext.filesDir, FILE_NAME)
        if (file.exists()) {
            try {
                val json = file.readText()
                val type = object : TypeToken<List<DownloadTask>>() {}.type
                val loadedList: List<DownloadTask> = gson.fromJson(json, type) ?: emptyList()
                _downloadTasks.value = loadedList
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @Synchronized
    private fun saveTasks() {
        try {
            val file = File(appContext.filesDir, FILE_NAME)
            val json = gson.toJson(_downloadTasks.value)
            file.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun download(title: String, downloadUrl: String, headers: Map<String, String>? = null) {
        val extension = downloadUrl.substringAfterLast('.', "").substringBefore('?').lowercase().let {
            if (it.isEmpty() || it.length > 5) "mp4" else it
        }
        val safeTitle = title.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
        val fileName = "${safeTitle}_${System.currentTimeMillis()}.$extension"
        val downloadDir = appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: appContext.filesDir
        val savePath = File(downloadDir, fileName).absolutePath

        val newTask = DownloadTask(
            id = UUID.randomUUID().toString(),
            title = title,
            downloadUrl = downloadUrl,
            headers = headers,
            savePath = savePath,
            progress = 0f,
            speed = "",
            status = DownloadStatus.PENDING,
            totalBytes = 0L,
            downloadedBytes = 0L,
            addedAt = System.currentTimeMillis()
        )

        _downloadTasks.value = _downloadTasks.value + newTask
        saveTasks()
        startServiceIfRunning()

        scope.launch {
            runTask(newTask)
        }
    }

    private fun startServiceIfRunning() {
        DownloadService.start(appContext)
    }

    private suspend fun runTask(task: DownloadTask) {
        semaphore.withPermit {
            // Check if still pending (might have been cancelled)
            val currentTask = _downloadTasks.value.find { it.id == task.id } ?: return@withPermit
            if (currentTask.status == DownloadStatus.COMPLETED || currentTask.status == DownloadStatus.FAILED) return@withPermit
            
            updateTaskStatus(task.id, DownloadStatus.DOWNLOADING)
            val success = performDownload(task)
            if (success) {
                // Now, copy the completed file to public storage if possible
                val publicUri = saveToPublicStorage(appContext, File(task.savePath), task.title)
                if (publicUri != null) {
                    _downloadTasks.value = _downloadTasks.value.map {
                        if (it.id == task.id) {
                            it.copy(status = DownloadStatus.COMPLETED, savePath = publicUri.toString())
                        } else it
                    }
                    saveTasks()
                    // Delete the temporary file from app-private storage since it's now in public storage
                    try {
                        File(task.savePath).delete()
                    } catch (e: Exception) {}
                } else {
                    updateTaskStatus(task.id, DownloadStatus.COMPLETED)
                }
            } else {
                // If it was cancelled, status might be changed to FAILED or removed
                val updatedTask = _downloadTasks.value.find { it.id == task.id }
                if (updatedTask != null && updatedTask.status == DownloadStatus.DOWNLOADING) {
                    updateTaskStatus(task.id, DownloadStatus.FAILED)
                }
            }
            checkStopService()
        }
    }

    private fun saveToPublicStorage(context: Context, srcFile: File, title: String): Uri? {
        val resolver = context.contentResolver
        val extension = srcFile.extension.let { if (it.isEmpty()) "mp4" else it }
        val mimeType = when (extension) {
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            else -> "video/mp4"
        }
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, srcFile.name)
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/IPTVMine Pro")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "IPTVMine Pro")
                if (!publicDir.exists()) {
                    publicDir.mkdirs()
                }
                val destFile = File(publicDir, srcFile.name)
                @Suppress("DEPRECATION")
                put(MediaStore.Video.Media.DATA, destFile.absolutePath)
            }
        }
        
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        
        var uri: Uri? = null
        try {
            uri = resolver.insert(collection, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    srcFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                return uri
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (uri != null) {
                try {
                    resolver.delete(uri, null, null)
                } catch (ex: Exception) {}
            }
        }
        return null
    }

    private fun checkStopService() {
        val hasActive = _downloadTasks.value.any { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING }
        if (!hasActive) {
            DownloadService.stop(appContext)
        }
    }

    private fun updateTaskStatus(id: String, status: DownloadStatus) {
        _downloadTasks.value = _downloadTasks.value.map {
            if (it.id == id) {
                it.copy(status = status, speed = if (status == DownloadStatus.COMPLETED) "" else it.speed)
            } else it
        }
        saveTasks()
    }

    private fun updateTaskProgress(id: String, progress: Float, downloaded: Long, total: Long, speed: String) {
        _downloadTasks.value = _downloadTasks.value.map {
            if (it.id == id) {
                it.copy(
                    progress = progress,
                    downloadedBytes = downloaded,
                    totalBytes = total,
                    speed = speed
                )
            } else it
        }
    }

    fun cancelTask(id: String) {
        val task = _downloadTasks.value.find { id == it.id } ?: return
        val pathOrUri = task.savePath
        if (pathOrUri.startsWith("content://")) {
            try {
                val uri = Uri.parse(pathOrUri)
                appContext.contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            val file = File(pathOrUri)
            if (file.exists()) {
                file.delete()
            }
        }
        _downloadTasks.value = _downloadTasks.value.filter { it.id != id }
        saveTasks()
        checkStopService()
    }

    fun deleteTask(id: String) {
        val task = _downloadTasks.value.find { id == it.id } ?: return
        val pathOrUri = task.savePath
        if (pathOrUri.startsWith("content://")) {
            try {
                val uri = Uri.parse(pathOrUri)
                appContext.contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            val file = File(pathOrUri)
            if (file.exists()) {
                file.delete()
            }
        }
        _downloadTasks.value = _downloadTasks.value.filter { it.id != id }
        saveTasks()
    }

    private suspend fun performDownload(task: DownloadTask): Boolean {
        val requestBuilder = Request.Builder().url(task.downloadUrl)
        task.headers?.forEach { (key, value) ->
            requestBuilder.addHeader(key, value)
        }
        val request = requestBuilder.build()

        var success = false
        var inputStream: java.io.InputStream? = null
        var outputStream: FileOutputStream? = null
        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return false
            val body = response.body ?: return false
            val contentLength = body.contentLength()
            
            inputStream = body.byteStream()
            val file = File(task.savePath)
            outputStream = FileOutputStream(file)

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var downloaded = 0L
            var lastUpdate = System.currentTimeMillis()
            var lastDownloadedBytes = 0L
            var speedStr = ""

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                // Check if task was cancelled/removed
                val current = _downloadTasks.value.find { it.id == task.id }
                if (current == null || current.status != DownloadStatus.DOWNLOADING) {
                    return false
                }

                outputStream.write(buffer, 0, bytesRead)
                downloaded += bytesRead

                val now = System.currentTimeMillis()
                if (now - lastUpdate >= 1000) {
                    val progress = if (contentLength > 0) downloaded.toFloat() / contentLength else 0f
                    val speedBytes = downloaded - lastDownloadedBytes
                    speedStr = formatSpeed(speedBytes)
                    updateTaskProgress(task.id, progress, downloaded, contentLength, speedStr)
                    
                    lastUpdate = now
                    lastDownloadedBytes = downloaded
                    
                    // Trigger notification update via service if running
                    DownloadService.updateNotification(appContext)
                }
            }
            outputStream.flush()
            
            // Final progress update
            val progress = 1f
            updateTaskProgress(task.id, progress, downloaded, downloaded, "")
            success = true
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try {
                inputStream?.close()
            } catch (e: Exception) {}
            try {
                outputStream?.close()
            } catch (e: Exception) {}
        }
        return success
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        val kb = bytesPerSec / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format("%.1f MB/s", mb)
            kb >= 1.0 -> String.format("%.0f KB/s", kb)
            else -> "$bytesPerSec B/s"
        }
    }
}
