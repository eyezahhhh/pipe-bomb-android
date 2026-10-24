# Conventions

Read when: writing Kotlin/Compose code, adding a package, or reviewing a change.
Imitate the existing code once the scaffold exists; this file defines the target.

## Language and layout

- Kotlin, 4-space indent, trailing commas, expression bodies for small functions.
- Package root: `net.pipebomb.android`.
- Package by responsibility, not by Android layer name alone:
  `core/`, `data/`, `playback/`, `feature/<name>/`.
- `ktlint` + Android Lint are the style gate. Do not reformat unrelated files.
- No wildcard imports. Order: Android, third-party, project.
- Prefer `val` and immutable data. Mark state holders `@Stable`/`@Immutable` only when
  verified, not by default.

## Naming

- Composables: `PascalCase` nouns (`TrackRow`, `AlbumCard`), not `...Component`.
- ViewModels: `<Screen>ViewModel`, state `<Screen>UiState`.
- Repositories: `<Domain>Repository` with an interface only when there is more than
  one implementation or it crosses a module boundary.
- API DTOs keep server names (`Track`, `Album`, `Playlist`) in `core/model`.
- Value types for identity: `TrackKey`, `AlbumRef`, `ArtistRef`, `PlaylistId`.
- Boolean properties read as predicates: `isPlaying`, `hasArtwork`, `canSave`.
- Use `Screen` suffix only for route-level composables.

## Compose

- Composables are stateless by default; hoist state to the ViewModel.
- Route composables collect state and pass plain data + lambdas down. No ViewModel
  parameters below the route level.
- No business logic, API calls, or `Context` lookups in composables.
- Use `remember` only for UI-local state (expanded, scroll), never for server data.
- Provide `@Preview` for shared components and non-trivial screens with representative
  states (loaded/empty/error/playing).
- Use `LazyColumn`/`LazyVerticalGrid` with stable keys. Never use `Column` for long
  lists.
- Use `Modifier` as the first optional parameter; never nest scrollables that scroll
  in the same direction.
- Apply design tokens from `PipeBombTheme`; no inline `Color`, `dp`, or `sp` for
  system values outside `core/design`.
- Strings in `strings.xml`; no hardcoded user-facing text in composables.
- Icon-only buttons require content descriptions.

## ViewModels and state

- Expose `StateFlow<UiState>`; build state with `stateIn(viewModelScope, ...)`.
- UI state is immutable and exhaustive. Use sealed interfaces for mutually exclusive
  screen states, not a bag of nullable fields.
- One-off effects use `Channel<UiEffect>` exposed as `receiveAsFlow()`.
- ViewModel actions are explicit methods; no raw setters for individual fields.
- Never expose `Throwable`, `Response<T>`, or DTO wrappers to the UI. Map to
  `ApiError` and user-safe messages.
- `viewModelScope` only; playback work belongs to the service/application scope.

## Repositories and API

- Repositories own suspend calls and return domain models or `Result<T, ApiError>`.
- No Retrofit `Response<T>` above `data/`.
- Use `withContext(ioDispatcher)` for blocking work; Retrofit suspends already.
- Batch track lookups in chunks of 50 and deduplicate keys.
- Cache metadata in memory for 10 minutes; do not duplicate the web's query cache
  with an unbounded map.
- Never log request bodies, cookies, or auth headers.

## Attributes

- Access attributes only through `AttributeReader`. No `attributes["title"]` in UI.
- Standard fallback keys are listed in `attributes.md`; use them as fallbacks,
  not as a required schema.
- Unknown attribute types must be skipped, not thrown.
- Buffer attributes always produce `Resource`; render via the shared image component.

## Playback

- Features talk to `PlaybackController`, never `ExoPlayer` or `MediaSession`.
- Queue entries are `TrackKey`, never URLs or sessions.
- All media goes through `CacheDataSource`; no second player path.
- Session resolution belongs to `PlaybackResolver`; no ad-hoc audio-session calls.

## Persistence

- Preferences DataStore for settings/queue. No SharedPreferences unless wrapping
  cookies in the CookieJar.
- Serialize queue snapshots with kotlinx.serialization; version the schema.
- Clearing the server or account must clear all dependent state; centralise this in
  one `SessionManager`/`AppStateReset` function.

## Errors and offline

- Model errors as `ApiError`; show `message` for server errors when present.
- 401 is global: emit once, route to Login, clear session. Do not toast per request.
- 404 in a list means omit/skip, not crash.
- Offline: keep cached content, show an offline banner, disable write actions.
- Retry only idempotent requests; no automatic POST retries.

## Tests

- Unit-test pure logic: attribute reader, key factory, queue reducers, URL builder,
  reporter threshold, error mapping.
- Use MockWebServer for auth-cookie, error, and paging contracts.
- Instrument playback against a local server for both `stream` and `hls`.
- Name tests by behaviour: `shuffle preserves original order`, not `testShuffle`.
- No sleeps in tests; use Turbine/Flow test utilities and idling resources.

## Logging

- Use a thin logger; debug builds may log feature events, release builds do not.
- Never log cookies, `Authorization`, server credentials, or full URLs with tokens.
- Playback errors include track key and type, not the resolved URL.

## Git and changes

- Keep UI, tokens, and docs in sync in the same change.
- A new shared component needs a preview and a row in `components.md` if it maps to a
  web component.
- A new design token needs a matching entry in `design-tokens.md` or
  `shapes-and-motion.md`, and in the web theme.
- Do not add a dependency without checking it is needed for the current milestone.
- Never commit secrets, local server URLs with credentials, or generated build output.

## Don't

- Don't introduce a domain/DTO mapper layer for every model.
- Don't split into Gradle modules before build times justify it.
- Don't add Room, WorkManager, or download code before the downloads milestone.
- Don't use Material defaults that fight the web's visual language.
- Don't create a second design system for a single screen.
