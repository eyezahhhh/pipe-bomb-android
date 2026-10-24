# Testing

Read when: adding or debugging tests, or changing the API/playback boundary.

## Commands

- Unit: `./gradlew testDebugUnitTest`
- Lint: `./gradlew lintDebug`
- Full gate: `./gradlew lintDebug testDebugUnitTest assembleDebug`
- Instrumented (when an emulator is running): `./gradlew connectedDebugAndroidTest`

## What exists

| Area | Location | Coverage |
| ---- | -------- | -------- |
| URL parsing | `app/src/test/.../core/net/ServerUrlParserTest.kt` | local/public schemes, paths, invalid input |
| API errors | `app/src/test/.../core/net/ApiErrorTest.kt` | status mapping and server messages |
| Cookies | `app/src/test/.../core/net/PersistentCookieJarTest.kt` | persist, domain isolation, expiry, clear |
| Contract | `app/src/test/.../core/net/ApiContractTest.kt` | MockWebServer, unknown fields, cookie header, search defaults |
| Attributes | `app/src/test/.../core/attributes/AttributeReaderTest.kt` | formatted/raw, types, buffers, unknown |
| Track identity | `app/src/test/.../core/model/TrackKeyTest.kt` | round-trip and malformed keys |
| Track cache | `app/src/test/.../data/TrackCacheTest.kt` | TTL and clearing |

## Rules

- Test behaviour and edge cases, not implementation details.
- MockWebServer fixtures should follow the pinned spec (`openapi.lock`).
- Playback tests use a local server and the demo/remote catalog; don't assert on
  external network content.
- Never log or assert on cookies/tokens.
- No sleeps in unit tests; use coroutine test utilities and idling for Compose.

## Still needed

- Instrumented playback (stream + HLS, background, process restore).
- Paging tests beyond page one.
- Download/offline tests once downloads land.
- Screenshot/visual parity checks against the web tokens.
