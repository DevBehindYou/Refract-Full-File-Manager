# Hardening Pass 1 — Notes

Done after Phase 2, addressing four gaps self-flagged in `PHASE_1_NOTES.md` and
`PHASE_2_NOTES.md`, at the user's explicit go-ahead. Not new roadmap phase work — no
`FileRepository`, use cases, or real backends were added here, deliberately.

Same standing caveat as both phase notes files: nothing in this project has been
compiled in the sandbox that built it. This pass adds more unverified surface area
than it removes, particularly the `:benchmark` module (see §3) — if anything, treat
that module as higher-risk than Phase 1/2's code, not lower, precisely because it's
newer and touches less-certain tooling (a `com.android.test` module, `androidx.benchmark`
plugin, macrobenchmark APIs).

## 1. Fault-injecting decorator tests

`FaultInjectingBackendsTest.kt` — four test classes (`FailAfterNBytesTest`,
`DenyWriteTest`, `SlowBackendTest`, `VanishingSourceTest`), each testing the decorator
against `InMemoryBackend` as the wrapped delegate, the same way Phase 3/7 will use them.
`SlowBackendTest` uses `kotlinx-coroutines-test`'s virtual time (`currentTime` inside
`runTest`) to verify the delay deterministically rather than a real wall-clock wait, and
includes a test documenting that `listChildren` specifically can't be delayed by this
decorator (it's not a `suspend` function — only the four `suspend` methods can have
`delay()` inserted before they run).

This was the one item from the original hardening list I'd actually call a bug-risk
rather than a polish item — worth reading `FaultInjectingBackendsTest.kt` directly if
anything in this codebase deserves a second pair of eyes.

## 2. Locale-aware natural sort

`NaturalOrderComparator` changed from a singleton `object` to a `class` taking an
optional `Locale` (defaulting to `Locale.getDefault()`), using `java.text.Collator` at
`SECONDARY` strength (case-insensitive, accent-sensitive) for non-digit segments instead
of plain `Char` comparison. Digit-run numeric comparison is unchanged in spirit but
reimplemented on top of a proper tokenizer (alternating digit/non-digit segments) rather
than an inline two-pointer scan, which also naturally handles leading zeros correctly
(`"file007"` and `"file7"` now compare equal, as they should numerically).

**This is a breaking change to Phase 2's public API** — anything that referenced
`NaturalOrderComparator` as a bare object (there was exactly one call site outside its
own file: `SortSpec.comparator()`) now needs `NaturalOrderComparator()` or
`NaturalOrderComparator.ROOT`. `SortSpec.comparator()` was updated to construct one
fresh per call, deliberately not cached — see the class's own KDoc for why (a cached
Collator would freeze whatever locale was active when the singleton was first touched,
never reflecting a later runtime locale change).

New tests in `FileNodeSortTest.kt`: leading-zero equality, a longer-but-numerically-smaller
digit run test, an accented-character adjacency test (can't run in this sandbox to
directly confirm the exact `Collator` ordering — flagged with moderate-not-full
confidence in the test's own comment), and `SortField.TYPE` coverage (grouping by mime
type, natural-order fallback within a group, `null` mime type sorting first) that Phase
2 shipped without.

## 3. `:benchmark` module scaffold

New `:benchmark` module (`com.android.test`), added to root `settings.gradle.kts`, with
one test: `StartupBenchmark.coldStartupNoCompilation`, measuring `MainActivity`'s cold
start via `StartupTimingMetric`. This directly targets Phase 1's still-open AC4.

**What this does and doesn't prove:** the module now exists and is (as far as careful
reading of its own config can confirm without compiling it) structurally correct. It
does *not* produce an actual measured baseline — that requires a real device or
emulator, which this sandbox has never had. Treat AC4 as "scaffolded, not satisfied"
until someone runs it.

Specific decisions and risks, in descending order of how much I'd want a second look:

- **Deliberately not using the `refract.android.application` convention plugin.**
  `:benchmark` applies `com.android.test` directly with an explicitly-applied
  `org.jetbrains.kotlin.android` plugin, rather than relying on AGP 9's built-in Kotlin
  support the way `:app` does. AGP 9's built-in-Kotlin announcement (confirmed via web
  search during Phase 1) specifically called out `com.android.application` and
  `com.android.library` — I found no confirmation it also covers `com.android.test`.
  Given that uncertainty, applying the classic plugin explicitly felt like the safer
  default; if it turns out built-in Kotlin *does* cover `com.android.test` too, having
  both applied could plausibly conflict. This needed a judgment call either way.
- **`<profileable android:shell="true" />` added to `app/src/main/AndroidManifest.xml`**,
  with `tools:targetApi="29"` to acknowledge it's above minSdk 27 (the element itself
  no-ops gracefully below API 29). This is what lets the benchmark measure a non-release
  build without a full signing/minification setup. Unverified against real tooling.
- **`experimentalProperties["android.experimental.self-instrumenting"] = true`** in
  `benchmark/build.gradle.kts` — included because macrobenchmark modules commonly need
  it for self-instrumenting measurement; not independently confirmed beyond that general
  recollection.
- **Version numbers for `androidx.test.ext:junit` and `androidx.benchmark:*`** are
  `[BEST-EFFORT]` like everything else in the catalog, tagged as such inline.
- **No CI wiring for `:benchmark`.** Its one real test needs a device, which CI doesn't
  have here, and I wasn't confident enough in the exact Gradle task name a
  `com.android.test` module exposes for a compile-only check (its task naming differs
  from `:app`'s) to add a CI step I couldn't verify wouldn't just fail on a typo. Worth
  adding once someone can confirm the right task name against real tooling.
- **No `ktlint`/`detekt` applied to `:benchmark`** — kept the module's plugin surface
  minimal given everything else about it is already less certain than the rest of the
  project; can be added later the same way `:app`/`:lint-rules` have them.

## 4. Where this leaves Phase 1/2's open items

- Phase 1 AC4: scaffolded (see §3), not satisfied.
- Phase 2's `NaturalOrderComparator` locale-collation gap (`PHASE_2_NOTES.md` §3): closed,
  see §2 above.
- The fault-injecting decorators' own correctness (implicit gap, not previously listed
  as an open item because it hadn't been noticed yet): closed, see §1 above.
