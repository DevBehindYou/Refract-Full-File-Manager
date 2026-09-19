package com.devbehindyou.refract

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MobileNavigationDeviceTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun systemBackReturnsFromStorageToHome() {
        compose.onNodeWithTag("tab_storage").performClick()
        compose.onNodeWithTag("storage_screen").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("home_screen").assertIsDisplayed()
    }

    @Test
    fun browseBackReturnsToOriginatingStorageTab() {
        compose.onNodeWithTag("tab_storage").performClick()
        compose.onNodeWithTag("tab_browse").performClick()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("storage_screen").assertIsDisplayed()
    }

    @Test
    fun activityRecreationKeepsSelectedTab() {
        compose.onNodeWithTag("tab_storage").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("storage_screen").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("home_screen").assertIsDisplayed()
    }
}
