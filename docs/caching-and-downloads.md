# Caching and downloads

Read when: changing media storage, cache keys, offline behaviour, or the future
downloads feature. Playback/session/queue behaviour lives in `playback.md`.

## Cache layer

One `SimpleCache` lives in app-specific external storage
(`getExternalFilesDir("media")`) with an LRU evictor and
`StandaloneDatabaseProvider`. Exactly one instance may exist per process; a second
instance fails on the cache lock.

All playback uses `CacheDataSource`, including v1 streaming, so cached tracks replay
instantly and downloads reuse the same bytes later.

```
PipeBombMediaSourceFactory
  -> CacheDataSource.Factory
     -> SimpleCache
     -> upstream (OkHttp / default HTTP data source)
```

## Cache identity

`CacheKeyFactory` rules:

| Source | Key |
| ------ | --- |
| Progressive | `track:<pluginId>:<libraryId>:<trackId>:stream` |
| HLS playlist | `track:<pluginId>:<libraryId>:<trackId>:hls:<path>` |
| HLS segment | `track:<pluginId>:<libraryId>:<trackId>:hls:<path>` |

- Exclude host, port, session id, and query tokens. A new session must hit the same
  cache entry for the same track.
- Strip query strings from HLS child URIs before using the path.
- If a plugin can return different audio for the same track ID, add a server- or
  plugin-provided generation token later; never encode sessions into the key.
- Changing server URL clears the cache because track IDs are server-scoped.
- Switching accounts keeps the media cache (it is not user-scoped) but clears
  user-specific state.

## Downloads (future, design now)

V1 exposes no download UI, but the storage design must not require a migration.

**Model**

- A download is a pinned cache entry plus metadata:
  `trackKey`, display title/artist/album, artwork URL snapshot, bytes, state,
  requestedAt, completedAt, lastError.
- State: `queued`, `downloading`, `completed`, `failed`, `paused`.
- Metadata lives in Room only when downloads ship; audio bytes stay in `SimpleCache`.
- Do not add Room before this milestone.

**Downloading**

- Use Media3 `DownloadManager`/`DownloadHelper` where it supports the source, with
  the same `SimpleCache` and `CacheKeyFactory`.
- Fall back to a custom segment downloader for plugins whose HLS manifest is not
  Media3-downloadable.
- Run downloads through WorkManager with a foreground notification for long jobs.
- Resolve a fresh session per download; on 404/410 re-resolve and retry the failed
  request. A track-sized download should complete inside one session lifetime.
- If a plugin's sessions are too short for reliable HLS downloads, flag it and propose
  a persistent download-token endpoint to the server. Never cache signed URLs as a
  workaround.

**Eviction**

- The LRU evictor may remove unpinned streamed audio freely.
- Pinned downloads are never evicted by LRU; the user removes them, or the storage cap
  forces an explicit choice.
- Settings shows total cache size, downloaded size, and clear-cache actions.

**Playback offline**

Because keys are stable and all playback goes through `CacheDataSource`, a pinned
download plays with no network. Do not add a separate offline player path.

## Failure handling

| Failure | Behaviour |
| ------- | --------- |
| Cache corruption | Clear the entry and retry; never crash the service |
| Download interrupted | Resume if the source supports ranges; otherwise restart |
| Session expiry mid-download | Re-resolve and retry the failed request |
| Storage full | Pause downloads, surface the cap, require user action |
| Server URL change | Clear cache and download index; keys are server-scoped |

## Testing

- Play the same track twice and verify the second play is served from cache.
- Verify cache keys are stable across different sessions for the same track.
- Verify HLS playlist and segment keys do not contain session ids or query tokens.
- Once downloads ship: airplane-mode playback, eviction protects pinned entries,
  interrupted download resumes or restarts cleanly.

## Invariants

- One cache instance per process.
- Cache identity is `TrackKey`-derived, never session-derived.
- Downloads are pinned cache entries, not a parallel media store.
- Offline playback reuses the normal player path.
