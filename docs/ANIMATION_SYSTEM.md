# Animation System

Motion in Refract does three jobs: it explains **where things came from**, it confirms
**that a touch registered**, and it makes glass read as a **physical material**. Anything
that does none of these is deleted.

---

## 1. Spring catalogue

All spatial motion uses springs. Only opacity and colour use duration-based tweens.

```kotlin
object Motion {
    // spatial
    val Snappy   = spring<Float>(dampingRatio = 0.90f, stiffness = 1400f) // ~120ms settle
    val Standard = spring<Float>(dampingRatio = 0.85f, stiffness =  700f) // ~220ms
    val Gentle   = spring<Float>(dampingRatio = 0.90f, stiffness =  350f) // ~380ms
    val Liquid   = spring<Float>(dampingRatio = 0.62f, stiffness =  520f) // visible overshoot
    val Settle   = spring<Float>(dampingRatio = 1.00f, stiffness =  900f) // no overshoot

    // opacity / colour
    const val FadeFast = 90     // ms
    const val FadeStd  = 150
    const val FadeSlow = 240
}
```

`Liquid` is reserved for the glass nav indicator and the FAB→sheet morph. Overshoot
elsewhere reads as sloppiness rather than character.

## 2. Duration ceiling

| Class | Max |
|---|---|
| Touch feedback | 120 ms |
| In-screen state change | 250 ms |
| Screen transition | 350 ms |
| Sheet/dialog entrance | 300 ms |
| Anything else | **there is no anything else** |

Nothing in the app animates for longer than 350 ms. Long animations feel expensive to users
who open a file manager 20 times a day.

## 3. Screen transitions

| Transition | Spec |
|---|---|
| Forward (folder → subfolder) | Incoming: slide X from +8% + fade in `FadeStd`, scale 0.98→1.0 with `Standard`. Outgoing: slide X to −4%, fade to 0.6, scale to 0.98 |
| Back | Exact reverse, driven by `PredictiveBackHandler` progress so the gesture scrubs it |
| Tab switch (bottom nav) | Cross-fade `FadeStd` + 6dp Y rise on the incoming content. **No horizontal slide** — tabs are peers, not a sequence |
| List item → preview | Shared-element container transform: thumbnail bounds → viewer bounds, `Standard`, 280 ms |
| Modal (sheet) | Slide Y from 100%, `Standard`; scrim fades `FadeStd`; content behind scales to 0.985 |
| Dialog | Scale 0.92→1.0 + fade, `Snappy` |

### Predictive back

Every destination implements `PredictiveBackHandler`. During the gesture:
the current screen scales to 0.92 and translates toward the gesture edge, corner radius
animates 0→24dp, and the destination is revealed behind it. Releasing below threshold
springs back with `Settle`.

## 4. Bottom navigation motion

The signature component. Five behaviours, composed:

| Behaviour | Spec |
|---|---|
| **Indicator travel** | The glass pill slides to the new item with `Liquid`. It **stretches** while travelling: width scales to 1.18× at mid-travel, back to 1.0 at rest, driven by velocity |
| **Icon morph** | Outlined → filled, cross-faded over `FadeFast`, with a 1.0→0.88→1.0 scale pulse on `Snappy` |
| **Label** | Inactive labels at 0.65 alpha; active at 1.0 with weight 500→600, `FadeStd` |
| **Touch compression** | On press, the whole bar scales to 0.985 and the pressed item to 0.94, `Snappy`. Released with `Standard` |
| **Highlight response** | Tier A only: the specular band's `highlightPos` uniform animates toward the touch X over 400 ms, then drifts back |

Tier B drops the specular response and the stretch (indicator translates only).
Tier C keeps only translate + icon morph.

## 5. Component motion

| Component | Motion |
|---|---|
| List row press | Background `surfaceDim` fade in `FadeFast`, scale 0.995 with `Snappy` |
| Selection enter | Checkbox slides in from leading edge, content shifts right 48dp, `Standard`, staggered 12 ms per visible row (max 8 rows staggered) |
| Selection action bar | Rises from below with `Standard`, nav bar drops out simultaneously |
| Context menu | Scale 0.9→1.0 from the anchor corner + fade, `Snappy` |
| Search bar focus | Expands to full width, back arrow rotates in, `Standard` |
| Breadcrumb | New segment slides in from trailing edge with `Snappy`; scrolls itself into view |
| Operation progress | Determinate bar interpolates with `Gentle` — never jumps |
| Operation complete | Progress bar collapses into a check glyph, `Liquid`, then snackbar |
| Pull to refresh | Glass droplet stretches with drag (elastic), snaps with `Liquid` on release |
| Empty state | Glyph fades in + 8dp rise, `Gentle`, 60 ms after the list resolves |
| Error state | Shake: ±6dp X, 2 cycles, `Snappy`. Once, never repeated |
| Thumbnail load | Cross-fade `FadeStd` from the placeholder. No scale, no shimmer sweep |
| Sheet drag | 1:1 with finger; dismiss below 40% or on velocity > 800dp/s |
| FAB → sheet | Container transform: FAB circle morphs into the sheet's top edge, `Liquid`, 300 ms |

## 6. Glass-specific motion (Tier A)

| Effect | Spec |
|---|---|
| Ambient specular drift | `highlightPos` animates 0→1 over 8 s, `LinearEasing`, infinite. **One at a time app-wide**, and only on the frontmost glass surface |
| Press refraction bloom | `distortion` uniform × 1.35 over 120 ms on press, back over 200 ms |
| Surface entrance | `opacity` 0→target and `blurRadius` 0→target over 200 ms, so glass "condenses" rather than popping |
| Backdrop settle | When the content behind stops scrolling, the backdrop layer is re-captured once — never per frame |

Ambient drift stops entirely when: reduce motion is on, battery < 20%, the tier is B or C,
or a file operation is running.

## 7. Reduce-motion behaviour

When `Settings.Global.ANIMATOR_DURATION_SCALE == 0` or the accessibility reduce-motion flag
is set:

* All springs → `tween(120, LinearEasing)` on opacity only. No translation, no scale.
* Container transforms → cross-fade.
* Predictive back → instant.
* Nav indicator → moves instantly, icon morph becomes an instant swap.
* Ambient glass animation → off.
* Pull-to-refresh elastic → a plain progress indicator.

Nothing becomes unusable and no information is lost, because motion never carries meaning
alone.

## 8. Haptics

| Event | Constant |
|---|---|
| Selection toggle | `HapticFeedbackType.TextHandleMove` |
| Long press | `HapticFeedbackType.LongPress` |
| Operation complete | `VibrationEffect.EFFECT_CLICK` |
| Operation failed | Double click pattern |
| Nav tab change | `TextHandleMove` |
| Reaching a drag drop target | `TextHandleMove` |

All haptics check `View.isHapticFeedbackEnabled` and the system setting.

## 9. Performance rules for motion

* Animate `graphicsLayer` properties (translation, scale, alpha) — never layout properties,
  never `padding`, never `size` in a list.
* Use `Modifier.graphicsLayer { }` with a lambda so animation runs in the draw phase and
  skips recomposition.
* Never animate a shader uniform through recomposition; drive it from an `Animatable` read
  inside the draw lambda.
* Stagger a maximum of 8 items; beyond that everything animates together.
* No animation may run while a `LazyColumn` is flinging except the item's own press state.
