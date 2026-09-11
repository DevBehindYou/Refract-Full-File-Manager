# Design System

Everything visual comes from a token. **No feature code contains a raw colour, dp value,
blur radius, or duration.** If a value is needed that has no token, add the token.

Tokens live in `core.designsystem` and are exposed through `RefractTheme`.

---

## 1. Colour

Two base themes, each with a full semantic role set. Dynamic colour (Material You, API 31+)
maps onto the same roles and is on by default with an opt-out.

### Light

| Role | Hex | Use |
|---|---|---|
| `background` | `#F6F7F9` | App background beneath everything |
| `surface` | `#FFFFFF` | Cards, sheets, opaque content |
| `surfaceVariant` | `#EDEFF3` | Secondary containers, skeletons |
| `surfaceDim` | `#E4E7EC` | Pressed states on surface |
| `onBackground` | `#0E1116` | Primary text |
| `onSurfaceVariant` | `#5A6270` | Secondary text, metadata |
| `outline` | `#C7CCD4` | Dividers, 1dp borders |
| `primary` | `#2F6BFF` | Selection, active nav, primary action |
| `onPrimary` | `#FFFFFF` | |
| `primaryContainer` | `#DEE8FF` | Selected row background |
| `error` | `#C5292B` | Destructive text/icon |
| `errorContainer` | `#FBE0E0` | Error banners |
| `success` | `#1E7A45` | Completed operations |
| `warning` | `#A86A00` | Low storage, risky action |

### Dark

| Role | Hex |
|---|---|
| `background` | `#0B0D10` |
| `surface` | `#14181D` |
| `surfaceVariant` | `#1D232A` |
| `surfaceDim` | `#0F1317` |
| `onBackground` | `#EAEEF4` |
| `onSurfaceVariant` | `#9AA4B2` |
| `outline` | `#2C333C` |
| `primary` | `#6E9BFF` |
| `onPrimary` | `#06122E` |
| `primaryContainer` | `#1B2C56` |
| `error` | `#FF6B6B` |
| `errorContainer` | `#3A1618` |
| `success` | `#4ED08A` |
| `warning` | `#E0A64A` |

### Category accents

Used only for the category icons and the storage bar. Colour-blind-safe (checked against
deuteranopia and protanopia); each category also has a distinct glyph, so colour is never
the sole signal.

| Category | Light | Dark |
|---|---|---|
| Images | `#2F6BFF` | `#6E9BFF` |
| Video | `#8E4EC6` | `#B98BE6` |
| Audio | `#0E8A7A` | `#3FC3B0` |
| Documents | `#C77400` | `#E5A64B` |
| Archives | `#6B7280` | `#9AA4B2` |
| APKs | `#1E7A45` | `#4ED08A` |
| Downloads | `#0072A8` | `#4FB2E0` |
| Other | `#8A8F98` | `#767D88` |

## 2. Typography

Base family: **system default** (Roboto / OEM sans). One optional accent: a variable grotesk
for the app title only. No custom body font — it costs size and breaks OEM font scaling.

| Token | Size / Line | Weight | Use |
|---|---|---|---|
| `displayLarge` | 32 / 40 | 600 | Storage total, empty-state headline |
| `titleLarge` | 22 / 28 | 600 | Screen titles |
| `titleMedium` | 17 / 24 | 600 | Sheet headers, section titles |
| `bodyLarge` | 16 / 22 | 400 | File names, primary list text |
| `bodyMedium` | 14 / 20 | 400 | Dialog and description text |
| `labelLarge` | 14 / 18 | 600 | Buttons |
| `labelMedium` | 12 / 16 | 500 | Metadata (size · date), breadcrumb |
| `labelSmall` | 11 / 14 | 500 | Nav labels, chips, badges |
| `mono` | 13 / 20 | 400 | Paths, text preview, checksums |

Rules: never below 11sp. All sizes in `sp`. `TextOverflow.MiddleEllipsis` for file names
(the extension matters more than the middle of the name).

## 3. Spacing

4dp base grid.

| Token | dp | Use |
|---|---|---|
| `space.xxs` | 2 | Icon-to-badge |
| `space.xs` | 4 | Inside chips |
| `space.sm` | 8 | Icon-to-text |
| `space.md` | 12 | List row vertical padding |
| `space.lg` | 16 | Screen horizontal padding, card padding |
| `space.xl` | 24 | Section separation |
| `space.xxl` | 32 | Above/below major headers |
| `space.huge` | 48 | Empty-state vertical rhythm |

Screen gutter is `space.lg` on compact, `space.xl` on medium, `space.xxl` on expanded.

## 4. Shape

| Token | Radius | Applies to |
|---|---|---|
| `shape.pill` | 50% | Nav bar container, chips, search bar, FAB |
| `shape.card` | 20dp | Glass cards, storage card |
| `shape.sheet` | 28dp top | Bottom sheets |
| `shape.dialog` | 28dp | Dialogs |
| `shape.menu` | 18dp | Context menus, dropdowns |
| `shape.row` | 14dp | Selected/pressed list-row highlight |
| `shape.thumb` | 10dp | Thumbnails |
| `shape.button` | 14dp | Standard buttons |

Glass surfaces always use `pill`, `card`, `sheet`, `dialog` or `menu`. Never a square corner
on glass — the rim highlight needs curvature to read as thickness.

## 5. Elevation

**Optical layering over shadows.** The hierarchy is communicated by material and tint, with
shadows only where a floating element must separate from arbitrary content.

| Level | Technique | Used by |
|---|---|---|
| `0` | Flat on background | Content lists |
| `1` | `surfaceContainerLow`, tonal elevation 1dp | Cards, section containers |
| `2` | `surfaceContainer`, tonal elevation 2dp | Toolbars, search bar, active drop target highlight |
| `3` | `surfaceContainerHigh`, tonal elevation 3dp | Bottom nav, FAB, selection bar, Transfer Bubble rail |
| `4` | `surfaceContainerHighest`, tonal elevation 6dp + scrim | Sheets, dialogs, Quick Peek overlay |

## 6. Material 3 Elevation & Surface Tokens

Refract uses Material 3 tonal elevation and surface container roles (`surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest`) instead of excessive borders or blur shaders.

```kotlin
data class SurfaceElevationTokens(
    val level0: Dp = 0.dp,
    val level1: Dp = 1.dp,
    val level2: Dp = 3.dp,
    val level3: Dp = 6.dp,
    val level4: Dp = 8.dp,
    val level5: Dp = 12.dp,
)
```

Interactive states (hover, drag pickup, drop target activation) animate tonal elevation and subtle scale (e.g. pickup lifts to 8dp elevation and 1.03 scale) with tactile haptic confirmation.

## 7. Iconography

* Material Symbols Rounded, weight 400, optical size 24, filled for selected states.
* File-type icons are a **single glyph plus a category-accent tint**, never a multicolour
  illustration — they must remain legible at 24dp and in high-contrast mode.
* Custom glyphs only for: the app icon, the storage meter segments, and the empty states.

## 8. Density and sizing constants

| Token | Value |
|---|---|
| `size.touchTarget` | 48dp |
| `size.listRow` | 64dp (72dp with thumbnail) |
| `size.gridCell` | 112dp min, adaptive |
| `size.icon` | 24dp |
| `size.iconLarge` | 40dp |
| `size.thumb` | 44dp (list), fill (grid) |
| `size.navBarHeight` | 64dp |
| `size.navBarInset` | 12dp from the navigation-bar inset |
| `size.border` | 1dp |
| `size.maxContentWidth` | 720dp (content centres beyond this) |

## 9. Theming implementation

```kotlin
@Composable
fun RefractTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = colorSchemeFor(darkTheme, dynamicColor),
        typography = RefractTypography,
        shapes = RefractShapes,
        content = content,
    )
}
```

Feature code reads `MaterialTheme.colorScheme`, `MaterialTheme.typography`, and
`MaterialTheme.shapes` — never ad-hoc literals.
