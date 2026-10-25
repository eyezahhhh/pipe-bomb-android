# 007. Android Auto uses a MediaLibraryService with context-encoded media IDs

Status: accepted

Context: Pipe Bomb audio requires a per-track server session. Android Auto browses a
catalogue and sends only the selected `MediaItem` to the session; it does not send the
sibling items that should form the queue. Stock `MediaSessionService` also does not
expose a browse tree.

Decision:

- Extend `MediaLibraryService` and provide a browse tree over recent, playlists,
  saved content, and history.
- Encode the playback context (album, playlist, artist, saved, history, search) into
  every track media ID.
- On `onSetMediaItems`/`onAddMediaItems`, expand the selected context, resolve the
  tracks, and return `MediaItemsWithStartPosition`.
- Cap a selection at 200 tracks for v1. The server keeps stream sessions in memory;
  resolving beyond that is unnecessary risk and latency.
- Add an external queue mode to `PlaybackController` so ExoPlayer owns the Auto
  playlist while the phone UI stays synchronized.
- Fetch artwork through the authenticated HTTP client and embed bytes in media
  metadata; raw resource URLs require cookies and are not usable directly by Auto.

Consequences:

- Auto and the phone share one player, cache, and session.
- Auto queue playback works with next/previous and correct start index.
- The 200-track cap and read-only queue mutations are explicit v1 limitations.
- Lazy session resolution and Automotive OS support can be added later without
  changing the public media IDs.
