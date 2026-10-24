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
