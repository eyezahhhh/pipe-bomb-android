# App infrastructure

Read when: changing state holders, persistence, networking/error handling,
navigation, image loading, or compatibility behaviour. Package layout and data flow
live in `architecture.md`.

## State

- One ViewModel per destination, exposing `StateFlow<UiState>`.
- UI state is immutable; actions are explicit methods or a sealed `Event` type.
- One-off effects (snackbar, navigation) use a `Channel`, not state.
- Compose collects with `collectAsStateWithLifecycle`.
- Queue, shuffle, repeat, and now-playing state live in the playback layer, not in
  a screen ViewModel, so background playback and process recreation behave correctly.
- Keep metadata in memory for 10 minutes, mirroring the web client's `staleTime`.
  Do not keep a second unbounded cache.

## Persistence

| Data | Store |
| ---- | ----- |
| Server URL, volume, repeat/shuffle, download reserve, mobile-data downloads | Preferences DataStore |
| Selected storage location and pending switch | Small synchronous SharedPreferences bootstrap |
| Queue order, current index, position | DataStore JSON snapshot, written throttled |
| Auth cookie | OkHttp `CookieJar` backed by app-private storage |
| Last authenticated user | Small synchronous bootstrap SharedPreferences for offline launch |
| Streamed audio | Media3 `SimpleCache` in the selected app-specific directory |
| Downloads | Same `SimpleCache` + Room download metadata and Media3 download index |

The storage pointer is synchronous because `SimpleCache` must choose its directory
before asynchronous DataStore collection can start. All other settings remain in
Preferences DataStore.

Changing the server URL clears cookies, queue, download metadata, Media3 downloads,
and cache. Switching accounts keeps ordinary media cache (cache keys are not
user-scoped) and downloads, but clears user-specific DataStore state. Changing storage
location deletes all downloads and cached audio and restarts the process.

## Networking and errors

- Retrofit + OkHttp + kotlinx.serialization.
- JSON: `ignoreUnknownKeys = true`, `coerceInputValues = true`, explicit nulls off.
- Timeouts: 15s connect, 30s read. Do not retry non-idempotent requests.
- Retry idempotent GETs on network errors and 5xx with capped exponential backoff.
  Propagate `CancellationException` unchanged.
- Map failures to a sealed `ApiError`: `Unauthorized`, `Forbidden`, `NotFound`,
  `Conflict`, `Client`, `Server`, `Local`, `Network`, `Parse`.
- A 401 emits a global session-expired event; the app shell routes to Login.
- Persist the last authenticated user. If session refresh fails with `Network`, keep the
  cached user and open the app shell so downloaded audio remains playable offline.
  A 401 or other definitive auth failure still clears the session.
- Server errors use `{ error, message, statusCode }`; show `message` when present.
- Resolve relative URLs defensively, even though the server normally absolutises
  `Resource.url` and stream `baseUrl` against the request host.
- Default to HTTPS. Cleartext is permitted at the platform level because the user
  supplies the URL (self-hosted LAN servers are commonly HTTP); show a warning when
  the scheme is `http` and never hardcode HTTP endpoints.
- Manifest: `INTERNET`, `POST_NOTIFICATIONS` (runtime on Android 13+),
  `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`,
  `FOREGROUND_SERVICE_DATA_SYNC`.

## Navigation

Top-level destinations: Home, Search, Library, Settings. Detail destinations:
album, artist, track, playlist, user. The player and its queue are an overlay, not
navigation destinations.

- Use type-safe Navigation 3 routes (see `NavigationKeys.kt` and `Navigation.kt`).
- Album/artist routes support both database UUIDs and identity keys
  (`pluginId~identityId~value`), matching the web URL convention.
- The mini player sits above the bottom bar and is rendered from the same player host
  as Now Playing. Tap or drag it up to morph it into the full player; drag down to
  collapse. The host is an in-window overlay, not a dialog.
- Queue is the upward page of the player; swipe up from the player to open it.
- Media notification deep links use the same typed route builders.
- Back from a deep link lands on the relevant top-level tab, not the launcher home.

## Images

- Coil with memory and disk cache.
- `Resource.url` already contains size query parameters; add width/height when a
  component requests a smaller bitmap.
- Use the shared gradient + note fallback when no artwork exists.
- No crossfade or zoom animation; the web client does not use one.

## Compatibility

- Target API contract `0.2.0`, but tolerate drift with `ignoreUnknownKeys`.
- Unknown attribute types are skipped, not decoded.
- Missing endpoints (older server) degrade the specific feature, not the app.
- Optional fields are nullable in models even when the current server always sends them.
- Keep a single API version check surfaced in Settings for diagnostics.

## Offline mode

- Offline mode is downloads-first. Global connectivity is exposed as a StateFlow from
  `ConnectivityMonitor`.
- Home and Search use local download metadata when offline. Library defaults to the
  Downloads tab. Network-only tabs show placeholders rather than errors.
- Kept collection detail screens use ordered Room snapshots.

## Deliberate omissions

- No multi-module Gradle split until build times justify it.
- No domain/DTO mapper layer until a screen needs a distinct shape.
- No dynamic colour or light theme at launch.
- No HLS download support in v1; downloads are progressive-only until the dedicated
  server endpoint exists.
- No in-app server setup wizard in v1; link users to the web setup.
