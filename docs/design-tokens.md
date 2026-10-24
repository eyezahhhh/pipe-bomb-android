# Design tokens

Read when: choosing a colour, type style, spacing value, icon, or adding a design
token. For radii/elevation/motion read `shapes-and-motion.md`. For component structure
read `components.md`.

Canonical source: `../../website/src/app/theme.css` and
`../../website/src/app/globals.scss`. If web tokens change, update this file and the
Compose tokens in the same change.

## Colour

### Raw palette

| Token | Hex | Token | Hex |
| ----- | --- | ----- | --- |
| white | `#ffffff` | accent-50 | `#e6f9f0` |
| black | `#000000` | accent-100 | `#c2f0db` |
| gray-50 | `#fafafa` | accent-200 | `#8fe6c2` |
| gray-100 | `#f4f4f5` | accent-300 | `#5cdbab` |
| gray-200 | `#e4e4e7` | accent-400 | `#2ad094` |
| gray-300 | `#d4d4d8` | accent-500 | `#1db954` |
| gray-400 | `#a1a1aa` | accent-600 | `#17a34a` |
| gray-500 | `#71717a` | accent-700 | `#12803a` |
| gray-600 | `#52525b` | accent-800 | `#0d5c2a` |
| gray-700 | `#3f3f46` | accent-900 | `#08381a` |
| gray-800 | `#27272a` | success | `#22c55e` |
| gray-900 | `#18181b` | warning | `#f59e0b` |
| gray-950 | `#09090b` | error | `#ef4444` |
| | | info | `#3b82f6` |

### Semantic mapping

| Role | Value |
| ---- | ----- |
| bg-base | `#09090b` |
| bg-surface | `#18181b` |
| bg-elevated | `#27272a` |
| bg-subtle | `#18181b` |
| bg-hover | `#27272a` |
| bg-inverse | `#ffffff` |
| fg-primary | `#fafafa` |
| fg-secondary | `#d4d4d8` |
| fg-tertiary | `#71717a` |
| fg-muted | `#52525b` |
| fg-inverse | `#18181b` |
| border-default | `#27272a` |
| border-subtle | `#18181b` |
| border-strong | `#3f3f46` |
| border-focus | `#1db954` |
| accent-primary | `#1db954` |
| accent-hover | `#2ad094` |
| accent-active | `#17a34a` |
| accent-subtle | `#1db954` at 15% |
| overlay-scrim | black at 60% |
| overlay-hover | white at 8% |
| overlay-active | white at 12% |

Component tokens:

| Role | Value |
| ---- | ----- |
| button-primary | bg `#1db954`, fg white, hover `#2ad094`, active `#17a34a`, disabled `#3f3f46` |
| button-secondary | bg `#27272a`, fg `#fafafa`, hover `#3f3f46`, active `#52525b`, disabled `#27272a` |
| button-ghost | transparent, fg `#fafafa`, hover white 8%, active white 12% |
| button-danger | bg `#ef4444`, fg white, hover 15% darker, active 30% darker |
| input | bg `#09090b`, fg `#fafafa`, border `#27272a`, placeholder `#71717a`, focus `#1db954` |
| player | bg `#18181b`, fg `#fafafa`, controls `#d4d4d8`, progress track `#3f3f46`, buffered `#71717a`, played `#1db954` |
| list | transparent, hover white 6%, active white 12%, active fg `#fafafa` |
| state-success | fg `#22c55e`, bg 15% |
| state-warning | fg `#f59e0b`, bg 15% |
| state-error | fg `#ef4444`, bg 15% |
| state-info | fg `#3b82f6`, bg 15% |

Rules:

- Dark theme only at launch. Do not enable Material You dynamic colour.
- Fill `PipeBombColors` with the semantic roles, then map closest roles into
  `ColorScheme` for Material controls. Feature code uses `PipeBombTheme.colors`.
- Accent is for primary actions, playing state, progress fill, selected tint, focus,
  and underlined links. Never use it as a large background wash.
- Selection tint: accent at 12-20%, matching the web's 20% selected state.

## Typography

- Body/UI: **Inter** (`--font-main`). Display/headings: **Outfit** (`--font-heading`).
- Bundle both fonts. Outfit comes from
  `../../website/src/font/outfit/Outfit-Variable.woff2`; do not substitute Roboto.
- Android derives sp from web rem. Do not copy pixel sizes blindly.

| Web usage | Web size | Android style |
| --------- | -------- | ------------- |
| Track artist, metadata | 0.8rem | 13sp, Inter, normal, fg-secondary |
| Secondary body / owner | 0.875-0.9rem | 14sp, Inter, normal |
| Track title, body | 1rem | 16sp, Inter, medium |
| Inputs, nav username | 1.2rem | 19sp, Inter, medium |
| Section heading | 1.5rem | 24sp, Outfit, bold |
| Welcome greeting | 2.5rem | 34sp on phones (40sp on tablets), Outfit, bold |
| Page title | 3rem | 48sp, Outfit, bold |
| Album/artist hero | clamp 1.75-4.5rem | 28-56sp, Outfit, bold, width-aware |

Weights: 500 controls, 600 emphasis, 700 headings, 800 ghost artist name only. Display
line height 1.05-1.2; body default. No all-caps tracked-out labels.

## Spacing

4dp base with the web's rhythm: `2, 4, 6, 8, 10, 12, 16, 24, 32`.

- Screen padding 16dp. List gap 8dp. Grid gap 16dp. Section gap 24-32dp.
- Button content 16dp horizontal; minimum 48dp touch height.
- Card text inset 4dp, increasing to 8dp in selected states.

## Iconography

- Primary set: **Tabler Icons**, stroke 2.5 for icon buttons.
- Filled Tabler variants for transport/play-pause.
- Reuse Lucide glyphs where web uses them (`ListStart`, `ListEnd`, `Shuffle`).
- Visual sizes 18/24/32/42dp; enforce a 48dp hit area on Android.
- Generate Compose `ImageVector`s or vector drawables from the same SVG paths. Do not
  substitute a different Material silhouette.
- Default `fg-primary`; secondary controls `fg-secondary`; active playback/shuffle/
  repeat `accent-primary`.

## Token API

Expose from `core/design`:

- `PipeBombTheme.colors` — semantic roles.
- `PipeBombTheme.typography` — text styles.
- `PipeBombTheme.spacing` — spacing scale.
- `PipeBombTheme.icons` — shared icon vectors where useful.

No feature file may define its own palette, text style, or spacing constant. If a
value is missing, add a semantic token here and in the web theme first.

## Don't

- Don't use Material defaults: purple seed, dynamic colour, stock buttons.
- Don't use pure black surfaces except `bg-base`.
- Don't put accent text on accent backgrounds; primary buttons use white text.
- Don't add ad-hoc greys, extra accent hues, or light-theme fallbacks.
