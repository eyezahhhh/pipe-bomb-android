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

IDs are stable strings in `AutoIds`:

- `pb:root`, `pb:recent`, `pb:playlists`, `pb:saved`
- `pb:playlist:<uuid>`
- `pb:album:<uuid>`, `pb:artist:<uuid>`
- `pb:saved:tracks`, `pb:saved:albums`, `pb:saved:artists`
- `pb:action:<encoded context>:play|shuffle`
- `pb:track:<encoded context>:<encoded track key>`

The track/action context is encoded into the selected item because Android Auto sends
only the selected `MediaItem` to `onSetMediaItems`, not its siblings. Contexts include
recent snapshot, album, playlist, artist, saved tracks, search, and the internal
playback-resumption history context.

Collection categories are browsable only. Opening a playlist, album, artist, saved
tracks, or recently played shows Play and Shuffle actions at the top of the track list.
Selecting a track starts that collection at the selected track.

## Queue expansion

`AutoQueueResolver` expands a selected context into a playable queue:

- Recently played is cached as a deduplicated snapshot; track IDs carry the snapshot
  token so a live history update cannot replace the active queue.
- Fetch the complete ordered context from `AutoLibraryDataSource`.
- For a collection action, start at index 0 and shuffle the snapshot for the Shuffle item.
- Start at the selected track for normal track selection.
- Build metadata-only Media3 `MediaItem`s using the stable Auto media ID and
  `pipebomb://track/<base64 TrackKey>` URI.
- Load artwork only for the selected item; do not make per-track network calls while
  building the queue.
- Return `MediaItemsWithStartPosition` so playback starts at the selected track.
- ExoPlayer resolves a track through `LazyTrackMediaSource` when it prepares that item.

There is no track-count cap and no session creation during queue construction. A
250-track playlist returns all 250 items with zero audio-session requests. Sessions are
created only for items ExoPlayer actually prepares.

The browse tree paginates playlists, album/artist tracks, saved lists, and the recent
snapshot. Page 0 includes the two collection actions, so it returns `pageSize - 2`
tracks; later pages return the requested number without actions. This keeps every
`LibraryResult` within Android Auto's page-size contract and prevents duplicate pages
from resetting scroll position.

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

## Playback controls

The media session installs a custom button layout for Save/Saved, Shuffle off/on, and
Repeat off/all/one. The buttons update from `PlaybackController.state` and
`PlaybackSessionActions.saved`, and commands are handled by `onCustomCommand`.
Android Auto may place these custom actions in the transport row or overflow depending
on available space.

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

- Unit: `AutoIdsTest` covers root, track, recent snapshot, and collection action IDs.
- Unit: `AutoQueueResolverTest` verifies a 250-track metadata-only queue creates zero
  audio sessions.
- Instrumented: `AutoBrowseTest` connects a real `MediaBrowser` to `PlaybackService`
  and verifies root categories, custom playback commands, saved children, paged
  collection actions, full playlist expansion, synthetic queue URIs, and Next
  availability when authenticated content exists. The expansion tests are skipped when
  the test install has no authenticated catalog.
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

- Project phone queue mutations into the Auto playlist.
- Sync Auto shuffle/repeat state into `PlaybackUiState`.
- Full all-albums/all-artists browse endpoints.
- Automotive OS manifest and testing.
