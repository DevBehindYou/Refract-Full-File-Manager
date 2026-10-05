package com.devbehindyou.atomicfilemanager.service

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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.devbehindyou.atomicfilemanager.AtomicApp
import com.devbehindyou.atomicfilemanager.MainActivity
import com.devbehindyou.atomicfilemanager.domain.model.OperationProgress
import com.devbehindyou.atomicfilemanager.domain.model.OperationStatus
import com.devbehindyou.atomicfilemanager.domain.model.OperationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class FileOperationService : Service() {
    inner class LocalBinder : Binder() {
        val service: FileOperationService get() = this@FileOperationService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val queue get() = (application as AtomicApp).container.operationQueue

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val notification = buildNotification(title = "Preparing file operation", text = "Starting…", progress = null)
        val serviceType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        // startForeground comes first on every start, as Android requires, even for a cancel tap.
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)
        observeQueueOnce()
        if (intent?.action == ACTION_CANCEL) {
            queue.active.value?.let { queue.cancel(it.operation.id) }
        }
        return START_NOT_STICKY
    }

    private var observing = false

    /**
     * Mirrors the queue: shows the running operation and stops once nothing is running or waiting.
     * Called only after startForeground, so a queue that is already idle can't stop the service first.
     */
    private fun observeQueueOnce() {
        if (observing) return
        observing = true
        serviceScope.launch {
            combine(queue.active, queue.queued) { active, waiting -> active to waiting.size }.collect {
                    (active, waiting) ->
                if (active == null) {
                    if (waiting == 0) stopForegroundService()
                } else {
                    val status = active.status
                    if (status is OperationStatus.AwaitingInput) {
                        showActionNeeded(status.conflict.source.name)
                    } else {
                        updateProgress(titleFor(active.operation.type, waiting), progressOf(status))
                    }
                }
            }
        }
    }

    fun updateProgress(
        title: String,
        progress: OperationProgress?,
    ) {
        val text =
            if (progress != null && progress.itemsTotal > 0) {
                val name = progress.currentName ?: ""
                "${minOf(progress.itemsDone + 1, progress.itemsTotal)} of ${progress.itemsTotal} · $name"
            } else {
                progress?.currentName ?: "Working…"
            }
        val notification = buildNotification(title, text, progress)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /** A name clash waits for the user; tapping the notification opens the app, which shows the choice. */
    private fun showActionNeeded(name: String) {
        val notification =
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("Action needed")
                .setContentText("“$name” already exists. Open to choose what to do.")
                .setOngoing(true)
                .setContentIntent(openAppIntent())
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent())
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
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
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "File operations",
                    NotificationManager.IMPORTANCE_LOW,
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
        progress: OperationProgress?,
    ): Notification {
        val builder =
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(openAppIntent())
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent())
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_LOW)

        if (progress != null && progress.itemsTotal > 0) {
            builder.setProgress(progress.itemsTotal, progress.itemsDone, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    private fun cancelIntent(): PendingIntent =
        PendingIntent.getService(
            this,
            1,
            Intent(this, FileOperationService::class.java).apply { action = ACTION_CANCEL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_OPERATIONS)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val CHANNEL_ID = "atomic_file_operations"
        const val NOTIFICATION_ID = 1001
        const val ACTION_CANCEL = "com.devbehindyou.atomicfilemanager.action.CANCEL_OPERATION"

        /** Starts the service; the caller must be in the foreground (the user just asked for an operation). */
        fun start(context: Context) {
            val intent = Intent(context, FileOperationService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: IllegalStateException) {
                // Android 12+ refuses background starts; the operation still runs, just without the notification.
                Log.w("FileOperationService", "Could not start the operation notification", e)
            }
        }

        private fun titleFor(
            type: OperationType,
            waiting: Int,
        ): String {
            val verb =
                when (type) {
                    OperationType.COPY -> "Copying"
                    OperationType.MOVE -> "Moving"
                    OperationType.DELETE -> "Deleting"
                    OperationType.COMPRESS -> "Compressing"
                    OperationType.EXTRACT -> "Extracting"
                    else -> "Working on files"
                }
            return if (waiting > 0) "$verb · $waiting more waiting" else verb
        }

        private fun progressOf(status: OperationStatus): OperationProgress? =
            when (status) {
                is OperationStatus.Running -> status.progress
                is OperationStatus.Paused -> status.progress
                else -> null
            }
    }
}
