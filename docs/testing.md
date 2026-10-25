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
| Media cache keys | `app/src/test/.../playback/TrackCacheKeyFactoryTest.kt` | per-track/session identity, HLS segments |
| Playlist windowing | `app/src/test/.../feature/playlist/PlaylistViewModelTest.kt` | first page, lazy chunk, metadata preservation |
| History windowing | `app/src/test/.../feature/library/LibraryViewModelTest.kt` | lazy load, page mapping, no duplicate chunks |
| Component interaction | `app/src/test/.../ui/interaction/ComponentInteractionTest.kt` | mini-player swipes/tap/buffering, track long-press, player artwork swipes |
| Visual goldens | `app/src/test/.../ui/screenshot/ComponentScreenshotTest.kt` | track rows, mini player, player sheet, design tokens |

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
- Download/offline tests once downloads land.
- Tablet/foldable screenshot variants and Macrobenchmark performance baselines.
