package com.devbehindyou.atomicfilemanager.ui.components

import com.devbehindyou.atomicfilemanager.data.preview.ApkInfo
import com.devbehindyou.atomicfilemanager.data.preview.androidVersionName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ApkFactsTest {
    private val apk =
        ApkInfo(
            label = "Atomic",
            packageName = "com.example.atomic",
            versionName = "1.2.0",
            versionCode = 42,
            minSdk = 27,
            targetSdk = 36,
            permissionCount = 1,
            icon = null,
        )

    @Test
    fun `facts name the version, Android range and permissions`() {
        val facts = apkFacts(apk).associate { it.label to it.value }
        assertEquals("1.2.0 (42)", facts["Version"])
        assertEquals("Android 8.1 (API 27) or newer", facts["Needs"])
        assertEquals("Android 16 (API 36)", facts["Built for"])
        assertEquals("1 requested", facts["Permissions"])
    }

    @Test
    fun `missing values are left out or described plainly`() {
        val facts =
            apkFacts(apk.copy(versionName = null, minSdk = null, permissionCount = 0)).associate {
                it.label to it.value
            }
        assertEquals("(42)", facts["Version"])
        assertEquals(null, facts["Needs"])
        assertEquals("0 requested", facts["Permissions"])
        assertEquals("API 99", androidVersionName(99))
    }
}
