# Android Auto

Read when: changing the media library service, browse tree, Auto media IDs, queue
bridging, or Auto artwork.

## Service

`PlaybackService` extends Media3 `MediaLibraryService` and owns both the phone and Auto
surface through one `ExoPlayer` and one `SimpleCache`.

- `MediaLibrarySession` uses `AutoLibrarySessionCallback`.
- The notification provider is the default Media3 provider, so the same session serves
  the phone notification and Android Auto.
- `MainActivity` is the session activity, so Auto can deep-link back to the app.
- Manifest actions:
  - `androidx.media3.session.MediaLibraryService`
  - `androidx.media3.session.MediaSessionService`
  - `android.media.browse.MediaBrowserService`
  - `android.media.action.MEDIA_PLAY_FROM_SEARCH`
- `res/xml/automotive_app_desc.xml` declares `<uses name="media" />`.
- The `com.google.android.gms.car.application` meta-data must be a direct child of
  `<application>`, not the media service. Android Auto discovers media apps from that
  application-level declaration.

Android Auto is the target for v1. The MediaLibraryService structure is compatible
with Automotive OS later; the automotive feature manifest can be added separately.

## Browse tree

Root categories:

- Recently played
- Playlists
- Saved → Tracks / Albums / Artists
- History

IDs are stable strings in `AutoIds`:

- `pb:root`, `pb:recent`, `pb:playlists`, `pb:saved`, `pb:history`
- `pb:playlist:<uuid>`
- `pb:album:<uuid>`, `pb:artist:<uuid>`
- `pb:saved:tracks`, `pb:saved:albums`, `pb:saved:artists`
- `pb:track:<encoded context>:<encoded track key>`

The track context is encoded into the selected item because Android Auto sends only
the selected `MediaItem` to `onSetMediaItems`, not its siblings. Contexts include
album, playlist, artist, saved tracks, history, and search queries.

Browse categories are browsable but generally not playable. Album, playlist, artist,
saved-track, recent, and history items are playable, so Auto can start a full queue.

## Queue expansion

`AutoQueueResolver` expands a selected context into a playable queue:

- Fetch the context tracks from `AutoLibraryDataSource`.
- Start at the selected track.
- Resolve each track's audio session through `PlaybackResolver` with bounded
  concurrency.
- Build Media3 `MediaItem`s with the resolved URL, HLS MIME type, metadata, and stable
  media ID.
- Return `MediaItemsWithStartPosition` so playback starts at the selected track.
- Skip failed tracks and adjust the start index.

V1 hard limit: 200 tracks per selection. For larger contexts a 200-track window around
the selected item is used. Sessions are held in memory by the server; the cap avoids
unbounded resolution work.

## Phone synchronization

`PlaybackController` has an external queue mode for Auto:

- `prepareExternalQueue(tracks, startIndex)` updates controller state and current
  track.
- ExoPlayer owns the Auto playlist; controller listens to `onMediaItemTransition`.
- In external mode, phone next/previous/index-selection delegate to ExoPlayer so the
  Auto queue survives.
- Starting a new queue from the phone exits external mode and returns to the normal
  single-item playback path.

V1 limitation: queue mutation actions while an Auto queue is active (add next, remove,
reorder) are not projected back into the ExoPlayer Auto playlist. Treat Auto playback
as a read-only queue there until a follow-up.

## Artwork

`AutoArtworkStore` fetches resource images through the authenticated OkHttp client,
with an LRU byte cache.

- Browse children load artwork with bounded concurrency.
- Resolved queue items include artwork for the selected track.
- The fallback is the normal app artwork fallback when no image is available.

Do not use the raw resource URL directly: `/resources/...` requires the `auth_token`
cookie.

## Search and resumption

- Auto search queries the local library search endpoint and returns playable tracks.
- Track media IDs carry the search context so selecting a result expands into a search
  result queue.
- `onPlaybackResumption` restores the persisted queue, resolves the queue, and returns
  the saved index/position. For metadata-only resumption it returns the current item
  without starting playback.
- `MEDIA_PLAY_FROM_SEARCH` is declared for voice search.

## Testing

- Unit: `AutoIdsTest` covers ID round-trips and malformed input.
- Instrumented: `AutoBrowseTest` connects a real `MediaBrowser` to `PlaybackService`
  and verifies root, category, and saved children. The queue-expansion test is skipped
  when the test install has no authenticated catalog.
- Manual: Desktop Head Unit (DHU) or an Auto head unit for category layout, voice
  search, artwork, next/previous, and phone-state synchronization.
- Production Auto distribution requires Google approval; DHU is sufficient for
  development.

## Troubleshooting discovery

If the app is not listed in Android Auto:

- Confirm the `com.google.android.gms.car.application` meta-data is on `<application>`.
- Confirm the media service exports the `androidx.media3.session.MediaLibraryService`,
  `androidx.media3.session.MediaSessionService`, and
  `android.media.browse.MediaBrowserService` actions.
- For a sideloaded build, enable Android Auto developer mode and turn on “Unknown
  sources” in the Android Auto developer settings. Production distribution requires
  Google approval.
- Reinstall the APK, force-stop Android Auto, and relaunch it; the launcher caches app
  capabilities.
- Validate with the Desktop Head Unit (DHU) before testing on a head unit.

## Follow-ups

- Lazy per-item audio resolution to remove the 200-track cap.
- Project phone queue mutations into the Auto playlist.
- Sync Auto shuffle/repeat state into `PlaybackUiState`.
- Full all-albums/all-artists browse endpoints.
- Automotive OS manifest and testing.
