# Screen — Settings

## 1. Purpose

Every setting here exists because a real preference conflicts with another real preference.
Nothing is here to pad a list. If there is an obviously right default, it is the default and
there is no toggle.

## 2. Structure

```text
┌────────────────────────────────────────┐
│  ‹  Settings                           │
├────────────────────────────────────────┤
│  Appearance                            │
│    Theme                    System  ›  │
│    Accent colour           Dynamic  ›  │
│    Glass effects              Full  ›  │
│    Reduce motion            System  ›  │
│                                        │
│  Browsing                              │
│    Default view              List   ›  │
│    Default sort              Name   ›  │
│    Show hidden files            ○      │
│    Show file extensions         ●      │
│    Folders first                ●      │
│    Confirm before delete        ●      │
│                                        │
│  Files                                 │
│    Use trash                    ●      │
│    Keep deleted files      7 days   ›  │
│    Default copy conflict     Ask    ›  │
│    Thumbnail quality       Medium   ›  │
│                                        │
│  Storage access                        │
│    Access level         All files   ›  │
│    Manage folder access             ›  │
│    Troubleshoot access              ›  │
│                                        │
│  Privacy                               │
│    Crash reporting              ○      │
│    Clear search history             ›  │
│    Clear thumbnail cache   142 MB   ›  │
│                                        │
│  Advanced                              │
│    Rendering tier      Automatic    ›  │
│    Export diagnostics               ›  │
│    Reset settings                   ›  │
│                                        │
│  About                              ›  │
└────────────────────────────────────────┘
```

## 3. Notable settings and why they exist

**Glass effects — Full / Reduced / Off.** Automatic is the default and picks the tier by
capability (`../LIQUID_GLASS_RESEARCH.md` §5). The override exists because a user on a
thermally-throttling device knows their situation better than our heuristic does. "Off" must
produce a complete, correct, good-looking app — this is the setting that proves the glass
system is deletable.

**Rendering tier (Advanced) — Automatic / Force A / Force B / Force C.** A debug-flavour-only
control in release builds it is hidden. It exists so the device matrix in
`../testing/DEVICE_MATRIX.md` can be executed without four physical devices.

**Show hidden files — off by default.** Showing dotfiles by default confuses far more users
than it helps.

**Use trash — on by default.** Off means deletes are permanent, and the confirmation dialog
changes wording accordingly. This is the highest-consequence toggle in the app, so turning it
*off* shows an explicit confirmation.

**Default copy conflict — Ask / Keep both / Skip.** Replace is deliberately **not** offered as
a blanket default. A user cannot pre-consent to overwriting files they have not seen.

**Thumbnail quality — Low / Medium / High.** Trades cache size and decode cost against
appearance. Shows the current cache size next to the clear action so the trade-off is visible.

**Crash reporting — off by default,** and only present in the flavour that includes it
(`../PRIVACY.md` §4). The base flavour has no network permission at all, so this row is absent
rather than disabled — a disabled toggle implies the capability exists.

## 4. Behaviour

* Backed by **DataStore Preferences**, read as a `Flow<AppSettings>` at the ViewModel layer.
  Nothing reads `SharedPreferences` synchronously on the main thread.
* Changes apply **immediately**. There is no Save button and no confirmation for reversible
  settings.
* Destructive actions (Clear cache, Reset settings, Turn off trash) confirm and state exactly
  what will happen, including sizes and counts.
* Access-level rows are read-only status plus a deep link to the system settings page —
  we never imply the app can grant itself permission.
* Settings that cannot apply on this device are **hidden, not disabled** — with one exception:
  glass tier, which is shown with an explanation, because a user wondering why the app looks
  different from a screenshot deserves an answer.

```kotlin
data class SettingsUiState(
    val settings: AppSettings,
    val accessLevel: AccessLevel,
    val grantedTreeCount: Int,
    val thumbnailCacheBytes: Long,
    val effectiveGlassTier: GlassTier,
    val isDebugBuild: Boolean,
)
```

## 5. Animations

* Switches use the platform animation; nothing custom.
* Choice sheets slide up with `Motion.Standard`.
* A theme change cross-fades the whole window over 200 ms rather than snapping — implemented
  by animating the colour scheme, not by recreating the Activity.
* Changing the glass tier re-renders live so the user can see the difference while the sheet
  is still open.

## 6. Edge cases

| Case | Behaviour |
|---|---|
| Dynamic colour on API < 31 | Row hidden; accent list shows the static palettes only |
| Access changed outside the app | Detected in `onResume`, the status row updates |
| Cache clear while thumbnails are loading | Loads are cancelled first, then the cache is cleared; the UI reloads visible items |
| Reset settings | Restores defaults only — never touches favourites, trash, or files |
| Font scale 200% | Rows wrap to two lines; nothing truncates and no row loses its control |

## 7. Accessibility

* Each group header is a `heading()`.
* Every switch row is a single focusable node reading label, current state, and summary; the
  whole row is the toggle target, not just the switch.
* Choice rows announce the current value as part of the label.
* Destructive confirmations state consequences in plain language, and the destructive action
  is never the default-focused button.

## 8. Responsive

| Class | Layout |
|---|---|
| Compact | Single scrolling list |
| Medium | Same, constrained to 640dp and centred |
| Expanded | Two panes: groups on the left, the selected group's settings on the right |
