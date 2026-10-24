# 004. Generic attribute layer

Status: accepted

Context: Pipe Bomb has no fixed metadata schema. Plugins define attributes, including
types, keys, formatting, and buffer resources. A native app cannot be written against
`title`, `artist`, and `cover` as if they were guaranteed columns.

Decision: every entity exposes an `AttributeMap` decoded by one custom serializer.
All UI reads metadata through `AttributeReader`, which mirrors the web's semantics
(`values` vs `formatted`, multiple vs single, typed buffer resources). Standard keys
such as `title`, `artist`, `front`, `thumb`, and `name` are fallbacks, not a schema.
Unknown attribute types are skipped, never fatal.

Consequences:

- The app renders plugin-provided metadata without knowing plugin internals.
- Text must be laid out defensively: truncation, nullable values, and missing artwork
  are normal.
- No compile-time guarantee for any metadata key; tests need fixtures representing
  missing, partial, and unknown attributes.
- If a future server adds a new attribute type, the app must ignore it gracefully
  until support ships.
- The web helper `../../../website/src/lib/attribute.util.ts` remains the behavioural
  reference, so both clients read metadata the same way.
