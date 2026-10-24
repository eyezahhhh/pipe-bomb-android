# Pipe Bomb Android — agent guide

Native Android client for a self-hosted Pipe Bomb server. Kotlin, Jetpack Compose,
Media3. The web client is the canonical visual reference: the app must feel like the
same product, not stock Material 3.

| You are working on… | Read |
| ------------------- | ---- |
| System layout, package boundaries, data flow | `./docs/architecture.md` |
| State, persistence, networking, navigation | `./docs/app-infrastructure.md` |
| Colours, typography, spacing, icons | `./docs/design-tokens.md` |
| Shapes, elevation, motion | `./docs/shapes-and-motion.md` |
| Building a component or mapping a web component | `./docs/components.md` |
| Screen patterns, touch adaptation, visual consistency | `./docs/visual-language.md` |
| Playback, queue, notification, reporting | `./docs/playback.md` |
| Caching, offline playback, future downloads | `./docs/caching-and-downloads.md` |
| API calls, auth, paging | `./docs/api.md` |
| Metadata, artwork, attribute fallbacks | `./docs/attributes.md` |
| Code style and package rules | `./docs/conventions.md` |
| Build, test, install, emulator commands | `./docs/commands.md` |
| Test layout and coverage | `./docs/testing.md` |
| Why a major decision was made | `./docs/adr/README.md` |

## Invariants

- The web client is the visual source of truth: `../website/src/app/theme.css`,
  `../website/src/app/globals.scss`, and `../website/src/components/*/*.module.scss`.
- Server metadata is not fixed. Attributes are plugin-defined; use `AttributeReader`
  and only the standard fallback keys (`title`, `artist`, `front`, `thumb`, `name`).
- Never hardcode metadata schemas or assume every attribute exists.
- All playback resolves through `PlaybackResolver` and plays through `CacheDataSource`.
  UI and ViewModels never hold stream URLs.
- Cache and download identity is the stable `TrackKey`, never an audio session id,
  session URL, host, or query token.
- No server changes are assumed: auth is the `auth_token` cookie via OkHttp `CookieJar`.
- Dark theme only at launch. Dynamic colour is disabled so the brand stays consistent.
- The Pipe Bomb logo is fixed; do not redraw or replace it.
- Decode API responses with `ignoreUnknownKeys = true`; unknown attribute types must
  not crash the app.
- Never log cookies, auth headers, or full server URLs containing credentials.
- Cleartext HTTP is allowed only because the user supplies the server URL; warn on
  `http` and never ship a hardcoded plaintext endpoint.
- Minimum touch target is 48dp even when the web control is visually smaller.
- Quality gate: `./gradlew lintDebug testDebugUnitTest assembleDebug`.

## Reference material

- Web design tokens: `../website/src/app/theme.css`
- Web components: `../website/src/components/`
- Web player state: `../website/src/store/player.store.ts`
- Web attribute semantics: `../website/src/lib/attribute.util.ts`
- Canonical API contract: https://github.com/Pipe-Bomb/openapi-spec (`spec.json`, tracks server master)
- Local generated client snapshot: `../tanstack-api-client/openapi/spec.json`
- Product context: `../PRODUCT.md`

The Gradle project is scaffolded. Toolchain: Gradle 9.6, AGP 9.4, JDK 21, compile/target
SDK 37, min SDK 26. Run `./gradlew lintDebug testDebugUnitTest assembleDebug` before
finishing a change.
