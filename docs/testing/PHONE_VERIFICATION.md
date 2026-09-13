# Phone verification — 13 September 2026

Device: model 23076RN4BI (`sky`), Bliss ROM, Android 14 / API 34, 1080 × 2460 display, with a removable SD card. The user reports that the phone is rooted. Tests run as app instrumentation; no `su` commands or root-only filesystem operations were used. This is evidence for this device/ROM, not a stock-Android or multi-device certification.

## Device tests

The original four tests passed through Gradle's connected-device runner. The expanded suite passed **nine tests, zero failures and zero skips**, through AndroidJUnitRunner, and passed again after the layout fix (4.613 seconds). Each test emitted a successful completion status. Evidence is in `build/verification/phone-device-tests.log`, `phone-nine-tests.log`, and `phone-final-nine-tests.log`.

| Scenario | Result | Scope |
|---|---|---|
| Activity recreation | PASS | Activity launches and recreates without finishing/crashing |
| Verified local copy | PASS | 256 KiB copied byte-for-byte; source retained; no staging leftovers |
| Bubble database reopen | PASS | Staged reference survives database reopen without moving the source |
| Storage backend routing | PASS | Real `file:` backend create/list journey |
| Gallery hide/restore | PASS | Bytes restored; hidden directory contains `.nomedia`; does not prove media-scanner exclusion |
| Fast Obscure/restore | PASS | Repository round trip restores bytes and clears metadata |
| Private-storage move/restore | PASS | Original removed only on successful move; restored bytes match |
| Missing hidden file | PASS | Failed restore retains recovery metadata |
| Internal storage ↔ SD-card move | PASS | 2 MiB moved in both directions through the real operation engine; byte-for-byte comparison and source deletion verified |

The SD-card test uses `getExternalFilesDirs` and a UUID-named directory in the app's own removable-storage area. It does not test a SAF grant or arbitrary public SD-card directories. File-hiding tests use app-cache fixtures and an isolated database. No pre-existing user files were modified or deleted.

## Interface checks and findings

- Home, Browse and Storage opened and rendered on the phone. Browse displayed the shared-storage listing; no existing files were opened or changed. Storage displayed internal and removable volumes.
- Actual landscape rotation was confirmed by the window manager (`ROTATION_90`, 2460 × 1080). The Home screen changed to a navigation rail and a wide content layout. Screenshot: `build/verification/phone-landscape.png`.
- At **200% font scale**, the Home storage card failed: the right-hand values were squeezed into individual-character columns. Evidence: `phone-large-text.png`. `StorageOverviewCard` now uses wrapping rows for its header and storage figures. The rebuilt APK was installed and visually verified at 200%: the figures are readable on separate rows, without the vertical-character overflow (`phone-large-text-fixed.png`). This certifies the observed card layout, not every screen's accessibility.
- Changing display configuration returned the app to Home. The activity recreation test does not assert preservation of the selected tab or browsing location; that remains a navigation-state gap.
- Development-build cold launches reported 2,778 ms, 1,778 ms and 3,058 ms. These are `am start -W` diagnostics, not a controlled startup benchmark or a measurement of the first file-list frame. The roadmap's performance threshold is not certified.
- The captured Refract-process log contained no `FATAL EXCEPTION`, ANR or out-of-memory signature. This is a bounded observation of that process's available logs, not proof that every untested workflow is crash-free.

Font scale and rotation settings were captured before testing and restored afterward. The app remains installed for further testing.

## Final regression gates

The layout fix passed the 238 app unit/Robolectric tests, ktlint, detekt, debug/release builds, device-test packaging and both Android Lint variants. The combined run completed successfully in 9m 9s (`phone-fix-gates.log`). Lint remains at zero errors, 41 debug warnings and 33 release warnings, with one hint per variant. The unchanged custom lint-rule suite had already passed its 22 tests.

All nine device tests then passed against the installed fixed build. Source and artifact hashes are recorded in `phone-source-files.csv` and `phone-apk-artifacts.json`; lint counts are in `phone-lint-summary.json`. The phone was left at its original font scale 1.0 and locked portrait rotation. No `verification-*` folders remained at the top level of the app cache.

The initial connected-device runner removed its temporary app installation on completion. No Refract installation existed before that run. Subsequent checks used explicit APK updates and direct instrumentation, leaving the updated app installed. Future destructive/recovery tests should continue to use disposable fixtures and an isolated test installation.

## Remaining scope

Real SAF permission grant/revocation and provider disconnect workflows are not verified; no `OpenDocumentTree`/persistable-grant acquisition flow was found in the current app UI. SD-card app-private access must not be presented as SAF verification. USB removal during an operation, forced process termination at mutation boundaries, TalkBack/Switch Access, all preview formats, drag-and-drop completion/failure, and authenticated real-server protocol tests remain outstanding. Network and credential-security implementation gaps from the main report remain.

The global verification context and prioritized implementation gaps are in [App verification](VERIFICATION_REPORT.md).
