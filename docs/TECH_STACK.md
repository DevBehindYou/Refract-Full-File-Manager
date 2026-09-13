> Verified implementation status (13 September 2026): see [App verification](testing/VERIFICATION_REPORT.md). The tables below include planned dependencies; the active app uses manual AppContainer wiring, SQLiteOpenHelper, SharedPreferences and platform media APIs. Room, DataStore, Media3, Coil and Navigation Compose are not present in the app dependency graph.

# Technology Stack

**Rule: every dependency must earn its place.** Each entry below states what it does, why
the platform alternative is insufficient, and what we would do if it were removed. Anything
that cannot answer those three questions is not added.

Versions are indicative as of **August 2026**; pin exact versions in
`gradle/libs.versions.toml` and update deliberately, not automatically.

---

## 1. Core platform

| Dependency | Why | If removed |
|---|---|---|
| **Kotlin** (2.x, K2) | Mandated. Coroutines, sealed types, null safety. | N/A |
| **Jetpack Compose** (BOM, 1.10.x line) | Declarative UI is required for the tier-swappable glass system and adaptive layouts. XML would make the material system unmaintainable. | N/A |
| **Compose Material 3** | Baseline components, theming, dynamic colour, motion specs. We override the *material*, not the *system*. | Rebuild sheets, dialogs, snackbars by hand |
| **AndroidX Activity Compose** | `ComponentActivity`, `enableEdgeToEdge()`, predictive back plumbing. | N/A |
| **Kotlin Coroutines + Flow** | The entire concurrency and streaming model. | N/A |
| **Navigation Compose** | Type-safe routes (Kotlin serialization based), predictive back integration, deep links. | Hand-rolled back stack — not worth it |

## 2. Data and persistence

| Dependency | Why | If removed |
|---|---|---|
| **Room** | Favourites, recents, search index, operation history. SQLite with compile-time verified queries and Flow observation. | Raw SQLite — more code, no compile-time checks |
| **DataStore (Preferences)** | Settings. `SharedPreferences` has no coroutine/Flow API and does blocking I/O. | `SharedPreferences` with a wrapper; worse |
| **AndroidX DocumentFile** | SAF tree traversal. **Used carefully:** `DocumentFile.listFiles()` is notoriously slow because it re-queries per child. We use raw `DocumentsContract` + a single bulk `ContentResolver.query()` for listing, and `DocumentFile` only for one-off operations. | Write the `DocumentsContract` wrapper ourselves (we largely do anyway) |
| **Kotlinx Serialization** | Navigation type-safe routes, cached scan results. | Manual parcelling |

## 3. Dependency injection

| Dependency | Why | If removed |
|---|---|---|
| **Hilt** | ViewModel injection, scoping the storage backends and the operation service, swapping fakes in tests. The graph is genuinely non-trivial (3 storage backends selected at runtime, a service-scoped executor). | Manual DI container — viable but adds ~400 lines of boilerplate and makes test doubles awkward |

Koin was considered and rejected: runtime resolution errors in a graph this size are worse
than Hilt's build-time cost.

## 4. Images and media

| Dependency | Why | If removed |
|---|---|---|
| **Coil 3** | Thumbnail loading with memory + disk cache, Compose-native, coroutine-based, supports custom fetchers — we need one for SAF `content://` and one for APK icons. | Hand-rolled decode pipeline; loses caching and lifecycle correctness |
| **Media3 (ExoPlayer)** | Video and audio preview. `MediaPlayer` is unreliable across OEMs, has no Compose story, and no modern format support. We take `media3-exoplayer` + `media3-ui-compose` only, not the session/cast modules. | Hand off everything to an external player — acceptable degradation but poor UX |
| **`ThumbnailUtils` / `ContentResolver.loadThumbnail`** (platform) | Preferred thumbnail source on API 29+; Coil fetches through it. | Full decode of every image — unacceptable |
| **`PdfRenderer`** (platform, API 21+) | V1 PDF preview. No dependency needed. | Drop PDF preview |

## 5. Graphics / Liquid Glass

| Dependency | Why | If removed |
|---|---|---|
| **Haze** (`dev.chrisbanes.haze`, Apache-2.0) | Backdrop capture and blur that already picks the right implementation per API level (scrim on old versions, layered graphics layers around API 32, `RuntimeShader` on 33+). Reimplementing this correctly is weeks of work and a permanent maintenance burden. | Implement backdrop capture with `rememberGraphicsLayer()` on API 34+, `RenderEffect` on 31–33, scrim below. Roughly 600 lines, and worse on the versions we care least about. |
| **AGSL / `RuntimeShader`** (platform, API 33+) | Our own refraction shader sits on top of the captured backdrop. This is the part that must be ours. | Tier A collapses into Tier B |
| **`androidx.graphics.shapes`** | Optional: rounded polygon morphing for the nav indicator. Only if the shape morph is kept. | Use `RoundedCornerShape` + scale animation |

**Rejected:** any WebView-based glass, any OpenGL/Vulkan custom renderer, RenderScript
(deprecated since API 31).

## 6. Background work

| Dependency | Why | If removed |
|---|---|---|
| **Foreground `Service` + coroutines** (platform) | User-initiated file operations must run now, report byte-level progress, and be cancellable instantly. `foregroundServiceType="dataSync"`. | Operations die when the app backgrounds — unacceptable |
| **WorkManager** | Only for deferrable maintenance: search index refresh, thumbnail cache trim, storage rescan. Survives reboot, respects Doze. | Run maintenance on next app open — acceptable but worse |

We deliberately use **both**, for different jobs. Reasoning in
`architecture/FILE_OPERATIONS.md` §2.

## 7. Archives

| Dependency | Why | If removed |
|---|---|---|
| **`java.util.zip`** (platform) | MVP ZIP create/extract. Streaming, zero dependency, adequate. | N/A |
| **Apache Commons Compress** (V1, optional) | TAR, TAR.GZ, and better ZIP metadata. Pure Java, no NDK, no 16 KB page-size concern. | Drop TAR support |
| **zip4j** (V1, optional) | Password-protected ZIP if demanded. Evaluate size impact first. | Drop encrypted ZIP |
| **7-Zip-JBinding / native p7zip** | **Rejected** for MVP and V1: native libs, ABI splits, 16 KB page-size work, and a large attack surface for a niche format. |

## 8. Testing

| Dependency | Why |
|---|---|
| **JUnit 5** + **Turbine** | Unit tests and Flow assertions |
| **`de.mannodermaus:android-junit`** (Gradle plugin) | AGP's Android unit-test task doesn't run the JUnit Platform natively — this plugin bridges it. **Added in Phase 1**, not part of the original spec; see roadmap/PHASE_1_NOTES.md for the version chosen and why. |
| **MockK** | Kotlin-native mocking |
| **Robolectric** | Fast JVM tests for anything touching light Android classes |
| **Compose UI Test** | Semantics-based UI tests |
| **AndroidX Test / Espresso** | Instrumented flows and permission grants |
| **Truth** | Readable assertions |
| **`androidx.benchmark` (macro)** | Start-up and scroll benchmarks; also generates the baseline profile |
| **JankStats** | Runtime frame timing, feeds the glass tier watcher |

## 9. Tooling

* **Gradle version catalogs** (`libs.versions.toml`) — single source of dependency truth.
* **ktlint + detekt** — style and complexity gates in CI.
* **Android Lint** with custom rules: no `java.io.File` in `domain`/`ui`, no raw API-level
  integers, no `runBlocking` outside tests.
* **R8 full mode**, resource shrinking, baseline profile.
* **LeakCanary** in debug builds only.

## 10. Explicitly rejected

| Rejected | Reason |
|---|---|
| RxJava | Coroutines cover everything; two async models is a maintenance tax |
| Retrofit / OkHttp / Ktor | No network at MVP. Adding an HTTP client "for later" invites network features |
| Firebase Analytics / Crashlytics (default) | Contradicts `PRIVACY.md`. Crash reporting is opt-in and lives in a separate build flavour |
| Timber | A 40-line structured logger gives us operation IDs and redaction, which Timber does not |
| Glide / Picasso | Coil is Compose-native and Kotlin-first |
| Accompanist (most) | Almost all of it has graduated into Compose proper |
| RenderScript | Deprecated from API 31 |
| Any theme/icon-pack framework | Dilutes the design identity |

## 11. Gradle configuration summary

```kotlin
android {
    compileSdk = 36
    defaultConfig {
        minSdk = 27
        targetSdk = 36
        vectorDrawables.useSupportLibrary = true
    }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
kotlin { jvmToolchain(17) }
```

A CI job additionally compiles against the newest available SDK (37 at time of writing) so
that platform drift is caught before it becomes a Play deadline.
