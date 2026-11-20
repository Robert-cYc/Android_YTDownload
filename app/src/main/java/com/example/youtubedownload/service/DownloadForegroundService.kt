package com.example.youtubedownload.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.youtubedownload.MainActivity
import com.example.youtubedownload.R
import com.example.youtubedownload.data.DownloadCoordinator
import com.example.youtubedownload.domain.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DownloadForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observerJob: Job? = null
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        ensureChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            DownloadCoordinator.cancelActive(applicationContext)
            stopSelfSafely()
            return START_NOT_STICKY
        }

        val initialNotification = buildProgressNotification(
            title = "YouTube Downloader",
            text = "Preparing download...",
            progress = -1
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID_PROGRESS,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID_PROGRESS, initialNotification)
        }

        if (observerJob == null) {
            observerJob = serviceScope.launch {
                var wasRunning = false
                DownloadCoordinator.state.collectLatest { state ->
                    if (state.isRunning) {
                        wasRunning = true
                        val title = state.activeTitle.ifBlank { "YouTube Downloader" }
                        val (text, pct) = when (val st = state.activeStatus) {
                            DownloadStatus.Initializing -> "Preparing download..." to -1
                            is DownloadStatus.Downloading -> {
                                val pctInt = st.progress.toInt().coerceIn(0, 100)
                                val label = if (st.progress >= 0f) {
                                    "$pctInt%" + if (st.etaSeconds > 0) " • ETA ${st.etaSeconds}s" else ""
                                } else {
                                    "Processing..."
                                }
                                label to if (st.progress >= 0f) pctInt else -1
                            }
                            else -> "Working..." to -1
                        }
                        notificationManager.notify(
                            NOTIFICATION_ID_PROGRESS,
                            buildProgressNotification(title, text, pct)
                        )
                    } else if (wasRunning) {
                        postFinalNotificationIfNeeded(state.activeStatus)
                        stopSelfSafely()
                    }
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun postFinalNotificationIfNeeded(status: DownloadStatus) {
        when (status) {
            is DownloadStatus.Completed -> {
                val msg = if (status.fileCount > 1) {
                    "${status.fileCount} files saved to ${status.savedLocation}"
                } else {
                    status.fileName
                }
                val notification = NotificationCompat.Builder(this, CHANNEL_DONE)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("Download Completed")
                    .setContentText(msg)
                    .setContentIntent(createOpenAppIntent())
                    .setAutoCancel(true)
                    .build()
                notificationManager.notify(NOTIFICATION_ID_DONE, notification)
            }
            is DownloadStatus.Error -> {
                val notification = NotificationCompat.Builder(this, CHANNEL_DONE)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("Download Failed")
                    .setContentText(status.message)
                    .setContentIntent(createOpenAppIntent())
                    .setAutoCancel(true)
                    .build()
                notificationManager.notify(NOTIFICATION_ID_DONE, notification)
            }
            else -> Unit
        }
    }

    private fun buildProgressNotification(title: String, text: String, progress: Int): Notification {
        val cancelIntent = Intent(this, DownloadForegroundService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            0,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(createOpenAppIntent())
            .setProgress(100, progress.coerceAtLeast(0), progress < 0)
            .addAction(0, "Cancel", cancelPendingIntent)
            .build()
    }

    private fun createOpenAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun stopSelfSafely() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val progressChannel = NotificationChannel(
            CHANNEL_PROGRESS,
            "Active Downloads",
            NotificationManager.IMPORTANCE_LOW
        )
        val doneChannel = NotificationChannel(
            CHANNEL_DONE,
            "Download Results",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(progressChannel)
        notificationManager.createNotificationChannel(doneChannel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        observerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_PROGRESS = "yt_download_progress"
        private const val CHANNEL_DONE = "yt_download_done"
        private const val NOTIFICATION_ID_PROGRESS = 1001
        private const val NOTIFICATION_ID_DONE = 1002
        private const val ACTION_CANCEL = "com.example.youtubedownload.ACTION_CANCEL_DOWNLOAD"
    }
}
