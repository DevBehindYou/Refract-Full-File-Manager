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

Paths above are under `app/src/main/kotlin/com/devbehindyou/atomicfilemanager/`. New tests: `FileCollectionTest`, `PhoneFileIndexTest`, `BubbleDockTest`. These have not passed a completed run yet. Review scan behavior on actual Android volume paths, cached-result freshness and main-thread filtering/sorting performance before claiming all-phone coverage. Protected/unreadable folders cannot be promised.

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
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s 5ff095c7f80a shell am instrument -w -r com.devbehindyou.atomicfilemanager.test/androidx.test.runner.AndroidJUnitRunner
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

## Update — 20 September 2026 (Settings tab, restore-crash fix)

- New fourth tab, Settings (`ui/screens/SettingsScreen.kt`), backed by `SettingsRepository` (`SharedPreferencesSettingsRepository`, file `refract_settings`, exposed as `AppContainer.settingsRepository`). Settings: theme (system/light/dark), dynamic color (API 31+), show hidden files (default on), default hiding method (ask every time, Fast Obscure, Hide from Gallery, Private Storage), lock for hidden and private files, all-files access status, clear scan cache, version.
- Default hiding method: when set, `BrowseScreen` skips the choice dialog and hides straight away (`hideNode`), then shows a Toast. "Ask every time" keeps the old dialog. A custom hiding folder location is not implemented: a shared hidden folder would need per-volume handling because `renameTo` fails across volumes.
- Lock: `ui/security/BiometricAuth.kt` (`AuthGate`, `authenticate`, `canAuthenticate`) uses AndroidX BiometricPrompt with weak biometrics or the screen lock. It gates the Hidden Files screen in Browse and the Private files screen on Home. Turning the lock on or off needs a successful unlock; without any screen lock it cannot be turned on. `MainActivity` now extends `FragmentActivity` because BiometricPrompt requires it. New dependencies: `androidx.biometric:biometric:1.1.0`, `androidx.fragment:fragment-ktx:1.8.5`.
- Theme: `MainActivity` reads the settings and re-applies `enableEdgeToEdge` with a matching `SystemBarStyle` so status bar icons stay readable when the chosen theme differs from the system.
- Restore crash: hide and restore operations in `HiddenFilesRepositoryImpl` no longer throw (an uncaught `IOException` from `createNewFile()` in private-storage restore is the most likely cause); the UI shows failures in a snackbar or Toast. The real crash was not reproduced because no log was available, so confirm it on a device build.
- Passed locally: `ktlintCheck`, `detekt`. Not compiled or tested yet. First things to check in CI: `SharedPreferencesSettingsRepositoryTest`, the `MainActivitySmokeTest` on the `FragmentActivity` base class, and dependency resolution for the two new artifacts.
- Known issue seen on the phone: category lists include thumbnail cache files from dot folders such as `Movies/.thumbnails`. `PhoneFileIndex` should skip hidden directories.

## Update — 20 September 2026 (device findings, restore crash root cause, hidden folder)

- Installed CI run 35509823288 on the phone (`adb install -r`, no data loss). Settings tab, light theme (status bar icons stay readable) and the hide dialog with "Ask every time" work. Fast Obscure and Hide from Gallery restore fine.
- Restore crash root cause (reproduced with disposable fixtures in Download): restoring a Private Storage file threw `IllegalArgumentException: file: path must be absolute, was "file:/storage/emulated/0/Download"`. `HiddenFilesScreen` built the destination with `FileNodeId.file(item.originalLocation).raw.substringBeforeLast('/')`, which keeps the `file:` prefix, and then wrapped it in `FileNodeId.file` again. Nothing caught it, so the app crashed. Fixed with `HiddenItem.originalParent()`; the earlier crash-safety changes now show a snackbar instead of crashing if anything else fails. Existing tests passed the destination directly, which is why they never caught it; `HiddenFilesIntegrationTest` now restores through `originalParent()`.
- Hidden folder setting: `AppSettings.hiddenFolder`, default `Refract/Hidden`, relative to the root of the storage the file is on (`/storage/emulated/0/Refract/Hidden`, or `/storage/<uuid>/Refract/Hidden` on an SD card). It applies to Hide from Gallery only (a `.nomedia` marker is written there and name clashes get a numeric suffix). Fast Obscure still renames in place and Private Storage still uses the app's private folder. It is editable in Settings > File hiding > Change folder; input is validated by `normalizeHiddenFolder` (no `..`, no drive letters). `HiddenFilesRepositoryImpl` takes `hiddenFolderProvider` and `volumeRootResolver`; with the defaults in tests it keeps the old `.RefractHidden` next to the file.
- Browse UI oddities seen on the phone: the Hidden files screen is only reachable from the Sort menu; tapping Home > Downloads a second time can show an old Browse state (the view model is keyed by the start folder and keeps its last folder, once showing `/storage` with "Access denied"). Not fixed.
- Disposable device fixtures left behind: `/sdcard/Download/refract-fixture-fast.txt`, `refract-fixture-gallery.txt`, and one file still hidden in Private Storage (`refract-fixture-private.txt`). Delete after re-testing the fix.


## Update — 4 October 2026 (category scan, stale Downloads, dead Hilt code)

- Category scan: `PhoneFileIndex` skips hidden (dot-prefixed) files and folders, so `Movies/.thumbnails`, `.nomedia` and Fast Obscure output no longer show up in Images, Videos and the other collections. Covered by `PhoneFileIndexTest.skipsHiddenFilesAndEverythingInsideHiddenFolders`.
- Stale Browse state: every explicit open from Home, Storage or Storage Intelligence now goes through `openInBrowse` in `MainActivity`, which bumps a saved `browseOpenRequest` counter. `BrowseScreen` passes it to `BrowseViewModel.onOpenRequest`; a changed value clears folder history and reloads the start folder. Opening Home > Downloads a second time therefore lands in Download again instead of wherever the cached view model was left (once `/storage` with "Access denied"). Switching tabs with the bottom bar and rotation do not change the counter, so they keep the user's position. No view model test (none exist; the constructor needs the SQLite bubble repository). Check on the phone.
- Deleted `data/di/BackendModule.kt` and `BackendKey.kt` (unreferenced). The Hilt dependency stays because the backends still import `@ApplicationContext`.
- Checked locally: ktlint 1.0.1 CLI on the changed files. Gradle cannot run in the cloud session (dl.google.com is blocked by its network policy), so compile, detekt, Lint and tests are left to CI.
- Still open from earlier: Hidden files reachable only from the Sort menu; credential storage key/IV; `FileOperationService` unused; a second file operation cancels the first; SQLite reads on the main thread in repository `init`; disposable fixtures on the phone.

## Update — 4 October 2026 (all-in-one plan)

- Competitor research (Solid Explorer, MiXplorer, Files by Google, Total Commander, FX, Material Files, Cx, Amaze, Owlfiles, Samsung My Files) and a phased integration plan are in [`roadmap/ALL_IN_ONE_PLAN.md`](roadmap/ALL_IN_ONE_PLAN.md). Read its section 8 (integration contract) before adding any feature, and section 10 (scope decisions waiting on the owner) before Phase 3 or 4 work.
- The plan's first sprint (section 13) starts with Room plus moving repository `init` reads off the main thread, then the durable operation queue.

## Update — 4 October 2026 (Atomic design system)

- The owner's Atomic design system is now in the repo at [`design/ATOMIC_DESIGN_SYSTEM.md`](design/ATOMIC_DESIGN_SYSTEM.md) and is the visual source of truth. The UI reconstruction plan is [`roadmap/ATOMIC_UI_PLAN.md`](roadmap/ATOMIC_UI_PLAN.md) (track U in `ALL_IN_ONE_PLAN.md`). Plan only; no UI code has changed.
- Old design docs (`DESIGN_SYSTEM.md`, `COMPONENT_LIBRARY.md`, `UI_UX_GUIDELINES.md`, `ANIMATION_SYSTEM.md`, `LIQUID_GLASS_RESEARCH.md`) carry a "being replaced" banner. Do not build new UI from them.
- Key rule for anyone touching UI: file and folder names are never uppercased or set in Bebas Neue (it has no lowercase); names use Hanken Grotesk, as stored.
- Owner decisions waiting: `ATOMIC_UI_PLAN.md` §18 (dynamic colour, dark theme, name, icon, accent, tab label, mascot, date format).

## Update — 4 October 2026 (renamed to Atomic File Manager; owner design decisions)

- **Renamed** from Refract to **Atomic File Manager**: app label, `applicationId`, namespace and Kotlin package are now `com.devbehindyou.atomicfilemanager` (source folders moved), app class `AtomicApp`, theme `Theme.Atomic`, build plugin `atomic.android.application`, lint registry `AtomicIssueRegistry`, CI artifact `atomic-file-manager-apks`, databases/settings/notification channel `atomic_*`, default hidden folder `Atomic File Manager/Hidden`. Earlier entries in this file use the old names and paths.
- **Not renamed on purpose:** Fast Obscure footer `REFRACT_OBSCURE_V1` and extension `.refract_obscured` (files on users' storage carry them), the credential key-derivation string, the launcher art (`refract_launcher_art`, until the owner's new icon arrives) and the GitHub repository name.
- **Phone warning:** the new `applicationId` installs as a separate app next to the old Refract build, with empty settings and hidden-file records. Restore anything hidden with the old app (especially Private Storage, which is deleted with the old app) **in the old app** before uninstalling it. The disposable fixtures listed on 20 September are still on the phone.
- **Owner design decisions** are recorded in `roadmap/ATOMIC_UI_PLAN.md` §18: Signal Blue default accent with an optional accent-only WALLPAPER COLOURS setting (guard rails §4.5); light and dark themes; no mascot; ISO dates; Browse tab becomes FILES; new icon to come from the owner.
- Checked locally: ktlint CLI on all built Kotlin and Gradle files. Compile, detekt, Lint and tests run in CI.

## Update — 4 October 2026 (Atomic foundations, design canvas)

- CI run 12 (rename) passed: static analysis, unit tests and emulator tests.
- **Design:** all screens are mocked up on the owner's Claude design canvas "Atomic File Manager — UI/UX" (20 artboards: Home, Files, selection, category, search, file info, storage, cleanup, trash, operations, private & hidden, settings, sheets, states, first run, dark Home/Files, tablet dual pane, component kit). Screen work in U5 should follow it together with `ATOMIC_UI_PLAN.md` §7.
- **U2 part 1 (code):** `core/designsystem/` now holds the Atomic foundation: `AtomicColorRoles` (pure Kotlin, light + dark, unit-tested for contrast), `AccentResolver` (wallpaper-colour guard rails, unit-tested), `AtomicTypography` with bundled Bebas Neue / Hanken Grotesk / JetBrains Mono (`res/font`, licences in `assets/licenses`), spacing/shape/border/elevation/size/breakpoint/motion tokens, `Modifier.hardShadow`, and `AtomicTheme`, which maps everything onto Material 3. `ui/theme/` (old blue/teal theme and unused bouncy springs) is deleted. Wallpaper colours default to off; the Settings switch explains a fallback to Signal Blue.
- Material type roles all stay Hanken Grotesk/JetBrains Mono on purpose: existing screens put file names and Markdown headings in title/headline roles, and Bebas Neue has no lowercase.
- Expect the whole app to look different on the phone (ink/paper/Signal, new fonts, 4 dp corners) before any screen is restructured.
- Open in U2: `AtomicIcons`, lint baseline + re-enabling `NoHardcodedDp`, the new lint rules.
- **U2 part 2:** `core/designsystem/icons/AtomicIcons.kt` is the one icon set (outlined). New lint detector `AtomicDesignDetector` (NoHardcodedColor, NoFilledIcons, NoToast, NoAlertDialog, NoBouncySpring) plus `NoHardcodedDp` now report as **warnings** in the Lint report while screens migrate; they become errors at U8. Next: U3 atoms.
- **U3 atoms** are in `core/designsystem/atoms/` with a preview catalogue (`core/designsystem/preview/AtomicCatalog.kt`) and Robolectric tests (`AtomicAtomsComposeTest`). No screen uses them yet; U4 (shell + navigation) and U5 (screens) do. The lint-rules test module now prints full failure output in CI.
- **U4 part 1 (shell):** `MainActivity` now uses the Atomic header, bottom bar and rail (tab labelled FILES), an Atomic sheet for "More categories", an info sheet for About and snackbars instead of toasts. Test tags (`tab_*`, `bottom_nav_bar`, `nav_rail`, `about_button`) are unchanged. Overlay booleans (private files, category, storage analysis) still route screens; the saved route stack is U4 part 2.

## Update — 5 October 2026 (CI run 16 fixes, route stack)

- CI run 16 failed on two things, both fixed: Compose lint's `NonObservableLocale` error (`AtomicText` now reads the locale from `LocalConfiguration`), and the `NoFilledIcons` rule missing aliased imports (it now resolves the reference to `Icons.Filled`/`Default` instead of matching text).
- **U4 part 2:** private files, category and storage-analysis screens now go through a saved route stack (`core/navigation/`), replacing three overlay variables in `MainActivity`. Back pops the stack; tab history works as before. Navigation Compose was decided against; see `ATOMIC_UI_PLAN.md` §6.
- **U5 Settings:** rebuilt with Atomic components per the canvas: theme chips (System/Light/Dark), toggle cards (wallpaper colours, hidden files, lock), rows with sheets for the default hiding method and hidden folder (Material dialog removed), an About & licences sheet, and a danger zone for clearing the scan cache. Feedback goes through the app snackbar. "Verify copies" and "Reset settings" from the canvas are not shown yet: the first needs the operation queue, the second needs a confirm step that does not switch off the lock without unlocking. New molecule `AtomicChoiceCard`; Robolectric test `SettingsScreenComposeTest`.
- **U5 Home:** rebuilt per the canvas: permission warning with a Grant button, a storage card with meter per volume ("Details →" opens the Storage tab), a category grid (Images, Videos, Audio, Docs, Archives, APKs, Downloads, Private, More; 2/3/5 columns by width and font size) and the shared folders as file rows. `ui/components/CategoryGrid.kt` and `StorageOverviewCard.kt` are deleted. Test tags `home_screen`, `permission_warning_card`, `volume_item_<id>` and `app_private_storage_item` are kept; tiles are tagged `category_*`. Search, favourites, recents and per-category counts arrive with Phase 1. Robolectric test `HomeScreenComposeTest`.
- **U5 Files, part 1:** `BrowseScreen` now uses an Atomic pushed header (volume eyebrow, folder name as stored in `NameLarge` with an item counter), a mono breadcrumb that keeps its drop targets, a sort sheet (replaces the dropdown; "Hidden files" lives there), a "3 selected · All · Cancel" header with an action strip (Copy, Move, Zip, Delete, Info), an Atomic clipboard bar, Atomic loading/error/empty states, a progress bar for running operations, and confirm sheets for delete. File rows (`FileListItem`) use the Atomic row with an actions sheet instead of a dropdown, keep their TalkBack custom actions and tags. Dates everywhere are ISO (`2026-10-04 08:11`). Toasts in Files go to the app snackbar. Still Material: new folder, rename, details, hide, preview, bubble and drop dialogs (part 2).
- **U5 Files, part 2:** new folder, rename, file info and hide are Atomic sheets (same function names and test tags). Name checks moved into `fileNameError` (empty, "/", ".", "..", over 255 bytes; each message says how to fix it) and file info into `fileFacts` (exact bytes, mono path, access), both unit-tested. Rename is disabled until the name changes. Still Material: preview dialog/pane, bubble transfer and details, drop decision (part 3).
- **U5 Category:** pushed header with refresh, title row with a live counter ("1,204 files" / "N found so far"), Atomic search field, scanning and unreadable-folder notes, empty states for no access / no files / no match, and Atomic file rows (type icon, size · folder). Scanning, filtering and sorting logic is unchanged. Drop decision, bubble transfer and bubble details are Atomic sheets too.
- **U5 Storage:** featured "free on phone" tile, an Atomic storage card per volume (Browse / Grant access / Unavailable, "Nearly full · X left" at 80 %), an Analysis block (`open_storage_intelligence_button` kept), app cache size, network servers as rows with a named remove button and a confirm sheet, and the add-server form as a sheet (protocol chips, port check 1–65535, password field masked and never written to saved state). `AtomicTextField` gained `visualTransformation`; `AtomicIcons` gained `Network`. The canvas's Tools grid (Trash, Operations, Batch rename, Wi-Fi share) waits for those features in Phase 1–2.
- **U5 Storage analysis:** pushed header with rescan, "you could free" tile, partial-scan warning, category chips with counts, selectable Atomic rows (row toggles; a folder button opens the file in Files), "Keep one of each" for duplicates, a selection bar with count and size, and a confirm sheet naming count and bytes. **Bug fixed:** cleanup delete wrapped already-prefixed ids in `FileNodeId.file(...)`, which throws on `file:` ids, so deleting from the analysis screen crashed; it now passes the scanned nodes' ids. Selection rules live in `StorageCleanupSelection` with unit tests. Also fixed garbled "â€¢" separators that were in this screen. detekt CLI 1.23.8 now runs locally (scratchpad), so detekt failures can be caught before pushing.
- **U5 Private & hidden:** pushed header, method chips (Obscured / Gallery / Private) with a one-line explanation of each, a "N files · size" label with Restore all, rows with Restore and a named delete button, a "Not encryption" note and an Atomic snackbar for results (success is now reported too). **Behaviour change:** deleting a hidden file now asks first (it used to delete permanently on one tap). Row taps no longer act; restore is the explicit button.
- **U5 Files, part 3 (preview):** `FilePreviewPane`/`FilePreviewDialog` use an Atomic header (name as stored, mono size and ISO date, close/share/open-with), a shared Atomic loading/error state whose messages say what probably went wrong, Atomic PDF paging, a mono text viewer with a Wrap chip, archive entries as Atomic rows, and a fact sheet with a "Calculate checksums" button for other types (a checksum failure is now shown instead of ignored). Viewer logic is unchanged. Audio, video and Markdown viewers and Quick Peek still carry Material pieces.
- **Overlays and lock:** drag badges, Quick Peek and the transfer bubble rail use the Atomic card language (hard shadow, ink border, Atomic text/badge; bubbles fill with the accent when targeted and announce name and count). The AuthGate lock screen is Atomic. Every filled Material icon in `ui/` is gone; `AtomicIcons` gained Forward, Rewind10, Forward10, Code, Sort, Network. CI run 19 (through storage analysis) was fully green; run 20 covers the rest.

## Update — 5 October 2026 (Phase 0 first sprint, step 1)

- CI runs 19 and 20 were fully green (static analysis, unit tests and APKs, emulator) after the U5 screens.
- **H1:** `MainActivity` enumerates volumes on `Dispatchers.IO` (`loadVolumes`); a newer refresh cancels an older one.
- **H2:** `HiddenFilesRepositoryImpl` and `TransferBubbleRepositoryImpl` take an optional `loadScope`; the app container passes a process-wide scope, so their first SQLite read no longer runs on the main thread at start-up. Refreshes are serialized, so a slow first load can't overwrite newer data. Tests that construct repositories without a scope keep the synchronous load. New unit test for the deferred load.
- **StrictMode** in debuggable builds logs main-thread disk/network access and leaked closeables (log only, no crash).
- Room with KSP is still to come (needs a KSP version matching Kotlin 2.2.10, which can only be verified in CI).
- **Room (step 1, continued):** KSP 2.3.12 (independent of the Kotlin version; confirmed on Maven Central and the plugin portal) and Room 2.7.2 (best effort: Google Maven is unreachable from the build machine, so CI is the proof). `AtomicDatabase` (`atomic.db`, version 1, schema exported to `app/schemas`) starts with the **operation journal** (`OperationJournalEntity`/`OperationJournalDao`): every `FileOperation` field plus state and progress; on first open, QUEUED/RUNNING rows from a dead process become INTERRUPTED. Exposed as `AppContainer.operationJournal`; nothing writes to it yet; the operation queue (0.1) is next. Tests: `OperationJournalTest` (round trip incl. odd paths and SAF ids, progress, ordering, interrupted marking, pruning). Commit the generated `app/schemas/.../1.json` once a build produces it.
- **Operation queue (0.1, part 1):** `data/operations/OperationQueue` (app-wide, in `AppContainer.operationQueue`) runs file operations one at a time in request order and writes each step to the operation journal through one ordered writer. `BrowseViewModel` enqueues instead of cancelling the previous operation (**fixes "a second operation cancels the first"**); operations now outlive the Files screen. `runAndAwait` lets a caller wait for the result.
- **Bugs fixed:** (1) Storage analysis cleanup never deleted anything: it called `FileOperationsEngine.execute(op)`, which only builds a cold `Flow` that nobody collected. It now runs through the queue and waits before rescanning. (2) Transfer from a bubble emptied the bubble immediately, even when the move/copy then failed; it now empties only after a fully successful transfer.
- Still to do for 0.1: foreground service with a progress notification, Resume for INTERRUPTED journal rows, an Operations screen, and the conflict (ASK) decision flow through the queue.
- **Operation notification (0.1, part 2):** `FileOperationService` (already declared, never started before) is started by the container whenever the queue goes from idle to busy. It calls `startForeground` first on every start, then mirrors the queue: "Copying · 2 more waiting", "3 of 12 · name", progress bar; it stops when nothing is running or waiting. Its Cancel button cancels through the queue (the old static callback is gone). If Android refuses a background start (12+), the operation still runs without the notification.
- **Operations screen (0.1, part 3):** new route `AtomicRoute.Operations`, opened from Storage → Tools → Operations ("1 running" while busy). Shows the running operation (Atomic operation card with progress and Cancel), waiting operations (Remove), and up to 50 journal entries with **Resume** for interrupted/paused work and **Retry** for failed/partial work; both re-run the original request through the queue. Note: a resumed copy starts the request again from the beginning with its original collision policy; true mid-file resume needs per-item checkpoints (later). Wording in `OperationText` with unit tests.


## Update — 5 October 2026 (conflict decisions through the queue; Phase 0.1 part 4)

- **Found:** every paste, drop and bubble transfer asked for `CollisionPolicy.ASK`, but nothing ever asked. The engine treated ASK as "keep both" without telling anyone, so pasting over an existing file always created `name (1).ext`.
- **Engine:** `FileOperationsEngine.execute(operation, conflictResolver)`. With ASK, a real name clash emits `OperationStatus.AwaitingInput(Conflict(source, existing))` and suspends until the `ConflictResolver` answers (`ConflictChoice` REPLACE / KEEP_BOTH / SKIP, optional "apply to all" for the rest of that operation). Replace goes through the existing staged, verified copy, so the old file is only swapped out after the new one checks out. Explicit policies are never turned into questions. Without a resolver ASK still keeps both (old behaviour, used by tests and any caller outside the queue).
- **Folders:** Replace is not offered and is turned into keep-both if it arrives anyway (also when remembered by "apply to all"). Replacing a folder would need a merge with per-child conflicts and safe rollback; that is not built. Copying a file into its own folder duplicates it without asking.
- **Queue:** `OperationQueue` takes `engine: (FileOperation, ConflictResolver) -> Flow` and exposes `pendingConflict: StateFlow<PendingConflict?>` and `resolveConflict(pending, decision)`. A stale answer (for a conflict that is no longer current) is ignored. Cancelling a waiting operation clears the question. There is no timeout. While one operation waits, the queue waits behind it (the queue is serial; FILE_OPERATIONS.md §5's "other operations continue" needs the parallel queue, not built yet). The journal records the wait as PAUSED "Waiting for a decision".
- **UI:** `ui/components/ConflictSheet.kt` is an Atomic sheet hosted at the app root in `MainActivity`, so it appears over any screen. It shows the incoming file and the one already there (name, size, ISO date), a "Do this for every other name clash" checkbox, Replace (destructive, files only), Keep both, Skip and Cancel. Dismissing it decides nothing. Operations shows "… · waiting for your decision" with a **Decide** button that reopens it. The notification switches to "Action needed" with Cancel. Wording lives in `ConflictText` (unit-tested). Test tags: `conflict_sheet`, `conflict_replace`, `conflict_keep_both`, `conflict_skip`, `conflict_apply_all`, `conflict_cancel_operation`, `operation_waiting_for_decision`.
- **Side effect:** `MainActivity` now reads `operationQueue` at start, so the Room database builder and the interrupted-row sweep run on every launch rather than when Files first opens (both off the main thread; the builder does no disk work until the first query).
- **Checked locally:** the domain, operation and queue unit tests (141, including 10 new `ConflictResolutionTest`, 3 new queue tests) compiled with Kotlin 2.2.10 and passed in a scratch JVM project (Maven Central only; Room annotations stubbed). ktlint 1.0.1 and detekt 1.23.7 CLIs passed on every changed file. **Not checked locally:** the Compose files (`ConflictSheet`, `OperationsScreen`, `MainActivity`), `FileOperationService` and `ConflictTextTest` need the Android SDK, so CI is the proof. Not checked on the phone.
- **Phone check to do:** copy a file onto an existing one: the sheet appears; try Replace (contents change), Keep both (`name (1)`), Skip, Cancel and "apply to all" with 3 clashes; dismiss the sheet and reopen it from Storage → Tools → Operations → Decide; background the app during a clash and check the "Action needed" notification; a folder clash offers only Keep both / Skip.
- **Still open for 0.1:** resume prompt at start-up (today Resume is only in the Operations screen), `POST_NOTIFICATIONS` request before the first background operation (API 33+), a progress chip in Files that opens Operations, notification tap opening Operations directly, the process-kill instrumented test, per-item checkpoints for true mid-copy resume, buffer tuning (H8). Commit `app/schemas/.../1.json` once a CI build produces it (still not in the repo).

## Update — 5 October 2026 (start-up recovery, notification permission, Files banner; Phase 0.1 part 5)

- **Resume or discard at start-up:** `data/operations/OperationRecovery` runs once per process as the queue's new `startup` step. It reads QUEUED/RUNNING/PAUSED journal rows, marks them INTERRUPTED (the `markInterrupted` query now includes PAUSED, which only means "waiting for a name-clash answer" and dies with the process; `OperationJournalTest` updated), tidies their destination folders and publishes them as `pending`. `ui/components/RecoverySheet` (app root) offers **Resume** (re-enqueues each from the start; files already copied trigger the conflict sheet), **Discard** (marks CANCELLED "Discarded after restart") or **Decide later** (they stay in Operations with Resume).
- **Ordering fix:** the queue's runner and its journal writer both wait for `startup` to finish. Before, the sweep ran in parallel with the first enqueue and could mark a brand-new operation interrupted. The start-up sweep moved out of the `database` lazy block in `AtomicApp`.
- **Crash-safe Replace:** `VerifiedFileTransfer` used to set the old file aside as `.atomic-<uuid>.backup`. A crash at that moment left the original under a name nobody could map back. Backups are now `.atomic-orig-<id8>-<original name>` (anonymous `.atomic-<id8>.backup` only if that would exceed 255 bytes). `domain/usecase/InterruptedTransferCleanup` deletes staging files (`.atomic-<uuid>.partial`), renames a backup back when its original name is free, and **never deletes** a backup whose name is taken (reported instead). Only the destination folder itself is checked, not subfolders; old-format backups are left alone.
- **Notification permission (API 33+):** requested when the first operation starts, at most once per app session; the operation runs either way.
- **Notification tap opens Operations:** `MainActivity` is `singleTop` (manifest) and reads `EXTRA_OPEN = operations` in `onCreate` (fresh start only) and `onNewIntent`, then pushes `AtomicRoute.Operations`.
- **Files banner:** the progress line moved out of the per-pane list (dual pane showed it twice), is tappable ("Open operations", tag `operation_banner`), and also shows "waiting for your decision".
- **Tests:** `InterruptedTransferCleanupTest` (5), `OperationRecoveryTest` (4), queue start-up ordering (1), `RecoveryTextTest` (2). The domain + queue suites (151) pass in the scratch JVM project; ktlint and detekt pass on changed files. Test fake `FakeOperationJournal` is shared and follows the Room query rules. Note for tests: `advanceUntilIdle()` skips `backgroundScope` work; use `runCurrent()` there.
- **Phone check to do:** start a large copy, force-stop the app (`adb shell am force-stop com.devbehindyou.atomicfilemanager`), reopen: the sheet lists it and the destination has no `.atomic-*.partial`; try Resume, Discard and Decide later. Fresh install on Android 13+: the first copy asks for notification permission. Tap the progress notification: Operations opens. Tap the Files banner: Operations opens.
- **Still open for 0.1:** process-kill instrumented test, per-item checkpoints for true mid-copy resume, cleanup of staging files in subfolders, buffer tuning (H8), Room schema JSON in the repo.

## Update — 5 October 2026 (Trash; Phase 1.1)

- **Room schema history:** `app/schemas/.../1.json` is committed (taken from CI run 37350467447's log; artifact downloads are blocked from the cloud session, so CI now also prints the schema with `Print Room schemas`). The database is now **version 2**; commit `2.json` from the next CI log the same way.
- **Design:** `domain/usecase/TrashManager`. Each volume gets `<volume root>/.Atomic File Manager/Trash/` with a `.nomedia`. Trashing is a same-volume rename into a holder folder named by the entry id, keeping the item's own name inside (`Trash/<id>/notes.txt`), so equal names never clash and names survive a lost database. Only storage with rename + atomic move and a known volume root can trash (local `file:` ids under `/storage/...`); SAF, MediaStore-only, network and app storage get permanent delete with a reason.
- **Table `trash_entry`** (`TrashEntryEntity`, `TrashEntryDao`, `RoomTrashStore`): original parent, trashed id, holder id, volume root, size, deleted time and the operation id (for Undo). `MIGRATION_1_2` creates it; `AtomicDatabaseMigrationTest` builds a real v1 database from the 1.json statements and lets Room validate v2 after migrating.
- **Pipeline:** new `OperationType.TRASH` and `RESTORE_FROM_TRASH` run through the queue and engine (`FileOperationsEngine(trashManager = …)`; `trashManager` is now the first constructor parameter so `FileOperationsEngine { backend }` still works). If writing the record fails, the item is moved straight back.
- **Restore:** back to the original folder, recreating it below the volume root if it was deleted; a name taken meanwhile gets "name (1)", never overwritten.
- **Files:** `ui/components/DeleteSheet` replaces both delete confirmations: "Move to Trash … You can restore it for 30 days" with "Delete forever instead", or "Delete forever" with the reason (Trash off / storage has no Trash). After a move, the app snackbar says "Moved N items to Trash." with **Undo** for 10 s (restores exactly that operation's items).
- **Trash screen** (`AtomicRoute.Trash`, Storage → Tools → Trash with count and size): rows show "Deleted <ISO date> · restores to /Download · size · N days left" with Restore; tapping a row offers Restore or Delete forever; Restore all; danger zone **Empty Trash** with a confirm sheet.
- **Settings → Trash:** "Use Trash" (default on) and keep for 7 / 30 / 60 days (default 30). Items past the period are deleted once per process at start-up (no WorkManager yet).
- **Excluded:** category scans skip the dot folder; storage analysis now skips `.Atomic File Manager` entirely. Storage analysis cleanup still deletes permanently on purpose (its job is to free space) and says so.
- **Tests:** `TrashManagerTest` (8, incl. engine round trip), migration test, `DeleteTextTest`, `TrashTextTest`. Domain suites (159) pass in the scratch JVM project; ktlint/detekt clean on changed files.
- **Phone check to do:** delete a file in Download → snackbar Undo puts it back; delete again, force-stop, reopen, Storage → Trash → Restore: byte-identical in Download (the plan's acceptance test). Delete a folder whose parent you then delete → Restore recreates the parent. SD card: trash folder appears at the card root, not internal storage. Turn Trash off: delete sheet says permanent. Gallery must not show trashed photos.
- **Not done:** MediaStore trash (`createTrashRequest`) for media on API 30+, a 2 GB size cap, WorkManager daily purge, Undo for move/rename (FR-4.14 partly), trash for SAF trees.

## Update — 5 October 2026 (favourites and recents; Phase 1.2)

- `2.json` committed (from CI run 37379062610's log). The database is now **version 3** (`MIGRATION_2_3`); take `3.json` from the next CI log. `AtomicDatabaseMigrationTest` now runs 1 → 2 → 3 and exercises every table.
- **Tables:** `favourite` (id, name, isDirectory, addedAt) and `recent_item` (id, name, mimeType, size, openedAt; trimmed to the newest 100). Repositories `RoomFavouritesRepository` / `RoomRecentsRepository` behind `FavouritesRepository` / `RecentsRepository` in `domain/repository/ShortcutRepositories.kt`, exposed on `AppContainer`.
- **Favourites:** "Add to favourites" / "Remove from favourites" in every file and folder actions sheet in Files (`FavouriteToggle` on `FileListItem`, folded into one parameter to stay under detekt's limit). Home > Favourites lists them newest first; a folder opens in Files, a file opens the preview, and a target that is gone stays listed as "Missing · tap to remove" with a sheet to remove it (FR-6.5).
- **Recents:** every preview (`FilePreviewPane`) records the file as opened. Home > Recent merges those with photos, videos and audio changed in the last 7 days from MediaStore (`data/recents/RecentMediaSource`: small projection, stops after 6 rows, skips dot folders such as the Trash, returns nothing without media access), newest first, each file once (`mergeRecents`, unit-tested). Opened files that no longer exist are hidden. Long-press removes a recent. Both sections are hidden when empty (screens/HOME.md).
- **Tests:** `ShortcutsTest` (merge rule), `ShortcutTextTest`, migration test additions. Scratch JVM suite 161 passing; ktlint/detekt clean.
- **Phone check to do:** star a folder and a file in Files → both on Home; delete the starred file → "Missing"; remove it. Open a few previews → Home > Recent; take a photo → it appears as "Added …". Restart: both survive.
- **Not done:** a Favourites drawer in Files, "Locate" for a missing favourite, selection mode in the Recent list.
