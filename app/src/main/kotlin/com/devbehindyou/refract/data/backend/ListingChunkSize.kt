package com.devbehindyou.refract.data.backend

/**
 * Every backend's `listChildren` emits in chunks of this size rather than materialising a
 * whole directory before emitting anything (roadmap Phase 3: "streaming listChildren";
 * AC4's <100ms-to-first-chunk requirement). `testing/TEST_STRATEGY.md` §4 names "chunk
 * boundaries at 200/201/0 items" as a specific `FileSystemBackendTest` case — 200 is the
 * value that boundary set implies (0 = empty directory, 200 = exactly one full chunk, 201 =
 * one chunk plus a one-item remainder), not independently specified anywhere as a number.
 */
internal const val LISTING_CHUNK_SIZE = 200
