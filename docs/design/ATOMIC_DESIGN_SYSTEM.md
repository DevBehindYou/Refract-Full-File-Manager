# Atomic Design System — "Technical Editorial"

**Version 1.0 · October 2026**
**Source products:** Atomic Notes Android app (v2.03.5 screens) and the Atomic Notes Community website (`atomic-notes-community.vercel.app`)
**Owner:** Ashutosh Sharma (DevBehindYou)
**Audience:** UI/UX designers and front-end/Flutter developers building new products in the Atomic family

---

## How to use this document

This guide describes the visual and interaction language shared by the Atomic Notes app and the Atomic Community website, so a new product can look and feel like part of the same family from day one.

- **Web values are exact.** They are taken directly from the website's CSS (`globals.css`, `tailwind.config.ts`).
- **App values are measured from the app's screens.** Colours were sampled pixel by pixel and are exact. Sizes are converted from 1080 px-wide screenshots at a 2.625 density (about 411 dp wide), so treat dp sizes marked **≈** as close estimates and round them to the 4 dp grid.
- **"Rule"** means do this every time. **"Recommended"** means a fix or addition that the current products don't do yet but a new product should.
- Section 13 lists the known inconsistencies in the current products, so a new product doesn't copy them.

**Quick summary:** ink-black text on warm off-white paper, one electric-blue accent called **Signal**, tall condensed uppercase headlines, monospace uppercase labels, 1.5–2 px ink borders, and solid offset shadows with no blur.

---

## Table of contents

1. Design principles
2. Brand foundations
3. Colour
4. Typography
5. Spacing, layout and grid
6. Shape, borders and elevation
7. Iconography and imagery
8. Motion
9. Components
10. Patterns (screens and page sections)
11. Accessibility rules
12. Content and microcopy
13. Known inconsistencies — don't copy these
14. Implementation tokens (CSS, Tailwind, Flutter, JSON)
15. Starting a new product in the family
16. Quick reference card

---

## 1. Design principles

| # | Principle | What it means in practice |
|---|---|---|
| 1 | **Ink on paper** | Every screen starts as near-black ink (`#15171B`) on warm paper (`#F4F5F1`). Colour is the exception, not the base. |
| 2 | **One signal** | There is exactly one accent colour, Signal Blue (`#3A2FF0`). It marks what to press, what is on, and the one word in a headline that matters. If everything is blue, nothing is. |
| 3 | **Hard edges, honest depth** | Depth comes from solid offset shadows (e.g. `3px 3px 0 ink`) and visible borders — never blurred drop shadows or gradients for elevation. It should feel printed, not floating. |
| 4 | **Labels read like instruments** | Metadata, section labels, counters and timestamps are set in uppercase monospace with wide tracking: `2026-08-10 16:41`, `8 / 50`, `SESSION / ACTIVE`. |
| 5 | **Headlines are posters** | Titles use a tall condensed display face in uppercase with tight line-height. Body text stays a calm, readable grotesque. |
| 6 | **Show the real state** | Show numbers instead of vague words: `110 / 120 energy`, `8 notes on device · 8 in cloud`. Say exactly what a destructive action will do. |
| 7 | **Calm by default, loud on purpose** | Most of the UI is quiet ink and paper. Loud treatments (a Signal-filled card, a dark section, a red danger zone) are rare, so they carry weight. |

---

## 2. Brand foundations

### 2.1 Personality

Technical, trustworthy, editorial, slightly playful. Think a well-designed engineering notebook or a printed spec sheet, not a glossy SaaS dashboard.

### 2.2 Brand marks

| Asset | Description | File (Atomic Notes repo) |
|---|---|---|
| **Atom mark** | Three Signal-blue orbit ellipses rotated 0°/60°/120°, an ink nucleus, and three Signal electrons. | `public/hero-banner.svg`, `public/icons/atom.svg` |
| **App icon** | The atom on a dark rounded square. On the web it's shown at 28 px with a 7 px radius and a `2px 2px 0 ink` shadow. | `public/icon.png` |
| **Wordmark** | "ATOMIC NOTES" set in Bebas Neue, 1.5rem, 1 px tracking, ink. | (set in type, not an image) |
| **Atomic Coin** | A paper disc with a 5.5-unit Signal outer ring, a faint inner ring, three ink orbits, an ink nucleus, and a solid deep-blue (`#1D14A0`) offset shadow disc. | `public/icons/atomic-coin.svg`; small-size redraw in `src/components/CoinMark.tsx` |
| **Atomi** | The mascot: a round ink face with a dot-grid expression and Signal accents, animated. | `public/atomi.gif` |
| **Tier particles** | Tachyon, Antimatter, Monopole, Strangelet capacity-tier icons. | `public/icons/*.svg` (see §13: they use an off-brand blue ramp) |

**Naming.** Products in the family are named "Atomic ___". Features use physics vocabulary: Atomic Energy, Atomic Coins, Tachyon, Antimatter, Monopole, Strangelet. Keep this metaphor in new products, used sparingly and always with plain-language explanations.

---

## 3. Colour

### 3.1 Core palette

These five colours make up more than 95% of every screen.

| Token | Hex | RGB | Role | Share of app screens |
|---|---|---|---|---|
| `ink` | `#15171B` | 21, 23, 27 | Primary text, borders, solid buttons, dark sections, hard shadows | ≈ 10% |
| `paper` | `#F4F5F1` | 244, 245, 241 | Page and screen background, text on ink | ≈ 50% |
| `white` | `#FFFFFF` | 255, 255, 255 | Raised cards (note cards, feature cards, tables, list rows) | ≈ 17% |
| `surface` | `#EDEEE8` | 237, 238, 232 | Inset or "contained" panels: settings groups, energy simulator, table headers | ≈ 7% |
| `signal` | `#3A2FF0` | 58, 47, 240 | **The single accent**: primary actions, active and on states, links, key word in headlines | ≈ 1–5% |

**Rule:** keep Signal under about 5% of any screen. Its strength comes from being rare.

### 3.2 Neutral greys

| Token | Hex | Role |
|---|---|---|
| `ink-strong-body` | `#2B2E34` | Long-form body text on the web (lead paragraphs, FAQ answers). Slightly softer than ink. |
| `slate` | `#4A4D55` | Secondary text, mono labels, captions, inactive nav links, chip text |
| `slate-app` | `#45474B` | Secondary text in the app (note previews, descriptions). Treat it as the same role as `slate`. |
| `line` | `#C6C6CB` | Hairline dividers, inactive chip borders, quiet card borders |
| `track` | `#E2E3DD` (web) / `#E8E9E3` (app) | Empty part of progress bars |
| `raised` | `#F9FAF4` | Notification cards in the app (a step lighter than paper) |
| `ink-deep` | `#0B0C0E` | Code and verification blocks inside dark sections |

### 3.3 Accent family (derived from Signal)

| Token | Hex | Role |
|---|---|---|
| `signal` | `#3A2FF0` | Default accent |
| `signal-hover` | `#2A20C9` | Hover/pressed fill for Signal buttons |
| `signal-deep` | `#1D14A0` | Offset shadow under coin and atom icons |
| `signal-light` | `#8F88FF` | **Accent on dark (ink) backgrounds.** Use this, never plain Signal, on ink. |
| `signal-mist` | `#D8D6FF` | Secondary text on a Signal background; code text on dark |
| `signal-tint-16` | `rgba(58,47,240,0.16)` | "Our side" column in the dark comparison table |
| `signal-tint-07` | `rgba(58,47,240,0.07)` | Support pill background in the nav |
| `signal-tint-06` | `rgba(58,47,240,0.06)` | Highlighted table column on light |

### 3.4 Semantic colours

| Token | Hex | Role | Source |
|---|---|---|---|
| `error` | `#BA1A1A` | Error text, danger borders, destructive icon buttons | App and web |
| `on-error` | `#FFFFFF` | Icon or text on an `error` fill | App |
| `error-container` | `#FFDAD6` | Warning/requirement boxes, "EXECUTE" button fill | App |
| `on-error-container` | `#93000A` | Text inside `error-container` boxes | App |
| `error-soft` (web) | `#FBF1F0` with border `rgba(186,26,26,0.22)` | "Bad" list items in comparisons | Web |
| `negative-on-dark` | `#FF8A80` | ✕ marks in the dark comparison table | Web |
| `energy-high` | `#EB7D00` | Energy bar fill at 80 or more (nearly full) | App and web |
| `energy-low` | `#601D49` | Energy bar fill under 10 (nearly empty) | Web simulator |
| `energy-normal` | `#3A2FF0` | Energy bar fill between 10 and 79 | Web simulator |
| `live` | `#3DDC84` | "Server is live" dot only. **Not** a general success colour. | Web popup |
| `scrim` | `#000000` at 54% | Behind bottom sheets and modals (app) | App |
| `scrim-web` | `rgba(21,23,27,0.90)` | Image lightbox backdrop (web) | Web |

> **Recommended:** the system has no success or info colour yet. For confirmations, use ink text with a Signal check icon. If a new product truly needs a success state, add one green and test its contrast first.

### 3.5 Dark sections (ink backgrounds)

These are used for emphasis blocks: the "problem" section, the download section, the energy card in the app, the recovery-phrase list and the community popup.

| Element | Value |
|---|---|
| Background | `ink #15171B` |
| Headline | `paper #F4F5F1`, accent word in `signal-light #8F88FF` |
| Eyebrow label | `#8F88FF` |
| Lead text | paper at 78% (`#C3C4C2`) |
| Muted text | paper at 60–62% (`#9B9C9B` to `#9FA1A0`) |
| Hairlines | paper at 16%; container borders paper at 30–35% |
| Inner panels | paper at 4–6% over ink |
| Code blocks | `#0B0C0E` background, `#D8D6FF` text |
| Optional glow | `radial-gradient(320px circle at 0% 0%, rgba(58,47,240,.38), transparent 65%)` (community popup only) |

### 3.6 Contrast (WCAG 2.2)

| Foreground on background | Ratio | Result | Use for |
|---|---|---|---|
| ink on paper | 16.39 | AAA | All primary text |
| ink on white | 17.94 | AAA | Card text |
| ink on surface | 15.38 | AAA | Text in inset panels |
| `#2B2E34` on paper | 12.43 | AAA | Long-form body |
| slate on paper | 7.72 | AAA | Secondary text, labels |
| slate-app `#45474B` on white | 9.31 | AAA | App previews |
| signal on paper | 6.74 | AA | Links, accent words, mono labels |
| signal on white | 7.38 | AAA | Links in cards |
| white on signal | 7.38 | AAA | Primary button text |
| `#D8D6FF` on signal | 5.27 | AA | Secondary text on Signal cards |
| paper on ink | 16.39 | AAA | Text in dark sections |
| `#8F88FF` on ink | 6.10 | AA | Accent on dark |
| paper 78% on ink | 10.25 | AAA | Lead text on dark |
| paper 60% on ink | 6.51 | AA | Muted text on dark |
| error on paper | 5.90 | AA | Error messages |
| error on `#FFDAD6` | 5.00 | AA | Warnings |
| `#93000A` on `#FFDAD6` | 7.24 | AAA | Warning box body |
| energy orange on ink | 6.38 | AA | OK as text on dark |
| plum `#601D49` on paper | 10.84 | AAA | OK as text |
| **signal on ink** | **2.43** | **Fail** | **Never use for text.** Use `#8F88FF`. |
| **energy orange on paper** | **2.57** | **Fail** | **Bar fills only, never text on light.** |
| **line on paper** | **1.55** | — | Decorative hairlines only. Any border a user must see (inputs, toggles) uses ink. |

### 3.7 Colour do's and don'ts

- **Do** put the accent on one word or phrase per headline: "Writing is free. **Energy powers sync.**"
- **Do** use white cards on paper for things the user acts on, and surface panels for groups and settings.
- **Don't** introduce a second brand accent. A sibling product may *swap* Signal for its own accent (see §15) but still uses only one.
- **Don't** use gradients for surfaces, buttons or elevation. The only gradients in the system are the community popup's corner glow and the dot-grid texture.
- **Don't** use pure black `#000` for text or backgrounds; use ink.

---

## 4. Typography

### 4.1 Typefaces

All three are free Google Fonts under the SIL Open Font License.

| Role | Family | Weights used | Always / never |
|---|---|---|---|
| **Display** | **Bebas Neue** | 400 (its only weight) | Always UPPERCASE. Headlines, screen titles, card titles, big numbers, button labels in the app. Never for paragraphs. |
| **Body** | **Hanken Grotesk** | 400, 500, 700 | Sentence case. Paragraphs, descriptions, list items, form values. |
| **Mono** | **JetBrains Mono** | 400, 500, 700 | UPPERCASE with tracking for labels; normal case for code, hashes and commands. Timestamps, counters, eyebrows, chips, tags, web buttons. |

> The app screens visually match these families. Confirm against the app's `pubspec.yaml` before handing off Flutter code.

### 4.2 Web type scale (exact)

| Style | Family | Size | Line-height | Tracking | Case | Colour |
|---|---|---|---|---|---|---|
| Hero H1 | Display | `clamp(3.2rem, 9vw, 6.2rem)` | 0.95 | 1px | UPPER | signal with ink offset shadow (`3px 3px 0 ink`) |
| Page H1 (support, legal) | Display | `clamp(2.8rem, 7vw, 5rem)` / `clamp(2.6rem, 7vw, 4rem)` | 0.95 | 0.5px | UPPER | ink + signal accent |
| Section H2 | Display | `clamp(2.4rem, 6vw, 4rem)`; phones `clamp(2rem, 8.5vw, 2.6rem)` | 0.95 | 0.5px | UPPER | ink + signal accent |
| Tagline | Display | `clamp(1.7rem, 3.6vw, 2.6rem)` | 1.0 | 0.5px | UPPER | ink + signal accent |
| Showcase H3 | Display | `clamp(1.9rem, 3.4vw, 2.6rem)` | 0.95 | — | UPPER | ink |
| Card H3 | Display | 1.6–2rem | 0.95 | — | UPPER | ink |
| Small H3 / table title | Display | 1.4–1.7rem | 0.95 | — | UPPER | ink |
| Lead | Body | 1.12rem (18px) | 1.6 | — | Sentence | `#2B2E34`, max 62ch |
| Body long-form | Body | 1.05–1.08rem | 1.7–1.75 | — | Sentence | `#2B2E34` / ink |
| Body | Body | 1rem (16px) | 1.55–1.6 | — | Sentence | ink / slate |
| Eyebrow | Mono | 0.72rem | — | 2px | UPPER | signal |
| Label / number | Mono 700 | 0.78rem | — | 1px | UPPER | signal |
| Fact label (`dt`) | Mono | 0.70rem | — | 1.2px | UPPER | slate |
| Button | Mono | 0.74rem | — | 1.4px | UPPER | per button |
| Nav link | Mono | 0.72rem | — | 1px | UPPER | slate → ink |
| Chip | Mono | 0.68rem | — | 1.4px | UPPER | ink |
| Small print | Mono | 0.70rem | 1.6 | 0.3px | Sentence | slate |
| Code | Mono | 0.9em | — | — | as is | ink on surface |

### 4.3 App type scale (measured from the screens, in sp)

| Style | Family | Size ≈ | Example |
|---|---|---|---|
| Screen title (top level) | Display | 40 sp | "NOTES", "SETTINGS" |
| Screen title (pushed, beside back button) | Display | 30 sp | "ATOMIC ENERGY", "SECURITY" |
| Hero number | Display | 44–48 sp | "110" in the energy card, "45" coins |
| Card title | Display | 22–24 sp | "MEETING NOTES: PROJECT KICKOFF" |
| Settings row title | Display | 20–22 sp | "PROFILE", "CLOUD SYNCHRONIZATION" |
| Button label | Display | 18–20 sp | "BUY ATOMIC COINS", "CONVERT" |
| Body / description | Body | 15–16 sp | Note previews, explanations |
| Section label | Mono | 12–13 sp, tracking ≈ 1.5 | "MANAGE", "DEVICE LOCK", "INBOX" |
| Timestamp / counter | Mono | 11–12 sp | "2026-08-10 16:41", "8 / 50", "1/5" |
| Chip / pill | Mono | 11–12 sp, tracking ≈ 1 | "NEWEST", "ARMED", "ON" |

### 4.4 Typography rules

1. **Split headline.** Write headlines as two short statements; the second (or its key word) goes in Signal. "Phone first. **Drive second.** Server in between."
2. **Eyebrow above every section title.** A mono, uppercase, Signal label such as `HOW IT WORKS` or `ATOMIC ENERGY · TRY IT`. Use a middle dot `·` to join parts.
3. **Numbers are monospace** wherever they can change: counters, balances, timestamps, versions, hashes. Big hero numbers may use Display.
4. **Dates use ISO format** in the UI: `2026-08-23 21:44`. Long-form prose may write "28 September 2026".
5. **Display line-height stays at 0.95–1.05.** Never loosen it; the tight stack is part of the look.
6. **Body text never goes above 75 characters per line** (`max-width: 62–70ch`).
7. **Recommended:** a minimum size of 12 px / 12 sp for any mono label. The current site goes down to about 10 px (§13).

---

## 5. Spacing, layout and grid

### 5.1 Spacing scale

The products use these steps (px on web, dp in app). Build new work on the bold values.

2 · **4** · 6 · **8** · 10 · **12** · 14 · **16** · 18 · **22** · **24** · 26 · 28 · **32** · 36 · 40 · **44** · **56** · 60 · **74**

| Use | Value |
|---|---|
| Gap inside a chip / icon-to-label | 6–9 |
| Gap between chips or tags | 8 |
| Gap between list rows / timeline items | 10–12 |
| Card padding (web) | 22 (16 on phones) |
| Card padding (app) | ≈ 16 dp |
| Grid gap between cards | 18–24 (web), ≈ 8–12 dp (app note grid) |
| Section title → content | 22–36 |
| Section vertical padding (web) | 74 desktop · 60 tablet · 46 phone |
| Screen side margin | 22 web desktop · 16 web phone · 16 dp app |

### 5.2 Web layout

| Item | Value |
|---|---|
| Content container (`.wrap`) | max 1040 px; 1100 px at ≥ 1240 px; 820 px at ≤ 980 px |
| Side gutter | 22 px; 16 px at ≤ 560 px |
| Sections | full-bleed with a 1 px `line` top border between them |
| Common grids | 2-column `1.1fr / 0.9fr` (text + visual); 3-column feature grid; 4-column steps and tiers |

**Breakpoints**

| Width | What changes |
|---|---|
| ≥ 1240 | Container grows to 1100 |
| ≤ 1100 | Nav link gap tightens to 12; the "+Coins" badge hides |
| ≤ 980 | Container 820; steps and tiers go to 2 columns; footer to 2 columns |
| ≤ 820 | Everything single column; nav becomes a burger menu; comparison table stacks |
| ≤ 560 | Phone: 16 px gutter, hero buttons full width, steps 1 column, footer 1 column |

### 5.3 App layout

| Item | Value (≈ dp) |
|---|---|
| Screen side margin | 16 |
| Top app bar content height | 56–64, followed by a **1 dp ink divider** across the full width |
| Note grid | 2 columns, staggered (masonry), 8–12 gap |
| Title row | Display title on the left, mono counter right-aligned to the title's baseline (`8 / 50`), then a 1 dp ink rule under both |
| Section label row | Mono label on the left, optional status pill or text action on the right, then a 1 dp ink rule |
| Bottom bar | Paper background, 1 dp ink top border, centred active pill |
| Touch targets | **Minimum 48 × 48 dp** (Recommended: some current chips and ✕ buttons are smaller) |

---

## 6. Shape, borders and elevation

### 6.1 Corner radius

| Token | Value | Used on |
|---|---|---|
| `radius-xs` | 3 px | Progress bars, small inner cards |
| `radius-sm` | **4 px / 4 dp** | **Default.** Buttons, inputs, app cards, app icon buttons, square popup |
| `radius-md` | 6 px | Web content cards: features, facts, steps, tiers, callouts, tables |
| `radius-brand` | 7 px | App icon tile (web nav) |
| `radius-lg` | 8 px | Gallery screenshots, blog cards, lightbox image |
| `radius-xl` | 10 px | Article cover image |
| `radius-sheet` | ≈ 28 dp (top corners only) | App bottom sheets |
| `radius-device` | 30 px outer / 24 px screen | Phone mockup frames |
| `radius-pill` | 999 px | Chips, tags, status pills, segmented toggles, support pill, FAQ toggle |

**Rule:** rectangles are nearly square (4–6). Fully round is only for pills and round icon buttons. Nothing in between (e.g. 12–16 px "soft" cards) except bottom sheets.

### 6.2 Borders

| Token | Value | Use |
|---|---|---|
| `border-hair` | 1 px `line` | Dividers between rows, quiet list items, section separators |
| `border-rule` | 1 px `ink` | Header divider, title underline, nav bottom edge |
| `border-structure` | 1.5 px `ink` | Cards, tables, chips, feature icons, FAQ list top |
| `border-control` | 2 px `ink` | Buttons, inputs, stepper buttons, segmented toggles |
| `border-selected` | 2 px `signal` | Selected card in the app, "on" settings card |
| `border-danger` | 2 px `error` | Danger-zone container, "EXECUTE" buttons (1.5 px) |
| `border-priority` | 4 px left border | Update cards: `error` critical, `energy-high` high, `signal` normal, `line` low |
| `border-dashed` | 1.5 px dashed `ink` | "Meet Atomi" mascot block |

### 6.3 Elevation: hard offset shadows

**There are no blurred shadows.** Elevation is a solid copy of the shape, offset down and right.

| Token | Value | Use |
|---|---|---|
| `shadow-1` | `2px 2px 0 ink` | Brand icon, small icon buttons in the app editor |
| `shadow-2` | `3px 3px 0 ink` | **Primary buttons**, light buttons on Signal |
| `shadow-3` | `4px 4px 0 ink` or `signal` | Compact fact sheets |
| `shadow-4` | `5px 5px 0 ink` | Feature cards, tier cards, square popup (Signal) |
| `shadow-5` | `6px 6px 0 ink` | Fact sheets, Signal callouts and notes |
| `shadow-6` | `8px 8px 0 ink` or `signal` | Quote blocks, phone mockups |

**Shadow colour rule:** use **ink** by default and **signal** to mark "the recommended/free/featured one" (e.g. the free Tachyon tier and the energy rules table). On dark backgrounds the shadow is Signal.

**Interaction with shadows**

| State | Behaviour |
|---|---|
| Hover (cards) | Move `-2px, -2px` and grow the shadow by 2 px; ink shadow turns Signal |
| Hover (buttons) | Background darkens or inverts; no lift |
| Pressed (buttons) | Move `+2px, +2px` and the shadow drops to 0, like a key being pressed |

In Flutter: `BoxShadow(color: ink, offset: Offset(3, 3), blurRadius: 0)`.

---

## 7. Iconography and imagery

### 7.1 Icons

| Context | Style |
|---|---|
| **Web** | Custom 24 × 24 line icons, 1.5 stroke, round caps and joins, no fill, ink. Placed in a 40 × 40 tile: 1.5 px ink border, 6 px radius, paper background, 7 px padding. |
| **App** | Material Symbols **Outlined** (back arrow, cloud sync, bolt, logout, copy, bell, megaphone, rocket), 20–24 dp, ink or white on ink/Signal. |
| Arrows | Use the text arrow "→" in mono for list bullets ("→ Biometric lock") and link endings ("Join the server →"). |
| Close | "✕" or "×" in a 28–30 px hit area, never smaller visually than 16 px. |

**Rule:** one icon style per surface. Never mix filled and outlined icons, and never use emoji as UI icons.

### 7.2 Icon containers in the app

| Container | Spec (≈ dp) |
|---|---|
| Back button | 36 × 36 ink square, 4 radius, white arrow |
| Primary icon action (sync) | 48 × 40 Signal fill, 1.5 ink border, 4 radius, white icon |
| Toolbar icon button (editor) | 40 × 40 ink fill (or `error` fill for delete), 4 radius, `2px 2px 0 ink` shadow, white icon |
| List item icon tile | 40 × 40, 1.5 ink border, 4 radius, paper fill, ink icon |
| Coin / energy tile | 40–56 square, paper fill, 1.5 ink border; the energy tile shows the atom with one Signal electron |

### 7.3 Texture

**Dot grid:** `radial-gradient(rgba(21,23,27,0.12) 1.2px, transparent 1.3px)` at a 22 × 22 px pitch. Use it for hero areas only, never behind body text blocks.

### 7.4 Imagery

- **Product screenshots** always sit in a phone frame: ink body, 30 px radius, 7 px bezel, 8 px camera dot, and a hard `8px 8px 0` shadow in Signal or ink. Stacked mockups rotate ±5°.
- **Avatars** are pixel-art portraits in a 1.5 dp ink-bordered square (4 radius), not circles.
- **No stock photography and no 3D renders.** Illustrations are flat, ink plus Signal.

---

## 8. Motion

| Token | Duration | Easing | Use |
|---|---|---|---|
| `motion-press` | 120 ms | ease | Button press translate |
| `motion-hover` | 150 ms | ease | Colour and background changes, card lift |
| `motion-toggle` | 200 ms | ease | FAQ "+" rotating to "×", chip state |
| `motion-enter` | 300–350 ms | ease | Panels fading in, popup slide-up (16 px), bar width changes |
| `motion-reveal` | 500 ms | ease | Scroll reveal: opacity 0→1, `translateY(16px)` → 0, once only |
| `motion-ambient` | 34 s linear loop | linear | Atom orbit rotation (decorative) |
| `motion-flourish` | 500 ms | ease | Coin flips 360° on the Y axis when the Support pill is hovered |

**Rules**

1. Motion confirms an action or reveals content. It never decorates text.
2. No springs, bounces or overshoot. Everything uses `ease` or `linear`.
3. **Always respect `prefers-reduced-motion`** (and the OS animation setting in the app). Remove transforms and keep only short opacity fades.
4. Nothing auto-plays sound or loops near reading content, except the small Atomi mascot.

---

## 9. Components

Each component lists its web spec (exact) and app spec (≈ dp) where it exists in both.

### 9.1 Buttons

| Variant | Fill | Text | Border | Shadow | Hover | Use |
|---|---|---|---|---|---|---|
| **Primary** (`btn-signal`) | signal | white | 2 ink | `3px 3px 0 ink` | press: translate 2,2 + no shadow | The one main action per view: "Download the APK", "Buy Atomic Coins", "Convert" |
| **Solid** (`btn`) | ink | paper | 2 ink | none | press: translate | Strong secondary: "Save changes", "Reload data", "Got it", "Reset" |
| **Ghost** (`btn-ghost`) | transparent | ink | 2 ink | none | inverts to ink / paper | Secondary: "See how it works", "Log out", "Learn more", "Copy phrase" |
| **Ghost on dark** | transparent | paper | 2 paper | none | inverts to paper / ink | Secondary inside dark sections |
| **Light on Signal** | paper | ink | 2 ink | `3px 3px 0 ink` | lift -1,-1, shadow +1 | The action inside a Signal callout |
| **Destructive** (app) | `error-container` | `error` | 1.5 `error` | none | — | "EXECUTE" in the danger zone |
| **Destructive icon** (app) | `error` | white icon | 2 ink | `2px 2px 0 ink` | — | Delete in the editor toolbar |
| **Text action** | none | signal, mono caps | none | — | underline | "MARK ALL READ", "OPEN ENERGY →", "APP INFO →" |

**Sizing**

| | Web | App (≈ dp) |
|---|---|---|
| Height | 46–48 px (padding 13 × 22) | 52 primary · 44 secondary |
| Label | Mono 0.74rem, 1.4px tracking, UPPER | Display 18–20 sp, UPPER (mono for small pill buttons) |
| Radius | 4 | 4 |
| Icon | 16–18, 9 px gap, leading | 18–20, leading ("+ BUY ATOMIC COINS", "⚡ CONVERT") |
| Full width | On phones in hero/CTA groups | Primary actions in sheets and cards are full width |

**Rules**

- One Primary per view. Pair it with a Ghost, never with a second Primary. The exception is equal-weight platform choices, such as "Support on Patreon" and "Support on Ko-fi".
- Labels are verbs plus objects in uppercase: "BUY ATOMIC COINS", "LOCK ON THIS DEVICE", "I WROTE IT DOWN".
- Disabled: 40% opacity, no shadow, `cursor: not-allowed`.
- Focus (Recommended everywhere): `2px solid signal` outline with a 2–3 px offset (white or `#8F88FF` on Signal or ink).

### 9.2 Chips, tags, pills and badges

| Component | Spec | States |
|---|---|---|
| **Filter chip** (app) | Height ≈ 32 dp, horizontal padding 14, pill, mono 11–12 sp caps | Off: paper fill, 1.5 `line` border, slate text. On: ink fill, paper text. |
| **Promise chip** (web hero) | Padding 7 × 11, 1.5 ink border, pill, mono 0.68rem, 1.4px tracking | Default paper; **featured** ("No AI") is ink fill with paper text |
| **Status tag** (roadmap) | Padding 4 × 10, pill, mono 0.62rem | `Next`: 1 `line` border, slate. `Now`: Signal fill, white. `Shipped`: ink fill, paper |
| **Status pill** (app) | ≈ 56 × 26 dp, pill, mono caps | Signal fill + white text for active states: "ON", "ARMED" |
| **Badge** | Padding 2 × 7, pill, mono 0.62rem, 0.8px tracking | Signal fill + white (e.g. "+COINS"); inverts on hover of its parent |
| **Notification count** | ≈ 16 dp circle, Signal fill, white mono numeral | Top-right of the bell button |
| **Unread dot** | ≈ 8 dp circle, Signal | Before an unread notification title |

### 9.3 Cards and containers

| Card | Fill | Border | Radius | Shadow | Padding | Anatomy |
|---|---|---|---|---|---|---|
| **Feature card** (web) | white | 1.5 ink | 6 | `5px 5px 0 ink`; hover lifts with Signal shadow | 22 | Icon tile + right-aligned Signal mono label → Display H3 → slate body → hairline → mono "→" bullet list |
| **Fact sheet** (web) | white | 1.5 ink | 6 | `6px 6px 0` ink or Signal | rows 12 × 16 | Definition list: mono slate label (`dt`) on the left, body 500 value (`dd`) on the right; 1 px `line` between rows; stacks on phones |
| **Signal callout** | signal | 2 ink | 6 | `6px 6px 0 ink` | 22–26 | `#D8D6FF` mono label → white Display H3 → white body → light-on-Signal button. Used for "Important" and "Early supporter reward". |
| **Quote block** | signal | 2 ink | 6 | `8px 8px 0 ink` | 26 × 24 | White Display quote 1.8–2.4rem → mono caps citation at 80% opacity |
| **Module / panel** | surface | 1 `line` | 4 | none | 24 (18 phone) | Neutral container for interactive tools, such as the energy simulator and admin KPIs |
| **Dark module** | ink | ink | 4 | none | 24 | Paper text; for the energy card and recovery phrase |
| **Update card** (web) | white | 1 `line` + 4 px left priority border | 4 | none | 24 | Mono type/priority label → Display title → slate body |
| **Tier card** | white | 1.5 ink | 6 | `5px 5px 0 ink` (Signal for the free tier) | 16 | 72 px particle icon → Display name → 1.5 ink rule → mono "30 NOTES · FREE" row |
| **Platform card** | white | 1.5 ink | 6 | `5px 5px 0 ink` | 22 | Mono tag → Display name → bullet list → Primary button pinned to the bottom |
| **Note card** (app) | white | 1 `line` | 4 | none | ≈ 16 | Mono timestamp (+ right-aligned "1/5" progress) → 1 dp ink rule → Display title (2 lines max) → body preview (4 lines, ellipsis) or checklist preview (≤ 4 items, "+1 MORE") |
| **Selected note card** (app) | white | 2 signal | 4 | none | ≈ 16 | Adds a Signal-filled checkbox at the top right |
| **Settings group card** (app) | surface | 1.5 `line` | 4 | none | ≈ 16 | E.g. session card: pixel avatar + Signal mono "SESSION / ACTIVE" + Display "SIGNED IN" + body + full-width Ghost button |
| **"On" settings card** (app) | surface | 2 signal | 4 | none | ≈ 16 | Display title + body on the left, toggle on the right |
| **Notification card** (app) | raised `#F9FAF4` | 1.5 `line` | 4 | none | ≈ 16 | Icon tile + unread dot + Display title + mono timestamp + ✕ → body → Ghost action button ("→ LEARN MORE") |
| **Energy card** (app) | ink | — | 4 | none | ≈ 24 | Mono label "ATOMIC ENERGY" (**use `#8F88FF`**) → Display "110" → mono "of 120 capacity" → energy bar → paper body text; atom tile at the top right |
| **Stat / KPI** (web admin) | surface | 1 `line` | 4 | none | 24 | Signal mono label → mono 1.9rem value → slate mono caption |

**Rules**

- White cards on paper are for content the user owns or acts on. Surface panels are for settings and tools.
- A card has **either** a hard shadow **or** a left priority border, never both.
- Card titles use Display; everything inside a card stays left-aligned.

### 9.4 Lists, tables and accordions

| Component | Spec |
|---|---|
| **Settings list row** (app) | Full-width row ≈ 56 dp, Display 20–22 sp title on the left, Signal "→" on the right, 1 dp `line` divider below. The group starts with a mono slate label and a 1 dp ink rule ("MANAGE"). |
| **Activity row** (app) | White card row, 1.5 `line` border, 4 radius: coin/atom icon → body title + mono timestamp → right-aligned mono delta ("-5 ENERGY", "+40 ENERGY" in Signal, "-1 COINS") |
| **Danger zone** (app) | Section label → `error` warning sentence → container with surface fill and 2 dp `error` border → white rows (mono caps action name + "EXECUTE" destructive button) |
| **Recovery phrase list** (app) | Ink card, rows split by paper-at-16% hairlines: small mono index (1–6) in slate + Display word in paper |
| **Steps strip** (web) | White, 1.5 ink border, 6 radius, 4 equal columns divided by 1 px `line`; each step has a Signal mono number "01", Display H3 and slate body; a 22 px Signal circle with a white "→" sits on each divider. 2 columns at ≤ 980, 1 at ≤ 560 (arrows hidden). |
| **Comparison table** (web, dark) | Two columns on ink: "them" (paper 62%, `#FF8A80` ✕) vs "us" (Signal 16% tint, 500 weight, `#8F88FF` →). Header row in mono caps; "us" header Signal-filled. Stacks to one column ≤ 820. |
| **Data table** (web, light) | White, 1.5 ink border, 6 radius; mono caps header on surface, the highlighted column header Signal-filled with its cells tinted Signal 6%; row headers bold body; 1 px `line` rows |
| **Timeline / roadmap** | White rows, 1 `line` border, 4 radius, 14 × 16 padding: Signal mono number → body title → status tag on the right |
| **FAQ accordion** | 1.5 ink top border; each item has a 1 px `line` bottom; Display question (1.35–1.8rem) + a 30 px round "+" button (1.5 ink border) that rotates 45° into a Signal-filled "×" when open; answer in body 1.05rem / 1.7, max 70ch |

### 9.5 Inputs and controls

| Control | Spec |
|---|---|
| **Text input / select / textarea** | White fill, **2 px ink** border, 4 radius, 8 × 12 padding, body font. Label above in mono caps slate ("TYPE", "PRIORITY"). Focus: Signal 2 px outline (Recommended). Error: `error` border + `error` helper text below. |
| **Toggle switch** (app) | Material 3 size ≈ 52 × 32 dp. On: Signal track, white thumb. Off (Recommended): paper track with a 2 dp ink outline and an ink thumb. |
| **Checkbox** (app) | ≈ 18–20 dp square, 2 dp radius. Off: 1.5 slate border. On: Signal fill, white check, and the item text gets strikethrough in slate. |
| **Stepper** | Two ≈ 48 dp square buttons (2 ink border, 4 radius, paper/white) around a Display number with a mono caption ("3 / COINS"). Web: 42 px. |
| **Segmented toggle** | Pill container with a 2 px ink border; mono caps segments 11 × 20 padding; the active segment is ink-filled with paper text. Full width on phones. |
| **Tabs** (web admin) | Separate pill buttons, 2 ink border, mono caps; active is ink-filled |
| **Validation message** | Body 14–15 sp in `error`, directly under the control, explaining the fix: "That would overflow your 120 cap. Use some energy first or convert fewer coins." |

### 9.6 Navigation

**Web top nav**

| Part | Spec |
|---|---|
| Bar | Sticky, 62 px tall, `rgba(244,245,241,0.86)` + 10 px backdrop blur, 1 px ink bottom border |
| Brand | App icon 28 px (7 radius, `2px 2px 0 ink`) + Display wordmark 1.5rem |
| Links | Mono 0.72rem caps, 1px tracking, slate → ink on hover; **active: ink text + 2 px Signal underline**; gap 22 (12 at 821–1100) |
| Featured link | **Support pill:** pill, 1.5 Signal border, Signal 7% fill, Signal text 600, 18 px coin icon, "+COINS" badge; fills Signal on hover and when active |
| CTA | Primary button, 9 × 16 padding |
| ≤ 820 px | Burger "☰"; links drop down full width on paper with a 1 px ink bottom border, 16 px gap |

**App navigation**

| Part | Spec (≈ dp) |
|---|---|
| Home header | Pixel avatar (36, 1.5 ink border) + mono "ATOMIC" label over Display "@HANDLE" on the left; notification bell (Ghost icon button) and Signal sync button on the right; full-width 1 dp ink divider |
| Pushed screen header | Ink back button (36) + Display title (≈ 30 sp) on the same line; full-width 1 dp ink divider |
| Bottom bar | Paper, 1 dp ink top border; **one** active destination shown as an ink pill (≈ 104 × 44, 4 radius) with icon + mono caps label in paper; other destinations are icon-only in ink |
| Floating actions | Stacked bottom-right: primary "+ NEW NOTE" (ink fill, paper mono label, ≈ 52 tall, 4 radius) with a secondary "CHECKLIST" above it (paper fill, 1.5 ink border, ≈ 40 tall) |

**Web footer:** 1 px ink top border, 40 px padding, 4 columns (`1.4fr 1fr 1fr 1fr`): Display brand + Signal mono tagline + slate small print, then link columns with mono caps slate heads. Links are ink, turning Signal on hover. The Support link is Signal 600 with a coin icon.

### 9.7 Overlays

| Overlay | Spec |
|---|---|
| **Bottom sheet** (app) | Paper fill, ≈ 28 dp top corners, ≈ 32 × 4 dp drag handle in `#45474B`, 16 dp padding, 54% black scrim. Content: Signal mono label + 1 dp ink rule → content → full-width Primary button. |
| **Info sheet** (app) | Same frame: Signal mono label ("COIN STORE") → Display 2-line headline → body → full-width Solid button ("GOT IT") |
| **Lightbox** (web) | `rgba(21,23,27,0.9)` backdrop; image with a 2 px paper border and 8 radius; 46 px paper square buttons (2 ink border, 6 radius) for close and previous/next; mono caption "Label · 3 / 15"; Esc and arrow keys work |
| **Floating promo popup** (web) | Bottom-left, 320 px wide, 12 px padding: ink frame with a Signal corner glow, 4 radius, `5px 5px 0 signal` → header (icon + Display title + mono status with a green live dot) → paper inner card (Display headline with Signal word, body) → full-bleed Signal CTA strip with a 3 px ink top border. Appears after 5 s, closes with ✕ or Esc, slides up 16 px over 350 ms. |
| **Dropdown panel** (app energy popover) | Paper, 1.5 `line` border, 4 radius; rows split by hairlines; ends with a Signal text action. *Must sit inside a Material ancestor (see §13).* |

### 9.8 Data display

| Component | Spec |
|---|---|
| **Energy bar** | Web 14 px tall, 2 ink border, 3 radius, `#E2E3DD` track. App ≈ 8 dp tall, no border, paper track on ink. **Fill colour by value: < 10 plum `#601D49`, 10–79 Signal, ≥ 80 orange `#EB7D00`.** Width animates over 350 ms. |
| **Counter** | Mono "8 / 50" right-aligned on the title baseline |
| **Hero number** | Display numeral + small mono unit: "110 / of 120 capacity", "45" |
| **Storage tiles** (app) | Two equal tiles: Display number + mono caps caption ("NOTES ON DEVICE" / "NOTES IN CLOUD"); the cloud tile gets a Signal number and a Signal border |
| **Verify block** (web dark) | Paper-35% border, 6 radius, paper-4% fill; mono caps keys; values in `#0B0C0E` code wells with `#D8D6FF` text, `word-break: break-all` |
| **Code** | Inline: mono 0.9em on surface, 2 × 6 padding, 3 radius. Block: ink background, paper text, 16 padding, 6 radius |

### 9.9 Feedback and states

| State | Treatment |
|---|---|
| **Loading** | Mono caps slate text with an ellipsis: "CHECKING SESSION…", "LOADING KPIS…". Recommended for longer waits: an ink 2 px indeterminate bar. No spinners on content. |
| **Empty** | A surface module with one plain sentence and a next step: "No active updates right now. Check back soon." |
| **Error inline** | `error` body text under the control, saying what happened and how to fix it |
| **Warning / requirement** | `error-container` box, 1.5 `line` border, 4 radius; mono caps title and body in `on-error-container` |
| **Destructive confirm** | State the effect plainly with the target and numbers: "Apply +40 energy to name@example.com? Energy stops at 120, coins at 0." |
| **Success** | Confirm in words using real numbers ("Published to 1,204 users"). Flip status pills to Signal "ON"/"ARMED". |
| **Unread** | Signal 8 dp dot before the title; read items drop the dot (no bold/regular switching) |
| **Selected** | 2 px Signal border + Signal checkbox; the header changes to "1 SELECTED" with "ALL · CANCEL · DELETE" actions (DELETE is `error` fill) |

---

## 10. Patterns (screens and page sections)

### 10.1 Web page anatomy

1. **Sticky nav** (§9.6)
2. **Hero** on paper with the dot-grid texture: eyebrow ("LOCAL-FIRST NOTES FOR ANDROID · V2.03.5") → giant Display H1 in Signal with an ink offset shadow → Display tagline split ink/Signal → lead (max 62ch) → Primary + Ghost buttons → promise chips → stack of three rotated phone mockups on the right
3. **Content sections** separated by 1 px `line` borders, each starting with an eyebrow and a split H2
4. **At most two dark sections per page**, used for the core argument (the problem) and the final call to action (download)
5. **Footer**

### 10.2 Section recipes

| Recipe | Layout |
|---|---|
| **Answer + facts** | Left: H2 + answer paragraph. Right: fact sheet with an ink shadow. |
| **Story + quote** | Left: long-form text (1.08rem / 1.75). Right: Signal quote block. |
| **Showcase** | Two phone mockups (one with a Signal shadow, the second offset 44 px down) beside "01 · LABEL" + H3 + body. Alternate sides down the page. |
| **Feature grid** | 3 × 2 feature cards |
| **How it works** | Steps strip, then a two-column row: data table + list with a measured-results fact sheet |
| **Interactive demo** | Surface module with live numbers on the left and a stack of Ghost buttons on the right, followed by a mono log |
| **Callout band** | Full-width Signal callout: icon + text + light button (e.g. Early supporter reward) |
| **Gallery** | Horizontal scroll-snap strip of 210 px screenshot cards (2 ink border, 8 radius, mono caption bar with a Signal index), opening a lightbox |
| **Roadmap + updates** | Two columns: timeline with status tags; latest update cards with a Ghost "All updates" button |

### 10.3 App screen anatomy

1. Status bar on paper
2. Header (home or pushed) with a 1 dp ink divider
3. Screen title row (top-level screens) with counter and ink rule
4. Optional filter chips
5. Content: grouped under mono section labels with ink rules
6. Bottom bar (top-level) or full-width primary action (flows)

### 10.4 Flow patterns

| Pattern | Example |
|---|---|
| **Numbered steps** | Mono "STEP 1 OF 2 / WRITE IT DOWN" label, then instructions, then a Ghost secondary action and a full-width Primary confirm |
| **Explain the cost before the action** | The energy card states the rules ("Standard sync 5, instant 10. Local notes are always free.") above the buy and convert buttons |
| **Coming soon** | An info sheet that says what's missing, why, and the alternative ("Until it ships, coins are not sold in-app. You can support the build on Patreon.") |
| **Two-key danger** | Danger actions live on a separate Developer Options screen, behind a warning sentence, each with its own Execute button |

---

## 11. Accessibility rules

1. **Contrast:** text must meet AA (4.5:1, or 3:1 for text ≥ 24 px). Use the §3.6 table. **Never put Signal text on ink, or orange text on paper.**
2. **Focus:** every interactive element shows a visible `:focus-visible` ring: 2 px Signal with a 2–3 px offset (`#8F88FF` or white on dark and Signal fills).
3. **Touch targets:** at least 48 × 48 dp in the app and 44 × 44 px on the web, even when the visual element is smaller.
4. **Don't use colour alone:** pair colour with text or shape: "ON" pills, strikethrough for done items, ✕/→ marks in comparisons, priority labels on update cards.
5. **Motion:** honour reduced-motion settings (§8).
6. **Text size:** body ≥ 16 px / 15 sp; mono labels ≥ 12 px / 12 sp (Recommended); support 200% zoom and Android font scaling without clipping. Display titles may wrap to 2 lines.
7. **Screen readers:** decorative icons are `aria-hidden`; icon-only buttons have labels ("Close", "Menu"); hidden overlays are `inert`/`aria-hidden`; uppercase is applied with CSS `text-transform`, not typed in caps, so screen readers read words normally.
8. **Borders that matter use ink.** Inputs, toggles, checkboxes and chips the user must find need at least 3:1 against their background; `line` is decorative only.

---

## 12. Content and microcopy

| Rule | Example |
|---|---|
| Short, declarative sentences; no hype words | "Notes live on your phone first." — not "Revolutionary AI-free note magic!" |
| Name the real thing and the real number | "+20 energy every 24h, up to 120." |
| Headlines: two beats, accent on the second | "Sideload it. **Keep control.**" |
| Labels: mono caps nouns | `DEVICE LOCK`, `STORAGE REPORT`, `INBOX` |
| Buttons: caps verb + object | `LOCK ON THIS DEVICE`, `COPY PHRASE`, `RELOAD DATA` |
| Destructive copy: say what happens and that it's final | "These actions run immediately and cannot be undone." |
| Explain why something is unavailable | "Atomic Coin purchases need the payment backend… Until it ships, coins are not sold in-app." |
| Consistent product nouns | Atomic Energy, Atomic Coins, Atomic Notes, Atomi. Capitalised every time. |
| Dates and times | `2026-08-23 21:44` in UI; "28 September 2026" in prose |
| Separators | Middle dot `·` between label parts, slash `/` for state pairs (`SESSION / ACTIVE`), arrow `→` for next |

---

## 13. Known inconsistencies — don't copy these

These exist in the current products. Fix them in new work, and in Atomic Notes when convenient.

| # | Issue | Where | Fix |
|---|---|---|---|
| 1 | Signal label on ink (2.43:1) | App energy card "ATOMIC ENERGY" label | Use `signal-light #8F88FF` on ink |
| 2 | Tier particle icons use their own blue ramp (`#2E2EFF`, `#6B6BFF`, `#A0A0FF`, `#D0D0FF`, `#0000CC`) instead of Signal | `public/icons/` tachyon, antimatter, monopole and strangelet SVGs | Rebuild from Signal, `signal-light`, `signal-mist` and `signal-deep` |
| 3 | Mono labels as small as 0.62rem (~10 px) | Website tags, footnotes, badges | 12 px minimum |
| 4 | Untokenised colours (`#2B2E34`, `#8F88FF`, `#D8D6FF`, many `#FFF` and rgba values) | Website CSS | Use the tokens in §14 |
| 5 | Error red is Material 3's default palette, not a brand choice | App and web | Fine to keep, but treat it as a token (`error`) and don't hand-pick new reds |
| 6 | Flutter's yellow double underline on text without a `Material` ancestor | App energy popover (mockup 13) | Wrap overlay content in `Material(type: MaterialType.transparency)` |
| 7 | No branded `:focus-visible` style on most web elements | Website | Apply §11.2 globally |
| 8 | Some app chips and ✕ buttons are smaller than 48 dp | App filter chips, checklist ✕ | Keep the visuals, enlarge the hit area |
| 9 | No dark theme | Both | If needed: ink background, `#1E2026` cards, paper text, `signal-light` accent, Signal fills keep white text |
| 10 | Admin `select`/`input` reference an undefined `--body` variable | Website admin | Use the body font token |

---

## 14. Implementation tokens

### 14.1 CSS custom properties (web)

```css
:root {
  /* core */
  --ink: #15171b;
  --paper: #f4f5f1;
  --white: #ffffff;
  --surface: #edeee8;
  --signal: #3a2ff0;

  /* neutrals */
  --text-body: #2b2e34;
  --slate: #4a4d55;
  --line: #c6c6cb;
  --track: #e2e3dd;
  --raised: #f9faf4;
  --ink-deep: #0b0c0e;

  /* accent family */
  --signal-hover: #2a20c9;
  --signal-deep: #1d14a0;
  --signal-light: #8f88ff;   /* accent on ink */
  --signal-mist: #d8d6ff;    /* secondary text on signal */

  /* semantic */
  --error: #ba1a1a;
  --on-error: #ffffff;
  --error-container: #ffdad6;
  --on-error-container: #93000a;
  --energy-high: #eb7d00;
  --energy-low: #601d49;
  --live: #3ddc84;

  /* type */
  --font-display: "Bebas Neue", sans-serif;
  --font-body: "Hanken Grotesk", system-ui, sans-serif;
  --font-mono: "JetBrains Mono", monospace;

  /* shape */
  --radius-xs: 3px;
  --radius-sm: 4px;
  --radius-md: 6px;
  --radius-lg: 8px;
  --radius-pill: 999px;

  /* borders */
  --border-hair: 1px solid var(--line);
  --border-rule: 1px solid var(--ink);
  --border-structure: 1.5px solid var(--ink);
  --border-control: 2px solid var(--ink);

  /* hard shadows */
  --shadow-1: 2px 2px 0 var(--ink);
  --shadow-2: 3px 3px 0 var(--ink);
  --shadow-4: 5px 5px 0 var(--ink);
  --shadow-5: 6px 6px 0 var(--ink);
  --shadow-6: 8px 8px 0 var(--ink);
  --shadow-signal-4: 5px 5px 0 var(--signal);

  /* motion */
  --ease: ease;
  --dur-press: 120ms;
  --dur-hover: 150ms;
  --dur-enter: 350ms;
  --dur-reveal: 500ms;
}
```

### 14.2 Tailwind (v3 config or v4 `@config`)

```ts
// tailwind.config.ts
export default {
  theme: {
    extend: {
      colors: {
        ink: "#15171B", paper: "#F4F5F1", surface: "#EDEEE8", signal: "#3A2FF0",
        "signal-light": "#8F88FF", "signal-hover": "#2A20C9", "signal-mist": "#D8D6FF",
        slate: "#4A4D55", line: "#C6C6CB", body: "#2B2E34",
        error: "#BA1A1A", "error-container": "#FFDAD6", "on-error-container": "#93000A",
        high: "#EB7D00", low: "#601D49",
      },
      fontFamily: {
        display: ["var(--font-bebas)", "sans-serif"],
        body: ["var(--font-hanken)", "system-ui", "sans-serif"],
        mono: ["var(--font-mono)", "monospace"],
      },
      borderRadius: { xs: "3px", std: "4px", md: "6px", lg: "8px" },
      boxShadow: {
        hard1: "2px 2px 0 #15171B", hard2: "3px 3px 0 #15171B", hard4: "5px 5px 0 #15171B",
        hard5: "6px 6px 0 #15171B", hard6: "8px 8px 0 #15171B", signal4: "5px 5px 0 #3A2FF0",
      },
    },
  },
};
```

### 14.3 Flutter (Material 3)

```dart
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

abstract final class AtomicColors {
  static const ink = Color(0xFF15171B);
  static const paper = Color(0xFFF4F5F1);
  static const white = Color(0xFFFFFFFF);
  static const surface = Color(0xFFEDEEE8);
  static const raised = Color(0xFFF9FAF4);
  static const signal = Color(0xFF3A2FF0);
  static const signalLight = Color(0xFF8F88FF); // accent on ink
  static const signalDeep = Color(0xFF1D14A0);
  static const slate = Color(0xFF45474B);
  static const line = Color(0xFFC6C6CB);
  static const track = Color(0xFFE8E9E3);
  static const error = Color(0xFFBA1A1A);
  static const errorContainer = Color(0xFFFFDAD6);
  static const onErrorContainer = Color(0xFF93000A);
  static const energyHigh = Color(0xFFEB7D00);
  static const energyLow = Color(0xFF601D49);
}

const atomicScheme = ColorScheme(
  brightness: Brightness.light,
  primary: AtomicColors.signal,
  onPrimary: Colors.white,
  secondary: AtomicColors.ink,
  onSecondary: AtomicColors.paper,
  error: AtomicColors.error,
  onError: Colors.white,
  errorContainer: AtomicColors.errorContainer,
  onErrorContainer: AtomicColors.onErrorContainer,
  surface: AtomicColors.paper,
  onSurface: AtomicColors.ink,
  onSurfaceVariant: AtomicColors.slate,
  surfaceContainerLowest: AtomicColors.white,
  surfaceContainerLow: AtomicColors.raised,
  surfaceContainer: AtomicColors.surface,
  outline: AtomicColors.ink,
  outlineVariant: AtomicColors.line,
  scrim: Colors.black,
);

const hardShadow = [BoxShadow(color: AtomicColors.ink, offset: Offset(3, 3), blurRadius: 0)];
final squareShape = RoundedRectangleBorder(borderRadius: BorderRadius.circular(4));

ThemeData atomicTheme() {
  final display = GoogleFonts.bebasNeue(color: AtomicColors.ink);
  final body = GoogleFonts.hankenGrotesk(color: AtomicColors.ink);
  final mono = GoogleFonts.jetBrainsMono(color: AtomicColors.slate, letterSpacing: 1.5);
  return ThemeData(
    useMaterial3: true,
    colorScheme: atomicScheme,
    scaffoldBackgroundColor: AtomicColors.paper,
    textTheme: TextTheme(
      displayLarge: display.copyWith(fontSize: 48, height: 0.95),
      headlineLarge: display.copyWith(fontSize: 40, height: 0.95),
      headlineMedium: display.copyWith(fontSize: 30, height: 0.95),
      titleLarge: display.copyWith(fontSize: 24, height: 1.0),
      titleMedium: display.copyWith(fontSize: 20, height: 1.0),
      bodyLarge: body.copyWith(fontSize: 16, height: 1.5),
      bodyMedium: body.copyWith(fontSize: 15, height: 1.5, color: AtomicColors.slate),
      labelLarge: mono.copyWith(fontSize: 13),
      labelMedium: mono.copyWith(fontSize: 12),
      labelSmall: mono.copyWith(fontSize: 12, letterSpacing: 1.0), // 12 sp minimum
    ),
    dividerTheme: const DividerThemeData(color: AtomicColors.ink, thickness: 1, space: 1),
    cardTheme: CardThemeData(
      color: AtomicColors.white, elevation: 0, margin: EdgeInsets.zero,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(4),
        side: const BorderSide(color: AtomicColors.line),
      ),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        backgroundColor: AtomicColors.signal, foregroundColor: Colors.white,
        minimumSize: const Size.fromHeight(52), shape: squareShape,
        side: const BorderSide(color: AtomicColors.ink, width: 2),
        textStyle: display.copyWith(fontSize: 20, letterSpacing: 0.6),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: AtomicColors.ink, minimumSize: const Size.fromHeight(44),
        shape: squareShape, side: const BorderSide(color: AtomicColors.ink, width: 2),
        textStyle: display.copyWith(fontSize: 18, letterSpacing: 0.6),
      ),
    ),
    chipTheme: ChipThemeData(
      shape: const StadiumBorder(side: BorderSide(color: AtomicColors.line, width: 1.5)),
      backgroundColor: AtomicColors.paper, selectedColor: AtomicColors.ink,
      labelStyle: mono.copyWith(fontSize: 12, color: AtomicColors.slate),
      secondaryLabelStyle: mono.copyWith(fontSize: 12, color: AtomicColors.paper),
      showCheckmark: false,
    ),
    switchTheme: SwitchThemeData(
      trackColor: WidgetStateProperty.resolveWith((s) =>
          s.contains(WidgetState.selected) ? AtomicColors.signal : AtomicColors.paper),
      thumbColor: WidgetStateProperty.resolveWith((s) =>
          s.contains(WidgetState.selected) ? Colors.white : AtomicColors.ink),
      trackOutlineColor: WidgetStateProperty.all(AtomicColors.ink),
    ),
    bottomSheetTheme: const BottomSheetThemeData(
      backgroundColor: AtomicColors.paper, showDragHandle: true,
      dragHandleColor: Color(0xFF45474B),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(28))),
    ),
  );
}
```

Hard shadows are not part of Material elevation. Wrap primary buttons and cards in a `Container(decoration: BoxDecoration(boxShadow: hardShadow, borderRadius: BorderRadius.circular(4)))` or a small `HardShadow` widget, and keep `elevation: 0` everywhere.

### 14.4 Design tokens JSON (W3C DTCG — for Figma Tokens Studio, Style Dictionary)

```json
{
  "color": {
    "ink":                { "$type": "color", "$value": "#15171B" },
    "paper":              { "$type": "color", "$value": "#F4F5F1" },
    "white":              { "$type": "color", "$value": "#FFFFFF" },
    "surface":            { "$type": "color", "$value": "#EDEEE8" },
    "raised":             { "$type": "color", "$value": "#F9FAF4" },
    "signal":             { "$type": "color", "$value": "#3A2FF0" },
    "signal-hover":       { "$type": "color", "$value": "#2A20C9" },
    "signal-deep":        { "$type": "color", "$value": "#1D14A0" },
    "signal-light":       { "$type": "color", "$value": "#8F88FF" },
    "signal-mist":        { "$type": "color", "$value": "#D8D6FF" },
    "text-body":          { "$type": "color", "$value": "#2B2E34" },
    "slate":              { "$type": "color", "$value": "#4A4D55" },
    "line":               { "$type": "color", "$value": "#C6C6CB" },
    "track":              { "$type": "color", "$value": "#E2E3DD" },
    "ink-deep":           { "$type": "color", "$value": "#0B0C0E" },
    "error":              { "$type": "color", "$value": "#BA1A1A" },
    "error-container":    { "$type": "color", "$value": "#FFDAD6" },
    "on-error-container": { "$type": "color", "$value": "#93000A" },
    "energy-high":        { "$type": "color", "$value": "#EB7D00" },
    "energy-low":         { "$type": "color", "$value": "#601D49" },
    "live":               { "$type": "color", "$value": "#3DDC84" }
  },
  "font": {
    "display": { "$type": "fontFamily", "$value": "Bebas Neue" },
    "body":    { "$type": "fontFamily", "$value": "Hanken Grotesk" },
    "mono":    { "$type": "fontFamily", "$value": "JetBrains Mono" }
  },
  "radius": {
    "xs":   { "$type": "dimension", "$value": "3px" },
    "sm":   { "$type": "dimension", "$value": "4px" },
    "md":   { "$type": "dimension", "$value": "6px" },
    "lg":   { "$type": "dimension", "$value": "8px" },
    "pill": { "$type": "dimension", "$value": "999px" }
  },
  "border-width": {
    "hair":      { "$type": "dimension", "$value": "1px" },
    "structure": { "$type": "dimension", "$value": "1.5px" },
    "control":   { "$type": "dimension", "$value": "2px" }
  },
  "shadow": {
    "hard-2": { "$type": "shadow", "$value": { "color": "#15171B", "offsetX": "3px", "offsetY": "3px", "blur": "0px", "spread": "0px" } },
    "hard-4": { "$type": "shadow", "$value": { "color": "#15171B", "offsetX": "5px", "offsetY": "5px", "blur": "0px", "spread": "0px" } },
    "hard-5": { "$type": "shadow", "$value": { "color": "#15171B", "offsetX": "6px", "offsetY": "6px", "blur": "0px", "spread": "0px" } },
    "signal-4": { "$type": "shadow", "$value": { "color": "#3A2FF0", "offsetX": "5px", "offsetY": "5px", "blur": "0px", "spread": "0px" } }
  },
  "duration": {
    "press":  { "$type": "duration", "$value": "120ms" },
    "hover":  { "$type": "duration", "$value": "150ms" },
    "enter":  { "$type": "duration", "$value": "350ms" },
    "reveal": { "$type": "duration", "$value": "500ms" }
  }
}
```

### 14.5 Figma setup

- **Colour styles:** `core/ink`, `core/paper`, `core/white`, `core/surface`, `core/signal`, `neutral/*`, `accent/*`, `semantic/*`. Names match the tokens above.
- **Text styles:** `display/xl` (hero), `display/l` (H2), `display/m` (card H3), `display/s` (row titles), `body/lead`, `body/default`, `body/small`, `mono/eyebrow`, `mono/label`, `mono/caption`.
- **Effect styles:** `shadow/hard-1` … `shadow/hard-6`, `shadow/signal-4`. All with **0 blur**.
- **Grids:** web 12-column on a 1040 container with a 22 gutter; app 4-column with a 16 margin and 8 gutter.
- **Components:** build variants for each §9 table with `state = default / hover / pressed / focus / disabled / selected` properties.

---

## 15. Starting a new product in the family

**Keep these the same (they are the family resemblance):**

- Ink and paper as the base, white cards, surface panels
- Bebas Neue / Hanken Grotesk / JetBrains Mono, with the same roles and casing
- 4–6 px radii, ink borders, hard offset shadows, no blur
- Eyebrow + split headline, mono labels and counters, ISO timestamps
- Header with a 1 dp ink divider, bottom bar with one ink pill, bottom sheets
- Danger zone, warning box and destructive button patterns
- Motion timings and reduced-motion behaviour

**What a sibling product may change:**

- **The accent colour.** A new product may replace Signal with its own single accent, if:
  1. it reaches **≥ 4.5:1 against paper** and **white text on it reaches ≥ 4.5:1**;
  2. you also define its `-light` variant (≥ 4.5:1 on ink), `-hover` (≈ 15% darker) and `-deep` (shadow) variants;
  3. it isn't red, orange or plum, which already mean error, high energy and low energy.
- **The mascot and icon set**, as long as they stay flat, ink-outlined and accent-filled.
- **The physics vocabulary**, which can extend to the new product's domain.

**Checklist before handing designs to development**

- [ ] Only one accent appears, and it covers less than 5% of each screen
- [ ] Every text pair passes the §3.6 contrast table
- [ ] No blurred shadows, no gradients on surfaces or buttons
- [ ] All headings and buttons uppercase via styles (not typed caps)
- [ ] Mono labels ≥ 12 px / 12 sp; touch targets ≥ 48 dp
- [ ] Focus, pressed, disabled and selected states designed for every interactive component
- [ ] Reduced-motion variant defined for every animation
- [ ] Empty, loading, error and destructive-confirm states designed for every screen

---

## 16. Quick reference card

```
COLOURS   ink #15171B · paper #F4F5F1 · white #FFFFFF · surface #EDEEE8 · signal #3A2FF0
          slate #4A4D55 · line #C6C6CB · signal-light (on ink) #8F88FF · error #BA1A1A
          error-container #FFDAD6 · energy-high #EB7D00 · energy-low #601D49
TYPE      Display: Bebas Neue, UPPERCASE, line-height 0.95
          Body: Hanken Grotesk 400/500/700, 16px, line-height 1.6
          Mono: JetBrains Mono, UPPERCASE labels, tracking 1–2px, ≥ 12px
SHAPE     radius 4 (controls/app cards) · 6 (web cards) · 999 (pills) · 28 (sheet tops)
BORDERS   1px line (dividers) · 1px ink (rules) · 1.5px ink (cards) · 2px ink (controls)
SHADOW    hard, no blur: 3px 3px 0 ink (buttons) · 5–6px (cards) · 8px (phones/quotes)
MOTION    120 press · 150 hover · 350 enter · 500 reveal · ease · respect reduced motion
LAYOUT    web container 1040 / gutter 22 (16 phone) · app margin 16dp · 4/8 grid
RULE #1   One signal. If everything is blue, nothing is.
```
