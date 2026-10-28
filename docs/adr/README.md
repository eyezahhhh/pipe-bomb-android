# Architecture decision records

Read when: you need the rationale behind a structural choice, or you are about to
change one. ADRs are append-only; supersede rather than rewrite.

| ADR | Decision |
| --- | -------- |
| [001](./001-native-kotlin-compose.md) | Native Kotlin + Compose + Media3, not a WebView/RN/KMP client |
| [002](./002-stable-media-cache-keys.md) | Cache and downloads keyed by `TrackKey`, never by session |
| [003](./003-cookie-jar-auth.md) | Cookie-jar auth with no server changes |
| [004](./004-generic-attribute-layer.md) | One generic attribute layer with web-compatible fallbacks |
| [005](./005-single-module.md) | Single Gradle module with enforced package boundaries |
| [006](./006-web-visual-source-of-truth.md) | The web client is the visual source of truth |
| [007](./007-android-auto-media-library.md) | Android Auto uses a MediaLibraryService with context-encoded media IDs |
| [008](./008-downloads-and-removable-storage.md) | Downloads are pinned cache entries with selectable internal/external storage |
| [009](./009-synchronized-collection-downloads.md) | Kept playlists/albums synchronize downloads with reference-counted release |
| [010](./010-lazy-synthetic-track-sources.md) | Playback queues use lazy synthetic TrackKey sources and full metadata queues |
| [011](./011-player-morph.md) | Mini and full players share one progress-driven in-window morph |
