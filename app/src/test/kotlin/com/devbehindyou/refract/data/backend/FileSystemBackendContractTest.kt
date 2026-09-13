package com.devbehindyou.refract.data.backend

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.repository.StorageBackend
import com.devbehindyou.refract.domain.repository.StorageBackendContractTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Proves [FileSystemBackend] against the shared contract (Phase 3 AC1). Runs against a real
 * temporary directory on the machine actually running the test
 * (`testing/TEST_STRATEGY.md` §4: "Robolectric with real temporary directories") — the
 * highest-confidence contract test in this phase, since `java.io.File`/NIO.2 aren't shadowed
 * by Robolectric at all; this is genuinely real file I/O, identical to how it behaves on a
 * device.
 */
@RunWith(RobolectricTestRunner::class)
class FileSystemBackendContractTest : StorageBackendContractTest() {
    @get:Rule
    val tempFolder = TemporaryFolder()

    override fun backend(): StorageBackend = FileSystemBackend(ApplicationProvider.getApplicationContext<Context>())

    override suspend fun rootId(backend: StorageBackend): FileNodeId {
        val root = tempFolder.newFolder("contract-test-root")
        return FileNodeId.file(root.absolutePath)
    }
}
