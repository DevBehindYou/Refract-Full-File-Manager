package com.devbehindyou.atomicfilemanager

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import com.devbehindyou.atomicfilemanager.data.backend.FileSystemBackend
import com.devbehindyou.atomicfilemanager.data.backend.MediaStoreBackend
import com.devbehindyou.atomicfilemanager.data.backend.SafBackend
import com.devbehindyou.atomicfilemanager.data.backend.StorageBackendSelector
import com.devbehindyou.atomicfilemanager.data.backend.network.FtpBackend
import com.devbehindyou.atomicfilemanager.data.backend.network.NetworkCredentialsStore
import com.devbehindyou.atomicfilemanager.data.backend.network.SftpBackend
import com.devbehindyou.atomicfilemanager.data.backend.network.SmbBackend
import com.devbehindyou.atomicfilemanager.data.backend.network.WebDavBackend
import com.devbehindyou.atomicfilemanager.data.database.HiddenFilesDatabaseHelper
import com.devbehindyou.atomicfilemanager.data.database.TransferBubbleDatabaseHelper
import com.devbehindyou.atomicfilemanager.data.database.room.AtomicDatabase
import com.devbehindyou.atomicfilemanager.data.database.room.OperationJournalDao
import com.devbehindyou.atomicfilemanager.data.database.room.RoomFavouritesRepository
import com.devbehindyou.atomicfilemanager.data.database.room.RoomRecentsRepository
import com.devbehindyou.atomicfilemanager.data.database.room.RoomTrashStore
import com.devbehindyou.atomicfilemanager.data.operations.OperationQueue
import com.devbehindyou.atomicfilemanager.data.operations.OperationRecovery
import com.devbehindyou.atomicfilemanager.data.preview.ImagePreviewHelper
import com.devbehindyou.atomicfilemanager.data.preview.MediaPreviewHelper
import com.devbehindyou.atomicfilemanager.data.preview.PdfPreviewHelper
import com.devbehindyou.atomicfilemanager.data.recents.RecentMediaSource
import com.devbehindyou.atomicfilemanager.data.repository.FolderSortPreferences
import com.devbehindyou.atomicfilemanager.data.repository.HiddenFilesRepositoryImpl
import com.devbehindyou.atomicfilemanager.data.repository.SharedPreferencesSettingsRepository
import com.devbehindyou.atomicfilemanager.data.repository.TransferBubbleRepositoryImpl
import com.devbehindyou.atomicfilemanager.data.repository.volumeRootOf
import com.devbehindyou.atomicfilemanager.data.volume.PhoneFileIndex
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.repository.BackendType
import com.devbehindyou.atomicfilemanager.domain.repository.FavouritesRepository
import com.devbehindyou.atomicfilemanager.domain.repository.FolderSortMemory
import com.devbehindyou.atomicfilemanager.domain.repository.HiddenFilesRepository
import com.devbehindyou.atomicfilemanager.domain.repository.RecentsRepository
import com.devbehindyou.atomicfilemanager.domain.repository.SettingsRepository
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import com.devbehindyou.atomicfilemanager.domain.repository.TransferBubbleRepository
import com.devbehindyou.atomicfilemanager.domain.repository.TrashStore
import com.devbehindyou.atomicfilemanager.domain.usecase.CreateDirectoryUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.DeleteFileUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.FileOperationsEngine
import com.devbehindyou.atomicfilemanager.domain.usecase.GetDirectoryListingUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.GetNodeUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.InspectArchiveUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.ReadFileContentUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.RenameFileUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.StorageAnalyzerUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.TrashManager
import com.devbehindyou.atomicfilemanager.service.FileOperationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

interface AppContainer {
    val storageBackendSelector: StorageBackendSelector
    val getDirectoryListingUseCase: GetDirectoryListingUseCase
    val getNodeUseCase: GetNodeUseCase
    val createDirectoryUseCase: CreateDirectoryUseCase
    val renameFileUseCase: RenameFileUseCase
    val deleteFileUseCase: DeleteFileUseCase
    val fileOperationsEngine: FileOperationsEngine
    val inspectArchiveUseCase: InspectArchiveUseCase
    val readFileContentUseCase: ReadFileContentUseCase
    val pdfPreviewHelper: PdfPreviewHelper
    val imagePreviewHelper: ImagePreviewHelper
    val networkCredentialsStore: NetworkCredentialsStore
    val transferBubbleRepository: TransferBubbleRepository
    val hiddenFilesRepository: HiddenFilesRepository
    val storageAnalyzerUseCase: StorageAnalyzerUseCase
    val mediaPreviewHelper: MediaPreviewHelper

    val settingsRepository: SettingsRepository

    /** Process-wide so the phone-wide category scan survives Activity recreation (rotation). */
    val phoneFileIndex: PhoneFileIndex

    /** Durable record of every file operation (ALL_IN_ONE_PLAN.md Phase 0.1). */
    val operationJournal: OperationJournalDao

    /** The one place file operations run, one at a time, journaled. */
    val operationQueue: OperationQueue

    /** Work a previous process left unfinished, offered once as "Resume or discard". */
    val operationRecovery: OperationRecovery

    /** The app Trash (ALL_IN_ONE_PLAN.md 1.1): what is in it, restore and delete for good. */
    val trashManager: TrashManager
    val trashStore: TrashStore

    /** Starred files and folders, and recently opened files (ALL_IN_ONE_PLAN.md 1.2). */
    val favouritesRepository: FavouritesRepository
    val recentsRepository: RecentsRepository

    /** Sort order chosen per folder in Files (FR-3.3). */
    val folderSortMemory: FolderSortMemory
}

class DefaultAppContainer(private val application: Application) : AppContainer {
    /** Lives as long as the process; used for start-up loads that must not block the main thread. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database by lazy { AtomicDatabase.create(application) }

    override val operationJournal: OperationJournalDao get() = database.operationJournal()

    override val operationQueue: OperationQueue by lazy {
        OperationQueue(
            engine = { operation, resolver -> fileOperationsEngine.execute(operation, resolver) },
            journal = operationJournal,
            scope = appScope,
            // A fresh process can't still be running anything the journal says is running.
            startup = { withContext(Dispatchers.IO) { operationRecovery.sweep() } },
        ).also { queue ->
            // Show the progress notification whenever the queue goes from idle to busy.
            appScope.launch {
                queue.active
                    .map { it != null }
                    .distinctUntilChanged()
                    .filter { it }
                    .collect { FileOperationService.start(application) }
            }
        }
    }

    override val trashStore: TrashStore by lazy { RoomTrashStore(database.trashEntries()) }

    override val folderSortMemory: FolderSortMemory by lazy { FolderSortPreferences(application) }

    override val favouritesRepository: FavouritesRepository by lazy { RoomFavouritesRepository(database.favourites()) }

    override val recentsRepository: RecentsRepository by lazy {
        val media = RecentMediaSource(application)
        RoomRecentsRepository(database.recentItems(), media = media::query)
    }

    override val trashManager: TrashManager by lazy {
        TrashManager(
            backendFor = { id -> storageBackendSelector.forNode(id) },
            store = trashStore,
            volumeRootOf = ::trashVolumeRoot,
        ).also { trash ->
            // Items past the retention period go for good, once per process, off the main thread.
            appScope.launch(Dispatchers.IO) {
                val days = settingsRepository.settings.value.trashRetentionDays
                runCatching { trash.purgeDeletedBefore(System.currentTimeMillis() - days * DAY_MILLIS) }
            }
        }
    }

    override val operationRecovery: OperationRecovery by lazy {
        OperationRecovery(
            journal = operationJournal,
            queue = operationQueue,
            backendFor = { id -> storageBackendSelector.forNode(id) },
            scope = appScope,
        )
    }

    override val hiddenFilesRepository: HiddenFilesRepository by lazy {
        val helper = HiddenFilesDatabaseHelper(application)
        HiddenFilesRepositoryImpl(
            application,
            helper,
            hiddenFolderProvider = { settingsRepository.settings.value.hiddenFolder },
            loadScope = appScope,
        )
    }

    override val transferBubbleRepository: TransferBubbleRepository by lazy {
        val helper = TransferBubbleDatabaseHelper(application)
        TransferBubbleRepositoryImpl(helper, loadScope = appScope)
    }

    override val networkCredentialsStore by lazy {
        NetworkCredentialsStore(application)
    }

    private val fileSystemBackend by lazy { FileSystemBackend(application) }
    private val safBackend by lazy { SafBackend(application) }
    private val mediaStoreBackend by lazy { MediaStoreBackend(application) }
    private val ftpBackend by lazy {
        FtpBackend(networkCredentialsStore, BackendType.FTP)
    }
    private val ftpsBackend by lazy {
        FtpBackend(networkCredentialsStore, BackendType.FTPS)
    }
    private val webdavBackend by lazy {
        WebDavBackend(networkCredentialsStore)
    }
    private val sftpBackend by lazy {
        SftpBackend(
            networkCredentialsStore,
        )
    }
    private val smbBackend by lazy { SmbBackend(networkCredentialsStore) }

    private val backends: Map<BackendType, StorageBackend> by lazy {
        mapOf(
            BackendType.FILE to fileSystemBackend,
            BackendType.SAF to safBackend,
            BackendType.MEDIASTORE to mediaStoreBackend,
            BackendType.FTP to ftpBackend,
            BackendType.FTPS to ftpsBackend,
            BackendType.WEBDAV to webdavBackend,
            BackendType.SFTP to sftpBackend,
            BackendType.SMB to smbBackend,
        )
    }

    override val storageBackendSelector: StorageBackendSelector by lazy {
        StorageBackendSelector(backends)
    }

    override val getDirectoryListingUseCase: GetDirectoryListingUseCase by lazy {
        GetDirectoryListingUseCase { id -> storageBackendSelector.forNode(id) }
    }

    override val getNodeUseCase: GetNodeUseCase by lazy {
        GetNodeUseCase { id -> storageBackendSelector.forNode(id) }
    }

    override val createDirectoryUseCase: CreateDirectoryUseCase by lazy {
        CreateDirectoryUseCase { id -> storageBackendSelector.forNode(id) }
    }

    override val renameFileUseCase: RenameFileUseCase by lazy {
        RenameFileUseCase { id -> storageBackendSelector.forNode(id) }
    }

    override val deleteFileUseCase: DeleteFileUseCase by lazy {
        DeleteFileUseCase { id -> storageBackendSelector.forNode(id) }
    }

    override val fileOperationsEngine: FileOperationsEngine by lazy {
        FileOperationsEngine(backendSelector = {
                id ->
            storageBackendSelector.forNode(id)
        }, trashManager = trashManager)
    }

    override val inspectArchiveUseCase: InspectArchiveUseCase by lazy {
        InspectArchiveUseCase { id -> storageBackendSelector.forNode(id) }
    }

    override val readFileContentUseCase: ReadFileContentUseCase by lazy {
        ReadFileContentUseCase { id -> storageBackendSelector.forNode(id) }
    }

    override val pdfPreviewHelper: PdfPreviewHelper by lazy {
        PdfPreviewHelper(application) { id -> storageBackendSelector.forNode(id) }
    }

    override val imagePreviewHelper: ImagePreviewHelper by lazy {
        ImagePreviewHelper { id -> storageBackendSelector.forNode(id) }
    }

    override val storageAnalyzerUseCase: StorageAnalyzerUseCase by lazy {
        StorageAnalyzerUseCase(
            backendSelector = { id -> storageBackendSelector.forNode(id) },
            readFileContentUseCase = readFileContentUseCase,
        )
    }

    override val mediaPreviewHelper: MediaPreviewHelper by lazy {
        MediaPreviewHelper(
            application,
        ) { id -> storageBackendSelector.forNode(id) }
    }

    override val settingsRepository: SettingsRepository by lazy {
        SharedPreferencesSettingsRepository(application)
    }

    override val phoneFileIndex: PhoneFileIndex by lazy {
        PhoneFileIndex(getDirectoryListingUseCase)
    }
}

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/** Trash lives at the root of the volume a local file is on; other storage has no Trash. */
private fun trashVolumeRoot(id: FileNodeId): FileNodeId? =
    if (id.prefix == FileNodeId.Prefix.FILE) {
        volumeRootOf(File(id.raw.removePrefix(FileNodeId.Prefix.FILE.scheme)))?.let { FileNodeId.file(it.absolutePath) }
    } else {
        null
    }

/**
 * Application class for Atomic File Manager.
 */
class AtomicApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) enableStrictMode()
        container = DefaultAppContainer(this)
    }

    /**
     * Debug builds log disk and network access on the main thread and leaked closeables
     * (ALL_IN_ONE_PLAN.md §16.1). Log only: a crash here would hide the real problem in tests.
     */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy
                .Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy
                .Builder()
                .detectLeakedClosableObjects()
                .detectLeakedSqlLiteObjects()
                .penaltyLog()
                .build(),
        )
    }
}
