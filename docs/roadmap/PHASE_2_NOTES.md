# Phase 2 — Implementation Notes

Same caveat as Phase 1 (`PHASE_1_NOTES.md` §0), unchanged: this sandbox still has no
network access to Maven/Google's repos, so this code has still never been compiled.
Phase 2 is pure Kotlin with no new external dependencies beyond what Phase 1 already
pinned (plus `kotlinx-coroutines-test` and `junit-jupiter-params`, added this phase —
see `gradle/libs.versions.toml`), so the compilation risk here is lower than Phase 1's
(no new unverifiable Gradle/AGP/Lint-API surface), but it's still unverified. If it
fails to compile, it'll be an ordinary Kotlin type error, not a tooling mystery.

## 1. Phase 0 status, revisited

Last phase flagged Phase 0 (the storage research/verification phase) as not done, with
a general "don't skip it" warning from the roadmap. Before starting Phase 2, I checked
what's actually still open in `ANDROID_STORAGE_RESEARCH.md` §10: four items, all
narrow and device-variable (USB OTG OEM exposure, `ACTION_OPEN_DOCUMENT_TREE` OEM
quirks, work-profile permission checks, AGSL shader timing). None of them affect the
shape of a pure-Kotlin domain model. Phase 3 (real backends) and Phase 11 (glass) still
need them — Phase 2 didn't.

## 2. The four flagged decisions, as implemented

1. **`OperationSnapshot` vs `FileOperation`/`OperationStatus`.** Implemented
   `FileOperation` + `OperationStatus` exactly per `FILE_OPERATIONS.md` §1 (the
   detailed, authoritative source), and added `OperationSnapshot` as
   `data class OperationSnapshot(val operation: FileOperation, val status: OperationStatus)`
   — matching the one place `OperationSnapshot` is actually used
   (`screens/OPERATIONS.md`: "all three render the same `OperationSnapshot` stream").
   See `domain/model/Operation.kt`.

2. **`@Immutable` dropped from `FileNode`.** `STORAGE_ARCHITECTURE.md`'s example
   annotates it with `androidx.compose.runtime.Immutable`, which would fail this
   phase's own AC1 the moment `NoAndroidInDomain` runs against real code. Omitted, with
   a comment on the type explaining why. If a UI-layer stability hint turns out to
   matter later, it belongs on a `core.ui`-side projection type, not here.

3. **`AccessTarget`, `NameProblem`, `FailedItem`, `UndoToken`, `Conflict`** — none of
   these are concretely defined anywhere in `/docs`; they're referenced by
   `FileError`/`OperationSummary`/`OperationStatus` but never spelled out. Gave each a
   minimal, honest definition (see `domain/model/FileError.kt` and
   `domain/model/Operation.kt`), marked `Provisional` in their KDoc. `AccessTarget`
   should get real shape from Phase 4 (`PERMISSIONS.md`'s `requiredStepsFor`);
   `NameProblem`/`FailedItem`/`UndoToken`/`Conflict` from Phase 7's real operation
   implementation. Don't treat Phase 2's version of these five types as settled.

4. **`FileNodeIdTest` written as a plain JUnit 5 test, not Robolectric.**
   `TEST_STRATEGY.md` §4 files it under data-layer/Robolectric tests, but the actual
   logic (string parsing/validation) needs zero Android APIs, and Phase 2's own AC2
   wants it as this phase's proof. Went with the plain-JVM version; if that
   categorization in `TEST_STRATEGY.md` was deliberate for a reason not visible from
   the doc text, worth a second look.

## 3. Smaller decisions made without a check-in

- **`getOrReturn()`'s call shape.** `FILE_OPERATIONS.md` §4's `copyFile` example calls
  it bare (`.getOrReturn()`, no lambda) to early-return `Failure` from the enclosing
  function. That's not reproducible in real Kotlin — non-local `return` needs to
  happen inside a lambda passed to an `inline` function. Confirmed this is illustrative
  pseudocode (that same example also references an unconstructed `clock` field and
  calls `DiskFull(required = ...)` without the `available` argument `ERROR_MODEL.md`
  requires), and implemented the idiomatic equivalent instead:
  `inline fun <T> FileResult<T>.getOrReturn(onFailure: (FileError) -> Nothing): T`,
  used as `.getOrReturn { return Failure(it) }`. See `domain/model/FileResult.kt`.

- **`InMemoryBackend` and the four fault-injecting decorators live under `src/test/`**,
  not `src/main/` — they're test infrastructure (`TEST_STRATEGY.md` §3 calls them
  "fakes, not mocks, for StorageBackend"), and nothing in the product needs an
  in-memory backend at runtime. `StorageBackendContractTest` lives there too, as an
  abstract class Phase 3's real backend tests are expected to subclass directly (same
  module, no `testFixtures` plumbing needed yet — still one `:app` module per Phase 1).

- **`InMemoryBackend`'s ids are opaque (`file:/mem/<counter>`), not path-derived.** A
  real `FileSystemBackend`'s ids will encode a path (per `STORAGE_ARCHITECTURE.md`'s
  own `file:` example), but doing that in the fake means a directory rename has to
  cascade a path change to every descendant's id — real complexity not needed to be
  behaviourally correct against `StorageBackendContractTest`. Documented on the class
  itself, not just here.

- **`StorageBackend` placed under `domain/repository/`**, not a new `domain/backend/`
  package — `DOMAIN_LAYER.md`'s package tree names exactly three domain subpackages
  (`model/`, `repository/`, `usecase/`); respected that as closed rather than adding a
  fourth.

- **`NaturalOrderComparator` doesn't do locale-aware collation** (e.g. via
  `java.text.Collator`) — only digit-run-aware, case-insensitive comparison.
  `TEST_STRATEGY.md` §3 mentions "locale collation" as part of the broader
  `SortComparatorTest` description; this phase's actual testable AC (file2 before
  file10) doesn't require it. Flagged as a known gap in the comparator's own KDoc
  rather than silently claimed as done. **Closed in `HARDENING_PASS_1_NOTES.md` §2** —
  `NaturalOrderComparator` now uses `java.text.Collator`; if you're looking at this
  file for the current API shape, check the hardening notes first.

- **`FileError`'s per-variant `severity`/`recoverable`/`action`.** `ERROR_MODEL.md`
  defines the three properties and gives a message-mapping table (title/body/action
  per error) but doesn't assign `severity` per variant explicitly, and a few
  table-listed "actions" (Extract safely, Fix name, Got it) don't map cleanly onto the
  9-case closed `RecoveryAction` set. Mapped each of the 22 variants by hand, inferring
  `severity` from the table's implied UI treatment, reusing the closest existing
  `RecoveryAction` where one fits, and leaving `action = null` (with a comment)
  wherever the table implies bespoke UI (a conflict dialog, an archive-specific dialog)
  that doesn't exist yet rather than force-fitting a generic action. This is this
  file's own judgment call in ~15 of the 22 cases, not a re-statement of settled spec —
  worth a real review once the corresponding screens exist.

## 4. What's still missing from a "complete" domain layer

Deliberately not built this phase — later phases' territory per the roadmap:
`FileRepository`/other higher-level repository interfaces (`DOMAIN_LAYER.md`'s
`repository/` list beyond `StorageBackend` itself), every use case, `SearchQuery`,
`UserPreferences`, `StorageBreakdown`, `StorageBackendSelector`, and the three real
backend implementations. Building any of these now would be jumping ahead of the
roadmap's phase order.
