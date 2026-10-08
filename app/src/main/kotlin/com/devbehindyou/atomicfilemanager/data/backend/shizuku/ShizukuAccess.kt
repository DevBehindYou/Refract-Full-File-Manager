package com.devbehindyou.atomicfilemanager.data.backend.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

/** Where the Labs feature stands, for the Storage screen. */
enum class ShizukuState {
    /** The Labs switch is off (the default). */
    OFF,

    /** Shizuku isn't installed or isn't running. */
    NOT_RUNNING,

    /** Shizuku runs but hasn't allowed this app yet. */
    NEEDS_PERMISSION,
    READY,
}

/** Pure state rules; unit-tested. */
object ShizukuStates {
    fun of(
        enabled: Boolean,
        running: Boolean,
        granted: Boolean,
    ): ShizukuState =
        when {
            !enabled -> ShizukuState.OFF
            !running -> ShizukuState.NOT_RUNNING
            !granted -> ShizukuState.NEEDS_PERMISSION
            else -> ShizukuState.READY
        }
}

/**
 * The Labs switch, Shizuku's permission and the connection to [ShizukuFileService]
 * (ALL_IN_ONE_PLAN.md 4.3). Off unless the user turns it on; nothing talks to Shizuku before that.
 */
class ShizukuAccess(private val context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("atomic_labs", Context.MODE_PRIVATE)
    private val lock = Mutex()
    private var service: IShizukuFileService? = null
    private var connection: ServiceConnection? = null

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
            if (!value) release()
        }

    fun state(): ShizukuState {
        if (!enabled) return ShizukuState.OFF
        val running = runCatching { Shizuku.pingBinder() && !Shizuku.isPreV11() }.getOrDefault(false)
        val granted =
            running &&
                runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
        return ShizukuStates.of(enabled = true, running = running, granted = granted)
    }

    /** Asks Shizuku to allow this app; [onResult] gets true when allowed. */
    fun requestPermission(onResult: (Boolean) -> Unit) {
        val listener =
            object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(
                    requestCode: Int,
                    grantResult: Int,
                ) {
                    if (requestCode != REQUEST_CODE) return
                    Shizuku.removeRequestPermissionResultListener(this)
                    onResult(grantResult == PackageManager.PERMISSION_GRANTED)
                }
            }
        runCatching {
            Shizuku.addRequestPermissionResultListener(listener)
            Shizuku.requestPermission(REQUEST_CODE)
        }.onFailure {
            Shizuku.removeRequestPermissionResultListener(listener)
            onResult(false)
        }
    }

    /** The running file service, started on first use; null when Labs is off or Shizuku isn't ready. */
    suspend fun service(): IShizukuFileService? =
        lock.withLock {
            service?.takeIf { it.asBinder().isBinderAlive }?.let { return@withLock it }
            if (state() != ShizukuState.READY) return@withLock null
            withTimeoutOrNull(BIND_TIMEOUT_MS) { bind() }.also { service = it }
        }

    private suspend fun bind(): IShizukuFileService? =
        suspendCancellableCoroutine { cont ->
            val conn =
                object : ServiceConnection {
                    override fun onServiceConnected(
                        name: ComponentName?,
                        binder: IBinder?,
                    ) {
                        val connected = binder?.takeIf { it.pingBinder() }?.let(IShizukuFileService.Stub::asInterface)
                        if (cont.isActive) cont.resume(connected)
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        service = null
                    }
                }
            connection = conn
            runCatching { Shizuku.bindUserService(args(), conn) }.onFailure { if (cont.isActive) cont.resume(null) }
        }

    /** Stops the service process; used when the Labs switch is turned off. */
    fun release() {
        val conn = connection ?: return
        connection = null
        service = null
        runCatching { Shizuku.unbindUserService(args(), conn, true) }
    }

    private fun args(): Shizuku.UserServiceArgs =
        Shizuku.UserServiceArgs(ComponentName(context.packageName, ShizukuFileService::class.java.name))
            .daemon(false)
            .processNameSuffix("files")
            .version(SERVICE_VERSION)

    private companion object {
        const val KEY_ENABLED = "shizuku_android_data"
        const val REQUEST_CODE = 0x5A
        const val BIND_TIMEOUT_MS = 10_000L

        /** Bump when [ShizukuFileService] changes, so Shizuku restarts the old process. */
        const val SERVICE_VERSION = 1
    }
}
