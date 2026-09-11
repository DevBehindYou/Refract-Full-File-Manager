package com.devbehindyou.refract.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.devbehindyou.refract.MainActivity
import com.devbehindyou.refract.R
import com.devbehindyou.refract.domain.model.OperationProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class FileOperationService : Service() {

    inner class LocalBinder : Binder() {
        val service: FileOperationService get() = this@FileOperationService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            onCancelRequested?.invoke()
            stopForegroundService()
            return START_NOT_STICKY
        }

        val notification = buildNotification(title = "File Operation in progress", text = "Initializing...", progress = null)
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)

        return START_NOT_STICKY
    }

    fun updateProgress(title: String, progress: OperationProgress?) {
        val text = if (progress != null && progress.itemsTotal > 0) {
            val name = progress.currentName ?: ""
            "Processing ${progress.itemsDone + 1} of ${progress.itemsTotal} $name"
        } else {
            progress?.currentName ?: "Processing files..."
        }
        val notification = buildNotification(title, text, progress)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun stopForegroundService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "File Operations",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of background file operations"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        title: String,
        text: String,
        progress: OperationProgress?
    ): Notification {
        val cancelIntent = Intent(this, FileOperationService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (progress != null && progress.itemsTotal > 0) {
            builder.setProgress(progress.itemsTotal, progress.itemsDone, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    companion object {
        const val CHANNEL_ID = "refract_file_operations"
        const val NOTIFICATION_ID = 1001
        const val ACTION_CANCEL = "com.devbehindyou.refract.action.CANCEL_OPERATION"

        var onCancelRequested: (() -> Unit)? = null
    }
}
