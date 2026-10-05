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
import com.devbehindyou.atomicfilemanager.data.operations.OperationQueue
import com.devbehindyou.atomicfilemanager.data.preview.ImagePreviewHelper
import com.devbehindyou.atomicfilemanager.data.preview.MediaPreviewHelper
import com.devbehindyou.atomicfilemanager.data.preview.PdfPreviewHelper
import com.devbehindyou.atomicfilemanager.data.repository.HiddenFilesRepositoryImpl
import com.devbehindyou.atomicfilemanager.data.repository.SharedPreferencesSettingsRepository
import com.devbehindyou.atomicfilemanager.data.repository.TransferBubbleRepositoryImpl
import com.devbehindyou.atomicfilemanager.data.volume.PhoneFileIndex
import com.devbehindyou.atomicfilemanager.domain.repository.BackendType
import com.devbehindyou.atomicfilemanager.domain.repository.HiddenFilesRepository
import com.devbehindyou.atomicfilemanager.domain.repository.SettingsRepository
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import com.devbehindyou.atomicfilemanager.domain.repository.TransferBubbleRepository
import com.devbehindyou.atomicfilemanager.domain.usecase.CreateDirectoryUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.DeleteFileUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.FileOperationsEngine
import com.devbehindyou.atomicfilemanager.domain.usecase.GetDirectoryListingUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.GetNodeUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.InspectArchiveUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.ReadFileContentUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.RenameFileUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.StorageAnalyzerUseCase
import com.devbehindyou.atomicfilemanager.service.FileOperationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
}

class DefaultAppContainer(private val application: Application) : AppContainer {
    /** Lives as long as the process; used for start-up loads that must not block the main thread. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database by lazy {
        AtomicDatabase.create(application).also { db ->
            // A fresh process can't still be running anything the journal says is running.
            appScope.launch(Dispatchers.IO) { db.operationJournal().markInterrupted(System.currentTimeMillis()) }
        }
    }

    override val operationJournal: OperationJournalDao get() = database.operationJournal()

    override val operationQueue: OperationQueue by lazy {
        OperationQueue(
            engine = { operation, resolver -> fileOperationsEngine.execute(operation, resolver) },
            journal = operationJournal,
            scope = appScope,
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
        FileOperationsEngine { id -> storageBackendSelector.forNode(id) }
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
