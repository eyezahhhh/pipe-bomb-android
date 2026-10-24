# Component library mapping

Read when: building or restyling a UI component, or looking for the web reference for
an Android control. Colour/type/spacing values live in `design-tokens.md`; radii,
elevation, and motion live in `shapes-and-motion.md`.

The web component library is a set of small CSS-module components under
`../../website/src/components/<name>/`. Reuse its **visual result**, not its DOM or
interaction mechanics. Touch needs larger hit areas, sheets instead of popovers, and
long-press instead of right-click.

## Shared primitives

| Component | Web reference | Web spec | Android implementation |
| --------- | ------------- | -------- | ---------------------- |
| Button | `button/` | 8dp radius, 4x8px padding, primary/secondary/ghost/danger, focus ring, loading hides label | `PipeBombButton`: 40-48dp height, 16dp horizontal padding, same variants; spinner centered when loading |
| Icon button | `icon-button/` | 24/32/42/56dp sizes, simple/background/ghost, round, 2.5 stroke, loading ring, press expands background | `PipeBombIconButton`: keep visual size, 48dp minimum hit target, no ripple beyond a subtle press tint |
| Text input | `text-input/` | bg base, 1dp default border, 4dp radius, 1.2rem text, 2dp accent focus | `PipeBombTextField`: 48-56dp height, same colours, accent focus border, error state uses state-error |
| Checkbox | `checkbox/` | 24dp, 4dp radius, accent when checked, tick drops in | Material-free custom checkbox; 48dp hit target |
| Tabs | `tabs/` | wrapping row, 8dp gap, tab buttons use primary/secondary button styles | Segmented row of `PipeBombButton`s or custom tab row; never underline-only Material tabs |
| Dropdown | `dropdown/` | elevated surface, 8dp radius, strong border, options 2x8px, hover list-hover | `ExposedDropdownMenu` restyled with elevated surface/strong border; or bottom sheet on small screens |
| Modal | `modal/` | centered, surface bg, 8dp radius, strong border, 8px vertical padding, 24x32 content, scrim 50-60%, 300ms open/200ms close, close top-right | `Dialog` for confirmations/forms; full-screen or large sheet for long content; same surface/border/radius |
| Context menu | `context-menu/` | elevated bg, 8dp radius, md shadow, strong border, rows 8x12px at 0.9rem | Long-press bottom sheet (primary); compact `DropdownMenu` only for pointer/TV |
| Tooltip | `tooltip/` | elevated bg, 8dp radius, 8px padding, strong border | Skip on touch; use content descriptions and helper text |
| Notification | `notification/`, `notification-list/` | 240px white inverse toast, 4dp radius, spinner, auto-dismiss accent progress bar, bottom-right above player | Snackbar/toast overlay above mini player; inverse surface, 4dp radius, accent progress line. Do not use Material's default snackbar colours |
| Progress track | `progress-track/` | 30px hit area, 6px visual track, 5dp radius, 20dp thumb, accent fill, buffered shimmer | Custom slider: 32-40dp height, 4-6dp track, 16-20dp thumb, accent played/buffered colours |
| Progress bar | `progress-bar/` | 10px track, 5dp radius, accent fill, loading segment | Linear progress with 4-6dp height, same colours; indeterminate uses buffered colour |
| Spinner | `spinner/` | 16/24/32/48 sizes, 2/3/5/7 border, accent or dark, 0.7s spin | Circular progress indicator with matching sizes/colours; no Material default track colour |
| Album art fallback | `album-art-fallback/` | 135deg `#323232 -> #242424`, music-note glyph, accent at 30% | Shared `AlbumArtFallback` composable; note glyph from the same icon set |
| Resource image | `resource-image/` | square, resource URL with optional `width`/`height` query, fallback on error | Coil `AsyncImage` with size request, 4dp row / 8dp grid radius, same fallback |
| Bookmark button | `bookmark-button/`, `track-bookmark-button/` | outline/filled bookmark, loading state | `PipeBombIconButton` with filled state in fg-primary; do not tint it accent |
| Paginator | `paginator/` | page controls | Prefer infinite scroll; use explicit paginator only where the web does |

## Rows, cards, and lists

| Component | Web reference | Web spec | Android implementation |
| --------- | ------------- | -------- | ---------------------- |
| Track row | `list-track/` | 54px, bg surface, 8dp radius, 42dp art, 42dp number column, title 1rem, artist 0.8rem, hover bg-hover, playing/active state | `TrackRow`: 56-64dp, same art/title hierarchy, number swaps to play/pause on press, overflow via long-press/ellipsis |
| Track list | `track-list/`, `list/` | column, 8dp gap, virtualised on web | `LazyColumn` with stable keys (`TrackKey`); 8dp gap |
| Queue row | `queue-track/` | 56px, 48dp art, drag handle, past items 50% opacity | `QueueRow` in a `LazyColumn` with drag-to-reorder; handle always visible in edit mode |
| Queue list | `queue-list/` | sidebar, drag reorder, virtualised | Bottom sheet from mini player; drag handle + long-press reorder |
| Album card | `grid-album/` | 180px card, 180px square art, 8dp radius, bold title, 0.9rem artists, selection accent 20% + art 160/180 | `MediaCard`: adaptive grid min 140-160dp, square art 8dp, 2-line title, 1-line artist; selection tint + check badge |
| Artist card | `grid-artist/` | 180px card, 160px circular art, 20dp radius fallback, centered bold name, selection tint | `ArtistCard`: circular art, centered name, same fallbacks |
| Playlist card | `grid-playlist/` | album-like card, square art, title, owner line | `PlaylistCard`: same as album card with owner subtitle |
| Grid | `grid/` | flex wrap, 16dp gap | `LazyVerticalGrid` adaptive, 16dp gap |
| Horizontal shelf | `horizontal-scroller/`, `*-scroller/` | heading 1.5rem Outfit bold, 16dp gaps, chevrons + grid toggle | `LazyRow` with `SectionHeader`; no chevrons, optional "See all" |
| Section header | page headings | 1.5rem Outfit bold | `SectionHeader` (24sp) with optional action |

## App chrome

| Component | Web reference | Web spec | Android implementation |
| --------- | ------------- | -------- | ---------------------- |
| Top bar | `top-bar/` | 60px, logo 42px, full-width search, user menu, selection mode replaces search | `TopAppBar`: 56-64dp, logo/brand, search icon, avatar; contextual top bar for selection |
| Sidebar navigation | `navbar/`, `navbar-link/`, `navbar-section/` | surface panel inset 8px, 8dp radius, resizable 50-400px, sections, 30s playlist refresh | `NavigationBar` on phones; `NavigationRail` on tablets/foldables. Use the same surface panel styling |
| Queue sidebar | `sidebar/` | surface panel, header "Up Next", clear button, virtualised queue | Queue bottom sheet; header + clear action |
| Player bar | `player/` | 90px, surface bg, 72dp art, center transport, progress + times, bookmark, volume, queue toggle | Mini player 64-72dp above bottom nav; full Now Playing route; no volume slider on phone (system volume) |
| Now playing row | player left | 72dp art, 4dp radius, title + artist | `NowPlayingBar` and full player header with the same hierarchy |
| Login | `login/` | centered 300px form, 8dp gaps, primary + ghost buttons | Edge-to-edge centered column, 16dp padding, same field/button styles |
| Welcome banner | `welcome-banner/` | 36px top padding, 2.5rem Outfit greeting, accent username, radial accent glow | Home header with 24-32dp padding, same greeting logic (morning/afternoon/evening) |
| Settings layout | `settings/` | system/user sidebars, list entries | Settings list with grouped sections; server/user split preserved |

## Detail headers

| Component | Web reference | Web spec | Android implementation |
| --------- | ------------- | -------- | ---------------------- |
| Album hero | `album/[albumId]/` page | 400px, blurred art + gradient, bottom-aligned 180dp art, clamp title, meta, actions | Collapsing header: blurred art/gradient, 160-200dp art, 28-40sp title, action row |
| Artist hero | `artist/[artistId]/` page | 450px, ghost name at 8% opacity, 130dp thumb with 20dp radius, genres as pills, actions | Collapsing header with ghost name optional; circular/rounded portrait, pills, actions |
| Playlist header | `playlist/[playlistUuid]/` page | 240dp art, 3rem Outfit title, owner/visibility, actions | Header with 160-200dp art, 28-40sp title, owner, visibility/action row |
| Action row | `track-buttons/`, `album-buttons/`, `playlist-buttons/` | large play button 56dp, shuffle, list-start, list-end, bookmark, more | Primary play 56dp + 48dp secondary icon buttons; overflow for less-used actions |

## States

| State | Web reference | Android |
| ----- | ------------- | ------- |
| Loading list | `list-track-skeleton`, `queue-track-skeleton` | Shimmer/skeleton rows; no spinner for full lists |
| Loading page | `spinner/` | Centered spinner only for first load |
| Empty | search "No results" | Centered message + action; match typography |
| Error | inline text/notifications | Inline retry card for page errors; snackbar for transient actions |
| Selection | accent 20% tint + check badge | Same tint; check badge top-right of art; contextual top bar |
| Playing | row active, play icon replaces number | Accent-tinted row + pause icon; current track indicated everywhere |

## Component rules

- Build one shared implementation per control (`PipeBombButton`, `PipeBombIconButton`,
  `PipeBombTextField`, `TrackRow`, `MediaCard`, `SectionHeader`, `EmptyState`) and use
  it everywhere. No per-screen copies.
- Keep the web's visual sizes for artwork and icon glyphs, but never below a 48dp
  touch target.
- Prefer bottom sheets over desktop popovers. Prefer long-press over right-click.
- Use the same icon silhouettes as web (Tabler/Lucide). Do not silently substitute
  Material icons.
- Web hover states become Android press states. Do not leave hover-only affordances
  invisible on touch; make the action discoverable via overflow.
- Do not add Material-only patterns (FAB, navigation drawer, top app bar with large
  collapsing title) unless they preserve the visual language above.

## Where to look

- Web components: `../../website/src/components/`
- Tokens: `./design-tokens.md`, `./shapes-and-motion.md`
- Screen patterns: `./visual-language.md`
