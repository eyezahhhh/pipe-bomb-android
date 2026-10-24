# 001. Native Kotlin, Compose, and Media3

Status: accepted

Context: Pipe Bomb has a complete Next.js web client. The Android app needs reliable
background playback, media notifications, headset/Bluetooth controls, and eventually
Android Auto. Self-hosted users expect an app-quality experience, not a wrapped site.

Decision: build a native Android client with Kotlin, Jetpack Compose, and Media3.
Do not use a WebView/TWA, React Native, or Kotlin Multiplatform for v1.

Consequences:

- Best access to Media3, `MediaSessionService`, caching, and future downloads.
- No reuse of the web React components; the visual language is reimplemented with
  Compose tokens and documented in `../design-tokens.md` and
  `../shapes-and-motion.md`.
- Separate build/test/CI pipeline and Android SDK requirement.
- A clear path to Android Auto, Chromecast, and widgets.
- If iOS becomes a priority, extract shared Kotlin domain/networking code then; do not
  pre-pay for KMP now.
