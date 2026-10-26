# Playback and queue

Read when: touching audio playback, the queue, media notification, background
service, or playback reporting. For caching, offline playback, and downloads read
`caching-and-downloads.md`.

## Core model

| Type | Meaning |
| ---- | ------- |
| `TrackKey` | `pluginId + libraryId + trackId`, the only stable media identity |
| `PlaybackQueue` | Ordered `TrackKey` list, current index, shuffle/repeat state |
| `ResolvedMedia` | `trackKey` + resolved URL + session id + `stream`/`hls` + download snapshot |

- The queue never stores URLs, sessions, or resolved media. Every queue item uses a
  synthetic `pipebomb://track/<base64 TrackKey>` URI.
- A queue may contain hundreds of metadata-only items; none creates a session during
  queue construction.
- `LazyTrackMediaSource` resolves a track when ExoPlayer prepares that item.
- A session failure is recoverable: resolve again and retry once, then surface an error.
- Changing server or account clears resolved media and queue state.

## Session resolution

`GET /tracks/{pluginId}/{libraryId}/{trackId}/audio` returns:

```json
{ "id": "...", "baseUrl": "https://host/streaming/<id>", "type": "stream" }
```

or `type: "hls"`. The server owns the instance and may evict it at any time. Resolve
immediately before preparing a track; do not resolve the whole queue.

`PlaybackResolver`:

```kotlin
suspend fun resolve(trackKey: TrackKey, forceRefresh: Boolean = false): ResolvedMedia
```

- Keep a per-track in-memory session cache for 10 minutes.
- Deduplicate concurrent resolves per key with an in-flight map.
- Before creating a session, return a completed download from Room. Register its
  persisted session id and use the saved media URL so offline playback works.
- If a download exists but its storage is unavailable, fail with a local error instead
  of silently streaming from a different location.
- On 404/410 from the media URL, invalidate the session and resolve once more.
- Resolve `baseUrl` against the configured server URL, then append `/stream` or
  `/hls/playlist.m3u8`.
- Streaming endpoints are `OptionalAuth` on the server, but send the cookie anyway.

## Media3 graph

```
PlaybackController (app layer)
  -> MediaSessionService (foreground, media notification)
     -> ExoPlayer
        -> LazyTrackMediaSourceFactory
           -> LazyTrackMediaSource (per synthetic item)
              -> PlaybackResolver on prepare
              -> CacheDataSource.Factory
                 -> SimpleCache (single app-wide instance)
                 -> ProgressiveMediaSource | HlsMediaSource
```

Modules: `media3-exoplayer`, `media3-exoplayer-hls`, `media3-datasource`
(contains `CacheDataSource`/`SimpleCache`), `media3-session`, `media3-database`.

Android Auto uses the same player through `MediaLibraryService`; browse-tree and queue
bridging details live in `android-auto.md`.

- `MediaSessionService` owns the player and publishes the notification.
- `PlaybackController` is the only API features see: commands in, `StateFlow` out.
- Audio attributes use `handleAudioFocus = true`; enable
  `setHandleAudioBecomingNoisy(true)`.
- Provide a media session callback for headset/Bluetooth transport controls.
- Cache and data-source construction are defined in `caching-and-downloads.md`.

## Queue semantics

Port the web behaviour from `../../website/src/store/player.store.ts`:

- Repeat modes: `off`, `all`, `one`.
- Shuffle preserves original order; disabling restores it while keeping the current
  track at its original position.
- `playNow` replaces the queue; `playNext` inserts after current; `addToEnd` appends.
- Removing the current item selects the next item or stops when empty.
- Reordering adjusts the current index correctly.
- Previous restarts the track when position > 3s, otherwise goes to the previous track.
- Persist queue, index, position, shuffle, repeat, and `originalQueue` on a throttle.
  Restore paused on launch; do not autoplay.
- Queue, pager, and current-track metadata resolve local-first: downloaded row metadata is
  applied immediately, then remote catalog metadata refreshes when online.
- Queue entries without local metadata do not show an indefinite `Loading…` offline;
  they show `Unavailable offline`.

## Preload and gapless

The web uses two audio elements and preloads the next track 10 seconds before the end.
Media3 should reproduce the result, not the mechanism:

- Queue items remain synthetic; ExoPlayer asks `LazyTrackMediaSource` to resolve an
  item when that item is prepared.
- Use Media3 preloading (`PreloadMediaSource` or built-in next-item buffering) for a
  gapless handoff.
- If an item resolve fails, keep the queue intact and show a non-blocking error; retry
  once when preparation resumes.
- Never resolve the entire queue up front.

## Playback reporting

`PlaybackReporter` observes position/duration and reports once per track at 50% of
duration or 240 seconds, whichever is lower. Payload:

```json
{ "pluginId": "...", "libraryId": "...", "trackId": "...", "clientName": "Pipe Bomb Android" }
```

`POST /playback-history/me`. Failures are non-fatal; retry later with WorkManager.

## Notification and background

- Media3 default media notification with artwork, title, artist, play/pause,
  previous/next, and seek.
- Load artwork with Coil into a bitmap; fall back to the gradient placeholder.
- Foreground service type `mediaPlayback`.
- Stop the service when the queue is empty and playback is idle, following Media3
  lifecycle guidance.
- No polling loops in the service; request metadata on demand.
- Playback must survive screen-off, backgrounding, audio focus loss, and process
  recreation of the UI.

## Failure handling

| Failure | Behaviour |
| ------- | --------- |
| Session resolve 404 | Refresh session, retry once, then show "Unable to play" |
| Session resolve failure | Publish a failing media period; ExoPlayer reports the error without crashing |
| Media URL 404/410 | Invalidate session, re-resolve, seek to last position |
| Buffer stall over 20s | Invalidate session, retry once at the last position, then pause |
| Network loss | Keep queue; the stall/error path retries once, then pauses with an error |
| Decode error | Retry once, then pause and show an error; keep the queue |
| Cache corruption | Clear the cache entry and retry; never crash the service |
| Auth expired | Pause and emit session-expired; playback cannot resume until login |

## Testing

- Unit-test synthetic URI round-trips, resolved source cache keys/MIME types, queue
  reducers, shuffle/restore, and reporter thresholds.
- Instrumented tests against a local server: play a `stream` producer and an `hls`
  producer, seek, background, kill the process, verify queue restore.
- Verify that building a large queue performs no session requests and that a buffer
  stall retries once from the last position.
- Test session expiry by invalidating the session between resolve and play.
- Test cache reuse and offline playback with `caching-and-downloads.md`.

## Invariants

- No playback URL is exposed outside `playback/`.
- No session id or query token appears in a cache key or queue item URI.
- One player, one media session, one cache instance.
- Queue state is the source of truth for order; ExoPlayer playlist items are synthetic
  projections of `TrackKey` values.
