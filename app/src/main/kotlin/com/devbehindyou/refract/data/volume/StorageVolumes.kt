package com.devbehindyou.refract.data.volume

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import com.devbehindyou.refract.domain.model.StorageType
import com.devbehindyou.refract.domain.model.StorageVolumeInfo
import java.io.File

/**
 * "Volume enumeration" (roadmap Phase 3 deliverable). Lists what storage volumes
 * physically exist on the device — internal, SD card, USB — using
 * `StorageManager.storageVolumes`, never by hardcoding `/storage/emulated/0` or scanning
 * `/storage/[wildcard]` (`ANDROID_STORAGE_RESEARCH.md`'s explicit rule).
 *
 * Deliberately does **not** resolve [StorageVolumeInfo.rootNodeId] or
 * [StorageVolumeInfo.requiresGrant] to real, permission-aware values — that needs live
 * permission-check logic (`Environment.isExternalStorageManager()`, SAF grant lookups)
 * that belongs to Phase 4's `StorageAccessManager`/`ResolveStorageAccessUseCase`, not this
 * raw enumeration. Every entry here has `rootNodeId = null` and `requiresGrant = true` as a
 * conservative placeholder; Phase 4 is expected to take this list and layer real access
 * resolution on top of it.
 *
 * **Confidence note:** API 30+'s `StorageVolume.directory` is a real, documented API. Below
 * API 30, there is no direct public API for a volume's file path at all — the fallback here
 * matches `storageVolumes` against `Context.getExternalFilesDirs()` by list position and
 * strips the known `/Android/data/<package>/files` suffix. That positional match is a
 * commonly-used real-world heuristic, not a documented contract, and
 * `ANDROID_STORAGE_RESEARCH.md` §10 lists OEM storage-volume behaviour as exactly the kind
 * of thing left for device verification. Treat the API-below-30 path here as the
 * least-trustworthy code in this phase — genuinely unverifiable without real hardware
 * across OEMs.
 */
object StorageVolumes {

    fun enumerate(context: Context): List<StorageVolumeInfo> {
        val storageManager = context.getSystemService(StorageManager::class.java) ?: return emptyList()
        val volumes = storageManager.storageVolumes
        val legacyExternalDirs: Array<File?> =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) context.getExternalFilesDirs(null) else emptyArray()

        return volumes.mapIndexed { index, volume ->
            val directory = resolveDirectory(context, volume, index, legacyExternalDirs)
            toStorageVolumeInfo(context, volume, index, directory)
        }
    }

    private fun resolveDirectory(
        context: Context,
        volume: StorageVolume,
        index: Int,
        legacyExternalDirs: Array<File?>,
    ): File? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return runCatching { volume.directory }.getOrNull()
        }
        // Positional heuristic — see class KDoc.
        val appSpecificDir = legacyExternalDirs.getOrNull(index) ?: return null
        val suffix = "/Android/data/${context.packageName}/files"
        return if (appSpecificDir.path.endsWith(suffix)) {
            File(appSpecificDir.path.removeSuffix(suffix))
        } else {
            null
        }
    }

    private fun toStorageVolumeInfo(
        context: Context,
        volume: StorageVolume,
        index: Int,
        directory: File?,
    ): StorageVolumeInfo {
        val type = when {
            volume.isPrimary -> StorageType.INTERNAL_SHARED
            volume.isRemovable -> StorageType.SD_CARD // Can't reliably distinguish USB OTG
            // from an SD card at this API surface alone — ANDROID_STORAGE_RESEARCH.md §10
            // flags exactly this as an open, device-verification-only question.
            else -> StorageType.VIRTUAL
        }
        val space = directory?.takeIf { it.canRead() }?.let { dir ->
            runCatching { StatFs(dir.absolutePath) }
                .map { it.totalBytes to it.availableBytes }
                .getOrNull()
        } ?: if (volume.isPrimary) {
            runCatching {
                val stat = StatFs(context.filesDir.absolutePath)
                stat.totalBytes to stat.availableBytes
            }.getOrDefault(0L to 0L)
        } else {
            0L to 0L
        }

        return StorageVolumeInfo(
            id = volume.uuid ?: if (volume.isPrimary) "primary" else "volume-$index",
            label = runCatching { volume.getDescription(context) }
                .getOrNull()
                ?: if (volume.isPrimary) "Internal storage" else "Storage $index",
            type = type,
            totalBytes = space.first,
            freeBytes = space.second,
            isRemovable = volume.isRemovable,
            isMounted = volume.state == Environment.MEDIA_MOUNTED,
            rootNodeId = null, // See class KDoc — Phase 4's job.
            requiresGrant = true, // See class KDoc — Phase 4's job.
        )
    }
}
