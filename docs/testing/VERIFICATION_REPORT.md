# App verification — 13 September 2026

> Follow-up status, 15 September: 12 device tests passed on the subsequent mobile build. Newer category/private-files/edge-docking changes await build and phone verification. Work is paused at the user's request; see [agent handoff](../SESSION_HANDOFF.md) and [mobile verification](MOBILE_LAYOUT_VERIFICATION.md). Results below retain their original build scope.

This review checks the working tree based on `faa7cbad`, including the feature work already present before this verification. The supplied `walkthrough.md`, `verification_matrix.md`, `implementation_plan.md`, and two continuation prompts were treated as implementation claims to audit, not as fresh instructions to build every proposed feature.

**Release verdict: not fully verified.** Automated results are recorded below. A successful APK build does not prove Android-version compatibility, storage-provider behavior, recovery after process termination, accessibility, or production network support.

**Phone follow-up:** a rooted Android 14 / API 34 phone with an SD card is now connected. Nine device tests passed, including actual internal ↔ SD-card transfers and hiding round trips. A 200% text-size layout defect was found, fixed and visually rechecked. See [Phone verification](PHONE_VERIFICATION.md) for current device evidence and remaining scope. The initial no-device block below is historical.

## Project context and architecture

Refract is an Android file manager with local/SAF/MediaStore browsing, file operations, archive support, staged transfer bubbles, hiding modes, previews, and storage analysis. Its differentiators are the bubble workflow, press-and-hold previews, and several hiding modes. Many roadmap documents describe the intended product beyond what the code currently implements.

The active modules are `app`, `lint-rules`, and included `build-logic`; `benchmark` is commented out. UI uses Compose and Material 3, with view models and coroutines/Flow calling domain use cases through `StorageBackend`. Runtime dependencies are assembled by `AppContainer`. Bubbles and hiding metadata use `SQLiteOpenHelper`; network settings use SharedPreferences. Hilt is a declared dependency, but the runtime graph is manually assembled. Room, DataStore, Media3, Coil, and Navigation Compose are not the implemented stack described by parts of the documentation. Audio/video previews use platform media APIs.

The checked build pins AGP 9.1.1, Gradle 9.3.1, Kotlin 2.2.10, Compose BOM 2026.04.01, coroutines 1.9.0, and Robolectric 4.14.1. It targets/compiles API 36 with minimum API 27. These are repository versions, not recommendations about current library releases.

## Automated gates

Raw evidence is stored under `build/verification/` (ignored build output). Unit XML reports live in `app/build/test-results/testDebugUnitTest/` and `lint-rules/build/test-results/test/`; Android Lint reports live in `app/build/reports/`.

| Gate | Result |
|---|---|
| App unit / Robolectric / loopback suites | PASS: 238 tests in 35 suites; zero failures, errors or skips |
| Custom lint-rule unit suites | PASS: 22 tests in seven suites; zero failures, errors or skips |
| Total automated tests | **260 passed**, including 33 added app tests |
| `ktlintCheck` | PASS, initially 122 app Kotlin files; phone follow-up passes with 124 |
| `detekt` | PASS under the checked rule configuration |
| `assembleDebug`, `assembleRelease` | PASS; release uses debug signing and no shrinking |
| `assembleDebugAndroidTest` | PASS; initially four tests, expanded to nine during phone verification |
| `lintDebug`, `lintRelease` | PASS: zero errors; debug 41 warnings + one hint, release 33 warnings + one hint |
| Device execution | PASS on the connected API 34 phone: original four via Gradle, expanded nine via AndroidJUnitRunner; see phone report |

`test-summary.json` contains per-suite counts/timings; `verified-source-files.csv` records hashes of the tested app source tree. `baseline.patch`, `baseline-status.txt`, and `baseline-files.csv` preserve the pre-verification working-tree context. The final combined gate log is `verified-gates.log`.

The initial completed verification run finished successfully in 6m 42s: 159 tasks, 30 executed and 129 up-to-date. Earlier failed attempts remain as diagnostic evidence, including a formatting-introduced syntax error that was corrected before this successful run. The phone follow-up's layout fix also passed the combined gates in 9m 9s (`phone-fix-gates.log`); its newer source/artifact hashes are in the `phone-*` evidence files. The final source also passes `git diff --check`.

APK evidence is in `apk-artifacts.json`: debug 19,540,278 bytes, release 13,103,845 bytes, instrumented tests 370,621 bytes, with SHA-256 hashes for each. `release-signature.txt` records successful signature verification using the Android Debug certificate. `release-manifest.txt` confirms minimum API 27, target/compile API 36, and version `0.1.0-phase1`. Signing verification establishes artifact integrity, not production signing readiness.

The initial baseline gates passed, but the app ktlint integration only discovered build scripts under AGP's built-in Kotlin. Its source inputs now explicitly include production, unit-test, and device-test Kotlin. Existing source formatting therefore also needed correction. CI referenced nonexistent product flavors; it now uses actual debug/release tasks and compiles the device-test APK.

Gate interpretation follows the checked configuration: Android Lint disables `NoHardcodedDp`, does not check test sources or dependencies, and still reports warnings. Detekt uses the repository's rule configuration. No coverage percentage was measured, and test counts are not feature-completeness percentages. CI changes were exercised locally; a hosted GitHub Actions run was not triggered.

Remaining release-lint findings include selected-photo access handling (two), adaptive screen-size API usage (two), KTX suggestions (11), and dependency-version suggestions (eight). These are observed lint findings, not independently verified upgrade recommendations. The selected-photo and adaptive-layout cases belong in the outstanding permission/device matrix. Build output also retains experimental AGP configuration, Kotlin plugin-loading and Gradle deprecation warnings.

## Verification matrix

`PASS` below means the named automated scenario passed, not that the entire feature is certified. `PARTIAL` indicates implemented behavior with unverified or missing scenarios. `BLOCKED` requires unavailable external equipment. `FAIL` identifies a known implementation gap.

| Area | Evidence / current coverage | Overall scope status |
|---|---|---|
| FileSystem | Existing CRUD, special names, deep directories, 2,000-entry chunk test; device byte-copy journey passed on API 34 | PARTIAL: real read-only volumes, SD cards and USB disconnect remain |
| SAF / MediaStore | Controlled Robolectric providers: 451 rows across three chunks, denied access, null cursor, consumer cancellation, late cursor failure | PARTIAL: real Android providers, grants and removable media remain |
| Copy / move | Fault tests for same-size corruption, full disk, cancellation, failed overwrite, deletion failure, self-copy and descendant moves; copy within same folder with auto-renaming | PARTIAL: real internal/SD-card move passed; crash durability, concurrent writers and other providers remain |
| Archives | Existing compression/extraction and archive path-validation tests | PARTIAL: device workflows, huge/untrusted archive limits and interruption remain |
| Transfer bubbles | Existing repository/SQLite tests and foreign-key cascade coverage; device database-reopen journey passed on API 34 | PARTIAL: process-kill, drop/transfer failure lifecycle and all drag targets remain |
| Hiding | Real file round trips through repository on Robolectric and API 34 hardware; footer/collision/size safeguards | PARTIAL: startup recovery wiring, true process-kill and gallery scanner behavior remain |
| Storage analysis | Full multi-chunk enumeration, empty-first-chunk and cyclic-provider regressions | PARTIAL: device performance, changing providers and permission revocation during full cleanup remain |
| FTP | Loopback sockets cover multiline greeting, login success, authentication failure and rejected TLS negotiation | PARTIAL: LIST/transfers, NAT, disconnect and real-server TLS remain |
| SFTP / SMB | Current code tests reachability but does not implement protocol listing and transfer | FAIL: empty success results must not be treated as functional support |
| WebDAV | Existing error-path tests | PARTIAL: no full authenticated-server read/write/certificate matrix |
| Network credential protection | Deterministic package-derived AES key, fixed IV and plaintext fallback observed | FAIL: not AndroidKeyStore-backed credential protection |
| Media previews | Existing helper tests plus bounded single-line text input and checksum cancellation tests | PARTIAL: real audio/video/PDF/image fixtures, large images and lifecycle resource behavior remain |
| Quick Peek | Actual Compose pointer events on Robolectric: tap, hold/release and cancelled pointer | PARTIAL: real device drag/scroll conflicts, swipe, multi-touch and tablet behavior remain |
| Accessibility | Source semantics and lint are insufficient for assistive-technology certification | PARTIAL: Home card verified at 200% after a fix; TalkBack, Switch Access, contrast and other screens remain |
| API / form factors | Robolectric provider/hiding coverage on API 27; Compose pointer coverage on API 34 | PARTIAL: API 34 phone tested; API 27/29/30/33/36, tablet and foldable matrix remain |
| Performance | Existing bounded-buffer and synthetic listing/database tests | NOT TESTED: controlled startup, jank, 1 GB transfers, low-memory and thermal behavior |
| Process death / background | SQLite persistence primitives exist; operations run in view-model scope | FAIL/PARTIAL: no proven durable operation queue or complete startup recovery |
| Release packaging | Release is signed with the debug key; `isMinifyEnabled=false` | PARTIAL: buildable development artifact, not production signing or R8 validation |
| Logging / diagnostics | Build/test evidence exists; the documented application logger is not implemented | PARTIAL: operational diagnostics/redaction and crash reporting remain |

## Fixes made from verification findings

- File transfers stage output, verify its SHA-256 and length after writing, and publish it before deleting the source. Existing destinations survive copy/verification failure. Failed source deletion is reported. Directory moves keep sources until all child copies succeed. Invalid self/descendant destinations are rejected; same-folder copies can still use auto-renaming.
- FileSystem sync now syncs the actual file descriptor after closing the writer. SAF, MediaStore and filesystem enumeration report late failures instead of silently returning only the earlier chunks. Provider cancellation is propagated.
- Storage analysis consumes every chunk, avoids repeated files/directories, exposes partial scans and reuses the completed analysis instead of performing another full scan. Savings avoid counting the same temporary duplicate twice.
- Fast Obscure validates bounded footer metadata before changing a file, checks collisions before restore, and records recovery data before masking the header. Repository path handling now matches actual `file:` IDs. Failed/missing restores keep metadata and private-storage operations check deletion outcomes.
- Quick Peek distinguishes pointer cancellation from hold timeout and dismisses on cancellation. Text previews enforce a byte limit before a single long line can allocate unbounded memory; checksum cancellation is preserved.
- FTPS now refuses plaintext fallback when the server rejects `AUTH TLS`. This focused regression fix does not establish complete FTPS security: data-channel TLS and certificate/hostname behavior still need correction and integration testing.

The baseline regression run exposed eleven failing scenarios; the new FTP negotiation regression exposed another. Before/after logs are retained. These fixes improve observed behavior without establishing transactional safety across arbitrary provider failures or process termination.

### Added test evidence

Paths below are relative to `app/src/test/kotlin/com/devbehindyou/refract/`.

| Suite | Tests | Boundary exercised |
|---|---:|---|
| `domain/usecase/VerificationRegressionTest.kt` | 11 | Real operation/scanner use cases over controlled storage backends |
| `data/hide/FastObscureAdversarialTest.kt` | 5 | Actual temporary files, corrupted metadata and collisions |
| `data/hide/HiddenFilesIntegrationTest.kt` | 4 | Repository, SQLite and local files under Robolectric |
| `data/backend/ProviderListingIntegrationTest.kt` | 5 | ContentResolver and controlled cursors for SAF and MediaStore |
| `ui/interaction/QuickPeekComposeTest.kt` | 3 | Compose pointer dispatch under Robolectric |
| `domain/usecase/TextPreviewBoundsTest.kt` | 2 | Bounded input and coroutine cancellation |
| `data/backend/network/FtpProtocolIntegrationTest.kt` | 3 | Actual loopback TCP control-channel exchanges |

`app/src/androidTest/kotlin/com/devbehindyou/refract/StorageJourneyInstrumentedTest.kt` adds three device journeys: activity recreation, byte-for-byte local copying and bubble database reopening. Together with the existing storage-routing test, the initial device suite had four tests. The phone follow-up adds four `HiddenFilesDeviceTest` cases and one `RemovableStorageDeviceTest` case; all nine passed on API 34. Compilation is recorded separately from runtime execution.

## Remaining release work, in order

1. Complete crash-safe operation/hiding recovery and startup invocation; preserve staged bubble items on failed transfers. Verify forced termination at each mutation boundary against disposable files.
2. Replace credential protection with a versioned AndroidKeyStore-backed design and migration; finish FTPS protection. Implement SFTP/SMB or clearly disable their unsupported operations. Run authenticated disposable FTP/FTPS/SFTP/SMB/WebDAV server suites.
3. Expand the completed API 34 device pass to other devices/emulator images. Execute the remaining storage-permission, SAF, USB-disconnect, process-kill and real preview journeys. A database reopen test is not equivalent to killing Android's process.
4. Execute phone/tablet/foldable, accessibility and performance matrices. Enable and configure the benchmark module before making performance claims.
5. Configure production signing and decide on shrinking, then repeat verification on the actual shipping artifact. Reconcile remaining aspirational architecture, privacy and roadmap claims with implemented behavior.

No Android device was available during the initial automated pass, and the configured SDK has no emulator/system images. The subsequent API 34 phone run supersedes that block for its tested scenarios. Other API levels/form factors and unexercised workflows remain unverified; none are inferred from Robolectric or compilation.

The first device attempt could not resolve an uncached UTP test-runner dependency offline. Retrying with network access resolved that dependency and reached the actual device runner, which reported no connected devices. Evidence: `build/verification/device-tests-online.log` (2m 53s, 77 tasks). This is an environment block, not a passing device test or an observed application test failure.

## Reproduction

Run from the repository with JDK 21 and a configured Android SDK:

```powershell
.\gradlew.bat --no-daemon --max-workers=1 --continue :lint-rules:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest ktlintCheck detekt :app:lintDebug :app:lintRelease
.\gradlew.bat --no-daemon --max-workers=1 :app:connectedDebugAndroidTest
```

The combined automated gates used `--offline` with cached dependencies; the device-runner retry used network access to obtain its missing dependency. Robolectric additionally needs its Android framework images in Maven's local cache or network access. The second command requires an authorized device/emulator and installs the app/test APK. Test fixtures use disposable app-private files; testing removable media and destructive recovery needs dedicated fixtures.
