# Visual language

Read when: designing a screen, adapting a desktop pattern to touch, or reviewing a
UI change for consistency with `../../website`.

## Brand

- The Pipe Bomb mark is fixed. Use the asset from `../../website/public/logo.png` for
  the launcher/adaptive icon and in-app brand moments. Do not redraw it.
- Mood: dark, quiet, dense, premium. Artwork is the loudest element on screen.
- The accent is `#1db954`. It signals action and playback, not decoration.
- No marketing gradients, glassmorphism, glow effects, or animated backgrounds.
  The only gradients are the artwork fallback, hero scrims, and the greeting glow.

## App shell adaptation

The web is a desktop shell: top bar, left navigation, central content, right queue,
bottom player. Android must not copy the arrangement, only the visual language.

```
Phone                          Tablet / foldable
┌──────────────────────┐       ┌───────┬─────────────────────────┐
│ Top app bar          │       │ Rail  │ Top bar                 │
│                      │       │       ├─────────────────────────┤
│ Content              │       │       │ Content (list + detail) │
│                      │       │       │                         │
│ Mini player          │       │       │                         │
│ Bottom nav           │       │       │ Mini player             │
└──────────────────────┘       └───────┴─────────────────────────┘
```

- Phone bottom navigation: Home, Search, Library, Settings.
- Tablet/foldable: navigation rail, optional two-pane list/detail.
- Mini player persists above the bottom bar on every top-level screen. Hide it only
  on full-screen Now Playing and immersive auth/connect flows.
- Now Playing is a full-screen destination or large bottom sheet; queue is a bottom
  sheet opened from it.
- Edge-to-edge with system bar insets; the base background extends behind bars.
- Use a 16dp content inset. Grid gaps 16dp, list gaps 3dp.

## Screen archetypes

**Connect / Login**
Centered column, max ~360dp, 8dp gaps, primary action. Server URL field first, then
credentials. Show `needsSetup` as an explanatory state with a browser link, not a fake
wizard. Same input/button styling as web login.

**Home**
Greeting header (time-of-day + accent username), then horizontal shelves for playlists,
recent albums/artists, and recent tracks. The web uses a radial accent glow behind the
greeting; a subtle equivalent is allowed. Do not add a hero carousel.

**Search**
Search field in the top bar. Empty state shows source picker (local + ephemeral
plugins). Results: top tracks first, then artist shelf, album shelf, remaining tracks.
Keep section order from `search-results`.

**Library**
Tabs for Saved tracks/albums/artists, then playlists, libraries, history. Use
`SectionHeader` and the same row/card components. Saved lists paginate; do not load all.

**Detail (album/artist/playlist/track)**
Collapsing header with artwork treatment, title in Outfit, metadata, primary play
action, then content. Album: no artwork in track rows (web uses `noArt`). Artist: top
tracks + album shelf. Playlist: owner, visibility, track list with reorder.

**Player**
Mini player: horizontal queue-track pager for art/title/artist, with fixed play/pause.
Full player: full-width horizontal queue-track pager with neighbouring cover pages,
large art, title, artist, seek, transport, shuffle/repeat, save, queue. Keep the web's
control order: shuffle, previous, play/pause, next, repeat.
- Swipe horizontally anywhere in the player pager to change queue tracks; vertical drag
  dismisses the sheet. Do not rotate, arc, or scale the artwork during the swipe.
No fake volume slider on phones; hardware/system volume owns it.

**Queue**
"Up Next" header with clear action, current track highlighted, past items dimmed to
50%, drag handles in edit mode. Swipe-to-remove only if it does not conflict with
reorder; prefer an explicit edit mode.

**Settings**
Grouped list: Account, Server, Playback, Storage, About. Server URL and diagnostics
live here. Owner-only server settings remain on web for v1.

## Interaction translation

| Web | Android |
| --- | ------- |
| Hover reveals play button | Press/playing state reveals; row tap plays, overflow stays visible |
| Right-click context menu | Long-press bottom sheet |
| Shift-click multi-select | Long-press enters selection; tap toggles; contextual top bar |
| Drag-to-reorder with pointer | Explicit drag handle; haptic on pickup |
| Tooltip on hover | Content description; no tooltip on touch |
| Centered modal | Dialog for short content; full-screen/sheet for long content |
| Horizontal scroller chevrons | Swipe; optional "See all" |
| Keyboard shortcuts | Optional hardware-keyboard support; never the only path |
| Notification toast bottom-right | Snackbar above mini player, inverse surface |

## Artwork

- Album/playlist art: square, 8dp radius. Artist art: circular in grids, 20dp radius
  in hero.
- Heroes: blurred, slightly scaled, darkened artwork under a vertical scrim to
  `bg-surface`; title in white with a soft text shadow.
- No artwork: 135deg `#323232 -> #242424` gradient with an accent music note at 30%.
  Artist: circular gradient with a user glyph.
- Never stretch or crop inconsistently; Coil center-crop.
- Do not add colored placeholder blocks or random gradients per entity.

## Motion

- State changes 100-150ms; dialogs/sheets 200-300ms.
- Gentle spring only for sheets/dialogs and selection, never for lists.
- Artwork selection may shrink slightly, but prefer tint + check badge on touch.
- Loading uses skeletons for lists and a ring spinner for first paint.
- Respect reduced motion: remove overshoot, scaling, and shimmer; keep opacity changes.
- No page transition choreography in v1; standard navigation transitions only.

## Density and touch

- Web track rows are 54px; Android rows 56-64dp. Queue rows 56-64dp.
- Artwork in rows 42-48dp; grid cards 140-180dp.
- Minimum 48dp touch target, 8dp between adjacent targets.
- Text truncation: track/album titles 1 line in rows, 2 lines on cards; artists 1 line.
- Use `maxLines` + ellipsis; never let dynamic attribute text break layout.
- Lists should show ~6-8 tracks per phone screen; do not inflate row height for airiness.

## Accessibility

- Content descriptions for every icon-only control; use action names ("Play", "Save").
- Playing state announced, not colour-only.
- Contrast: fg-primary/secondary on base/surface pass AA at body sizes; never use
  fg-muted for essential text.
- Support font scale to 200%; rows grow vertically, text never clips.
- Focus order follows visual order; hardware keyboard focus rings use accent.
- Dynamic content (buffering, errors, track changes) announced politely.

## Consistency checklist

Before merging a UI change:

- [ ] Colours come from `PipeBombTheme.colors`; no raw hex.
- [ ] Type uses Outfit for display/headings and Inter for UI/body.
- [ ] 8dp radius for controls/cards, 4dp for inputs/small art, 12dp for hero art.
- [ ] Surfaces use the `#09090b -> #18181b -> #27272a` hierarchy, not shadows.
- [ ] Accent is used only for primary action/playing/progress/selection/focus.
- [ ] Artwork uses the shared image component and gradient fallback.
- [ ] Icons match Tabler/Lucide silhouettes used on web.
- [ ] Touch targets >= 48dp; no hover-only actions.
- [ ] Long-press opens the context sheet; selection has a contextual top bar.
- [ ] Mini player is present above bottom navigation except on Now Playing.
- [ ] Empty/loading/error states exist and use shared components.
- [ ] Reduced-motion and font-scale behaviour checked.
- [ ] No stock Material purple, dynamic colour, FAB, or navigation drawer.

## Anti-patterns

- Copying the desktop three-column shell onto a phone.
- Material defaults: dynamic colour, tonal elevation, stock M3 buttons, FABs.
- Light theme, OLED pure-black surfaces, large green headers, green-tinted cards.
- Carousels, auto-playing heroes, decorative animation, generic colour tiles.
- Hover/right-click-only actions, or a different icon family for parity actions.
