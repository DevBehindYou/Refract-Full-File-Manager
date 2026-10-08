package com.devbehindyou.atomicfilemanager.ui.screens

import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicChip
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicPushedHeader
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicToggleCard
import com.devbehindyou.atomicfilemanager.data.share.WifiShareController
import com.devbehindyou.atomicfilemanager.data.share.WifiShareState
import kotlinx.coroutines.delay
import java.io.File

/**
 * The guarded Wi-Fi share (ALL_IN_ONE_PLAN.md 3.4): one folder to a browser on the same Wi-Fi,
 * behind a one-time PIN, read-only unless uploads are switched on. It runs only while this screen
 * is open, and stops after 15 minutes without use.
 */
@Composable
fun WifiShareScreen(
    controller: WifiShareController,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val state by controller.state.collectAsState()
    val folders = remember { WifiShareText.folders() }
    var folderPath by rememberSaveable { mutableStateOf(folders.firstOrNull()?.second?.path.orEmpty()) }
    var allowUpload by rememberSaveable { mutableStateOf(false) }

    // Leaving the screen ends the share; nothing keeps serving in the background. A rotation
    // rebuilds the screen without leaving it, so the share carries on through that.
    val activity = LocalActivity.current
    DisposableEffect(controller) {
        onDispose {
            if (activity?.isChangingConfigurations != true) {
                controller.stop()
                controller.clear()
            }
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(Atomic.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("wifi_share"),
    ) {
        AtomicPushedHeader(onBack = onBack, title = "Wi-Fi share", eyebrow = "Same Wi-Fi only")
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(AtomicSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s16),
        ) {
            when (val s = state) {
                is WifiShareState.Running -> RunningShare(s, onStop = controller::stop)
                else -> {
                    (s as? WifiShareState.Stopped)?.let { AtomicText(it.message, AtomicTextRole.Body) }
                    AtomicText(WifiShareText.EXPLAINER, AtomicTextRole.BodySecondary)
                    AtomicText("Folder", AtomicTextRole.MonoLabel)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
                    ) {
                        folders.forEach { (label, dir) ->
                            AtomicChip(
                                label,
                                selected = folderPath == dir.path,
                                onSelectedChange = { folderPath = dir.path },
                            )
                        }
                    }
                    AtomicToggleCard(
                        title = "Allow uploads",
                        description = "Let the browser add files to this folder. Existing files are never replaced.",
                        checked = allowUpload,
                        onCheckedChange = { allowUpload = it },
                    )
                    AtomicButton(
                        "Start sharing",
                        onClick = { controller.start(File(folderPath), allowUpload) },
                        enabled = folderPath.isNotEmpty(),
                        variant = AtomicButtonVariant.Solid,
                        modifier = Modifier.fillMaxWidth().testTag("start_wifi_share"),
                    )
                }
            }
        }
    }
}

@Composable
private fun RunningShare(
    running: WifiShareState.Running,
    onStop: () -> Unit,
) {
    var sent by remember(running) { mutableIntStateOf(0) }
    var received by remember(running) { mutableIntStateOf(0) }
    LaunchedEffect(running) {
        while (true) {
            sent = running.server.downloads.get()
            received = running.server.uploads.get()
            delay(COUNTER_REFRESH_MS)
        }
    }
    AtomicText("On a computer on this Wi-Fi, open", AtomicTextRole.BodySecondary)
    AtomicText(running.url, AtomicTextRole.NameLarge, modifier = Modifier.testTag("wifi_share_url"))
    AtomicText("and enter the PIN", AtomicTextRole.BodySecondary)
    AtomicText(running.pin, AtomicTextRole.DisplayTitle, modifier = Modifier.testTag("wifi_share_pin"))
    AtomicText(WifiShareText.status(running, sent, received), AtomicTextRole.MonoMeta)
    AtomicText(WifiShareText.RUNNING_NOTE, AtomicTextRole.BodySecondary)
    AtomicButton(
        "Stop sharing",
        onClick = onStop,
        variant = AtomicButtonVariant.Destructive,
        modifier = Modifier.fillMaxWidth().testTag("stop_wifi_share"),
    )
}

private const val COUNTER_REFRESH_MS = 1_000L

/** Wording for [WifiShareScreen]; pure parts are unit-tested. */
internal object WifiShareText {
    const val EXPLAINER =
        "Share one folder with a browser on the same Wi-Fi. A PIN shown here is needed to open it, " +
            "it is read-only unless you allow uploads, and it never works over mobile data."
    const val RUNNING_NOTE =
        "Sharing stops when you leave this screen, tap Stop, or after 15 minutes without use."

    fun status(
        running: WifiShareState.Running,
        sent: Int,
        received: Int,
    ): String =
        listOfNotNull(
            "${running.folder.name} · ${if (running.allowUpload) "uploads on" else "read only"}",
            "$sent sent",
            if (running.allowUpload) "$received received" else null,
        ).joinToString(" · ")

    /** The common public folders that exist on this phone. */
    fun folders(): List<Pair<String, File>> =
        listOf(
            "Download" to Environment.DIRECTORY_DOWNLOADS,
            "Camera" to Environment.DIRECTORY_DCIM,
            "Pictures" to Environment.DIRECTORY_PICTURES,
            "Documents" to Environment.DIRECTORY_DOCUMENTS,
            "Music" to Environment.DIRECTORY_MUSIC,
            "Movies" to Environment.DIRECTORY_MOVIES,
        ).map { (label, type) -> label to Environment.getExternalStoragePublicDirectory(type) }
            .filter { it.second.isDirectory }
}
