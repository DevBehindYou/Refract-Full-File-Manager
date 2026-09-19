package com.devbehindyou.refract

import android.app.Application
import com.devbehindyou.refract.data.backend.FileSystemBackend
import com.devbehindyou.refract.data.backend.MediaStoreBackend
import com.devbehindyou.refract.data.backend.SafBackend
import com.devbehindyou.refract.data.backend.StorageBackendSelector
import com.devbehindyou.refract.domain.repository.BackendType
import com.devbehindyou.refract.domain.repository.StorageBackend
import com.devbehindyou.refract.domain.usecase.CreateDirectoryUseCase
import com.devbehindyou.refract.domain.usecase.DeleteFileUseCase
import com.devbehindyou.refract.domain.usecase.FileOperationsEngine
import com.devbehindyou.refract.domain.usecase.GetDirectoryListingUseCase
import com.devbehindyou.refract.domain.usecase.GetNodeUseCase
import com.devbehindyou.refract.domain.usecase.RenameFileUseCase

interface AppContainer {
    val storageBackendSelector: StorageBackendSelector
    val getDirectoryListingUseCase: GetDirectoryListingUseCase
    val getNodeUseCase: GetNodeUseCase
    val createDirectoryUseCase: CreateDirectoryUseCase
    val renameFileUseCase: RenameFileUseCase
    val deleteFileUseCase: DeleteFileUseCase
    val fileOperationsEngine: FileOperationsEngine
    val inspectArchiveUseCase: com.devbehindyou.refract.domain.usecase.InspectArchiveUseCase
    val readFileContentUseCase: com.devbehindyou.refract.domain.usecase.ReadFileContentUseCase
    val pdfPreviewHelper: com.devbehindyou.refract.data.preview.PdfPreviewHelper
    val imagePreviewHelper: com.devbehindyou.refract.data.preview.ImagePreviewHelper
    val networkCredentialsStore: com.devbehindyou.refract.data.backend.network.NetworkCredentialsStore
    val transferBubbleRepository: com.devbehindyou.refract.domain.repository.TransferBubbleRepository
    val hiddenFilesRepository: com.devbehindyou.refract.domain.repository.HiddenFilesRepository
    val storageAnalyzerUseCase: com.devbehindyou.refract.domain.usecase.StorageAnalyzerUseCase
    val mediaPreviewHelper: com.devbehindyou.refract.data.preview.MediaPreviewHelper

    /** Process-wide so the phone-wide category scan survives Activity recreation (rotation). */
    val phoneFileIndex: com.devbehindyou.refract.data.volume.PhoneFileIndex
}

class DefaultAppContainer(private val application: Application) : AppContainer {
    override val hiddenFilesRepository: com.devbehindyou.refract.domain.repository.HiddenFilesRepository by lazy {
        val helper = com.devbehindyou.refract.data.database.HiddenFilesDatabaseHelper(application)
        com.devbehindyou.refract.data.repository.HiddenFilesRepositoryImpl(application, helper)
    }

    override val transferBubbleRepository: com.devbehindyou.refract.domain.repository.TransferBubbleRepository by lazy {
        val helper = com.devbehindyou.refract.data.database.TransferBubbleDatabaseHelper(application)
        com.devbehindyou.refract.data.repository.TransferBubbleRepositoryImpl(helper)
    }

    override val networkCredentialsStore by lazy {
        com.devbehindyou.refract.data.backend.network.NetworkCredentialsStore(application)
    }

    private val fileSystemBackend by lazy { FileSystemBackend(application) }
    private val safBackend by lazy { SafBackend(application) }
    private val mediaStoreBackend by lazy { MediaStoreBackend(application) }
    private val ftpBackend by lazy {
        com.devbehindyou.refract.data.backend.network.FtpBackend(networkCredentialsStore, BackendType.FTP)
    }
    private val ftpsBackend by lazy {
        com.devbehindyou.refract.data.backend.network.FtpBackend(networkCredentialsStore, BackendType.FTPS)
    }
    private val webdavBackend by lazy {
        com.devbehindyou.refract.data.backend.network.WebDavBackend(networkCredentialsStore)
    }
    private val sftpBackend by lazy {
        com.devbehindyou.refract.data.backend.network.SftpBackend(
            networkCredentialsStore,
        )
    }
    private val smbBackend by lazy { com.devbehindyou.refract.data.backend.network.SmbBackend(networkCredentialsStore) }

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

    override val inspectArchiveUseCase: com.devbehindyou.refract.domain.usecase.InspectArchiveUseCase by lazy {
        com.devbehindyou.refract.domain.usecase.InspectArchiveUseCase { id -> storageBackendSelector.forNode(id) }
    }

    override val readFileContentUseCase: com.devbehindyou.refract.domain.usecase.ReadFileContentUseCase by lazy {
        com.devbehindyou.refract.domain.usecase.ReadFileContentUseCase { id -> storageBackendSelector.forNode(id) }
    }

    override val pdfPreviewHelper: com.devbehindyou.refract.data.preview.PdfPreviewHelper by lazy {
        com.devbehindyou.refract.data.preview.PdfPreviewHelper(application) { id -> storageBackendSelector.forNode(id) }
    }

    override val imagePreviewHelper: com.devbehindyou.refract.data.preview.ImagePreviewHelper by lazy {
        com.devbehindyou.refract.data.preview.ImagePreviewHelper { id -> storageBackendSelector.forNode(id) }
    }

    override val storageAnalyzerUseCase: com.devbehindyou.refract.domain.usecase.StorageAnalyzerUseCase by lazy {
        com.devbehindyou.refract.domain.usecase.StorageAnalyzerUseCase(
            backendSelector = { id -> storageBackendSelector.forNode(id) },
            readFileContentUseCase = readFileContentUseCase,
        )
    }

    override val mediaPreviewHelper: com.devbehindyou.refract.data.preview.MediaPreviewHelper by lazy {
        com.devbehindyou.refract.data.preview.MediaPreviewHelper(
            application,
        ) { id -> storageBackendSelector.forNode(id) }
    }

    override val phoneFileIndex: com.devbehindyou.refract.data.volume.PhoneFileIndex by lazy {
        com.devbehindyou.refract.data.volume.PhoneFileIndex(getDirectoryListingUseCase)
    }
}

/**
 * Application class for Refract.
 */
class RefractApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
