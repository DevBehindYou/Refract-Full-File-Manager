# Refract

A native Android file manager (Kotlin, Jetpack Compose, Hilt, coroutines). Built by an AI
assistant against the specification in `docs/`, phase by phase, following
`docs/roadmap/IMPLEMENTATION_ROADMAP.md`.

**Open this in Android Studio**, not Google's "AI Studio" (a separate, web-based
generative-AI tool that can't open or build a Gradle project at all).

## Before anything else: this code has never been compiled

Every phase of this project was built in a sandboxed environment with no network access
to Google's Maven, Maven Central, or `services.gradle.org` — only a short allowlist
covering GitHub, PyPI, npm, and crates.io. That means:

- No dependency has ever been resolved.
- `./gradlew build` has never actually been run against this code, at any point, for any
  module.
- Everything was written from documented API knowledge, cross-checked against official
  sources wherever a web search was possible, but nothing has been mechanically verified.

**This is a carefully-reasoned first draft that needs one real build to confirm — not a
verified-green project.** The rest of this document tells you exactly where to look first
if that build fails, ranked by how likely each item is to actually matter.

## Current status

| Phase | What it covers | Status |
|---|---|---|
| 0 | Real-device storage research | **Not done** — needs physical hardware; still blocks full confidence in Phase 3's `SafBackend`/`MediaStoreBackend` and will block Phase 11 (glass) |
| 1 | Project skeleton (Gradle, 7 custom Lint rules, empty Activity, CI) | Done |
| 2 | Domain model (`FileNode`, `FileError`, `StorageBackend` interface, ...) | Done |
| — | Hardening pass (decorator tests, locale-aware sort, `:benchmark` scaffold) | Done |
| 3 | Real storage backends (`FileSystemBackend`, `SafBackend`, `MediaStoreBackend`) | Done, with real open gaps — see below |
| 4–13 | Permissions, design system, browse UI, operations, search, storage analysis, glass, polish, release | **Not started** |

## First build: check these, in this order

Ranked by how likely each one is to actually break something, not by how interesting it is.

1. **`gradle/libs.versions.toml`** — every version is tagged `[CONFIRMED]` or
   `[BEST-EFFORT]` inline. If the build fails on dependency resolution, this file is the
   first place to look, before anything else. Two specific entries are the most likely
   culprits:
   - `lintApi` (`32.3.0`) — extrapolated from a historical "AGP version + 23 major"
     pattern confirmed only through AGP 8.x, not independently verified for AGP 9.x.
   - The `androidJunit5Plugin` **plugin ID itself**
     (`de.mannodermaus.android-junit`, not the older `-android-junit5`) — a plugin
     rename is exactly the kind of thing that produces a clean, easy-to-fix
     "plugin not found" error.
2. **AGP 9's built-in Kotlin support**, used in
   `build-logic/convention/src/main/kotlin/refract.android.application.gradle.kts`. This
   project targets AGP 9.3.0, which folds Kotlin support into `com.android.application`
   directly rather than needing a separate `org.jetbrains.kotlin.android` plugin — a
   change that happened after this assistant's reliable knowledge cutoff (Jan 2026) and
   was confirmed only via a live search, not direct experience. If
   `kotlin { jvmToolchain(17) }` fails to resolve there, `compileOptions` in the same file
   sets Java 17 as a fallback regardless.
3. **The Gradle wrapper jar** (`gradle/wrapper/gradle-wrapper.jar`) was generated locally
   using an old (2017-era) Gradle 4.4.1 install, since this sandbox couldn't reach
   `services.gradle.org`. It should still correctly bootstrap-download the real Gradle
   9.3.0 distribution — the wrapper protocol has stayed compatible for years — but run
   `./gradlew wrapper --gradle-version 9.3.0` once you have real network access, to
   regenerate it from a genuine current template.
4. **The `:benchmark` module** (`com.android.test`) is the newest, least-certain piece of
   tooling in the project — deliberately *not* using the same convention plugin as `:app`
   (see `docs/roadmap/HARDENING_PASS_1_NOTES.md` §3 for why), and its
   `experimentalProperties["android.experimental.self-instrumenting"]` flag is included
   from general recollection, not confirmed against current tooling. If anything fails to
   sync, this module is a reasonable first suspect.

## Once it compiles: real open gaps, not just "unverified"

These aren't compilation risks — they're places where a deliberate, honest limitation or
an actual undecided design question remains, even assuming everything above resolves
cleanly.

- **`MediaStoreBackend` cannot fully satisfy the shared `StorageBackendContractTest`.**
  MediaStore has no concept of an empty, explicitly-creatable directory — `createDirectory`
  honestly fails rather than fake one. This is a real, undecided conflict with Phase 3's
  AC1, found while implementing, not resolved. See `docs/roadmap/PHASE_3_NOTES.md` §3.
- **No `SafBackendContractTest` or `MediaStoreBackendContractTest` exist.** Building a
  reliable fake `ContentProvider`/`DocumentsProvider` for Robolectric was judged too
  large and too uncertain to execute correctly without being able to test it. What exists
  instead (`SafUriMappingTest`, `MediaStoreIdMappingTest`) tests the riskiest logic — URI
  and id parsing — directly, but full read/write/list/delete behavior against a real
  provider is unverified. See `docs/roadmap/PHASE_3_NOTES.md` §4.
- **Two acceptance criteria are structurally outside what any AI assistant working in a
  sandbox could verify:** Phase 3 AC2 (instrumented tests across 5 real API levels) and
  AC4 (a measured <100ms streaming latency). Both need a real device or emulator.
  Scaffolds exist for both; neither has ever actually run.
- **`RecoverableSecurityException`'s `IntentSender` has no representation in the
  `FileError` model.** `MediaStoreBackend` maps it to
  `FileError.PermissionDenied(AccessTarget)` as the closest existing signal, which loses
  the actual `IntentSender` a caller would need to launch to resolve it.
- **`OutputTarget.sync()` is a no-op in all three backends** — no durable-write guarantee
  is actually provided despite the interface exposing the method.
- **`StorageVolumes.enumerate()`'s pre-API-30 path** is a positional-match heuristic
  between `StorageManager.storageVolumes` and `Context.getExternalFilesDirs()`, not a
  documented contract — `ANDROID_STORAGE_RESEARCH.md` §10 lists exactly this kind of OEM
  variability as still open pending Phase 0.
- **`FileError`'s per-variant `severity`/`recoverable`/`action` mapping** (22 variants) is
  this project's own judgment call in roughly 15 of them — `ERROR_MODEL.md` gives a
  message-mapping table but doesn't assign these three properties per variant explicitly.
  Worth a real review once the screens that consume it exist.
- **Five supporting domain types have no concrete spec anywhere in `/docs`:**
  `AccessTarget`, `NameProblem`, `FailedItem`, `UndoToken`, `Conflict`. Each has a
  minimal, honestly-`Provisional`-marked definition, sufficient to compile and test now,
  not intended as settled design.

## Module map

```
:app            Single application module — domain/, data/, UI (empty beyond MainActivity)
:lint-rules     7 custom Lint checks enforcing the architecture's layer boundaries
:benchmark      Macrobenchmark module (com.android.test) — scaffolded, unrun
build-logic     Convention plugin (compileSdk/minSdk/targetSdk/Compose baseline for :app)
```

Inside `:app/src/main/kotlin/com/devbehindyou/refract/`:

```
domain/model/       FileNode, FileNodeId, FileError, FileResult, Operation, SortSpec, ...
domain/repository/  StorageBackend interface (the abstraction all three real backends implement)
data/backend/       FileSystemBackend, SafBackend, MediaStoreBackend, StorageBackendSelector
data/mapping/       Throwable -> FileError exception boundary
data/volume/        StorageManager-based volume enumeration
data/di/            Hilt @Module wiring the three backends into a BackendType-keyed map
```

Test fakes (`InMemoryBackend`, four fault-injecting decorators) live under
`:app/src/test/.../domain/testing/` — test-only, never shipped.

## Further reading

Each phase's full reasoning, every smaller decision, and everything flagged above in more
depth:

- `docs/roadmap/PHASE_1_NOTES.md` — Gradle/tooling setup, the 7 Lint rules
- `docs/roadmap/PHASE_2_NOTES.md` — the domain model
- `docs/roadmap/HARDENING_PASS_1_NOTES.md` — decorator tests, locale-aware sort, `:benchmark`
- `docs/roadmap/PHASE_3_NOTES.md` — the three real backends, including real bugs caught
  during review (worth reading even just for what *not* to trust yet)

The full original specification this project was built against is in `docs/` — start with
`docs/README.md` and `docs/roadmap/IMPLEMENTATION_ROADMAP.md` if you want the source of
truth for what each phase was actually asked to deliver.
