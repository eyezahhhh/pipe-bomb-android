# Shapes and motion

Read when: choosing a corner radius, elevation treatment, transition duration, or
animation behaviour. Tokens live here; colours/type/spacing live in `design-tokens.md`.

## Shapes

| Role | Radius |
| ---- | ------ |
| Inputs, small artwork, checkboxes | 4dp |
| Buttons, cards, panels, icon-button background | 8dp |
| Hero artwork, selection container | 12dp |
| Artist portrait | 20dp in hero, 50% in grids |
| Pills/genres | 100dp |
| Progress track/thumb | full |

Use `RoundedCornerShape` tokens. Album/playlist grid art is square with 8dp; artist
grid art is circular. Buttons are 8dp, never pill-shaped except tags/genres.

## Elevation

The web is mostly flat. Compose tonal elevation lightens surfaces and breaks the
palette, so use explicit surface colours and shadows only where the web does.

| Layer | Treatment |
| ----- | --------- |
| Base | `#09090b`, no shadow |
| Surface panel | `#18181b`, 8dp radius, no shadow |
| Floating menu/dropdown/tooltip | `#27272a`, 1dp `#3f3f46` border, 8dp radius, shadow |
| Modal | `#18181b`, 1dp `#3f3f46` border, 8dp radius, scrim |
| Hero artwork | 0/8/32 black at 50% shadow |

Shadow tokens: sm `0 1 2 black 25%`, md `0 4 8 black 35%`, lg `0 10 20 black 45%`.

## Motion

| Interaction | Duration | Notes |
| ----------- | -------- | ----- |
| Press / state change | 100-150ms | Background, opacity, icon colour |
| Hover-equivalent highlight | 150ms | Pointer/TV only, not touch-only |
| Modal/sheet enter | 300ms | Gentle spring; web uses overshoot |
| Modal/sheet exit | 200ms | Faster than enter |
| Notification enter/exit | 300ms | Reveal/collapse |
| Progress changes | 150ms | Linear |
| Selection scale | 150ms | Prefer tint + check badge on touch |

- Respect reduced motion: remove overshoot, scaling, and shimmer; keep opacity changes.
- Never animate playback progress; it is data, not decoration.
- No shared-element transitions between list and detail in v1; web has none.
- Top-level tab changes use a short horizontal slide matching tab order plus a fade.
- Detail pushes slide horizontally (280ms); Now Playing and Queue slide up (300ms) and down (260ms).
- No choreographed page sequences or shared-element transitions.

## Don't

- Don't add drop shadows to flat lists or cards.
- Don't use Material elevation overlays or tonal surface colours.
- Don't use long, decorative, or looping animations.
- Don't use a different radius on one screen because it "looks better".
- Don't animate every list item in; the web does not.
