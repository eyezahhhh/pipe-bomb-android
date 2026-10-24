# Android architecture

Read when: planning a feature, moving code between layers, or changing dependencies.
State, persistence, networking, navigation, and compatibility live in
`app-infrastructure.md`.

## Context

Pipe Bomb Android is a native client for an existing Pipe Bomb server. The server
owns libraries, metadata, users, playlists, playback history, and plugin execution.
The app owns presentation, playback orchestration, local cache, and queue state.

Constraints that shape the architecture:

- No server changes are assumed. Authentication is the `auth_token` cookie.
- Metadata is plugin-defined, so the client must render arbitrary attributes.
- Audio sessions are ephemeral. Playback resolves a session before playing.
- Downloads are a future core feature, so cache identity and storage must be stable
  from day one even though v1 only caches streamed audio.
- One phone app now; tablet and Android Auto later.

## Package map

One Gradle `app` module at first. Package boundaries are the enforcement mechanism
until build times or team size justify separate modules (ADR 005). Create each package
when its first real file lands; do not commit empty placeholder directories.

| Package | Responsibility |
| ------- | -------------- |
| `app/` | `Application`, `MainActivity`, navigation host, Hilt entry points |
| `core/model/` | Serializable API models, `TrackKey`, domain enums/IDs, pure Kotlin |
| `core/net/` | Retrofit services, JSON config, `CookieJar`, base URL, error mapping |
| `core/attributes/` | `AttributeMap` serializer and `AttributeReader` |
| `core/datastore/` | Preferences DataStore: server URL, queue snapshot, settings |
| `core/design/` | Theme, colour/type/shape tokens, shared drawing primitives |
| `ui/` | App shell, navigation graph, shared Compose components |
| `data/` | Repositories: auth, catalog, library, playlists, search, history |
| `playback/` | `MediaSessionService`, resolver, queue, cache, reporter |
| `feature/` | Screen-level composables, ViewModels, per-feature UI state |

## Dependency rules

```
feature -> ui + data + playback(contract)
data    -> core/model + core/net + core/attributes + core/datastore
playback-> data + core/model + core/net + core/datastore
ui      -> core/design + core/model
core/*  -> no feature or UI dependencies
```

- Compose never calls Retrofit. Screens call ViewModel methods; ViewModels call
  repositories; repositories own suspending API calls.
- No `ExoPlayer` or `MediaSession` references in feature code. Features observe the
  playback controller's state and send commands.
- `core/model` stays free of Android framework types so it can be unit-tested and
  shared with future platform surfaces.
- Reuse API models as domain models. Do not add a mapper layer until a screen needs
  a shape the API cannot provide.
- Repositories return models or `Result<T, ApiError>`; never `Response<T>` or raw
  Retrofit types.

## Data flow

**Boot**

1. Read server URL from DataStore. If absent, show Connect.
2. `GET /setup`; if `needsSetup`, stop and instruct the user to finish setup in a browser.
3. `GET /users/me`; 401 means Login, 200 means the app shell.
4. Restore the queue snapshot and playback position.

**Browse and search**

`Screen -> ViewModel -> Repository -> Retrofit -> DTO` and back as immutable state.
Paging sources wrap paginated endpoints; batch lookups use `TrackKey` chunks of 50.

**Playback**

`TrackRow -> PlaybackController.play(trackKey, list) -> MediaSessionService ->
PlaybackResolver (audio session) -> CacheDataSource -> Progressive/HLS source`.

The queue stores `TrackKey` values only. Metadata is fetched in batches for display.
Only the current and next track need resolved media sources.

**Playback history**

`PlaybackReporter` observes playback position and reports once per track at 50% or
240 seconds, matching the web client. Client name is `Pipe Bomb Android`.

## Where to look

- App infrastructure: `./app-infrastructure.md`
- Playback: `./playback.md`
- Caching and downloads: `./caching-and-downloads.md`
- API: `./api.md`; attributes: `./attributes.md`
- Visual rules: `./design-tokens.md`, `./shapes-and-motion.md`, `./components.md`,
  `./visual-language.md`
- Web state semantics: `../../website/src/store/player.store.ts`
- Web audio engine: `../../website/src/components/player-engine/player-engine.component.tsx`
- Canonical API spec: https://github.com/Pipe-Bomb/openapi-spec (`spec.json`)
- Local snapshot: `../../tanstack-api-client/openapi/spec.json`
