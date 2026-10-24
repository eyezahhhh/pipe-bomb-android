# API

Read when: adding an endpoint, changing auth, decoding a response, handling paging, or
checking compatibility. Attribute decoding lives in `attributes.md`.

## Contract source

Canonical schema: https://github.com/Pipe-Bomb/openapi-spec (`spec.json`). It is
generated from the server's `master` branch. The local
`../../tanstack-api-client/openapi/spec.json` is a snapshot and may lag; at the time of
writing the public spec also has `GET /playlists/public` and
`GET /ephemeral/creation-session/{uuid}`.

Use the public spec to:

- verify request/response shapes while implementing a repository;
- diff locally before upgrading models;
- pin a commit SHA when a build task regenerates or validates client code.

Raw spec:
`https://raw.githubusercontent.com/Pipe-Bomb/openapi-spec/master/spec.json`.
Do not read a moving `master` at runtime; pin a commit and bump it deliberately.
The current pin is in `openapi.lock` at the repo root.

## Client generation

When the Gradle project exists, pin a commit from `Pipe-Bomb/openapi-spec` and
generate Kotlin/Retrofit interfaces into build output or a dedicated generated source
set. Treat generated code as a starting point:

- Keep the hand-written `AttributeMap` serializer, `CookieJar`, and error mapping.
- Do not edit generated files by hand; diff and review them on deliberate spec bumps.
- Regenerate only when a new endpoint/shape is needed, not on every build.

## Base URL and auth

- User supplies a base URL, e.g. `https://music.example.com` or
  `http://192.168.1.20:3000`, optionally with a path prefix.
- Normalise to scheme + host + optional port + optional path; strip a trailing slash.
- `POST /users/login` and `POST /users/signup` return `User` and set `auth_token`.
- Cookie: `HttpOnly`, `SameSite=Lax`, 30 days, `Secure` over HTTPS. Not in JSON body.
- Use OkHttp `CookieJar` with app-private persistence (ADR 003). The server accepts
  `Authorization: Bearer <jwt>` but does not issue a token; do not rely on it.
- `GET /users/me` probes the session on launch. 401 routes to Login.
- `POST /users/logout` clears the cookie; also clear local user state.
- `GET /setup` returns `{ needsSetup }`. If true, direct the user to browser setup;
  v1 does not implement the setup wizard.

## Endpoint groups

| Feature | Endpoints |
| ------- | --------- |
| Connect | `GET /setup`, `GET /`, `GET /users/me` |
| Auth | `POST /users/login`, `POST /users/signup`, `POST /users/logout` |
| Home | `GET /playlists`, `GET /playlists/public`, `GET /playback-history/me`, `POST /tracks` |
| Search | `POST /search`, `GET /search/sources`, `POST /ephemeral/search` |
| Saved | `GET /tracks/saved`, `GET /albums/saved`, `GET /artists/saved` |
| Albums | `GET /albums/{albumUuid}`, `POST /albums`, `GET|POST /albums/{pluginId}/{identifierId}/{identity}`, `PUT|DELETE /save`, `GET /urls` |
| Artists | `GET /artists/{artistUuid}`, `POST /artists`, `GET|POST /artists/{pluginId}/{identifierId}/{identity}`, `PUT|DELETE /save`, `GET /urls` |
| Tracks | `GET /tracks/{pluginId}/{libraryId}/{trackId}`, `POST /tracks`, `GET /audio`, `GET /urls`, `PUT|DELETE /save` |
| Playlists | `GET /playlists`, `GET /playlists/public`, `PUT /playlists`, `GET|POST|DELETE /playlists/{uuid}`, `GET /all`, `PATCH /tracks`, `PATCH /attributes`, `PATCH /visibility`, `GET|POST|PATCH|DELETE /filters`, `GET|PUT|DELETE /members` |
| Libraries | `GET /libraries`, `GET /libraries/{pluginId}/{libraryId}`, `POST /libraries/{pluginId}/{libraryId}/search` |
| Playback | `GET /tracks/.../audio`, `POST /playback-history/me` |
| Ephemeral | `GET /ephemeral`, `POST /ephemeral/search`, `GET /ephemeral/creation-session/{uuid}`, `GET /ephemeral/attribute-buffer/{file}` |
| User | `GET /users/{uuid}`, `GET /users/search` |
| Images | `GET /resources/{dir}/{file}`, `GET /icons/{pluginId}/{iconId}` |

## Track and identity keys

`TrackKey` is `pluginId:libraryId:trackId`. Use it for queue entries, cache identity,
and route arguments. Do not use UUID alone: tracks can be ephemeral and have no UUID.

Albums and artists may be identified by a database `uuid` or an identity triple
`pluginId~identityId~value` (matching web routes). One resolve helper accepts either
form and dispatches to the UUID or identity endpoint. Ephemeral albums/artists can
have `uuid = null`; use the first identity for navigation.

Batch track lookup: `POST /tracks` with `{ tracks: [TrackIdDto, ...] }`, maximum 50 per
request. Deduplicate keys and chunk larger lists. Use for playlists, history, queue
display, and saved-track hydration.

## Paging and caching

| Endpoint | Limit / behaviour |
| -------- | ----------------- |
| `GET /tracks/saved` | `page`, `pageSize` (server caps at 30, default 20) |
| `GET /albums/saved`, `GET /artists/saved` | `page`, `pageSize` required by the spec |
| `GET /playback-history/me` | `page`, `pageSize`; use for Home recent activity |
| `GET /playlists/{uuid}` | pass `tracks` to include a preview; `/all` returns every track id |
| `POST /search` | full arrays; no paging |
| `POST /ephemeral/search` | full arrays per source |

Use Paging 3 for saved lists. For playlists, fetch `/all`, batch-hydrate tracks in
chunks of 50, and expose a paged list. Keep metadata in memory for 10 minutes.

Search request follows `SearchDto`: `withTracks`, `withAlbums`, `withArtists`,
`attributes` (send `[]` unless filtering), optional `query` and `sort`.

## Errors

Server errors are `{ "error": "...", "message": "...", "statusCode": 401 }`.

- 401: session expired; emit one global event, not a toast per request.
- 403: missing privilege; show `message`.
- 404: treat as "not found" for that entity; don't crash lists.
- 409: conflict (e.g. username taken); show inline form error.
- 5xx: retry only idempotent requests; otherwise show a retry action.
- Network/parse: show offline/retry state; never leak exception text to the user.

## Compatibility

- `ignoreUnknownKeys = true`; optional fields are nullable in models.
- If the server lacks an endpoint (older version), degrade that feature.
- Keep a diagnostic view with server URL, API version, app version, session status.
- When the public spec adds fields, update models only where the UI uses them.

## Where to look

- Public spec: https://github.com/Pipe-Bomb/openapi-spec
- Local snapshot: `../../tanstack-api-client/openapi/spec.json`
- Web track batching: `../../website/src/lib/track-batcher.util.ts`
- Web entity routing: `../../website/src/lib/api.util.ts`
