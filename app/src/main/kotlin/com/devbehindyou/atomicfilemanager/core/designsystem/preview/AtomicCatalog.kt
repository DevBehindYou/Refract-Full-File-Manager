package com.devbehindyou.atomicfilemanager.core.designsystem.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicBadge
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicCheckbox
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicChip
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicDivider
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconTile
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicLoading
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicMeter
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicProgressBar
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicStatusPill
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicSwitch
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextField
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons

/** Every atom in its main states, for Android Studio previews and later screenshot tests. */
@Composable
fun AtomicAtomsCatalog(darkTheme: Boolean) {
    AtomicTheme(darkTheme = darkTheme, wallpaperAccent = false) {
        Column(
            modifier =
                Modifier
                    .background(Atomic.colors.background)
                    .padding(AtomicSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
        ) {
            AtomicText("Atomic file manager", AtomicTextRole.DisplayTitle)
            AtomicText("README.md stays as stored", AtomicTextRole.Name)
            AtomicText("Storage", AtomicTextRole.MonoLabel)
            AtomicText("4.2 MB · 2026-10-04 08:11", AtomicTextRole.MonoMeta)
            AtomicDivider(strong = true)
            AtomicButton("Move to trash", onClick = {}, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8)) {
                AtomicButton("Got it", onClick = {}, variant = AtomicButtonVariant.Solid)
                AtomicButton("Cancel", onClick = {}, variant = AtomicButtonVariant.Ghost)
                AtomicButton("Execute", onClick = {}, variant = AtomicButtonVariant.Destructive)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AtomicButton("Restore", onClick = {}, variant = AtomicButtonVariant.Text)
                AtomicButton("Disabled", onClick = {}, enabled = false)
                AtomicIconButton(AtomicIcons.Back, "Back", onClick = {}, variant = AtomicIconButtonVariant.Back)
                AtomicIconButton(AtomicIcons.Search, "Search", onClick = {}, variant = AtomicIconButtonVariant.Accent)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AtomicChip("Date ↓", selected = true, onSelectedChange = {})
                AtomicChip("Hidden", selected = false, onSelectedChange = {})
                AtomicStatusPill("Locked")
                AtomicBadge(4)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AtomicIconTile(AtomicIcons.Image)
                AtomicIconTile(AtomicIcons.Lock, inverted = true)
                AtomicCheckbox(checked = true, onCheckedChange = {})
                AtomicCheckbox(checked = false, onCheckedChange = {})
                AtomicSwitch(checked = true, onCheckedChange = {})
                AtomicSwitch(checked = false, onCheckedChange = {})
            }
            AtomicTextField(
                value = "Tickets/2026",
                onValueChange = {},
                label = "Name",
                errorText = "A folder name can't contain \"/\". Try \"Tickets 2026\".",
            )
            AtomicMeter(fraction = 0.32f, contentDescription = "Phone, 32 percent used")
            AtomicMeter(fraction = 0.9f, contentDescription = "SD card, 90 percent used, nearly full")
            AtomicProgressBar(fraction = 0.36f, contentDescription = "Copying, 36 percent")
            AtomicLoading("Loading folder…")
        }
    }
}

@Preview(name = "Atoms · light", widthDp = 400, heightDp = 1100)
@Composable
private fun AtomsLightPreview() = AtomicAtomsCatalog(darkTheme = false)

@Preview(name = "Atoms · dark", widthDp = 400, heightDp = 1100)
@Composable
private fun AtomsDarkPreview() = AtomicAtomsCatalog(darkTheme = true)

@Preview(name = "Atoms · 200 % font", widthDp = 400, heightDp = 1600, fontScale = 2f)
@Composable
private fun AtomsLargeFontPreview() = AtomicAtomsCatalog(darkTheme = false)
