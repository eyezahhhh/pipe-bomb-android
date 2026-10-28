# Testing

Read when: adding or debugging tests, or changing the API/playback boundary.

## Commands

- Unit + JVM UI tests: `./gradlew testDebugUnitTest`
- Screenshot regression: `./gradlew verifyRoborazziDebug`
- Record/update screenshots: `./gradlew recordRoborazziDebug`
- Coverage: `./gradlew koverHtmlReportDebug`
- Lint: `./gradlew lintDebug`
- Full gate: `./gradlew lintDebug verifyRoborazziDebug koverHtmlReportDebug assembleDebug`
- Instrumented (when an emulator is running): `./gradlew connectedDebugAndroidTest`

## Test layers

| Layer | Tools | Runs on |
| ----- | ----- | ------- |
| Pure unit | JUnit, coroutines-test, Turbine | JVM |
| API/ViewModel | MockWebServer, real repositories | JVM |
| Compose interaction | Compose UI test + Robolectric | JVM |
| Screenshot | Roborazzi + Robolectric native graphics | JVM |
| Instrumented playback | Compose/Espresso, Media3, emulator | Device/emulator |

The PR gate runs unit, Compose, and screenshot tests. Instrumented playback tests are
reserved for real media/session behaviour and should run nightly or on a label.

## What exists

| Area | Location | Coverage |
| ---- | -------- | -------- |
| URL parsing | `app/src/test/.../core/net/ServerUrlParserTest.kt` | local/public schemes, paths, invalid input |
| API errors | `app/src/test/.../core/net/ApiErrorTest.kt` | status mapping and server messages |
| Cookies | `app/src/test/.../core/net/PersistentCookieJarTest.kt` | persist, domain isolation, expiry, clear |
| Contract | `app/src/test/.../core/net/ApiContractTest.kt` | MockWebServer, unknown fields, cookie header, search defaults |
| Server URL rewriting | `app/src/test/.../core/net/ServerUrlRewriterTest.kt` | loopback rewrite, remote passthrough |
| Attributes | `app/src/test/.../core/attributes/AttributeReaderTest.kt` | formatted/raw, types, buffers, unknown |
| Track identity | `app/src/test/.../core/model/TrackKeyTest.kt` | round-trip and malformed keys |
| Track cache | `app/src/test/.../data/TrackCacheTest.kt` | TTL and clearing |
| Queue metadata fallback | `app/src/test/.../data/TrackMetadataRepositoryTest.kt` | local download metadata and offline resolution without network |
| Media cache keys | `app/src/test/.../playback/TrackCacheKeyFactoryTest.kt` | stable progressive keys, session-free HLS paths |
| Synthetic media items | `app/src/test/.../playback/TrackMediaUriTest.kt` and `TrackMediaItemFactoryTest.kt` | URI round-trips, metadata IDs, cache-key/MIME rewriting |
| Lazy source failure | `app/src/androidTest/.../playback/LazyTrackMediaSourceTest.kt` | failed resolution reaches ExoPlayer as an error without crashing |
| Offline auth | `app/src/test/.../data/AuthRepositoryTest.kt` | cached user survives network failure |
| Download metadata | `app/src/test/.../data/download/DownloadRepositoryTest.kt` | enqueue, completion, collections, reference counts, blocked removal, incomplete states, failed retry |
| Collection subscription queue | `app/src/test/.../feature/downloads/CollectionSubscriptionQueueTest.kt` | two playlists serialize without drops; duplicate taps reject |
| Download pins | `app/src/test/.../playback/download/DownloadPinRegistryTest.kt` | track-prefix pinning and unpinning |
| Cache evictor concurrency | `app/src/test/.../playback/download/PinnedStreamCacheEvictorTest.kt` | concurrent span updates and pin refresh |
| Download reserve | `app/src/test/.../playback/download/DownloadSpaceTest.kt` | capacity floor and reserve boundary |
| Playlist windowing | `app/src/test/.../feature/playlist/PlaylistViewModelTest.kt` | first page, lazy chunk, metadata preservation |
| History windowing | `app/src/test/.../feature/library/LibraryViewModelTest.kt` | lazy load, page mapping, no duplicate chunks |
| Auto media IDs | `app/src/test/.../playback/auto/AutoIdsTest.kt` | recent snapshot/track/action ID round-trip, malformed IDs |
| Auto queue resolution | `app/src/test/.../playback/auto/AutoQueueResolverTest.kt` | full metadata queue, start index, no session requests |
| Auto collection paging | `app/src/test/.../playback/auto/AutoCollectionPagingTest.kt` | action rows, page offsets, page-size limits |
| Auto recent snapshots | `app/src/test/.../playback/auto/AutoRecentSnapshotStoreTest.kt` | stable tokens and expiry |
| Auto browse integration | `app/src/androidTest/.../playback/auto/AutoBrowseTest.kt` | root, custom commands, paged actions, full queue, synthetic URI, and Next |
| Component interaction | `app/src/test/.../ui/interaction/ComponentInteractionTest.kt` | pager selection guards, mini-player swipes/tap/buffering, player/queue drawer swipes, collapse drag reporting, media action icons/loading, track long-press |
| Visual goldens | `app/src/test/.../ui/screenshot/ComponentScreenshotTest.kt` | track rows, media actions, mini player, player overlay and queue drawer, design tokens |

## Fixtures and determinism

- Shared API/UI models live in `app/src/test/.../test/TestFixtures.kt`.
- `MainDispatcherRule` installs a test main dispatcher for ViewModel tests.
- Robolectric uses native graphics, SDK 36, and a fixed 412×915dp/xxhdpi device.
- Goldens live in `app/src/test/screenshots/` and must be reviewed with screenshot diffs.
- Prefer fake/local artwork and fixed clocks over network or wall-clock-dependent UI.

## Rules

- Test behaviour and edge cases, not implementation details.
- MockWebServer fixtures should follow the pinned spec (`openapi.lock`).
- Playback tests use a local server and the demo/remote catalog; don't assert on external network content.
- Never log or assert on cookies/tokens.
- No sleeps in unit tests; use coroutine test utilities and Compose idling.
- Update goldens deliberately with `recordRoborazziDebug`; never record blind.

## Still needed

- Instrumented playback (stream + HLS, background, notification, process restore).
- Pure queue reducer tests (shuffle/restore, repeat, remove-current).
- Session-expiry and retry-path tests.
- Instrumented progressive download, airplane-mode restart, reserve pause/resume,
  mobile-data pause/resume, removable-storage switch, collection sync, offline
  collection detail, and two overlapping playlist downloads.
- Signed-release install over a previous signed release with the same key.
- HLS download rejection integration coverage.
- Tablet/foldable screenshot variants and Macrobenchmark performance baselines.
