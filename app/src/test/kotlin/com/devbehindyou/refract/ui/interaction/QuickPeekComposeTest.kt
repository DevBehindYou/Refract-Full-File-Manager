package com.devbehindyou.refract.ui.interaction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.devbehindyou.refract.domain.model.AccessFlags
import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.StorageType
import com.devbehindyou.refract.ui.interaction.peek.mediaGestureArbiter
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real Compose pointer dispatch on Robolectric; not a physical-device gesture claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuickPeekComposeTest {
    @get:Rule
    val compose = createComposeRule()
    private var opens = 0
    private var peeks = 0
    private var dismissals = 0

    private fun content() {
        val node =
            FileNode(
                id = FileNodeId.file("/fixture/photo.jpg"),
                name = "photo.jpg",
                displayName = "photo.jpg",
                mimeType = "image/jpeg",
                size = 12,
                modifiedAt = 0,
                isDirectory = false,
                isHidden = false,
                parentId = FileNodeId.file("/fixture"),
                storageType = StorageType.INTERNAL_SHARED,
                access = AccessFlags.FULL,
                childCount = null,
                extras = null,
            )
        compose.setContent {
            MaterialTheme {
                Box(
                    Modifier.size(100.dp).testTag("preview-target").mediaGestureArbiter(
                        node = node,
                        onTap = { opens++ },
                        onQuickPeek = { peeks++ },
                        onDismissPeek = { dismissals++ },
                    ),
                )
            }
        }
    }

    @Test
    fun shortTapOpensOnceWithoutPreview() {
        content()
        compose.onNodeWithTag("preview-target").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(1, opens)
            assertEquals(0, peeks)
        }
    }

    @Test
    fun cancelledPointerDoesNotOpenOrPeek() {
        content()
        compose.onNodeWithTag("preview-target").performTouchInput {
            down(center)
            cancel()
        }
        compose.runOnIdle {
            assertEquals(0, opens)
            assertEquals(0, peeks)
        }
    }

    @Test
    fun stationaryHoldPeeksAndReleaseDismisses() {
        content()
        compose.onNodeWithTag("preview-target").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(1, peeks) }
        compose.onNodeWithTag("preview-target").performTouchInput { up() }
        compose.runOnIdle {
            assertEquals(0, opens)
            assertEquals(1, dismissals)
        }
    }
}
