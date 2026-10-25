# 009. Kept playlists and albums synchronize their downloads

Status: accepted

Context: users expect a downloaded playlist or album to remain complete as its track
list changes. Downloads are keyed by `TrackKey`, so a track can be referenced by
several collections and can also be manually downloaded. The server has no push
channel for playlist changes.

Decision:

- Treat playlist and album downloads as kept collections with ordered Room snapshots.
- Store collection metadata, membership, account identity, remote revision, and sync
  status in `download_collections` and `download_collection_tracks`.
- Add a `manual` flag to downloads. A track is retained while manual or referenced by
  at least one kept collection.
- Block individual removal of collection-managed tracks and show the owning
  collections. Users stop keeping the collection to release its exclusive tracks.
- Synchronize at startup, after login, when opening a kept collection, after local
  playlist edits, and every 6 hours with WorkManager. Playlists skip unchanged
  `dateModified` revisions; albums reconcile their current tracks.
- Add new members as hidden `Pending` rows and prepare a bounded window in the download
  worker. Removed members release only when no manual or other collection reference
  remains.
- Keep HLS unsupported silently inside collections with hidden `Unsupported` rows so
  periodic sync does not repeatedly create sessions.
- Store the owning account. Signing out keeps everything; a different account skips
  those collection syncs. Same-account 404 removes the missing collection; other
  failures retain downloads and surface a sync error.
- Offline mode is downloads-first: global banner, Home download shelves, Downloads-first
  Library, local download search, and ordered collection detail snapshots.

Consequences:

- Collection membership and display metadata stay current without redownloading audio.
- Shared tracks and manual downloads are protected by reference counting.
- Playlist changes while the app is closed are applied at the next periodic sync, not
  instantly.
- Audio changes behind an unchanged `TrackKey` cannot be detected until the server
  exposes a generation token or the track leaves and rejoins the collection.
