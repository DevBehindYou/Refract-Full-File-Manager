# UI Layer

## 1. Structure

```text
feature/browse/
├── BrowseRoute.kt        // hiltViewModel, collects state + effects, wires navigation
├── BrowseScreen.kt       // @Composable(state, onEvent) — pure, previewable
├── BrowseViewModel.kt
├── BrowseUiState.kt      // state + event + effect definitions
└── component/            // screen-specific composables
```

**`Route` vs `Screen`:** the Route is the only place a ViewModel or `NavController` is
touched. The Screen is a pure function of state, so every screen has previews and UI tests
that need no DI.

## 2. State hoisting

* No `@Composable` holds business state. `remember` is for UI-only state: scroll position,
  expanded/collapsed, text field value before commit, animation state.
* Everything else lives in the ViewModel and arrives as `state`.
* Callbacks are a single `onEvent: (Event) -> Unit`, not 15 separate lambdas — this keeps
  parameter lists stable and prevents lambda-allocation-per-item in lists.

## 3. List performance rules

```kotlin
LazyColumn(state = listState) {
    items(
        items = state.nodes,
        key = { it.id.raw },                       // stable keys — mandatory
        contentType = { if (it.isDirectory) "folder" else "file" },
    ) { node ->
        FileRow(node = node, onClick = remember(node.id) { { onEvent(Open(node.id)) } }, ...)
    }
}
```

| Rule | Reason |
|---|---|
| Stable `key` on every item | Without it, scroll position and animations break on any list change |
| `contentType` set | Lets Compose reuse the right node type; measurable scroll win on mixed lists |
| UI models are `@Immutable` | Prevents unnecessary recomposition |
| No lambda allocated per item per recomposition | `remember(key)` the callback |
| No `derivedStateOf` unless the derivation is expensive | It costs more than it saves for cheap reads |
| Never read a `State` in composition when a draw-phase lambda would do | `Modifier.graphicsLayer { alpha = animatedAlpha }` skips recomposition |
| Never nest scrollables of the same axis | Compose throws, and the design should not need it |
| `LazyColumn` for > 20 items, always | |

Recomposition counts are asserted in tests for `FileRow` and `LiquidBottomBar`.

## 4. Compose + glass

* `HazeState` is created once per screen scaffold and provided via `LocalHazeState`.
* The scrolling content carries `Modifier.hazeSource(state)`; glass chrome carries the
  effect modifier. **Only one source per screen.**
* Shader uniforms are driven from `Animatable`s read inside `graphicsLayer { }` lambdas so
  animation never triggers recomposition.
* Glass composables read `LocalGlassTier` and branch once, at the top — not per draw.

## 5. Previews

Every component ships previews for:
```kotlin
@Preview(name = "Light")            @Preview(name = "Dark", uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Font 2x", fontScale = 2f)
@Preview(name = "RTL", locale = "ar")
@Preview(name = "Small", widthDp = 320)  @Preview(name = "Tablet", widthDp = 840)
annotation class RefractPreviews
```
Plus a tier parameter (`A`/`B`/`C`) via `@PreviewParameter`. Preview data comes from
`core.ui.preview.SampleData` — never real file access.

## 6. Navigation in the UI layer

* One `NavHost` in `MainActivity`. Type-safe routes via Kotlin serialization.
* The Route composable receives navigation lambdas; it never holds the `NavController`.
* Bottom-bar destinations use `saveState`/`restoreState` with `launchSingleTop` so tab
  switching does not stack duplicates.
* See `NAVIGATION.md`.

## 7. Insets and edge-to-edge

* `enableEdgeToEdge()` in `MainActivity.onCreate` — mandatory at API 35+, harmless below.
* Content applies `Modifier.consumeWindowInsets` correctly so lists scroll under the bars
  while their last item clears the nav bar.
* The floating glass bottom bar sits `12dp` above `WindowInsets.navigationBars`.
* The IME is handled with `Modifier.imePadding()` on input surfaces only, never globally.

## 8. Forbidden in the UI layer

* `java.io.File`, `Uri`, `ContentResolver`, `DocumentFile` (Lint-enforced)
* `runBlocking`, `LaunchedEffect(Unit)` doing I/O
* `Modifier.blur` applied directly (go through `GlassSurface`)
* Hardcoded `dp`, colour, duration (Lint-enforced)
* Business decisions in `when` blocks — if a composable decides *whether* something is
  allowed, that decision belongs in the ViewModel or domain
* `Thread.sleep`, `System.currentTimeMillis()` for animation timing
