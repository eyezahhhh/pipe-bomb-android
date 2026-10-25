# Caching and downloads

Read when: changing media storage, cache keys, offline behaviour, downloads, or
storage settings. Playback/session/queue behaviour lives in `playback.md`.

## Storage

- Default storage is internal app storage: `filesDir/media`.
- Users may select internal, primary external, or a removable volume in
  Settings. Only app-specific external directories from `getExternalFilesDirs()` are
  used; there is no SAF, shared-storage folder, or broad storage permission.
- The selected location is persisted by stable volume ID. A missing selected volume
  starts the app with an internal fallback cache, keeps download metadata, disables
  downloads, and blocks playback of downloads stored on that volume.
- Changing location deletes all downloads and cached audio. There is no migration or
  copying step. The old directory is deleted on the next process start before the new
  cache opens.
- Streamed audio has a 512 MB unpinned LRU budget. Downloads are pinned and are not
  evicted by that budget.
- Downloads pause when free space reaches the user-selected reserve. The reserve is
  stored in Preferences DataStore and can be Off, a preset, or a custom MB value.

## Cache layer

One `SimpleCache` lives in the selected app-specific directory with
`PinnedStreamCacheEvictor` and `StandaloneDatabaseProvider`. Exactly one instance may
exist per process; a second instance fails on the cache lock.

All playback uses `CacheDataSource`, including streaming, so cached tracks replay
instantly and downloads reuse the same bytes.

```
PipeBombMediaSourceFactory
  -> CacheDataSource.Factory
     -> SimpleCache
     -> upstream (OkHttp / default HTTP data source)
```

`PinnedStreamCacheEvictor` enforces the streamed-audio budget against every cache
span whose key is not protected by a queued, downloading, paused, failed-partial, or
completed download.

## Cache identity

`TrackCacheKeyFactory` rules:

| Source | Key |
| ------ | ---- |
| Progressive | `track:<pluginId>:<libraryId>:<trackId>:stream` |
| HLS playlist | `track:<pluginId>:<libraryId>:<trackId>:hls:playlist.m3u8` |
| HLS segment | `track:<pluginId>:<libraryId>:<trackId>:hls:<segment path>` |

- Exclude host, port, session id, and query tokens.
- Progressive playback and downloads set the same explicit stream cache key.
- Strip the `/streaming/<sessionId>` prefix before deriving HLS keys.
- Strip query strings from HLS child URIs before using the path.
- If a plugin can return different audio for the same track ID, add a server- or
  plugin-provided generation token later; never encode sessions into the key.
- Changing server URL clears the cache and download index because track IDs are
  server-scoped.
- Switching accounts keeps ordinary streamed cache but clears user-specific state.
  Downloads are not user-scoped and remain.

## Downloads

V1 downloads progressive streams only. HLS tracks are rejected before a download row
is created with: “HLS downloads are not supported yet.” HLS playback and HLS streamed
caching continue to work. A future dedicated server download endpoint will replace
the progressive-only source resolver.

**Model**

- A download is a pinned cache entry plus metadata:
  `trackKey`, display title/artist/album, artwork URL snapshot, session id, media URL,
  bytes, state, requestedAt, completedAt, lastError.
- State: `Queued`, `Downloading`, `Paused`, `Completed`, `Failed`.
- Metadata lives in Room; audio bytes stay in `SimpleCache`.

**Downloading**

- Resolve a fresh progressive session before enqueue and again when the worker starts.
- Use Media3 `DownloadManager` with the explicit stream cache key.
- Run queued work through a Hilt `CoroutineWorker` with a data-sync foreground
  notification and network constraint.
- Reconcile Room against Media3 `DownloadIndex` on startup so interrupted work cannot
  leave stale rows.
- On a 404/410 or expired session, mark failed and let the user retry; retry resolves a
  fresh session.

**Free space**

- Before starting each track, reject if free space is at or below the user-selected
  reserve.
- During downloads, re-check on every progress pass. If the reserve is reached, pause
  the queue, keep partial downloads pinned, and expose Resume in Settings and the
  Downloads tab.
- Lowering the reserve or freeing space allows an explicit resume. The app never
  deletes downloads automatically to satisfy the reserve.

**Eviction**

- The LRU evictor may remove unpinned streamed audio freely.
- Pinned downloads are never evicted by LRU; the user or the storage location change
  removes them.
- “Clear cached audio” removes only unpinned streamed audio.
- “Remove all downloads” removes download metadata and pinned media.

**Playback offline**

Because keys are stable and all playback goes through `CacheDataSource`, a pinned
progressive download plays with no network. Persisted download metadata supplies the
display title/artist and the saved media URL after a process restart. The last
authenticated user is cached so a network failure does not force the Login screen;
a definitive 401 still does. Do not add a separate offline player path.

## Failure handling

| Failure | Behaviour |
| ------- | --------- |
| Cache corruption | Clear the entry and retry; never crash the service |
| Download interrupted | Keep metadata; mark failed with Retry |
| Session expiry mid-download | Mark failed; Retry resolves a fresh session |
| Storage full / below reserve | Pause, surface the reserve, require user action |
| Selected SD removed | Keep metadata, block affected downloads/playback, offer retry/location change |
| Server URL change | Clear cache and download index; keys are server-scoped |

## Testing

- Play the same track twice and verify the second play is served from cache.
- Verify progressive keys are stable across sessions.
- Verify HLS keys contain no session id or query token.
- Verify pinned spans survive the streamed cache budget.
- Verify HLS download attempts are rejected without creating a Room row.
- Airplane-mode playback of a progressive download after process restart.
- Reserve preflight rejection, mid-download pause, and resume after lowering reserve.
- Storage switch deletes downloads/cache and starts on the new location.

## Invariants

- One cache instance per process.
- Cache identity is `TrackKey`-derived, never session-derived.
- Downloads are pinned cache entries, not a parallel media store.
- Offline playback reuses the normal player path.
- HLS downloads are not implemented in v1.
