package com.samyak.iptvminepro.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.samyak.iptvminepro.MainActivity
import com.samyak.iptvminepro.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DownloadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var observeJob: Job? = null

    companion object {
        private const val NOTIFICATION_ID = 202
        private const val CHANNEL_ID = "downloads"

        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Foreground service start can be blocked when backgrounded on newer
                // Android versions; downloads still run in DownloadManager's own scope.
                e.printStackTrace()
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, DownloadService::class.java))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Notifications now update reactively via the service's flow observer.
        fun updateNotification(context: Context) { /* no-op */ }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Promote to foreground immediately to satisfy the startForegroundService
        // contract on O+ and avoid a "did not call startForeground" crash.
        promoteToForeground(buildNotification())

        if (observeJob == null) {
            observeJob = serviceScope.launch {
                DownloadManager.downloadTasks.collect { tasks ->
                    val hasActive = tasks.any {
                        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING
                    }
                    if (hasActive) {
                        notifyProgress()
                    } else {
                        stopSelfSafely()
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        observeJob?.cancel()
        observeJob = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun promoteToForeground(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun notifyProgress() {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopSelfSafely() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for movie downloads"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val tasks = DownloadManager.downloadTasks.value
        val activeTasks = tasks.filter { it.status == DownloadStatus.DOWNLOADING }
        val pendingTasks = tasks.filter { it.status == DownloadStatus.PENDING }

        val title = "Downloading Movies"
        val contentText = when {
            activeTasks.isNotEmpty() -> {
                val task = activeTasks[0]
                val pct = (task.progress * 100).toInt()
                val totalCount = activeTasks.size + pendingTasks.size
                if (totalCount > 1) "${task.title} ($pct%) + ${totalCount - 1} more"
                else "${task.title} ($pct%)"
            }
            pendingTasks.isNotEmpty() -> "Waiting in queue..."
            else -> "Downloads active"
        }

        val progress = if (activeTasks.isNotEmpty()) (activeTasks[0].progress * 100).toInt() else 0
        val indeterminate = activeTasks.isEmpty() && pendingTasks.isNotEmpty()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setProgress(100, progress, indeterminate)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }
}
