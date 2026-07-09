# Sift — Design Direction

A handoff brief for kickstarting the build. Sift is a mobile task manager organized by **urgency**, not by lists or calendar dates. This document is the design source of truth: the product idea, the principles, the full token system, the surfaces, and the implementation notes a developer needs to start.

The companion file `sift-tokens.js` in this project is the literal, runnable source of truth for tokens + sample data — every mock reads from it. Treat it as the spec for the token layer.

---

## 1. The idea

Most task apps make you file work into projects or pick a due date for everything. Sift asks one question instead: **how soon?** Tasks drop into six urgency **priorities**, and each task is tagged with a **bucket** (which part of your life it belongs to). That's the whole model.

- **Priorities** (in fixed order, most → least urgent): `asap · today · tomorrow · soon · later · one day`
- **Buckets**: `Work · Personal` (user-extensible)

A task is one line of text + one priority + one bucket. Optional time and note. That's it.

---

## 2. Principles

1. **Color does exactly two jobs.** Priority = urgency (a warm→cool temperature). Bucket = identity (a fixed hue + icon). Nothing else gets color. The canvas stays a calm neutral so these two signals never compete.
2. **Recognition over reading.** Picking a priority is picking a color you already know. Bucket is always *color + icon together*, never color alone — so it survives colorblindness, thumbnail scale, and a priority accent sitting right next to it.
3. **Capture is two taps.** Adding a task should never feel like filling a form. Title autofocuses; priority and bucket are one-tap chips, not dropdowns; everything else is optional and collapsed.
4. **Degrade by dropping detail, never by shrinking text.** When space is tight (widgets, dense boards) we remove information in a fixed order. Text never goes below a legible size.
5. **One token set; a theme is a swap.** Every surface reads from the same named tokens. Re-skinning the entire app — light, dark, bold, warm, cool — is swapping one object, not restyling components.

---

## 3. Type

| Role | Family | Notes |
|---|---|---|
| Display / headings / labels | **Bricolage Grotesque** | 600–700 weight, tight tracking (`-0.02em`). Priority names are **lowercase** (`asap`, `one day`). The wordmark is `sift` + a colored `.` in the asap accent. |
| Body / UI text | **Hanken Grotesk** | 400–600. All task text, metadata, descriptions. |
| Icons | **Material Symbols Rounded** | Rounded optical set. Use the `FILL` axis: filled (`FILL 1`) for bucket/priority glyphs, outline (`FILL 0`) for utility (schedule, notes). |
| Mono | `ui-monospace` | Times (`font-variant-numeric: tabular-nums`), token names/values, anything tabular. |

Priority icons: `asap → bolt`, `today → wb_sunny`, `tomorrow → wb_twilight`, `soon → eco`, `later → event`, `one day → bedtime`.
Bucket icons: `Work → work`, `Personal → person`.

---

## 4. Color system

Colors are authored in **oklch** and composited with **`color-mix(in oklab, …)`** so every tint stays perceptually even and re-derives correctly under any theme. Do not hand-pick tints — derive them from the base token (see §6).

### Neutrals (Paper, the default theme)
```
bg              oklch(0.967 0.006 74)   warm paper, app background
surface         oklch(0.996 0.004 80)   cards
surface-raised  oklch(1 0 0)            sheets, raised surfaces
border          oklch(0.905 0.007 74)   hairlines
border-strong   oklch(0.85 0.009 74)    grab handles, emphasized edges
text-primary    oklch(0.29 0.012 60)    task titles
text-secondary  oklch(0.52 0.012 60)    metadata
text-tertiary   oklch(0.66 0.01 60)     counts, hints
```
Whites and blacks are subtly *warm* (low chroma, hue ~60–80), never pure. Keep chroma ≤ ~0.02 on neutrals.

### Priority accents — the urgency temperature (Paper)
Warm at the top, cooling as urgency drops. This gradient *is* the information.
```
accent-asap      oklch(0.605 0.155 27)   red
accent-today     oklch(0.7 0.14 65)      amber
accent-tomorrow  oklch(0.72 0.12 95)     yellow-green
accent-soon      oklch(0.64 0.11 152)    green
accent-later     oklch(0.6 0.09 232)     blue
accent-oneday    oklch(0.57 0.1 292)     violet
```

### Bucket coding — color + icon, always paired (Paper)
```
bucket-work      oklch(0.55 0.13 255)   work
bucket-personal  oklch(0.62 0.15 8)     person
```

---

## 5. Themes

Five themes ship at launch, spanning the lightness/mood space. Each is one object listing **every** token explicitly — see `THEMES` in `sift-tokens.js`.

| Theme | Mood | Kind |
|---|---|---|
| **Paper** | calm warm light | default |
| **Slate** | calm cool dark | dark |
| **Ink** | bold high-contrast | bold (uses `boldFill` — solid pill fills) |
| **Linen** | cozy warm light | warm |
| **Cyber** | electric cool dark | cool |

Each theme also carries tuning knobs that control how much accent tints a surface, so the same derivation logic produces the right contrast in light vs dark: `headerMix`, `pillMix`, `bucketBgMix`, `accentTextMix`, plus `isDark` / `boldFill` flags and a `scrim` value. Dark themes lighten accent-text toward white; light themes darken toward black.

---

## 6. Derivation (the important part)

Components never reference raw accent colors directly. They use values **derived** from `theme + base color`. This is what keeps the system coherent across 5 themes from one set of bases. Mirror the `derive()` function in `sift-tokens.js`:

```
mix(c, p, base)  = color-mix(in oklab, c p, base)

headerBg     = mix(accent, headerMix%, surface)      // faint wash behind a priority header
pillBg       = boldFill ? accent : mix(accent, pillMix%, surface)   // the count pill
accentText   = isDark ? mix(accent, (100-accentTextMix)%, white)
                      : mix(accent, accentTextMix%, black)          // readable text/icon on the wash
bucket.bg       = mix(bucketColor, bucketBgMix%, surface)     // bucket chip background
bucket.onColor  = isDark ? mix(bucketColor, 12%, white) : bucketColor        // bucket text/icon color
overdueBg    = mix(asap, isDark?24%:13%, surface)
overdueText  = isDark ? mix(asap, 20%, white) : mix(asap, 74%, black)
```

**Build implication:** implement tokens as a theme object → derived values, exposed to the UI as CSS custom properties (`--surface`, `--text-primary`, `--accent-asap`, …) on a theme root. Swapping themes = swapping the variable set. The mocks already do this with inline `--vars`; production should centralize it (a theme provider / `:root[data-theme]` block).

---

## 7. Surfaces

Built and validated in this project (`Sift *.dc.html`):

- **A — The board** *(home)*. All six priorities as cards on the neutral canvas. Per-priority header wash, count pill, task rows. Two layouts via a top-right toggle (persisted per user): **2-column** = whole board at a glance; **1-column** = fuller rows with bucket label + time spelled out. Account avatar, bucket legend, FAB (bottom-right, neutral `text-primary` fill, `add`).
- **B — Minimize to glance**. The motion of collapsing the board back to a compact glance.
- **C — Focused priority**. Tap a priority → it becomes a full screen for that one priority. Real checkboxes, bucket as labelled chip, inline times/notes, bucket filter chips, and a quiet "Completed today" group at the bottom.
- **D — Home-screen widget**. Three sizes on a busy wallpaper. Always a near-opaque surface + hairline + soft shadow so it never fights the photo. **Large** stays in-widget for capture (flips to a stripped sheet: title, urgency, bucket) and shows full glance; **Medium/Small** show counts and an **Add task** button that *deep-links into the app's full Add sheet* (marked with an outward arrow). Degradation ladder: Large = task text → Medium = names+counts+one line → Small = counts only.
- **E — Add task sheet**. Native bottom sheet raised from the FAB (container transform). Title autofocuses; priority + bucket are one-tap chips reusing the exact board coding; time/note collapsed; primary button **names the destination** and recolors to that priority's accent ("Add to today"). Edit mode is the same sheet, pre-filled.

---

## 8. Components & patterns

- **Priority card** — `surface` bg, `border` hairline, `radius ~17px`. Header = `headerBg` wash + filled priority icon in `accentText` + lowercase name + count pill (`pillBg`/`accentText`).
- **Task row** — two densities. *Compact:* bucket icon tile (`bucket.bg` + `bucket.onColor`) + one-line title (ellipsis) + optional overdue tag / time. *Expanded:* larger bucket tile + title + meta line (bucket label · time). Overdue = small uppercase tag using `overdueBg`/`overdueText`, or an exclamation in the checkbox ring on focused view.
- **Chips** — one-tap select. Selected = accent-tinted bg + accent border + accent text; unselected = `surface` + `border` + `text-secondary`. Used for urgency, bucket, and filters.
- **FAB** — 56px, `radius 19px`, `text-primary` fill, `bg`-colored `add` glyph. Hero of the add-sheet container transform.
- **Bottom sheet** — `surface-raised`, top radius ~30px, grab handle in `border-strong`, scrim from theme `scrim`, board behind dims + blurs ~1.5px.
- **Widget tile** — near-opaque `surface` (≈93%), hairline light border, soft drop shadow + inner top highlight. Bucket dots keep hue at any scale.

---

## 9. Motion

- **Board ⇄ Focused priority (C):** shared-element. The tapped card scales/slides up to become the header band (~280ms, emphasized easing), accent wash deepens slightly, sibling priorities fade and fall away, rows cross-fade + restagger into the fuller layout. Back reverses it.
- **FAB ⇄ Add sheet (E):** container transform. The 56px FAB expands into the sheet surface (~300ms, emphasized easing), plus rotates into the close affordance, board behind dims to scrim + blur.
- **Priority change in Add sheet:** primary button bg + glow live-recolor to the selected priority accent.
- **Layout toggle (A):** background cross-fade on the segmented control (~0.18s).

---

## 10. Build notes for Claude Code

- **Token architecture first.** Port `sift-tokens.js` faithfully: `BUCKETS`, `PRIORITY_META` (order matters — it's the urgency scale), `BOARD`/sample data, `THEMES`, and `derive(theme)`. Expose derived values as CSS custom properties on a theme root; theme switch = swap the property set. Keep this portable — it mirrors how it should be built natively with named tokens.
- **Color:** oklch + `color-mix(in oklab, …)` throughout. Don't introduce hand-tuned hex tints; always derive from a base token so new themes work for free.
- **Persist:** layout choice (1/2 col) and selected theme per user.
- **Widgets:** Large size implements an in-widget add view; Medium/Small fire a deep link into the app's Add sheet. Plan the deep-link route (`sift://add`) early.
- **Accessibility:** bucket is never color-only — always render the icon too. Respect the legible-minimum rule in widgets (drop detail, don't shrink). Honor reduced-motion (skip the container transforms, keep a fade).
- **Fonts:** Bricolage Grotesque + Hanken Grotesk (Google Fonts) + Material Symbols Rounded.

---

*Default theme is Paper. When in doubt, open `sift-tokens.js` — the values there win over any number transcribed into this doc.*
