# Attributes

Read when: rendering metadata, adding a metadata field, handling artwork, or dealing
with missing/unknown fields. API/auth/paging lives in `api.md`.

## Shape

`attributes` is a map from plugin-defined key to a typed attribute:

```json
{
  "title": {
    "type": "string",
    "pluginId": "...",
    "sourceId": "...",
    "values": ["..."],
    "formatted": ["..."]
  },
  "front": {
    "type": "buffer",
    "values": [
      { "uuid": "...", "url": "https://.../resources/...", "extension": "webp", "sha256": "..." }
    ],
    "formatted": []
  }
}
```

Types: `string`, `boolean`, `integer`, `decimal`, `buffer`.

## AttributeReader

Mirror the web semantics from `../../website/src/lib/attribute.util.ts`:

```kotlin
fun string(map: AttributeMap?, key: String, multiple: Boolean = false, formatted: Boolean = true): List<String>?
fun integer(map: AttributeMap?, key: String, multiple: Boolean = false): List<Long>?
fun decimal(map: AttributeMap?, key: String, multiple: Boolean = false): List<Double>?
fun boolean(map: AttributeMap?, key: String, multiple: Boolean = false): List<Boolean>?
fun resource(map: AttributeMap?, key: String): Resource?
fun any(map: AttributeMap?, key: String): AttributeValue?
```

Rules:

- Return `null` when the key is absent, the value list is empty, or the type differs.
- When `formatted = true` and `formatted` is non-empty, return those strings;
  otherwise stringify raw values.
- Buffer values are `Resource` objects, never strings.
- Decode unknown attribute `type` values as `UnknownAttribute` or skip them. Never
  throw while rendering a list.
- Unknown keys are expected; the UI is driven by attributes, not a fixed schema.

## Standard fallback keys

These are conventions the web client already relies on. They are not a schema; always
fall back gracefully.

| Entity | Key | Type | Fallback |
| ------ | --- | ---- | -------- |
| Track | `title` | string | `track.title`, then "Unknown Track" |
| Track | `artist` | string[] | `track.artists`, then "Unknown Artist" |
| Track | `front` | buffer | gradient placeholder |
| Track | `genre` | string | omit |
| Track | `duration` | decimal | omit / player duration |
| Album | `title` | string | "Unknown Album" |
| Album | `artist` | string[] | `album.artists` |
| Album | `front` | buffer | gradient placeholder |
| Album | `year` | integer | omit |
| Artist | `name` | string | "Unknown Artist" |
| Artist | `thumb` | buffer | circular user-icon placeholder |
| Artist | `genres` | string[] | omit |
| Playlist | `title` | string | "Unnamed Playlist" |
| Playlist | `thumb` | buffer | gradient placeholder |

## Artist formatting

1. If `track.artists`/`album.artists` exists, render each linked artist name and its
   `joinPhrase`; use `", "` when `joinPhrase` is null.
2. Otherwise use the `artist` string attribute list joined with `", "`.
3. Otherwise "Unknown Artist".

Navigate to the artist UUID route, or the identity route when `uuid` is null and an
identity exists.

## Rendering rules

- Read every metadata value through `AttributeReader`; no raw map lookups in UI.
- Assume text can be long, empty, or non-Latin. Use `maxLines` + ellipsis and dynamic
  row heights for font scale.
- Buffer attributes always render through the shared image component; no direct
  network calls.
- Artwork size query parameters should match the rendered size.
- Never derive identity, navigation, or playback keys from a display attribute.
- Never cache an attribute map beyond the metadata cache TTL (10 minutes).

## Testing

- Fixtures for: missing attributes, empty values, formatted vs raw, multiple values,
  unknown type, buffer resource, and wrong-type value for a known key.
- Verify no exceptions from `AttributeReader` for any malformed map.
- Verify title/artist/artwork fallbacks render on the track row, album card, artist
  card, and player.

## Invariants

- One reader implementation; no ad-hoc parsing.
- Unknown attribute types never crash the app.
- Standard keys are fallbacks, not a required metadata schema.
