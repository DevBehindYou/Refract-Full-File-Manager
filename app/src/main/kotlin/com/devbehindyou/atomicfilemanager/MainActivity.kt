package com.devbehindyou.atomicfilemanager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicHomeHeader
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicSettingsRow
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicBottomBar
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicDestination
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicInfoSheet
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicNavRail
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSheet
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSnackbarHost
import com.devbehindyou.atomicfilemanager.data.volume.StorageVolumes
import com.devbehindyou.atomicfilemanager.domain.model.FileCategory
import com.devbehindyou.atomicfilemanager.domain.model.FileCollection
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.HideMode
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.model.StorageVolumeInfo
import com.devbehindyou.atomicfilemanager.domain.model.ThemeMode
import com.devbehindyou.atomicfilemanager.domain.repository.SettingsRepository
import com.devbehindyou.atomicfilemanager.ui.screens.BrowseScreen
import com.devbehindyou.atomicfilemanager.ui.screens.CategoryScreen
import com.devbehindyou.atomicfilemanager.ui.screens.HiddenFilesScreen
import com.devbehindyou.atomicfilemanager.ui.screens.HomeScreen
import com.devbehindyou.atomicfilemanager.ui.screens.SettingsScreen
import com.devbehindyou.atomicfilemanager.ui.screens.StorageIntelligenceScreen
import com.devbehindyou.atomicfilemanager.ui.screens.StorageScreen
import com.devbehindyou.atomicfilemanager.ui.security.AuthGate
import kotlinx.coroutines.launch

enum class NavigationTab(val title: String) {
    HOME("Home"),
    BROWSE("Files"),
    STORAGE("Storage"),
    SETTINGS("Settings"),
}

// FragmentActivity (a ComponentActivity) because BiometricPrompt needs one.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            val app = application as AtomicApp
            val settings by app.container.settingsRepository.settings.collectAsState()
            val darkTheme =
                when (settings.themeMode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
            // Keep status and navigation bar icons readable when the chosen theme differs from the system's.
            DisposableEffect(darkTheme) {
                val lightNavScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
                val darkNavScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(lightNavScrim, darkNavScrim) { darkTheme },
                )
                onDispose {}
            }
            AtomicTheme(darkTheme = darkTheme, wallpaperAccent = settings.dynamicColor) {
                AtomicAppContent()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtomicAppContent() {
    val context = LocalContext.current
    val app = context.applicationContext as AtomicApp
    val phoneIndex = app.container.phoneFileIndex
    val settingsRepository = app.container.settingsRepository
    val settings by settingsRepository.settings.collectAsState()
    var collectionName by rememberSaveable { mutableStateOf<String?>(null) }
    var showMoreCategories by rememberSaveable { mutableStateOf(false) }
    var showPrivateFiles by rememberSaveable { mutableStateOf(false) }
    var currentTab by rememberSaveable { mutableStateOf(NavigationTab.HOME) }
    val defaultPath = Environment.getExternalStorageDirectory()?.absolutePath ?: context.filesDir.absolutePath
    var selectedFolderRaw by rememberSaveable { mutableStateOf(FileNodeId.file(defaultPath).raw) }
    val selectedFolderId = FileNodeId(selectedFolderRaw)

    // Bumped on every explicit open from Home, Storage or analysis. Browse view models are cached
    // per start folder, so without this a second open of Downloads showed wherever the user had
    // navigated to last time. Tab switches and rotation leave it unchanged and keep the position.
    var browseOpenRequest by rememberSaveable { mutableIntStateOf(0) }
    var tabHistory by rememberSaveable { mutableStateOf(emptyList<String>()) }

    fun navigateTab(tab: NavigationTab) {
        if (tab != currentTab) {
            // Keep one entry per tab so repeated tab switching cannot grow the Back stack without bound.
            tabHistory = tabHistory.filterNot { it == tab.name || it == currentTab.name } + currentTab.name
            currentTab = tab
        }
    }

    fun openInBrowse(folderId: FileNodeId) {
        selectedFolderRaw = folderId.raw
        browseOpenRequest++
        navigateTab(NavigationTab.BROWSE)
    }

    fun navigateBack() {
        currentTab = tabHistory.lastOrNull()?.let(NavigationTab::valueOf) ?: NavigationTab.HOME
        tabHistory = tabHistory.dropLast(1)
    }
    var pendingVolumeId by rememberSaveable { mutableStateOf<String?>(null) }

    var volumes by remember { mutableStateOf<List<StorageVolumeInfo>>(emptyList()) }
    var hasStorageAccess by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun notify(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    var analysisRootRaw by rememberSaveable { mutableStateOf<String?>(null) }
    val storageIntelligenceRootId = analysisRootRaw?.let(::FileNodeId)

    fun checkAccess(): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                val read =
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                    ) == PackageManager.PERMISSION_GRANTED
                read
            }
        }.getOrDefault(false)
    }

    fun refreshVolumes() {
        hasStorageAccess = checkAccess()
        val enumerated = StorageVolumes.enumerate(context)
        volumes =
            if (enumerated.isNotEmpty()) {
                enumerated
            } else {
                val totalBytes = runCatching { context.filesDir.totalSpace }.getOrDefault(0L).coerceAtLeast(1L)
                val freeBytes = runCatching { context.filesDir.freeSpace }.getOrDefault(0L)
                listOf(
                    StorageVolumeInfo(
                        id = "primary",
                        label = "Internal Shared Storage",
                        type = StorageType.INTERNAL_SHARED,
                        totalBytes = totalBytes,
                        freeBytes = freeBytes,
                        isRemovable = false,
                        isMounted = true,
                        rootNodeId = null,
                        requiresGrant = !hasStorageAccess,
                    ),
                )
            }
    }

    LaunchedEffect(volumes) {
        val pending = volumes.firstOrNull { it.id == pendingVolumeId }
        if (pending?.rootNodeId != null && pending.isMounted) {
            openInBrowse(pending.rootNodeId)
            pendingVolumeId = null
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    refreshVolumes()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) {
            refreshVolumes()
        }

    fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent =
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    context.startActivity(intent)
                } catch (_: Exception) {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        ),
                    )
                }
            }
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ),
            )
        }
    }

    fun openVolume(volume: StorageVolumeInfo) {
        when {
            !volume.isMounted -> notify("This storage isn't mounted.")
            volume.rootNodeId != null -> {
                openInBrowse(volume.rootNodeId)
            }
            !hasStorageAccess -> {
                pendingVolumeId = volume.id
                requestStorageAccess()
            }
            else -> notify("This storage is unavailable. Reconnect it and try again.")
        }
    }

    BackHandler(enabled = tabHistory.isNotEmpty() && storageIntelligenceRootId == null) {
        navigateBack()
    }

    LaunchedEffect(Unit) {
        refreshVolumes()
    }

    val configuration = LocalConfiguration.current
    val isExpanded = configuration.screenWidthDp >= 600

    fun openCategory(category: FileCategory) {
        if (category == FileCategory.OTHER) {
            showMoreCategories = true
        } else if (category == FileCategory.DOWNLOAD) {
            openInBrowse(
                FileNodeId.file(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath,
                ),
            )
        } else {
            collectionName =
                when (category) {
                    FileCategory.IMAGE -> FileCollection.IMAGES
                    FileCategory.VIDEO -> FileCollection.VIDEOS
                    FileCategory.AUDIO -> FileCollection.AUDIO
                    FileCategory.DOCUMENT -> FileCollection.DOCUMENTS
                    FileCategory.ARCHIVE -> FileCollection.ARCHIVES
                    FileCategory.APK -> FileCollection.APKS
                    else -> FileCollection.OTHER
                }.name
        }
    }

    if (showPrivateFiles) {
        BackHandler { showPrivateFiles = false }
        AuthGate(
            required = settings.requireAuthForHidden,
            title = "Unlock private files",
            onDenied = { showPrivateFiles = false },
        ) {
            HiddenFilesScreen(
                repository = app.container.hiddenFilesRepository,
                onNavigateBack = { showPrivateFiles = false },
                initialMode = HideMode.PRIVATE_STORAGE,
                privateOnly = true,
            )
        }
        return
    }
    if (collectionName != null) {
        CategoryScreen(
            collection = FileCollection.valueOf(collectionName!!),
            roots = volumes.filter { it.isMounted }.mapNotNull { it.rootNodeId },
            index = phoneIndex,
            onBack = { collectionName = null },
            onGrantAccess = { requestStorageAccess() },
        )
        return
    }
    if (showMoreCategories) {
        AtomicSheet(label = "More categories", onDismiss = { showMoreCategories = false }) {
            listOf(
                FileCollection.PDFS,
                FileCollection.TEXT,
                FileCollection.EBOOKS,
                FileCollection.FONTS,
                FileCollection.OTHER,
            ).forEach { collection ->
                AtomicSettingsRow(
                    title = collection.title,
                    onClick = {
                        showMoreCategories = false
                        collectionName = collection.name
                    },
                )
            }
        }
    }

    if (storageIntelligenceRootId != null) {
        StorageIntelligenceScreen(
            rootId = storageIntelligenceRootId!!,
            onNavigateBack = { analysisRootRaw = null },
            onOpenFile = { file ->
                analysisRootRaw = null
                openInBrowse(file.id)
            },
        )
        return
    }

    val destinations =
        remember {
            listOf(
                AtomicDestination(NavigationTab.HOME.name, NavigationTab.HOME.title, AtomicIcons.Home, "tab_home"),
                AtomicDestination(
                    NavigationTab.BROWSE.name,
                    NavigationTab.BROWSE.title,
                    AtomicIcons.Files,
                    "tab_browse",
                ),
                AtomicDestination(
                    NavigationTab.STORAGE.name,
                    NavigationTab.STORAGE.title,
                    AtomicIcons.Storage,
                    "tab_storage",
                ),
                AtomicDestination(
                    NavigationTab.SETTINGS.name,
                    NavigationTab.SETTINGS.title,
                    AtomicIcons.Settings,
                    "tab_settings",
                ),
            )
        }
    val selectTab: (AtomicDestination) -> Unit = { navigateTab(NavigationTab.valueOf(it.key)) }
    val screen: @Composable () -> Unit = {
        MainScreenContent(
            currentTab = currentTab,
            volumes = volumes,
            hasStorageAccess = hasStorageAccess,
            selectedFolderId = selectedFolderId,
            browseOpenRequest = browseOpenRequest,
            onFolderSelected = ::openInBrowse,
            onNavigateBack = ::navigateBack,
            onRequestStorageAccess = { requestStorageAccess() },
            onBrowseVolume = ::openVolume,
            onCategorySelected = ::openCategory,
            onOpenPrivateFiles = { showPrivateFiles = true },
            onOpenStorageIntelligence = { analysisRootRaw = it.raw },
            settingsRepository = settingsRepository,
            onClearScanCache = phoneIndex::invalidate,
        )
    }

    Scaffold(
        containerColor = Atomic.colors.background,
        snackbarHost = { AtomicSnackbarHost(snackbarHostState) },
        topBar = {
            if (currentTab != NavigationTab.BROWSE) {
                val home = currentTab == NavigationTab.HOME
                AtomicHomeHeader(
                    eyebrow = if (home) "Atomic" else "Atomic File Manager",
                    title = if (home) "File manager" else currentTab.title,
                    modifier = Modifier.background(Atomic.colors.background).statusBarsPadding(),
                    actions = {
                        AtomicIconButton(
                            icon = AtomicIcons.Info,
                            contentDescription = "About Atomic File Manager",
                            onClick = { showAboutDialog = true },
                            modifier = Modifier.testTag("about_button"),
                        )
                    },
                )
            }
        },
        bottomBar = {
            if (!isExpanded) {
                AtomicBottomBar(
                    destinations = destinations,
                    selectedKey = currentTab.name,
                    onSelect = selectTab,
                    modifier = Modifier.testTag("bottom_nav_bar").navigationBarsPadding(),
                )
            }
        },
    ) { innerPadding ->
        if (isExpanded) {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding),
            ) {
                AtomicNavRail(
                    destinations = destinations,
                    selectedKey = currentTab.name,
                    onSelect = selectTab,
                    modifier = Modifier.testTag("nav_rail"),
                )
                Surface(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    color = MaterialTheme.colorScheme.background,
                ) { screen() }
            }
        } else {
            Surface(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding),
                color = MaterialTheme.colorScheme.background,
            ) { screen() }
        }
    }

    if (showAboutDialog) {
        AtomicInfoSheet(
            label = "About",
            headline = "Atomic File Manager",
            body =
                "An offline-first, private file manager in the DevBehindYou Atomic family. No account, no ads, " +
                    "no tracking. Nothing leaves your phone unless you connect a server yourself.",
            onDismiss = { showAboutDialog = false },
        )
    }
}

@Composable
private fun MainScreenContent(
    currentTab: NavigationTab,
    volumes: List<StorageVolumeInfo>,
    hasStorageAccess: Boolean,
    selectedFolderId: FileNodeId,
    browseOpenRequest: Int,
    onFolderSelected: (FileNodeId) -> Unit,
    onNavigateBack: () -> Unit,
    onRequestStorageAccess: () -> Unit,
    onBrowseVolume: (StorageVolumeInfo) -> Unit,
    onCategorySelected: (FileCategory) -> Unit,
    onOpenPrivateFiles: () -> Unit,
    onOpenStorageIntelligence: (FileNodeId) -> Unit,
    settingsRepository: SettingsRepository,
    onClearScanCache: () -> Unit,
) {
    when (currentTab) {
        NavigationTab.HOME -> {
            HomeScreen(
                volumes = volumes,
                hasStorageAccess = hasStorageAccess,
                onNavigateToVolume = onBrowseVolume,
                onNavigateToCategory = onCategorySelected,
                onOpenPrivateFiles = onOpenPrivateFiles,
                onNavigateToFolder = onFolderSelected,
                onRequestStorageAccess = onRequestStorageAccess,
            )
        }
        NavigationTab.BROWSE -> {
            BrowseScreen(
                initialFolderId = selectedFolderId,
                onNavigateBack = onNavigateBack,
                openRequest = browseOpenRequest,
            )
        }
        NavigationTab.STORAGE -> {
            StorageScreen(
                volumes = volumes,
                onBrowseVolume = onBrowseVolume,
                onBrowseFolder = onFolderSelected,
                onOpenStorageIntelligence = onOpenStorageIntelligence,
            )
        }
        NavigationTab.SETTINGS -> {
            SettingsScreen(
                settingsRepository = settingsRepository,
                hasStorageAccess = hasStorageAccess,
                onRequestStorageAccess = onRequestStorageAccess,
                onClearScanCache = onClearScanCache,
            )
        }
    }
}
