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
- Downloads default to Wi-Fi only. The Settings toggle can allow downloads on metered
  networks; collection sync metadata may still refresh on any connection.

## Cache layer

One `SimpleCache` lives in the selected app-specific directory with
`PinnedStreamCacheEvictor` and `StandaloneDatabaseProvider`. Exactly one instance may
exist per process; a second instance fails on the cache lock.

All playback uses `CacheDataSource`, including streaming, so cached tracks replay
instantly and downloads reuse the same bytes.

```
LazyTrackMediaSourceFactory
  -> LazyTrackMediaSource (resolve on prepare)
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
- Switching accounts keeps ordinary streamed cache, downloads, and kept-collection
  subscriptions. Auto-sync skips collections belonging to a different account until
  that account signs in again.

## Downloads

V1 downloads progressive streams only. Single-track HLS requests are rejected with
“HLS downloads are not supported yet.” In collection snapshots, HLS members become
hidden `Unsupported` rows and are skipped silently. HLS playback and HLS streamed
caching continue to work. A future dedicated server endpoint will replace the
progressive-only source resolver.

**Model**

- A download is a pinned cache entry plus metadata:
  `trackKey`, display title/artist/album, artwork URL snapshot, session id, media URL,
  bytes, state, requestedAt, completedAt, lastError.
- State: `Pending` (hidden preparation), `Queued`, `Downloading`, `Paused`,
  `Completed`, `Failed`, and `Unsupported` (hidden HLS skip).
- A download is manually requested (`manual = true`) or owned by kept collections.
- Metadata lives in Room; audio bytes stay in `SimpleCache`.

**Manual vs kept downloads**

- Manual downloads remain until removed by the user.
- Albums and playlists can be kept downloaded. Their ordered membership is stored in
  `download_collections` and `download_collection_tracks`.
- A track is retained while manual or referenced by at least one kept collection.
- Removing a collection releases tracks with no manual or other collection reference.
- Collection-managed tracks cannot be removed individually. The UI names the owning
  collections and directs the user to stop keeping them.
- Signing out keeps everything; another account cannot sync those collections until
  the owning account returns.

**Collection synchronization**

- Playlists use `dateModified` to skip unchanged membership fetches; albums reconcile
  their current track list.
- Sync runs at startup, after login, when opening a kept collection, after relevant
  local playlist edits, and every 6 hours through WorkManager.
- New members become hidden `Pending` rows; removed members are released according to
  reference counts.
- Display metadata is refreshed from the remote collection without redownloading audio.
- Same-account 404 unsubscribes a missing collection. 403, network, and server failures
  retain downloads and show a sync error. Account mismatch skips sync.
- Playlist and album screens open the ordered local snapshot when offline or when the
  server cannot be reached.

**Downloading**

- Resolve a fresh progressive session when the worker prepares each track.
- Prepare a bounded window of downloads at a time so large collections do not create
  hundreds of live server sessions.
- Use Media3 `DownloadManager` with the explicit stream cache key.
- Run queued work through a Hilt `CoroutineWorker` with a data-sync foreground
  notification. The WorkManager constraint is `UNMETERED` by default, or `CONNECTED`
  when mobile-data downloads are allowed.
- If the device moves to mobile data while downloads are disallowed, pause Media3
  downloads until Wi-Fi returns or the setting is enabled.
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

## Offline mode

Connectivity is tracked by `ConnectivityMonitor`. The app is downloads-first offline:

- A slim “Offline · Showing downloads” banner appears below the app bar and links to
  Library → Downloads.
- Home shows kept collections and recently downloaded tracks instead of network shelves.
- Library opens Downloads; other tabs show an offline placeholder.
- Search filters downloaded tracks and kept collections locally.
- Kept album/playlist screens render their ordered local snapshot with a “Downloaded
  copy” label. Non-kept details show a connect-to-view message.
- Downloaded playback continues. A non-downloaded stream reports that it is unavailable
  offline.
- Writes that require the server remain unavailable; local collection removal and
  manual download removal still work.
- On reconnect the banner is replaced briefly by a snackbar and collection sync runs.

## Failure handling

| Failure | Behaviour |
| ------- | --------- |
| Cache corruption | Clear the entry and retry; never crash the service |
| Download interrupted | Keep metadata; mark failed with Retry |
| Session expiry mid-download | Mark failed; Retry resolves a fresh session |
| Storage full / below reserve | Pause, surface the reserve, require user action |
| Mobile data disallowed | Pause, show the Wi-Fi wait state, keep queued work |
| Selected SD removed | Keep metadata, block affected downloads/playback, offer retry/location change |
| Server URL change | Clear cache and download index; keys are server-scoped |

## Testing

- Play the same track twice and verify the second play is served from cache.
- Verify progressive keys are stable across sessions.
- Verify HLS keys contain no session id or query token.
- Verify pinned spans survive the streamed cache budget.
- Verify single-track HLS attempts are rejected and bulk HLS members remain hidden.
- Verify collection add/remove, shared references, manual retention, and account mismatch behavior.
- Verify offline Home, Downloads-first Library, local search, collection detail fallback, and blocked per-track removal.
- Airplane-mode playback of a progressive download after process restart.
- Reserve preflight rejection, mid-download pause, and resume after lowering reserve.
- Mobile-data default, enabling the setting, and automatic pause when a download moves
  from Wi-Fi to a metered network.
- Storage switch deletes downloads/cache and starts on the new location.

## Invariants

- One cache instance per process.
- Cache identity is `TrackKey`-derived, never session-derived.
- Downloads are pinned cache entries, not a parallel media store.
- Offline playback reuses the normal player path.
- HLS downloads are not implemented in v1; kept collections skip them silently.
- Kept collections are subscriptions; manual downloads remain independent.
