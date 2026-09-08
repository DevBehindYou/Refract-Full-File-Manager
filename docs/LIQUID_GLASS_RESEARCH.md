# Liquid Glass Research and the Refract Rendering System

This document separates four things that are constantly conflated:

1. **Apple's Liquid Glass design principles** — conceptual, transferable.
2. **What Android supports natively** — concrete, version-gated.
3. **What must be recreated by hand** — shader and compositing work.
4. **What must degrade** — the fallback ladder.

We are not porting Apple frameworks. We are not porting CSS. We are rebuilding the
*perceptual result* using Compose, `RuntimeShader`, `RenderEffect`, and `GraphicsLayer`.

---

## 1. What Liquid Glass actually is (Apple, iOS 26 / macOS Tahoe generation)

Apple's material is not "blur + white overlay". Blur is one of six ingredients. The
perceptual signature comes from the combination:

| Ingredient | What it does | Why it matters |
|---|---|---|
| **Backdrop sampling** | The surface samples what is *behind* it, not itself | Without this it is frosted plastic, not glass |
| **Refraction / displacement** | Content behind is optically bent, strongest at the edges | This is the single most identifying cue |
| **Edge lensing (Fresnel-like)** | Rim brightens and distorts more than the centre | Reads as thickness |
| **Specular highlight** | A moving bright band responding to device tilt / interaction | Reads as a physical surface catching light |
| **Adaptive tint & saturation** | Slight colour lift sampled from the backdrop | Keeps glass sitting *in* the scene |
| **Fluid morphing** | Shapes stretch, merge and settle with spring physics | Reads as liquid, not as a rectangle appearing |

Apple's own accessibility guidance is the important half: the material must survive
**Reduce Transparency** and **Reduce Motion**, and it must never be the sole carrier of
meaning or the reason text fails contrast.

### The two Apple rules we adopt as hard constraints

1. **Glass is chrome, never content.** Navigation, controls, sheets, overlays. Never the
   surface that text is primarily read from.
2. **Glass floats above one continuous scrolling layer.** If glass sits on glass, the
   refraction stacks and legibility collapses. Maximum of **two** glass layers on screen.

---

## 2. LiquidAccessories — what we take from it

`DevBehindYou/LiquidAccessories` is an optical Liquid Glass UI library: 76 standalone
HTML/CSS/JS components whose stated philosophy is that they **refract a background grid
rather than merely blurring it**, with responsiveness, accessibility, and no build step.

**What transfers (design and engineering philosophy):**

* *Refraction over blur.* The test of a glass component is whether straight lines behind it
  visibly bend at the rim. This becomes our Tier A acceptance test: render the component
  over a white orthogonal grid and confirm edge displacement.
* *Component-first, not effect-first.* Each glass component owns its own material
  parameters; there is no single global "glassify" filter.
* *No build step / no exotic dependency.* Translated to Android: no third-party rendering
  engine, no WebView, no Skia hacking. Compose + AGSL + the platform.
* *Accessibility declared up front,* not retrofitted.
* *Every component ships a fallback that keeps the same anatomy.* Same shape, same padding,
  same affordance — only the material differs.

**What does not transfer:**

* `backdrop-filter`, SVG `feDisplacementMap`, and CSS `filter` chains have no Android
  equivalent. Do not attempt a mechanical translation.
* Web layering assumptions (the backdrop is always available and cheap) are false on
  Android, where capturing the backdrop costs a `GraphicsLayer` readback.
* The 76-component surface area is far too much for a file manager. We port **13** glass
  components (see [`COMPONENT_LIBRARY.md`](COMPONENT_LIBRARY.md)).

---

## 3. What Android gives us natively

| API | Facility | Use |
|---|---|---|
| Any | `Brush.linearGradient` / `radialGradient`, `drawWithCache` | Highlights, rims, tints, noise |
| Any | `Modifier.graphicsLayer`, `clip`, `shadow` | Shape, compositing, optical layering |
| Any | `androidx.compose.animation.core.spring` | Physics motion |
| 31+ | `RenderEffect.createBlurEffect` | Real blur (Compose `Modifier.blur` is backed by this) |
| 31+ | `RenderEffect.createChainEffect` / `createColorFilterEffect` | Blur + tint chains |
| 32 | Layered `GraphicsLayer` compositing | Approximate backdrop blur without shaders |
| 33+ | `RuntimeShader` + **AGSL** | Backdrop sampling, displacement, refraction, highlights |
| 33+ | `RenderEffect.createRuntimeShaderEffect` | Wiring an AGSL shader into the render node |
| 34+ | `GraphicsLayer` Compose API (`rememberGraphicsLayer`) | Clean backdrop capture without hacks |

**The hard boundary: true refraction requires `RuntimeShader`, i.e. API 33+.**
Everything below 33 approximates. This is not a limitation we can engineer around; it is a
platform fact, and the design must be beautiful without it.

### Backdrop capture

Compose's `Modifier.blur` blurs *the composable's own content*, which is useless for glass.
To blur what is behind, the backdrop must be captured into a `GraphicsLayer` and re-drawn
inside the glass surface with an effect applied. On API 34+ `rememberGraphicsLayer()` +
`record {}` + `drawLayer()` makes this first-class. Below that it requires the
"source/child" pattern.

**Recommendation:** use **Haze** (`dev.chrisbanes.haze`) for the capture-and-blur plumbing
rather than reimplementing it. It already selects the correct implementation per API level
(scrim on old versions, layered `GraphicsLayer`s around API 32, `RuntimeShader` on 33+),
and it is Apache-2.0 and actively maintained. Our own AGSL work then sits **on top** of the
captured backdrop for refraction, which Haze does not opinionate away.
This is the one graphics dependency we take; justification in
[`TECH_STACK.md`](TECH_STACK.md).

---

## 4. The three tiers

`GlassTier` is a domain enum. All glass composables read it from a `CompositionLocal`.
**Layout, padding, hit targets, shapes and information hierarchy are identical in all
three tiers.** Only the material changes.

### Tier A — Advanced Liquid Glass (API 33+, capable GPU)

* Backdrop captured to a `GraphicsLayer`, blurred, then sampled by an AGSL shader.
* **Edge refraction:** UV displacement that ramps up over the outer ~14dp of the shape,
  computed from a signed-distance approximation of the rounded rect.
* **Specular highlight:** a soft band whose position is driven by a slow ambient animation
  plus press position; opacity ~0.10–0.22.
* **Rim light:** 1dp inner stroke, gradient from 0.35 alpha (top-left) to 0.08 (bottom-right).
* **Adaptive tint:** average backdrop luminance sampled at low resolution, used to pick
  light-glass vs dark-glass tint so the component stays legible over both.
* **Noise:** 2–3% monochrome grain to kill gradient banding.
* Shader runs at the component's size only, never full-screen.

### Tier B — Lightweight Glass (API 31–32, or Tier A device under thermal/battery pressure)

* Real blur via `RenderEffect` / `Modifier.blur` on the captured backdrop, radius reduced
  ~40% versus Tier A.
* **No displacement shader.** Refraction is *suggested* by a stronger rim gradient and a
  narrow, brighter edge highlight.
* Specular highlight becomes a static gradient, animated only on press.
* Same tint and noise treatment, cheaper sampling.

### Tier C — Compatibility Glass (API 27–30, low-RAM, or Reduce Transparency on)

* **No backdrop capture at all.** Zero readback cost.
* Translucent scrim (`surface` at 0.82–0.92 alpha) + a vertical gradient that mimics the
  light falloff of the glass.
* 1dp border, tuned shadow for separation, no highlight animation.
* On Reduce Transparency, opacity goes to 1.0 and this becomes a solid Material surface.

### Tier selection

```kotlin
// domain
enum class GlassTier { ADVANCED, LIGHTWEIGHT, COMPATIBILITY }

interface GlassCapabilityManager {
    val tier: StateFlow<GlassTier>
    fun refresh()          // call on resume, thermal change, power-save change
}
```

Resolution order — **the first rule that fires wins, and downgrades always win**:

| # | Condition | Result |
|---|---|---|
| 1 | User forced a tier in Settings | that tier |
| 2 | `AccessibilityManager` reduce-transparency / high-contrast on | C |
| 3 | `PowerManager.isPowerSaveMode` | C |
| 4 | `PowerManager.currentThermalStatus >= THROTTLING_MODERATE` (API 29+) | one tier down |
| 5 | `ActivityManager.isLowRamDevice` or RAM < 3 GB | C |
| 6 | `Build.VERSION.SDK_INT < 31` | C |
| 7 | `Build.VERSION.SDK_INT in 31..32` | B |
| 8 | `Build.VERSION.SDK_INT >= 33` and measured frame budget OK | A |
| 9 | otherwise | B |

**Runtime demotion:** a `FrameTimingWatcher` samples jank over rolling 2-second windows
using `JankStats`. Three consecutive windows above the budget → drop one tier, show nothing
to the user, log it. The app never promotes back up within the same session (avoids
oscillation); it re-evaluates on next cold start.

**Battery:** when battery < 15% and not charging, cap at Tier B; when power save is on,
Tier C. Ambient highlight animations stop entirely below 20%.

---

## 5. The AGSL shader (Tier A)

Conceptual structure — the real file lives at
`core/designsystem/shader/liquid_glass.agsl`.

```glsl
uniform shader backdrop;     // the captured, pre-blurred backdrop
uniform float2 size;         // component size in px
uniform float  cornerRadius;
uniform float  edgeWidth;    // px over which refraction ramps in
uniform float  refractStrength;
uniform float  highlightPos; // 0..1 animated
uniform float  highlightStrength;
uniform float  tintLuma;     // sampled backdrop luminance

// signed distance to a rounded rectangle
float sdRoundRect(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - halfSize + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 coord) {
    float2 center = size * 0.5;
    float2 p = coord - center;
    float d = sdRoundRect(p, center, cornerRadius);   // <0 inside

    // 0 in the middle, 1 at the rim
    float edge = smoothstep(-edgeWidth, 0.0, d);

    // push the sample outward along the surface normal -> lensing
    float2 dir = normalize(p + 1e-4);
    float2 uv  = coord + dir * edge * edge * refractStrength;

    half4 col = backdrop.eval(clamp(uv, float2(0.0), size));

    // rim brightening (Fresnel-ish)
    col.rgb += half3(edge * edge * 0.18);

    // moving specular band
    float band = smoothstep(0.14, 0.0, abs((coord.x + coord.y) / (size.x + size.y) - highlightPos));
    col.rgb += half3(band * highlightStrength);

    return col;
}
```

Notes that matter in practice:

* `edge * edge` (quadratic) ramps refraction so the centre stays optically clean — a linear
  ramp makes text behind the glass swim and fails legibility review.
* `clamp` the sample coordinate; unclamped `eval` at the border produces smearing artefacts
  on some drivers. **[device-variable]**
* Compile the shader once and cache it. `RuntimeShader` construction is not free; measure it
  on a low-end device before enabling Tier A there.
* The blur happens *before* the shader (chained `RenderEffect`), never inside it. Doing a
  Gaussian in AGSL per-frame is the single easiest way to destroy the frame budget.

---

## 6. Performance rules for glass

| Rule | Reason |
|---|---|
| Total glass area ≤ **25% of viewport** at any time | Backdrop readback cost scales with area |
| Maximum **2** glass layers stacked | Compounding refraction destroys legibility and doubles cost |
| **Never** glass on a scrolling list item | Recomposition + readback per item is fatal |
| Glass shapes must be static during scroll | Re-recording the layer every frame negates caching |
| Ambient highlight animation: ≤ 1 running at a time | Continuous invalidation keeps the GPU awake |
| Shader uniforms updated via `graphicsLayer` lambda, not recomposition | Avoids recomposing the whole subtree per frame |
| Disable glass entirely while a file operation is running in-view | Reliability outranks polish |

**Budget:** glass rendering must cost **< 4 ms/frame** on the reference mid-tier device
(Snapdragon 6-series class, 6 GB RAM). If it does not, the device is demoted to Tier B.

---

## 7. Accessibility requirements (mandatory, not optional)

* **Reduce transparency** → Tier C, opacity 1.0. Detected via `AccessibilityManager` and,
  where available, `Settings.Global.TRANSITION_ANIMATION_SCALE == 0` as a motion proxy.
* **Reduce motion** → all ambient/specular animation off; springs replaced with 120 ms fades;
  container transforms replaced with cross-fades.
* **Contrast:** text on any glass surface must reach **4.5:1** against the *worst-case*
  backdrop, not the average. Enforced by placing an opaque tint beneath text within glass
  containers — the tint is part of the component, not an afterthought.
* Glass **never** encodes state on its own. A selected nav item is identified by icon fill,
  label weight, and `selected` semantics — the glass indicator is decoration.
* All glass surfaces are `Modifier.semantics { }`-transparent: they add no announcements.

---

## 8. Verification checklist for any new glass component

1. Renders correctly in all three tiers (screenshot test, 3 variants).
2. Over a white orthogonal grid, Tier A shows visible edge bending; Tier B/C do not (expected).
3. Text contrast ≥ 4.5:1 over black, white, and a photographic backdrop.
4. TalkBack traversal order unchanged versus a plain Material equivalent.
5. No frame over 16 ms during a 5-second scroll on the reference device.
6. Behaves under Reduce Transparency, Reduce Motion, Battery Saver, and font scale 2.0.
7. Removing the glass modifier entirely still leaves a usable, correctly laid-out component.

Rule 7 is the important one: **the glass system must be deletable.**
