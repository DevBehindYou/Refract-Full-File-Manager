package com.devbehindyou.refract.domain.repository

import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.testing.InMemoryBackend

/** Phase 2 AC3: "InMemoryBackend passes a shared StorageBackendContractTest." */
class InMemoryBackendContractTest : StorageBackendContractTest() {
    // A fresh InMemoryBackend per test — kotlinx-test's runTest calls backend() once per
    // test method body, so there's no cross-test state leakage to worry about.
    override fun backend(): StorageBackend = InMemoryBackend()

    override suspend fun rootId(backend: StorageBackend): FileNodeId = (backend as InMemoryBackend).rootId
}
