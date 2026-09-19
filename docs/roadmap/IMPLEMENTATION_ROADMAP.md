> Session checkpoint (15 September 2026): development is paused at the user's request. See [agent handoff](../SESSION_HANDOFF.md) for completed work, pending verification and resume order. No roadmap phase is newly certified by this documentation update.

> Verification status (13 September 2026): roadmap completion labels describe intent or implementation claims, not release certification. The [verification report](../testing/VERIFICATION_REPORT.md) records current evidence and prioritized remaining work.

# Implementation Roadmap

Thirteen phases, 0 through 12. Each phase has a goal, a deliverable list, and **acceptance
criteria that are verifiable by someone who did not write the code**. A phase is not complete
because the code exists; it is complete when the criteria pass.

**Build in this order.** The dependency chain is real: the storage abstraction cannot be
designed correctly without Phase 0's findings, the UI cannot be built without the design
system, and the glass system must come last because everything must work without it.

---

## Phase 0 — Research and verification

**Goal:** replace assumptions with measured facts before a line of production code.

**Deliverables**
* A throwaway probe app that, on each matrix device, enumerates volumes, attempts each access
  path, and dumps results to a file.
* Answers to Q1–Q8 in `../testing/DEVICE_MATRIX.md` §6, committed as tables.
* A confirmed decision on Haze vs. a hand-rolled backdrop capture, based on a measured
  frame-time comparison on low-end hardware.
* `ANDROID_STORAGE_RESEARCH.md` §10 emptied — every open question closed or explicitly deferred
  with a reason.

**Acceptance criteria**
1. Every Q1–Q8 row has a recorded result for at least three API levels.
2. The probe output for API 29 and API 30 is materially different, and the difference matches
   what `../architecture/PERMISSIONS.md` predicts. (If it does not, the docs are wrong and get
   corrected before Phase 1.)
3. A measured frame-time number exists for the AGSL refraction shader on low-end hardware.

**Do not skip this phase.** Every serious bug in an Android file manager traces back to a
platform assumption that was never verified.

---

## Phase 1 — Project skeleton

**Goal:** a buildable, lintable, testable app that does nothing.

**Deliverables:** Gradle setup with version catalog, single module, flavours (base / reporting),
Hilt, Compose, the seven custom Lint rules from `../CODING_RULES.md`, CI running lint + unit
tests, an empty Activity with the splash API.

**Acceptance criteria**
1. `./gradlew build` passes from a clean clone with no local configuration.
2. Each of the seven custom Lint rules fails the build when deliberately violated — proven by
   seven committed test cases.
3. CI runs on PR and completes in under 10 minutes.
4. Cold start to first frame is measured and recorded as the baseline.

---

## Phase 2 — Domain model

**Goal:** the vocabulary of the app, in pure Kotlin.

**Deliverables:** `FileNode`, `FileNodeId`, `AccessFlags`, `FileCategory`, `SortSpec`,
`FileError` hierarchy, `StorageBackend` interface, `OperationRequest`/`OperationSnapshot`,
`InMemoryBackend` plus the fault-injecting decorators.

**Acceptance criteria**
1. Zero Android imports in the domain package — enforced by `NoAndroidInDomain`, not by review.
2. `FileNodeId` round-trips for all three prefixes, with malformed input rejected, proven by test.
3. `InMemoryBackend` passes a shared `StorageBackendContractTest` that every real backend will
   later have to pass too.
4. Sort comparators pass the natural-ordering suite (`file2` before `file10`).

---

## Phase 3 — Storage backends

**Goal:** read the real filesystem, correctly, on every API level.

**Deliverables:** `FileSystemBackend`, `SafBackend`, `MediaStoreBackend`,
`StorageBackendSelector`, volume enumeration, exception → `FileError` mapping, streaming
`listChildren`.

**Acceptance criteria**
1. All three backends pass the same `StorageBackendContractTest`.
2. Instrumented listing succeeds on API 27, 29, 30, 33, 36 — with the *correct backend chosen
   automatically* on each.
3. `listFiles()` returning null produces `AccessDenied`, never an empty list. Explicitly tested.
4. Streaming emits the first chunk of a 10,000-item folder in under 100 ms.
5. Every mapped exception has a test; no silent `else -> Unknown`.

---

## Phase 4 — Permissions

**Goal:** the app is honest and functional at every access level, including none.

**Deliverables:** `StorageAccessManager`, `AccessLevel` resolution, the `AccessStep` list per
API level, SAF grant request and persistence, All Files Access flow, granular media
permissions, partial-grant handling, permission troubleshooting screen.

**Acceptance criteria**
1. On each matrix API level, the permission screen shows the correct steps with correct copy —
   verified by screenshot, per level.
2. Denying everything yields a usable app: app-private storage browses, volumes list with free
   space, and messaging is accurate rather than nagging.
3. A persisted SAF grant survives an app restart and a device reboot.
4. Revoking access externally is detected on resume with no crash.
5. `/Android/data` on API 30+ produces the honest explanation, not an empty folder.

---

## Phase 5 — Design system

**Goal:** tokens and primitives before screens, so no screen invents its own values.

**Deliverables:** colour schemes light/dark/dynamic, category accents, type scale, spacing and
shape scales, `Motion` spring catalogue, base components (rows, tiles, toolbars, sheets,
empty/error/loading states) built **without glass** — flat surfaces only.

**Acceptance criteria**
1. `NoHardcodedDp` passes across the whole UI package.
2. Screenshot baselines exist for every component in light and dark.
3. Every colour pair meets WCAG AA at the specified sizes, verified by a contrast test.
4. Every component renders correctly at 200% font scale.

---

## Phase 6 — Browse and navigation

**Goal:** the core of the app, flat and fast.

**Deliverables:** Home, Browse, folder browser, breadcrumb, sort, view modes, navigation graph,
selection mode, file information, bottom navigation, empty/error/no-access states.

**Acceptance criteria**
1. A 10,000-item folder opens in under 300 ms to first content and scrolls under 1% jank.
2. Empty, loading, error, and no-access are four visually distinct states — verified by
   screenshot and by a UI test asserting distinct semantics.
3. Scroll position and selection survive rotation and process death.
4. Full TalkBack pass with zero unlabelled interactive nodes.
5. Predictive back works on API 33+ and degrades correctly below it.

---

## Phase 7 — File operations

**Goal:** the part that can destroy data. Slowest phase; do not compress it.

**Deliverables:** foreground service, Room operation queue, copy/move/delete/rename/create,
byte verification, conflict resolution, trash, undo, progress notification, inline banner,
operations sheet, cancellation and pause.

**Acceptance criteria**
1. Every invariant in `../architecture/FILE_OPERATIONS.md` §2 has a passing named test.
2. The full soak suite in `../testing/TEST_STRATEGY.md` §7 passes on a physical device.
3. Killing the process mid-copy leaves **no partial target and an intact source** — verified
   manually as well as automatically.
4. A cross-volume move never deletes the source before verification succeeds. Proven with the
   `FailAfterNBytes` fault injector.
5. Operations survive backgrounding and screen-off on a Xiaomi or Oppo device.
6. Progress is byte-based and monotonic; ETA never appears before 3 seconds.

**Nothing in this phase ships on the strength of a code review alone.**

---

## Phase 8 — Search

**Deliverables:** three-source concurrent search, debounce, cancellation, filters, scope,
result cap, coverage warnings, recent queries, results UI.

**Acceptance criteria**
1. First result within 1 s on a 100k-file device.
2. Typing a new query cancels the previous search — verified by observing no stale emissions.
3. Restricted access shows a coverage warning naming exactly what is being searched.
4. 40,000 matches cap cleanly at 5,000 with a clear message and no memory growth.
5. Results appended while the user scrolls do not shift content under the finger.

---

## Phase 9 — Preview

**Deliverables:** preview host, image viewer, video, audio, text, PDF, APK info, unsupported
state, sharing via FileProvider, paging across siblings.

**Acceptance criteria**
1. A 108 MP image opens without OOM.
2. A 400 MB text file opens truncated with an honest message rather than crashing.
3. ExoPlayer is released on `onCleared` and paused on stop — verified by LeakCanary and by an
   instrumented assertion that no player survives navigation.
4. An unsupported codec produces the Open-with state, never a crash or a black screen.
5. Zoom, pan, and playback position survive rotation and process death.

---

## Phase 10 — Storage analysis

**Deliverables:** volume totals, category breakdown, scan engine with progress and
cancellation, largest folders and files, trash management, large-file cleanup.

**Acceptance criteria**
1. A full scan of a 128 GB device completes without ANR and with memory under budget.
2. Cancelling a scan preserves partial results, labelled as partial.
3. The breakdown sums to the used figure within rounding, and "Other" is expandable.
4. Restricted access produces an accurate coverage caveat rather than wrong totals.
5. No cleanup action deletes anything without per-item confirmation.

---

## Phase 11 — Glass system

**Goal:** the visual identity — added last, on top of a complete app.

**Deliverables:** capability detection, three tiers, `GlassSurface` and the glass primitives,
AGSL refraction shader, backdrop capture, tier override setting, reduce-transparency support.

**Acceptance criteria**
1. **Deleting the glass package leaves a complete, correct, good-looking app.** This is
   verified by actually building with a flat-surface substitution, not by assertion.
2. Tier A holds under 8 ms frame time on mid-tier hardware; if not, the effect is reduced until
   it does.
3. Tiers B and C look intentional, not degraded — screenshot review on real devices.
4. Reduce-transparency and reduce-motion system settings force tier C and disable transitions.
5. Text contrast over every glass surface meets AA in light and dark, over both a light and a
   dark backdrop.
6. Glass never exceeds 25% of the viewport, never stacks more than two layers, and never
   appears on list items. Verified by review against `../LIQUID_GLASS_RESEARCH.md` §6.

---

## Phase 12 — Release preparation

**Deliverables:** Baseline Profiles, R8 configuration and a shrunk-build smoke test, store
listing, Permissions Declaration Form for All Files Access, privacy policy, accessibility
audit, full device matrix pass, crash-reporting flavour verification.

**Acceptance criteria**
1. The full manual checklist in `../testing/DEVICE_MATRIX.md` §8 passes on all four required
   devices.
2. The release build (R8 on) behaves identically to debug on the key journeys — Room, Hilt,
   and serialisation survive shrinking.
3. All Files Access declaration is submitted with the file-management purpose prominent in the
   listing, and the app is verified to work fully without it.
4. Base flavour has no `INTERNET` permission in the merged manifest. Checked, not assumed.
5. No release-build log statement contains an unredacted file path or name.

---

## Sequencing rules

* **Never build glass before the app works.** If glass exists first, every later decision bends
  around it, and it stops being deletable.
* **Never build operations before backends are proven.** A copy engine over an unverified
  storage layer produces data loss, not bugs.
* **Never defer Phase 0.** The cost of discovering an API-29 truth in Phase 7 is a rewrite.
* Phases 8, 9, and 10 are mutually independent and can be parallelised if more than one person
  is working. Phases 0–7 are strictly sequential.
