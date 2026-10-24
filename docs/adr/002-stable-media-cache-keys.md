# 002. Stable media cache keys

Status: accepted

Context: the server returns ephemeral audio sessions (`stream` or `hls`) whose URLs
contain a session id. Downloads are planned as a core feature. If caching keyed off
session URLs, every replay would miss and downloads could never be reused offline.

Decision: every media request goes through one Media3 `SimpleCache`. Cache and
download identity is derived from `TrackKey` (`pluginId:libraryId:trackId`), with a
source suffix and, for HLS, the child URI path. Session ids, hosts, and query tokens
never enter a cache key. Downloads are pinned cache entries, not a parallel store.

Consequences:

- Streamed audio is reusable and downloaded audio plays offline without a second
  player path.
- Sessions must be re-resolved on expiry; `PlaybackResolver` owns that retry.
- Changing server URL clears the cache, because track ids are server-scoped.
- If a plugin changes audio for the same track id, the cache can go stale. Add a
  server/plugin generation token to the key only when that becomes a real problem.
- The cache policy is frozen early, avoiding a migration when downloads ship.
