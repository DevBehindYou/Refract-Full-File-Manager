package com.devbehindyou.atomicfilemanager.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One launchable app, as the app manager lists it (ALL_IN_ONE_PLAN.md 2.7). */
data class InstalledApp(
    val label: String,
    val packageName: String,
    val versionName: String?,
    /** Base APK plus any split APKs, in bytes. */
    val sizeBytes: Long,
    val installedAt: Long,
    val updatedAt: Long,
    val sourceDir: String,
    val splitSourceDirs: List<String>,
    val isSystem: Boolean,
) {
    val isSplit: Boolean get() = splitSourceDirs.isNotEmpty()
}

/**
 * Reads launchable apps through the manifest's launcher `<queries>`; `QUERY_ALL_PACKAGES` is never
 * requested, as Play restricts it. Exports copy the installed APK files as they are.
 */
class InstalledAppsReader(private val context: Context) {
    suspend fun list(): List<InstalledApp> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

            @Suppress("DEPRECATION")
            val activities = pm.queryIntentActivities(launcher, 0)
            activities
                .map { it.activityInfo.packageName }
                .distinct()
                .mapNotNull { name -> runCatching { toApp(pm, packageInfo(pm, name)) }.getOrNull() }
        }

    /**
     * Copies the APK into Download/Atomic File Manager/Apps. A split app (an app bundle install) is
     * written as one `.apks` ZIP of all its parts, which needs a split-aware installer.
     */
    suspend fun export(app: InstalledApp): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION")
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val folder = File(downloads, "Atomic File Manager/Apps").apply { mkdirs() }
                val target = File(folder, AppManagerRules.exportFileName(app))
                if (app.isSplit) {
                    ZipOutputStream(target.outputStream().buffered()).use { zip ->
                        (listOf(app.sourceDir) + app.splitSourceDirs).forEach { path ->
                            zip.putNextEntry(ZipEntry(File(path).name))
                            File(path).inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                } else {
                    File(app.sourceDir).copyTo(target, overwrite = true)
                }
                target
            }
        }

    private fun packageInfo(
        pm: PackageManager,
        name: String,
    ): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(name, PackageManager.PackageInfoFlags.of(0))
        } else {
            pm.getPackageInfo(name, 0)
        }

    private fun toApp(
        pm: PackageManager,
        info: PackageInfo,
    ): InstalledApp? {
        val app = info.applicationInfo ?: return null
        val splits = app.splitSourceDirs?.toList().orEmpty()
        val size = (listOf(app.sourceDir) + splits).sumOf { File(it).length() }
        return InstalledApp(
            label = app.loadLabel(pm).toString(),
            packageName = info.packageName,
            versionName = info.versionName,
            sizeBytes = size,
            installedAt = info.firstInstallTime,
            updatedAt = info.lastUpdateTime,
            sourceDir = app.sourceDir,
            splitSourceDirs = splits,
            isSystem = app.flags and ApplicationInfo.FLAG_SYSTEM != 0,
        )
    }
}

enum class AppSort(val title: String) { NAME("Name"), SIZE("Size"), INSTALLED("Installed"), UPDATED("Updated") }

/** Pure list and naming rules for the app manager; unit-tested. */
object AppManagerRules {
    private val unsafe = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]")

    fun visible(
        apps: List<InstalledApp>,
        query: String,
        showSystem: Boolean,
        sort: AppSort,
    ): List<InstalledApp> {
        val q = query.trim().lowercase(Locale.ROOT)
        val shown =
            apps.filter { app ->
                val matches =
                    q.isEmpty() || q in app.label.lowercase(Locale.ROOT) || q in app.packageName.lowercase(Locale.ROOT)
                (showSystem || !app.isSystem) && matches
            }
        return when (sort) {
            AppSort.NAME -> shown.sortedBy { it.label.lowercase(Locale.ROOT) }
            AppSort.SIZE -> shown.sortedByDescending { it.sizeBytes }
            AppSort.INSTALLED -> shown.sortedByDescending { it.installedAt }
            AppSort.UPDATED -> shown.sortedByDescending { it.updatedAt }
        }
    }

    /** "Maps 11.2.apk", or "… .apks" for a split app; characters a file name can't hold become "_". */
    fun exportFileName(app: InstalledApp): String {
        val version = app.versionName?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
        val base = (app.label.ifBlank { app.packageName } + version).replace(unsafe, "_").trim()
        return base + if (app.isSplit) ".apks" else ".apk"
    }
}
