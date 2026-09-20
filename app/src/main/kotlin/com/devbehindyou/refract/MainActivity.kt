package com.devbehindyou.refract

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.devbehindyou.refract.data.volume.StorageVolumes
import com.devbehindyou.refract.domain.model.FileCategory
import com.devbehindyou.refract.domain.model.FileCollection
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.HideMode
import com.devbehindyou.refract.domain.model.StorageType
import com.devbehindyou.refract.domain.model.StorageVolumeInfo
import com.devbehindyou.refract.domain.model.ThemeMode
import com.devbehindyou.refract.domain.repository.SettingsRepository
import com.devbehindyou.refract.ui.screens.BrowseScreen
import com.devbehindyou.refract.ui.screens.CategoryScreen
import com.devbehindyou.refract.ui.screens.HiddenFilesScreen
import com.devbehindyou.refract.ui.screens.HomeScreen
import com.devbehindyou.refract.ui.screens.SettingsScreen
import com.devbehindyou.refract.ui.screens.StorageIntelligenceScreen
import com.devbehindyou.refract.ui.screens.StorageScreen
import com.devbehindyou.refract.ui.security.AuthGate
import com.devbehindyou.refract.ui.theme.RefractTheme

enum class NavigationTab(val title: String) {
    HOME("Home"),
    BROWSE("Browse"),
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
            val app = application as RefractApp
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
            RefractTheme(darkTheme = darkTheme, dynamicColor = settings.dynamicColor) {
                RefractAppContent()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefractAppContent() {
    val context = LocalContext.current
    val app = context.applicationContext as RefractApp
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
    var tabHistory by rememberSaveable { mutableStateOf(emptyList<String>()) }

    fun navigateTab(tab: NavigationTab) {
        if (tab != currentTab) {
            // Keep one entry per tab so repeated tab switching cannot grow the Back stack without bound.
            tabHistory = tabHistory.filterNot { it == tab.name || it == currentTab.name } + currentTab.name
            currentTab = tab
        }
    }

    fun navigateBack() {
        currentTab = tabHistory.lastOrNull()?.let(NavigationTab::valueOf) ?: NavigationTab.HOME
        tabHistory = tabHistory.dropLast(1)
    }
    var pendingVolumeId by rememberSaveable { mutableStateOf<String?>(null) }

    var volumes by remember { mutableStateOf<List<StorageVolumeInfo>>(emptyList()) }
    var hasStorageAccess by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
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
            selectedFolderRaw = pending.rootNodeId.raw
            navigateTab(NavigationTab.BROWSE)
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
            !volume.isMounted -> Toast.makeText(context, "Storage is not mounted", Toast.LENGTH_SHORT).show()
            volume.rootNodeId != null -> {
                selectedFolderRaw = volume.rootNodeId.raw
                navigateTab(NavigationTab.BROWSE)
            }
            !hasStorageAccess -> {
                pendingVolumeId = volume.id
                requestStorageAccess()
            }
            else ->
                Toast.makeText(
                    context,
                    "This storage is unavailable. Reconnect it and try again.",
                    Toast.LENGTH_LONG,
                ).show()
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
            selectedFolderRaw =
                FileNodeId.file(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath,
                ).raw
            navigateTab(NavigationTab.BROWSE)
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
        AlertDialog(
            onDismissRequest = { showMoreCategories = false },
            title = { Text("More categories") },
            text = {
                Column {
                    listOf(
                        FileCollection.PDFS,
                        FileCollection.TEXT,
                        FileCollection.EBOOKS,
                        FileCollection.FONTS,
                        FileCollection.OTHER,
                    ).forEach { collection ->
                        TextButton(onClick = {
                            showMoreCategories = false
                            collectionName = collection.name
                        }) {
                            Text(collection.title)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMoreCategories = false }) { Text("Close") } },
        )
    }

    if (storageIntelligenceRootId != null) {
        StorageIntelligenceScreen(
            rootId = storageIntelligenceRootId!!,
            onNavigateBack = { analysisRootRaw = null },
            onOpenFile = { file ->
                selectedFolderRaw = file.id.raw
                analysisRootRaw = null
                navigateTab(NavigationTab.BROWSE)
            },
        )
        return
    }

    Scaffold(
        topBar = {
            if (currentTab != NavigationTab.BROWSE) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Refract",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = { showAboutDialog = true },
                            modifier = Modifier.testTag("about_button"),
                        ) {
                            Icon(Icons.Default.Info, contentDescription = "About Refract")
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                        ),
                )
            }
        },
        bottomBar = {
            if (!isExpanded) {
                NavigationBar(
                    modifier = Modifier.testTag("bottom_nav_bar"),
                ) {
                    NavigationBarItem(
                        selected = currentTab == NavigationTab.HOME,
                        onClick = { navigateTab(NavigationTab.HOME) },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        modifier = Modifier.testTag("tab_home"),
                    )
                    NavigationBarItem(
                        selected = currentTab == NavigationTab.BROWSE,
                        onClick = {
                            navigateTab(NavigationTab.BROWSE)
                        },
                        icon = { Icon(Icons.Default.Folder, contentDescription = "Browse") },
                        label = { Text("Browse") },
                        modifier = Modifier.testTag("tab_browse"),
                    )
                    NavigationBarItem(
                        selected = currentTab == NavigationTab.STORAGE,
                        onClick = { navigateTab(NavigationTab.STORAGE) },
                        icon = { Icon(Icons.Default.Storage, contentDescription = "Storage") },
                        label = { Text("Storage") },
                        modifier = Modifier.testTag("tab_storage"),
                    )
                    NavigationBarItem(
                        selected = currentTab == NavigationTab.SETTINGS,
                        onClick = { navigateTab(NavigationTab.SETTINGS) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        modifier = Modifier.testTag("tab_settings"),
                    )
                }
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
                NavigationRail(
                    modifier = Modifier.testTag("nav_rail"),
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    NavigationRailItem(
                        selected = currentTab == NavigationTab.HOME,
                        onClick = { navigateTab(NavigationTab.HOME) },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        modifier = Modifier.testTag("tab_home"),
                    )
                    NavigationRailItem(
                        selected = currentTab == NavigationTab.BROWSE,
                        onClick = {
                            navigateTab(NavigationTab.BROWSE)
                        },
                        icon = { Icon(Icons.Default.Folder, contentDescription = "Browse") },
                        label = { Text("Browse") },
                        modifier = Modifier.testTag("tab_browse"),
                    )
                    NavigationRailItem(
                        selected = currentTab == NavigationTab.STORAGE,
                        onClick = { navigateTab(NavigationTab.STORAGE) },
                        icon = { Icon(Icons.Default.Storage, contentDescription = "Storage") },
                        label = { Text("Storage") },
                        modifier = Modifier.testTag("tab_storage"),
                    )
                    NavigationRailItem(
                        selected = currentTab == NavigationTab.SETTINGS,
                        onClick = { navigateTab(NavigationTab.SETTINGS) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        modifier = Modifier.testTag("tab_settings"),
                    )
                }
                Surface(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MainScreenContent(
                        currentTab = currentTab,
                        volumes = volumes,
                        hasStorageAccess = hasStorageAccess,
                        selectedFolderId = selectedFolderId,
                        onFolderSelected = {
                            selectedFolderRaw = it.raw
                            navigateTab(NavigationTab.BROWSE)
                        },
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
            }
        } else {
            Surface(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding),
                color = MaterialTheme.colorScheme.background,
            ) {
                MainScreenContent(
                    currentTab = currentTab,
                    volumes = volumes,
                    hasStorageAccess = hasStorageAccess,
                    selectedFolderId = selectedFolderId,
                    onFolderSelected = {
                        selectedFolderRaw = it.raw
                        navigateTab(NavigationTab.BROWSE)
                    },
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
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About Refract", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Refract File Manager",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Version 0.1.0 • Modern Android File Explorer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text =
                            "An offline-first, private file manager built with Jetpack Compose and modern " +
                                "Android Storage APIs. Full access to internal, shared, and sandboxed storage.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Done")
                }
            },
        )
    }
}

@Composable
private fun MainScreenContent(
    currentTab: NavigationTab,
    volumes: List<StorageVolumeInfo>,
    hasStorageAccess: Boolean,
    selectedFolderId: FileNodeId,
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
