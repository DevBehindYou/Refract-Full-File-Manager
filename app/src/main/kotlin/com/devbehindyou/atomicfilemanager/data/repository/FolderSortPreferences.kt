package com.devbehindyou.atomicfilemanager.data.repository

import android.content.Context
import com.devbehindyou.atomicfilemanager.domain.repository.FolderSortMemory

/**
 * Remembers the sort order chosen in each folder (FR-3.3) in a small SharedPreferences file keyed
 * by the folder's node id. Capped so it can't grow without bound: past [MAX_FOLDERS] entries the
 * file is cleared and starts again, which only means some folders fall back to the default order.
 */
class FolderSortPreferences(context: Context) : FolderSortMemory {
    private val prefs = context.getSharedPreferences("atomic_folder_sort", Context.MODE_PRIVATE)

    override fun get(folderRaw: String): String? = prefs.getString(folderRaw, null)

    override fun set(
        folderRaw: String,
        value: String,
    ) {
        val editor = prefs.edit()
        if (prefs.all.size >= MAX_FOLDERS && !prefs.contains(folderRaw)) editor.clear()
        editor.putString(folderRaw, value).apply()
    }

    private companion object {
        const val MAX_FOLDERS = 500
    }
}
