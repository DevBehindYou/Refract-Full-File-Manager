package com.devbehindyou.atomicfilemanager.data.apps

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppManagerRulesTest {
    private fun app(
        label: String,
        pkg: String = "com.example.${label.lowercase()}",
        size: Long = 1,
        installed: Long = 0,
        updated: Long = 0,
        system: Boolean = false,
        version: String? = "1.0",
        splits: List<String> = emptyList(),
    ) = InstalledApp(label, pkg, version, size, installed, updated, "/data/app/$pkg/base.apk", splits, system)

    private val maps = app("Maps", size = 90, installed = 3, updated = 9)
    private val notes = app("notes", size = 5, installed = 9, updated = 1)
    private val phone = app("Phone", pkg = "com.android.dialer", size = 40, system = true)

    @Test
    fun `system apps hide by default and search covers name and package`() {
        val all = listOf(maps, notes, phone)
        assertEquals(listOf(maps, notes), AppManagerRules.visible(all, "", showSystem = false, sort = AppSort.NAME))
        assertEquals(listOf(phone), AppManagerRules.visible(all, "dialer", showSystem = true, sort = AppSort.NAME))
        assertEquals(listOf(notes), AppManagerRules.visible(all, " NOT ", showSystem = false, sort = AppSort.NAME))
    }

    @Test
    fun `sorts by name, size and dates`() {
        val all = listOf(notes, maps)
        assertEquals(listOf(maps, notes), AppManagerRules.visible(all, "", false, AppSort.NAME))
        assertEquals(listOf(maps, notes), AppManagerRules.visible(all, "", false, AppSort.SIZE))
        assertEquals(listOf(notes, maps), AppManagerRules.visible(all, "", false, AppSort.INSTALLED))
        assertEquals(listOf(maps, notes), AppManagerRules.visible(all, "", false, AppSort.UPDATED))
    }

    @Test
    fun `export names are safe and split apps become apks`() {
        assertEquals("Maps 1.0.apk", AppManagerRules.exportFileName(maps))
        assertEquals("A_B 2.apk", AppManagerRules.exportFileName(app("A/B", version = "2")))
        assertEquals("com.x.y.apk", AppManagerRules.exportFileName(app("", pkg = "com.x.y", version = null)))
        assertEquals(
            "Game 3.apks",
            AppManagerRules.exportFileName(app("Game", version = "3", splits = listOf("/s.apk"))),
        )
    }
}
