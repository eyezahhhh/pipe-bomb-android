# 010. Playback queues use lazy synthetic track sources

Status: accepted

Context: Android Auto needs the whole selected context in the player queue so next,
previous, and queue UI work immediately. Resolving every track's server audio session
while building that queue creates hundreds of sessions, adds latency, and couples queue
construction to the network. The 200-track cap in ADR 007 only limited the damage.

Decision:

- Give every playable track a stable synthetic URI:
  `pipebomb://track/<base64 TrackKey>`.
- Build phone and Auto queue items from metadata plus that URI. Do not call the audio
  session endpoint while constructing a queue.
- Install `LazyTrackMediaSourceFactory` in ExoPlayer. It recognises synthetic URIs and
  wraps each item in a `LazyTrackMediaSource`.
- Resolve the track only in `prepareSourceInternal`, then delegate to the downloaded
  file or a progressive/HLS child source.
- Keep `PlaybackResolver` session caching per track for 10 minutes, with per-track
  in-flight deduplication.
- Return the complete selected context to Android Auto with no 200-track cap.
- On a playback error or a 20-second buffer stall, invalidate the session and retry the
  current track once at the last position.

Consequences:

- Queue construction is metadata-only and works offline for downloaded tracks.
- Android Auto gets full next/previous behaviour without creating sessions up front.
- ExoPlayer controls when a session is created; the app never resolves the whole queue.
- Media items expose a stable `TrackKey` URI rather than a session URL.
- Auto queue mutation from the phone remains read-only.

Supersedes the resolution and 200-track parts of ADR 007.
