# Phase 3 — Implementation Notes

Same standing caveat as every prior phase: nothing here has been compiled in the sandbox
that built it. This phase raises the stakes on that caveat more than any before it — it's
the first code in this project touching real platform APIs (`java.io.File`/NIO.2, Storage
Access Framework, MediaStore) instead of pure Kotlin, and the first real Hilt
`@Module`/`@Binds`/`@IntoMap` code beyond Phase 1's bare `@HiltAndroidApp`.

## 0. What Phase 3 can and can't prove, honestly

Of the five acceptance criteria:
- **AC1** (all three backends pass `StorageBackendContractTest`): code exists, reasoned
  through carefully, **not run**. See §3 for a real, known gap in how completely this
  applies to `MediaStoreBackend`.
- **AC2** (instrumented, 5 API levels, correct auto-selection): **cannot be satisfied by
  me at all**. A scaffold exists (`StorageBackendSelectorInstrumentedTest`), honestly
  scoped down to what's structurally testable without a device — see its own KDoc.
- **AC3** (`listFiles()` null -> `AccessDenied`, never empty): implemented and reasoned
  through carefully for `FileSystemBackend` (see §2's note on how the actual mechanism
  changed from the AC's literal wording). Not run.
- **AC4** (<100ms to first chunk, 10k items): cannot be measured by me. The
  streaming/chunking *design* genuinely targets this (see §2) rather than just
  chunking a fully-buffered list after the fact, which is a real, meaningful bug I caught
  in my own first draft — but "designed to be fast" and "measured at <100ms" are
  different claims, and I can only make the first one.
- **AC5** (every mapped exception has a test): satisfied for everything in
  `ExceptionMapping.kt` as it currently stands — `ExceptionMappingTest.kt` covers every
  branch, including the one I added beyond `DATA_LAYER.md`'s original example
  (`java.nio.file.AccessDeniedException` — see §2).

## 1. The load-bearing architectural decision, restated

`StorageBackendSelector.forNode(id)` is a stateless dispatch on `id.prefix` — it does not,
and structurally cannot, decide which backend a *volume's root* should use in the first
place. That decision needs live permission state (All Files Access? A SAF grant? Neither?)
and belongs to Phase 4's `ResolveStorageAccessUseCase`, which doesn't exist yet. This was
flagged before implementation started and holds as designed — see
`StorageBackendSelector`'s own KDoc for the full reasoning.

## 2. Real bugs caught during review (before you find them differently)

Writing three backends against the same interface surfaced several real mistakes, caught
by re-reading my own draft rather than by running anything (which I can't do). Listed here
because catching them *this* way, rather than by execution, is weaker evidence than a
passing test — worth knowing which parts got this level of scrutiny and which didn't:

- **`FileSystemBackend.listChildren`'s first draft wasn't actually streaming.** It called
  `File.listFiles()` (one blocking call that must finish reading the whole directory)
  and then chunked the already-complete result — meaning "streaming" would have added zero
  latency benefit to the first chunk, directly undermining AC4's actual intent even while
  technically emitting multiple `Flow` values. Rewritten to use `java.nio.file.Files.
  newDirectoryStream` (NIO.2, available since API 26), which yields entries incrementally
  as the OS returns them. This is also why `ExceptionMapping.kt` gained a
  `java.nio.file.AccessDeniedException` branch beyond `DATA_LAYER.md`'s original example —
  a direct consequence of this fix, not a spec change.
- **`SafBackend.openInput` and `MediaStoreBackend.openInput` both silently swallowed a
  null check-open result.** `resolver.openInputStream(uri)?.close()` does nothing if the
  call returns null (rather than throwing) — the code then fell through to return
  `Success` regardless. Both fixed to treat a null check-open as `FileError.IoFailure`.
- **`MediaStoreBackend`'s `delete()`/`rename()` had `RecoverableSecurityException`
  handling that didn't actually differentiate anything** — the SDK-gated `if`/`else`
  produced the identical `FileError.AccessDenied` in both branches, and a separate
  `rename()` catch clause referenced `RecoverableSecurityException` *unconditionally*,
  contradicting the SDK-gating rationale stated right next to it in `delete()`. Both fixed
  to use one consistent, properly-gated pattern, now actually returning
  `FileError.PermissionDenied(AccessTarget)` for the recoverable case — though see the
  in-code comment on what this still loses (the real `IntentSender`).
- **A test I was about to write had a bug in its own name** — `FileNodeSortTest`'s "ties on
  the primary field fall back to natural name order" asserted the *opposite* (stable
  sort preserving input order, not a name-based tiebreak). Caught before this phase, during
  the hardening pass, but the same category of mistake — worth remembering this project's
  own tests aren't automatically correct just because they're tests.

## 3. `MediaStoreBackend` cannot fully satisfy `StorageBackendContractTest` — a real,
   not-yet-resolved conflict with AC1

Discovered while implementing this backend, not anticipated in the Phase 3 plan:
MediaStore has no concept of an empty, explicitly-creatable directory. A "folder" only
exists in MediaStore's index once it contains at least one indexed row. `createDirectory`
honestly returns failure rather than fake an empty-folder concept that doesn't exist —
but several of `StorageBackendContractTest`'s cases (the empty-directory case, the
duplicate-name-on-`createDirectory` case, and anything that calls `createDirectory` to set
up fixtures for other cases) are built on an assumption `MediaStoreBackend` structurally
cannot meet.

This is presented as a finding, not a resolved decision — no
`MediaStoreBackendContractTest` was written this phase (see §4), so this conflict hasn't
actually been exercised against real test failures yet, only reasoned through by reading
the two pieces of code side by side. Worth deciding deliberately, once someone can run the
contract test against this backend: does `MediaStoreBackend` get an overridden/partial
contract test, does `StorageBackendContractTest` itself need a "supports directories"
capability flag, or does `MediaStoreBackend` simply document known contract exceptions?
All three are reasonable; none was picked here.

## 4. The honest test-coverage gap: no `SafBackendContractTest` /
   `MediaStoreBackendContractTest`

A full contract test needs a working `ContentResolver` round-trip against a real
`DocumentsProvider`/MediaStore-like provider. Building a reliable fake provider for either
(registered via Robolectric's provider mechanism) is substantial additional scope I
judged I didn't have enough confidence to execute correctly without being able to test it
— and a low-confidence fake provider would add more untested surface area than it removes,
not less.

What exists instead: `SafUriMappingTest`/`MediaStoreIdMappingTest`, which test the
`toSafRef()`/`toMediaRef()` URI-and-id parsing logic directly (exposed as `internal`
specifically for this). This is real, meaningful coverage of the part I was least certain
about getting right — but it is **not** equivalent to proving `SafBackend`/
`MediaStoreBackend` actually read/write/list correctly against a real provider. That gap is
open. If anything in this phase deserves a real device/emulator session before being
trusted, it's these two backends' full CRUD behavior, not just their id math.

## 5. Confidence tiers, for quick reference

1. **Highest** — `FileSystemBackend`, `StorageBackendSelector`, `ExceptionMapping`: real
   file I/O (unshadowed by Robolectric), pure dispatch logic, and a fully-tested mapping
   function respectively. `FileSystemBackendContractTest` and `FileSystemBackendTest`
   exercise real behavior on whatever machine actually runs them.
2. **Medium** — `SafBackend`, `MediaStoreBackend`: well-established, stable platform APIs
   used correctly as far as careful reading can confirm, id-mapping logic actually tested,
   but full CRUD behavior against a real provider is unverified (§4), and Robolectric's
   shadows for these subsystems are inherently less complete than its `java.io.File`
   support.
3. **Lowest** — `StorageVolumes.enumerate()`'s API-below-30 path: a positional-match
   heuristic between `storageVolumes` and `getExternalFilesDirs()`, not a documented
   contract. `ANDROID_STORAGE_RESEARCH.md` §10 lists exactly this kind of OEM variability
   as still open. Also unresolved: `StorageType` classification for SAF/MediaStore nodes
   (`SD_CARD`/`MEDIA_INDEX` defaults) is a rough guess pending real volume
   cross-referencing, not yet implemented at the per-node level.

## 6. Smaller decisions and known limitations

- **`OutputTarget.sync()` is a no-op in all three backends.** None currently calls
  `FileDescriptor.sync()`/an equivalent before a stream closes — a durable-write guarantee
  isn't actually provided despite the interface exposing the method. Flagged in each
  backend's own `sync()` KDoc rather than silently assumed.
- **`moveWithin` is same-backend movement only** — no copy+delete fallback for
  cross-filesystem `File.renameTo()` failures. A true cross-volume move (with Phase 7 AC4's
  copy-verify-then-delete-source safety invariant) is orchestration above this interface,
  calling `openInput`/`openOutput` across two backends directly.
- **`SafBackend.moveWithin`'s fallback path (`currentParentDocumentId`) walks the entire
  tree from the root to find a document's current parent** — SAF has no "get parent" query,
  and the domain-level `moveWithin(id, newParent)` signature doesn't carry the current
  parent either. This is a real, expensive fallback, not an oversight; worth revisiting in
  Phase 7 when move performance actually gets tuned against real trees.
- **`FreeSpace()` on `SafBackend`/`MediaStoreBackend` always returns `0L`** — an honest
  "unknown," not a fabricated estimate. Neither has a portable, provider-agnostic
  free-space query at this level; `StorageVolumes.enumerate()` is the real answer, at the
  volume level.
- **`RecoverableSecurityException`'s `IntentSender` has no home in the current `FileError`
  model.** Mapped to `FileError.PermissionDenied(AccessTarget)` as the closest existing
  signal, which conveys "needs re-consent" but loses the actual `IntentSender` a caller
  would need to launch to resolve it. A real gap, not resolved here.
