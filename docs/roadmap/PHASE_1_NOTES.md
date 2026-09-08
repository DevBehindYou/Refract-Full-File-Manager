# Phase 1 — Implementation Notes

Written at delivery time, alongside the code, per AI_DEVELOPMENT_GUIDE.md's
documentation rule. Read this before the first `./gradlew build`.

## 0. The one thing to know before anything else

**This project has not been compiled.** The sandbox that generated it has no network
access to Google's Maven, Maven Central, or `services.gradle.org` — only a short
allowlist covering GitHub, PyPI, npm, and crates.io. That means:

- No dependency in this project has been resolved.
- `./gradlew build` (or any Gradle task) has never actually been run against this code.
- Every file was written from documented API knowledge and cross-checked against
  official sources where a web search was possible, but nothing here has been
  mechanically verified the way AI_DEVELOPMENT_GUIDE.md's "check compilation after
  every significant change" rule asks for.

Treat this delivery as a carefully-reasoned first draft that needs one real build to
confirm, not as a verified-green Phase 1. The sections below tell you exactly where to
look first if that build fails.

## 1. Decisions made on the plan's open questions

The approved plan flagged seven open questions. Since "go ahead" didn't answer each
individually, here's the concrete decision taken for each, recorded so it can be
revisited:

1. **Phase 0 not done.** Proceeded — Phase 1 as scoped touches nothing Phase 0 would
   derisk (no storage backend code exists yet). Do not start Phase 2 without it.
2. **`:benchmark` module.** *Not* created this pass. A real macrobenchmark module needs
   something to benchmark; with only a splash screen and an empty surface, a "cold
   start baseline" would be measuring noise. AC4 is left partially open — see §4.
3. **CI provider.** GitHub Actions, per the "F-Droid / GitHub flavour" language in
   PRIVACY.md/LOGGING_DIAGNOSTICS.md. Not stated explicitly anywhere in `/docs`.
4. **JUnit 5 on Android.** Added `de.mannodermaus:android-junit` (documented in
   TECH_STACK.md in the same change). See §3 for the version-naming risk.
5. **Empty Activity.** Genuinely empty — splash, then a themed `Surface` with no
   content, no placeholder text.
6. **Flavor dimension name.** `"distribution"` — not specified anywhere in `/docs`.
7. **Exact dependency versions.** Mixed confirmed/best-effort — see §3.

## 2. What's deliberately *not* here

- No `:benchmark` module (see §1.2).
- No feature code, no navigation graph, no screens beyond the one empty `Surface`.
- No R8/minification config (that's Phase 12's job per the roadmap).
- No launcher icon beyond a placeholder triangle mark — there's no design system yet
  (`core.designsystem` doesn't exist until a later phase), and "No glass" was explicit
  in this phase's constraints.
- `ktlint`/`detekt` are wired for `:app` and `:lint-rules` only, not `build-logic`
  (common convention; build tooling isn't usually held to the same style gate as
  application code — flagging the choice rather than leaving it silent).

## 3. Version provenance — read `gradle/libs.versions.toml` first

Every version in the catalog is tagged inline as `[CONFIRMED]` (checked against an
official source on 2026-08-28) or `[BEST-EFFORT]` (a reasonable value that could not be
checked from this sandbox). If the build fails on a dependency resolution error, that
file — not this one — is the source of truth for which values are provisional.

The single highest-risk entry is `lintApi` (`32.3.0`): it's extrapolated from a
historical "AGP version + 23 major" pattern (confirmed through AGP 8.x) rather than
independently confirmed for AGP 9.x. If `:lint-rules` fails to resolve, check this
first against the Lint API version actually bundled with whichever AGP 9.3.x patch
resolves.

The second highest-risk entry is the `androidJunit5Plugin` **plugin ID itself**
(`de.mannodermaus.android-junit`, not the older `de.mannodermaus.android-junit5`) —
evidence for the newer ID came from the project's own current README, but plugin
renames are exactly the kind of thing that silently breaks a build with a "plugin not
found" error. That failure mode is easy to diagnose and fix in one line if wrong.

## 4. AC4 (cold-start baseline) — partially open

**Update:** a `:benchmark` module scaffold was added in `HARDENING_PASS_1_NOTES.md` §3.
It still can't produce a real measured number without a device — read that section
before assuming this is resolved.

Phase 1's fourth acceptance criterion asks for a recorded cold-start baseline. Without
a `:benchmark` module (§1.2), this hasn't been captured. Once the app builds, the
smallest useful stand-in is:

```
adb shell am start -W com.devbehindyou.refract.base.debug/com.devbehindyou.refract.MainActivity
```

...and record the reported `TotalTime`. A real `androidx.benchmark` macrobenchmark
module (per TECH_STACK.md §8) should replace this once there's more to measure.

## 5. Lint detector implementation risk

The seven detectors in `:lint-rules` were written against Lint's `Detector`/
`SourceCodeScanner`/UAST APIs from documented knowledge, without being able to compile
against the real `lint-api` jar in this sandbox (see §0). The APIs used
(`getApplicableUastTypes`, `getApplicableMethodNames`/`visitMethodCall`,
`context.report(...)`, `context.isTestSource`, `Issue.create(...)`) have been stable
Lint fundamentals for years, so confidence is reasonably high — but this is the part of
the deliverable most likely to need small signature fixes on first compile.

Two detectors lean on source-text matching rather than full symbol resolution, as a
deliberate defensive choice given the inability to test-compile here:

- `NoRawApiLevelDetector` matches `Build.VERSION.SDK_INT` by rendered source text
  rather than resolving the reference, so it doesn't depend on an `android.os.Build`
  stub being resolvable in every invocation context.
- `NoHardcodedDpDetector` matches the `.dp` selector name and a literal receiver
  syntactically, for the same reason.

If real-world testing turns up false positives/negatives, these are the two most
likely places, and both are self-contained enough to tighten without touching the
other five.

## 6. AGP 9's built-in Kotlin support

This project targets AGP 9.3.0, which removed the separate `org.jetbrains.kotlin.android`
plugin in favour of Kotlin support built into `com.android.application`/
`com.android.library` directly. That change happened after this assistant's reliable
knowledge cutoff (Jan 2026); the direction was confirmed via a live search during this
session, but exact DSL surface details (e.g., whether `kotlin { jvmToolchain(17) }`
resolves exactly as written inside `refract.android.application.gradle.kts`) were not
independently verified against real tooling. If that block fails to resolve, the
`compileOptions { sourceCompatibility / targetCompatibility }` lines in the same file
set Java 17 regardless, as a fallback.

## 7. The Gradle wrapper jar

`gradle/wrapper/gradle-wrapper.jar` was generated locally using an old (2017-era,
Ubuntu-packaged) Gradle 4.4.1 install, since this sandbox has no network access to
`services.gradle.org`. The wrapper protocol has stayed backward/forward compatible for
years, so this jar should still be able to bootstrap-download the real Gradle 9.3.0
distribution correctly — but it's missing some newer wrapper conveniences (e.g., it
won't have the newest checksum-validation code paths). The properties file already
requests `distributionSha256Sum`-style validation via `validateDistributionUrl=true`,
but no `distributionSha256Sum` value is set (fabricating one would be actively wrong).
Recommended one-time fix, with real network access: run
`./gradlew wrapper --gradle-version 9.3.0` once to regenerate the jar from a genuine
current template.

## 8. Minor decisions not called out in the original plan

- `android:allowBackup="false"` on `<application>` — not specified anywhere in
  `/docs`; a reasonable default for an app that handles arbitrary user files, but worth
  a second look given the eventual settings/backup story.
- The `:lint-rules` and `build-logic` modules are excluded from the CI Lint job
  (Android Lint only runs against the `:app` module's variants — `:lint-rules` is a
  plain JVM module and isn't Lint-checkable in the same sense).
