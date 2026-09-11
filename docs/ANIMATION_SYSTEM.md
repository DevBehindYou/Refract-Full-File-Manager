# Animation System

Motion in Refract does three jobs: it explains **where things came from**, it confirms
**that a touch registered**, and it provides **physical direct-manipulation feedback**. Anything
that does none of these is deleted.

---

## 1. Spring catalogue

All spatial motion uses springs. Only opacity and colour use duration-based tweens.

```kotlin
object Motion {
    // spatial
    val Quick      = spring<Float>(dampingRatio = 0.90f, stiffness = 1400f) // ~120ms settle
    val Standard   = spring<Float>(dampingRatio = 0.85f, stiffness =  700f) // ~220ms
    val Emphasized = spring<Float>(dampingRatio = 0.75f, stiffness =  550f) // responsive, expressive
    val Gentle     = spring<Float>(dampingRatio = 0.90f, stiffness =  350f) // ~380ms
    val Settle     = spring<Float>(dampingRatio = 1.00f, stiffness =  900f) // no overshoot

    // opacity / colour
    const val FadeFast = 90     // ms
    const val FadeStd  = 150
    const val FadeSlow = 240
}
```

`Emphasized` is reserved for navigation indicator travel and dialog / preview expansions. Overshoot
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

## 4. Navigation motion

| Behaviour | Spec |
|---|---|
| **Indicator travel** | The active indicator slides to the new item with `Emphasized` |
| **Icon morph** | Outlined → filled, cross-faded over `FadeFast`, with a 1.0→0.88→1.0 scale pulse on `Quick` |
| **Label** | Inactive labels at 0.65 alpha; active at 1.0 with weight 500→600, `FadeStd` |
| **Touch compression** | On press, the item scales to 0.96 with `Quick`, released with `Standard` |

## 5. Direct-Manipulation & Interaction Motion

| Component | Motion |
|---|---|
| File drag pickup | Scale 1.0→1.03, elevation 0→8dp with `Quick`, haptic tick on pickup |
| Multi-selection drag | Stacks into max 3 card previews with item count badge |
| Drop target activation | Elevation rises to 3dp, border highlight pulses with `Standard` |
| Folder hover navigation | Dwell 500–750ms shows progress ring, folder opens with `Standard` without dropping drag session |
| Transfer bubble acceptance | Bubble pill scale pulse 1.0→1.12→1.0 with `Emphasized`, badge counter increments |
| Quick Peek expand | Scrim fades in `FadeFast`, preview surface scales 0.94→1.0 with `Standard` |
| Quick Peek release | Smooth reverse fade and scale dismiss |
| List row press | Background `surfaceDim` fade in `FadeFast`, scale 0.995 with `Quick` |
| Selection enter | Checkbox slides in from leading edge, content shifts right 48dp, `Standard` |
| Context menu | Scale 0.9→1.0 from anchor corner + fade, `Quick` |
| Operation progress | Determinate bar interpolates with `Gentle` — never jumps |
| Operation complete | Progress collapses into check glyph with `Emphasized`, then snackbar |
| Pull to refresh | Clean Material 3 pull-to-refresh spinner |

## 6. Reduce-motion behaviour

When `Settings.Global.ANIMATOR_DURATION_SCALE == 0` or the accessibility reduce-motion flag
is set:

* All springs → `tween(120, LinearEasing)` on opacity only. No translation, no scale.
* Container transforms → cross-fade.
* Predictive back → instant.
* Nav indicator → moves instantly, icon morph becomes an instant swap.
* Drag preview → static lift with no spring overshoot.

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
