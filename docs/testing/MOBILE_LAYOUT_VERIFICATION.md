# Mobile layout, navigation and volume access — 2026-09-13

This follow-up addresses the reported squeezed Storage Intelligence filters, network protocol selector, fixed transfer bubble position, SD-card routing, and system Back behavior.

## Source review

| Surface | Finding and change |
| --- | --- |
| Home | Category tiles no longer force square height; narrow screens and large fonts use two columns. Existing page scrolling and wrapping overview card retained. |
| Browse | Selection actions scroll instead of compressing; clipboard text yields space to actions. System Back closes search/selection/peek or navigates folder history. The primary browser model survives activity recreation and tab changes. |
| Storage | Volume figures wrap; the network section reserves space for its add button. Protocol choices wrap without splitting their names; host and port fields each get a full row. The form already scrolls when constrained by the keyboard. |
| Storage Intelligence | All four filters are available through horizontal scrolling with single-line labels. Scan/selection summaries wrap. Empty-result messages wait for completion. System Back returns to Storage. |
| Hidden Files | Scrollable tabs keep all three names readable. System Back returns to Browse. |
| File actions and hiding | Details and hide-dialog bodies scroll within available dialog height. |
| Preview | Audio content scrolls on short screens; Markdown header wraps; metadata values appear below labels. Text wrap control has a 48 dp touch target. Image/PDF/video use the available preview area. |
| Transfer bubbles | Rail position is draggable, constrained to the app viewport and saved as relative coordinates across resizing/reopening. Accessibility actions also reposition it. Transfer choices wrap; details content scrolls and its item list has a bounded height. |
| App navigation | Tab history and selected entry location are saved. Back returns through tab history after folder history; analysis has its own Back handler. Dialogs and sheets retain their framework dismissal handlers. |

## SD-card diagnosis

Volume enumeration previously returned `rootNodeId = null` for every device volume. Both navigation callbacks substituted internal storage when a root was null, including for an SD card. Enumeration now resolves the actual directory for each volume after permission checks. Pre-API-30 directories are matched through `StorageManager.getStorageVolume`, not array position. Unavailable volumes do not redirect to internal storage. Missing shared-storage permission opens Android's grant flow and resumes the selected volume after access is granted.

This uses direct shared-storage access for a file manager. SAF tree grant/revocation and provider-disconnection coverage remain separate work.

## Verification

Status updated 15 September 2026: work is paused at the user's request after documenting the session. See [agent handoff](../SESSION_HANDOFF.md) for source changes, environment and resume commands.

- `mobile-gates-2.log`: format, detekt, 238 app tests, debug and instrumentation APK assembly passed in 2m 51s (88 tasks).
- `mobile-device-tests.log`: **12 tests passed**, 10.023s, including three navigation tests plus the existing storage/hiding tests.
- Physical checks passed for opening the public SD root, hardware Back and edge-swipe folder navigation, free bubble dragging, readable network protocols and horizontally scrollable analysis filters.
- Physical checks found Downloads wrapping awkwardly and network-dialog keyboard overlap. Follow-up fixes are in source but not yet visually verified.
- New global categories, dedicated private-files routing and bubble edge/fling docking are implemented in source, with classification/index/docking tests added. They await a successful compile and test run; prior passes do not cover them.
- Latest build log `categories-pinned-compile.log` stops during build-logic setup without a completion result. Disk/RAM pressure interrupted progress. No successful latest APK installation is claimed.

Evidence PNG/XML pairs are in ignored `build/verification/`: `mobile-sd-root`, `mobile-back-sd-root`, `mobile-gesture-back`, `mobile-bubble-dragged`, `mobile-network-normal`, `mobile-network-keyboard`, `mobile-analysis-filters` and `mobile-home-normal`. Preserve these before cleaning build output.

Every-screen large-font/landscape/keyboard verification remains pending. This source audit does not certify every file format, storage provider or Android version. The Storage form's existing scrolling was insufficient for keyboard overlap; its newer IME-inset fix must be checked on-device.
