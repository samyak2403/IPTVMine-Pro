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

class DownloadService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 202
        private const val CHANNEL_ID = "downloads"
        private const val ACTION_UPDATE = "com.samyak.iptvminepro.ACTION_UPDATE_NOTIF"

        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            context.stopService(intent)
        }

        fun updateNotification(context: Context) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_UPDATE
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_UPDATE) {
            updateNotification()
        } else {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for movie downloads"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
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
                val activeCount = activeTasks.size
                val totalCount = activeCount + pendingTasks.size
                if (totalCount > 1) {
                    "${task.title} ($pct%) + ${totalCount - 1} more"
                } else {
                    "${task.title} ($pct%)"
                }
            }
            pendingTasks.isNotEmpty() -> "Waiting in queue..."
            else -> "Downloads active"
        }

        val progress = if (activeTasks.isNotEmpty()) {
            (activeTasks[0].progress * 100).toInt()
        } else {
            0
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setProgress(100, progress, activeTasks.isEmpty() && pendingTasks.isNotEmpty())
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }
}
