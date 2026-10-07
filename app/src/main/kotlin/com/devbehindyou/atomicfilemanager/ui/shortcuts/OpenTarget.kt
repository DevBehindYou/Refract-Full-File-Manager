package com.devbehindyou.atomicfilemanager.ui.shortcuts

import com.devbehindyou.atomicfilemanager.ui.screens.PaletteCommand

/**
 * What a launch from outside the app asks to show: the operation notification, a launcher
 * shortcut, a pinned folder or the storage widget (ALL_IN_ONE_PLAN.md 4.5). Carried as one
 * string extra so it survives the intent round trip; pure so it is unit-tested.
 */
sealed interface OpenTarget {
    data object Operations : OpenTarget

    data object Palette : OpenTarget

    data class Command(val command: PaletteCommand) : OpenTarget

    data class Folder(val raw: String) : OpenTarget

    fun encode(): String =
        when (this) {
            Operations -> OPERATIONS
            Palette -> PALETTE
            is Command -> COMMAND + command.name
            is Folder -> FOLDER + raw
        }

    companion object {
        const val OPERATIONS = "operations"
        const val PALETTE = "palette"
        private const val COMMAND = "command:"
        private const val FOLDER = "folder:"

        /** Null for anything unknown, such as a shortcut left over from an older version. */
        fun parse(value: String?): OpenTarget? =
            when {
                value == null -> null
                value == OPERATIONS -> Operations
                value == PALETTE -> Palette
                value.startsWith(COMMAND) ->
                    PaletteCommand.entries.firstOrNull { it.name == value.removePrefix(COMMAND) }?.let(::Command)
                value.startsWith(FOLDER) -> value.removePrefix(FOLDER).takeIf { it.isNotBlank() }?.let(::Folder)
                else -> null
            }
    }
}
