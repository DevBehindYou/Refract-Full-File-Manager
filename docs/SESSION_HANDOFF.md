# Agent handoff — 15 September 2026

Read this before resuming development. This records the current working tree, not a release certification. Preserve all existing edits and untracked source files; do not reset the repository to HEAD. No commit or push was made for this follow-up.

## User requests still in scope

1. Complete app verification against the supplied workflow and verification documents.
2. Review every mobile screen for cramped labels, scrolling, keyboard overlap, large fonts and landscape.
3. Correct SD-card navigation and Android Back/edge-swipe navigation.
4. Make the transfer + bubble draggable and snap to a screen edge when released or flung.
5. Home categories must collect matching files across readable internal storage and SD card: Images, Videos, Audio, Docs, Archives and APKs. Downloads opens the actual Download folder. More opens extra categories.
6. Private files must open a dedicated user-facing collection, not the app's internal files directory containing `profileInstalled`.
7. Keep the supplied `Refract-Icon.png` as the launcher artwork and remove the duplicated header inset.

The user most recently requested ONLY documentation updates, then stopping the process and task. Work is paused; the next-actions list is for a future explicitly resumed session. They also authorized cleaning Codex cache to relieve disk pressure. This does not authorize deleting conversations, settings, projects or phone data, or moving arbitrary folders. Attached continuation prompts were reference material, not independent instructions.

## Actual architecture

Native Kotlin, Compose/Material 3; app, lint-rules and included build-logic modules. Runtime wiring uses `AppContainer`, domain use cases and `StorageBackend`; the benchmark module is disabled. Hilt is declared but the active graph is manual. Bubbles/hiding use SQLiteOpenHelper; network settings use SharedPreferences. Do not assume the proposed Room/DataStore/Navigation Compose architecture is implemented. See [verification report](testing/VERIFICATION_REPORT.md).

Pinned toolchain: AGP 9.1.1, Gradle 9.3.1, Kotlin 2.2.10, Compose BOM 2026.04.01, Java 21, compile/target API 36, min API 27.

## Implemented and checked on the phone

- Header: root Scaffold consumes applied insets in both layouts. Browse back/search bounds moved from y=263–329 to y=159–225, removing duplicate status-bar space.
- Icon: supplied PNG imported unchanged into launcher resources; app-info icon visually checked.
- SD: `StorageVolumes` resolves the actual mounted root after access checks. Missing/unavailable roots no longer fall back silently to internal storage. Actual public SD root opened on the phone.
- Back: Browse handles overlays, selection, search and folder history before root navigation; app tab history is saved. Primary Browse view model survives recreation. Hardware Back and edge gesture returned from SD Documents to its root.
- Mobile layout: scrolling/wrapping choices and summaries in Storage, Storage Intelligence, action/hide/transfer dialogs, previews and hidden-file tabs. All network protocol names were readable; analysis filters could scroll to Temp & Cache.
- Bubble: dragging and saved relative position worked on the installed version. Edge snapping is newer and not yet verified.

## Newer source changes awaiting successful build and phone checks

| Area | Source and intended behavior |
| --- | --- |
| Collections | `domain/model/FileCollection.kt`: extension/MIME classification, directory exclusion, Docs includes PDF/text/ebooks, APK extension takes priority over ZIP MIME. |
| Phone scan | `data/volume/PhoneFileIndex.kt`: cancellable IO scan through directory-listing use case across readable volume roots; partial results, canonical-directory deduplication, cache and manual refresh, unreadable-folder count. |
| Category UI | `ui/screens/CategoryScreen.kt`: searchable filtered collection, scan status, refresh, permission entry, file previews. MainActivity routes Home categories to it. |
| More | MainActivity popup for PDFs, text, ebooks, fonts and other files. Downloads remains a direct folder route. |
| Private files | Home opens `HiddenFilesScreen(initialMode = PRIVATE_STORAGE, privateOnly = true)` with a dedicated title and no mode tabs; it no longer exposes the app sandbox directory. This is the existing private hiding mode, not a newly encrypted vault. |
| Edge bubble | `TransferBubbleRail.kt`: velocity tracking, projected edge selection, spring animation, saved left/right position and physical absolute offset. Rail remains hosted in Browse; a global overlay on every tab has not been implemented. |
| Home | Category grid uses three columns on typical phones, two on narrow/large-font layouts, four on wider layouts, to prevent Downloads splitting awkwardly. |
| Keyboard | Network dialog adds IME/system-bar padding and disables platform decor fitting after a physical check found keyboard overlap. Needs recheck. |
| Other | Browse title ellipsis; scrolling Quick Peek content; removed accidental nested scrolling in details rows. |

Paths above are under `app/src/main/kotlin/com/devbehindyou/refract/`. New tests: `FileCollectionTest`, `PhoneFileIndexTest`, `BubbleDockTest`. These have not passed a completed run yet. Review scan behavior on actual Android volume paths, cached-result freshness and main-thread filtering/sorting performance before claiming all-phone coverage. Protected/unreadable folders cannot be promised.

## Evidence and limits

Evidence lives in ignored `build/verification/`; it may not survive clean/clone. Preserve it or copy needed evidence before cleaning build outputs.

| Run | Result and scope |
| --- | --- |
| Earlier baseline | 238 app unit/Robolectric tests and 22 custom lint tests passed. Debug/release and both Android Lint variants passed; lint had 0 errors, 41 debug / 33 release warnings. See PHONE_VERIFICATION and VERIFICATION_REPORT for exact earlier scope. |
| `mobile-gates-2.log` | BUILD SUCCESSFUL, 2m 51s, 88 tasks: format, detekt, app unit tests, debug and instrumentation APK assembly. Predates latest collection/private/edge/keyboard changes. |
| `mobile-device-tests.log` | `OK (12 tests)`, 10.023s: previous 9 storage/hiding tests plus 3 navigation tests. Not a result for the newest source. |
| `mobile-final-build-2.log` | Debug APK assembled; Android Lint failed with insufficient disk space. Not a complete gate pass. |
| `categories-pinned-compile.log` | Last recorded progress stops at build-logic generatePrecompiledScriptPluginAccessors, with no success/failure footer. No Java process was listed on 15 September recheck; treat this attempt as incomplete, not an active or successful build. |

Physical evidence pairs PNG/XML: `mobile-sd-root`, `mobile-back-sd-root`, `mobile-gesture-back`, `mobile-bubble-dragged`, `mobile-network-normal`, `mobile-network-keyboard`, `mobile-analysis-normal`, `mobile-analysis-filters`, `mobile-home-normal`. Header/icon: `header-fixed.png`, `header-fixed-ui.xml`, `launcher-icon-installed.png`. Home storage overview was separately fixed and checked at 200% font scale in the earlier phone pass.

The 12 device tests include storage routing, hiding/restoring bytes and metadata, and internal/SD transfers using disposable app-owned fixtures. Public SD navigation was a separate visual check; neither proves SAF grants/revocation. Navigation tests cover Storage→Home Back, Browse→origin Storage Back and Storage-tab recreation.

## Machine and phone setup

Repository: `C:\Users\temp\Documents\GitHub\Refract-Full-File-Manager`.

- SDK: `C:\Users\temp\AppData\Local\Android\Sdk` (ignored local.properties points here).
- Java: `C:\Program Files\Java\jdk-21`.
- Global Gradle home: `C:\Users\temp\.gradle`.
- Pinned Gradle was restored from the official distribution with SHA-256 verification at `build/verification/gradle-runtime/gradle-9.3.1/bin/gradle.bat`. The downloaded ZIP was deleted after extraction; check the executable still exists. Avoid substituting Gradle 9.6.1.
- Disk exhaustion repeatedly interrupted builds; RAM was also nearly exhausted with other Android builds active. Disposable Codex browser/GPU cache cleanup was executed, leaving sessions/settings intact. Concurrent activity prevented reliable measurement of bytes recovered. C: had 3.75 GiB free on 15 September recheck; remeasure before building. Do not kill unrelated IDE builds or clear all global caches.
- Phone serial `5ff095c7f80a`, model 23076RN4BI, Android 14/API 34, 1080×2460, SD UUID `F929-FA97` (Samsung SD card). Recheck connection; historical connection is not current proof.
- Use SDK platform-tools adb, never the obsolete `C:\adb\adb.exe`. Set ANDROID_USER_HOME as below.
- Install updates with `adb install -r`; no uninstall, clear-data or changes to existing user files. Use disposable fixtures and isolated metadata for mutation tests.
- Last saved display settings: font_scale 1.0, accelerometer_rotation 0, user_rotation 0. Re-read before testing and restore after changes. `mobile-original-display.json` records the earlier snapshot.

Suggested PowerShell build setup (run sequentially, inspect each result):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:GRADLE_USER_HOME = 'C:\Users\temp\.gradle'
$env:JAVA_TOOL_OPTIONS = '-Duser.home=C:\Users\temp'
$env:ANDROID_USER_HOME = 'C:\Users\temp\.android'
$gradleRunner = '.\build\verification\gradle-runtime\gradle-9.3.1\bin\gradle.bat'
& $gradleRunner --no-daemon --max-workers=1 :app:ktlintFormat :app:compileDebugKotlin
# After compilation succeeds:
& $gradleRunner --no-daemon --max-workers=1 :app:detekt :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
# Then remaining release/lint gates when disk permits:
& $gradleRunner --no-daemon --max-workers=1 :app:assembleRelease :app:lintDebug :app:lintRelease
```

Device helper: `build/verification/phone_ui.py` captures screenshots/XML and supports semantic taps, Back and swipes. Foreground `.MainActivity` before testing and verify successful taps before dependent actions. Direct instrumentation command after installing matching app/test APKs:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s 5ff095c7f80a shell am instrument -w -r com.devbehindyou.refract.test/androidx.test.runner.AndroidJUnitRunner
```

## Next actions in order

1. Check free disk/RAM, pinned runtime and phone availability; restart the incomplete compile with a fresh log. Fix actual compiler/formatter failures, preserving all working-tree changes.
2. Run classification/index/docking tests and existing regression gates. Do not describe prior passes as certification of current source.
3. Install the final debug APK with data preserved. Verify every category contains only matching files from both volumes, Downloads route, More popup and private-files screen; check empty, scanning, denied-access and refresh states.
4. Verify release and fling dock to both edges, survive rotation/reopening and keep buttons reachable. Resolve global bubble visibility if necessary to meet the user's across-screens request.
5. Check network dialog with keyboard, Home labels, analysis filters, hidden files, previews and action sheets at normal and 200% font size and actual landscape. Re-run hardware/gesture Back and the 12 device tests. Restore display settings.
6. Update verification documents with new artifact-specific results and remaining limits.

Full-app verification also still lacks real SAF grant/revocation/disconnection, USB removal during operations, process-death mutation recovery, TalkBack/Switch Access, all preview formats and authenticated real-server protocol tests. SFTP/SMB placeholders, credential-security gaps and durable operation recovery are tracked in VERIFICATION_REPORT; do not claim release readiness.

## Recovery caution

A prior Python edit using Windows default encoding truncated HiddenFilesScreen. It was recovered from a hash-verified baseline and our changes reapplied; subsequent Kotlin UTF-8 checks passed. Do not rerun `category_finish_edits.py` (one-time recovery) or partially applied `category_navigation_edits.py`. Use explicit UTF-8 or apply_patch for new edits. Baseline commit used for that recovery was `faa7cbadccb9dfd6953229fbae7b169a48cd17a3`; it is not permission to restore other files from HEAD.

## Update — 19 September 2026

The developer has no room for Android Studio or the SDK on their machine, so builds and tests run on GitHub Actions (`.github/workflows/ci.yml`). Treat CI as the build authority. Local Gradle is limited to tasks that need no SDK (`ktlintFormat`, `ktlintCheck`, `detekt`, `wrapper`).

- CI had failed on every push since the first one: `gradle/actions/setup-gradle` rejected `gradle/wrapper/gradle-wrapper.jar` (unknown SHA-256), so no CI run ever compiled code. The jar and `gradlew`/`gradlew.bat` were regenerated with the pinned Gradle 9.3.1 (jar SHA-256 `b3a875dd…` matches services.gradle.org), and `gradlew` is now mode 100755 in the git index.
- `ci.yml` now also runs on `workflow_dispatch`, gives CI a larger Gradle/Kotlin heap through `~/.gradle/gradle.properties`, uploads the debug, release and androidTest APKs as the `refract-apks` artifact, and has a manual-only emulator job for the instrumented tests.
- Category scan: `PhoneFileIndex` now lives in `AppContainer` (survives rotation), resolves each directory's canonical path once, keeps symlinked volume roots scannable, and expires its cache after two minutes. `CategoryScreen` filters and sorts off the main thread. `FileCollection` no longer allocates a set per file.
- Browse: selecting many files no longer re-filters the whole folder for every visible row; search/sort results can no longer arrive out of order; name sorting no longer allocates a lowercase string per comparison. Tab Back history keeps one entry per tab.
- Merged from a reviewed external copy: `SftpBackend`/`SmbBackend` no longer fabricate success. They were sockets-only stubs that returned `Success` for writes, deletes, directory creation and empty listings. They now extend `UnimplementedProtocolBackend` and fail with `ProviderUnavailable`, advertise no capabilities, and have regression tests. The Storage screen copy says SFTP and SMB are not supported yet. `.gitignore` covers crash dumps, `.kotlin/` and `build-logic/convention/bin/`, which are also untracked now.
- Not merged from that copy: explicit `lifecycle-*`/`core-ktx` dependencies (the app already built and passed its gates without them) and a permanent heap increase in the checked-in `gradle.properties` (this machine is memory-starved; CI sets its own). Its `BUILD_READINESS_NOTES.md` states that nothing has ever been compiled and that the dependencies were build blockers; both are wrong for this project (see the earlier baseline runs above).
- Still open: `data/di/BackendModule.kt` and `BackendKey.kt` are dead Hilt code (no plugin applied, no references) and can be deleted; credential storage uses a hard-coded key and constant IV with a plaintext fallback (`NetworkCredentialsStore`); `FileOperationService` is never started, so long copies run in `viewModelScope`; starting a second file operation cancels the running one (`BrowseViewModel.runOperation`); the bubble and hidden-file repositories read SQLite on the main thread in `init`.
- Nothing here has been compiled or run in CI yet. Push, then read the run with `gh run view --log-failed`.
