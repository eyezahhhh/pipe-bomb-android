# 008. Downloads use pinned Media3 cache entries with user-selectable storage

Status: accepted

Context: downloads need to survive offline playback and work with internal, external,
and removable app-specific storage. The server currently returns progressive or HLS
audio sessions. A dedicated single-file download endpoint is planned later but does not
exist. Users need control over free space and storage location.

Decision:

- Default to internal app storage. Expose internal, primary external, and removable
  app-specific directories in Settings only.
- Never use SAF or a shared/visible storage folder. The app owns its media directory.
- Use one Media3 `SimpleCache` in the selected directory, shared by streaming and
  downloads.
- Store download metadata in Room and audio bytes in `SimpleCache`. Download cache keys
  are pinned and protected from the streamed-audio LRU budget.
- Keep the streamed-audio budget at 512 MB. Free-space protection is a user-selected
  reserve, not a fixed download quota.
- Downloads are progressive-only in v1. Resolve the audio session before enqueueing and
  reject HLS with a clear message. HLS playback and streaming cache remain supported.
- Use Media3 `DownloadManager`, an explicit `track:...:stream` cache key, and a
  WorkManager `CoroutineWorker` with a data-sync foreground notification.
- Re-resolve the session when the worker starts so queued downloads do not use an
  expired URL.
- Changing storage deletes all downloads and cached audio rather than migrating them.
  The old directory is removed on the next process start, then the app opens the new
  cache.
- If selected storage is unavailable, keep download metadata and run streaming from an
  internal fallback cache. Block downloads and playback of downloaded content until the
  volume returns or the user changes location.

Consequences:

- Offline playback reuses the normal player and cache path.
- HLS cannot be downloaded until the dedicated server endpoint lands, at which point
  the source resolver can return a progressive file without changing Room, storage, or
  UI architecture.
- Free-space behavior is predictable and user-controlled, with no arbitrary 2 GB cap.
- A storage switch requires a process restart because `SimpleCache` is process-wide and
  must not be opened twice.
