# Commands

Read when: building, testing, installing, or setting up the Android project.

## Prerequisites

- JDK 21 (the environment may default to a newer JDK; set `JAVA_HOME`).
- Android SDK with `platforms;android-37.0`, `build-tools;36.0.0`, and `platform-tools`.
- `local.properties` with `sdk.dir=...` (ignored by git).
- A device/emulator for install and instrumented work.

## Build and verify

| Action | Command |
| ------ | ------- |
| Debug APK | `./gradlew assembleDebug` |
| Unit + JVM UI tests | `./gradlew testDebugUnitTest` |
| Screenshot tests | `./gradlew verifyRoborazziDebug` |
| Record screenshots | `./gradlew recordRoborazziDebug` |
| Coverage report | `./gradlew koverHtmlReportDebug` |
| Lint | `./gradlew lintDebug` |
| Full gate | `./gradlew lintDebug verifyRoborazziDebug koverHtmlReportDebug assembleDebug` |
| Install debug | `./gradlew installDebug` |
| Signed release APK | `./gradlew assembleRelease` (see `releasing.md`) |
| Verify release signature | `$ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk` |
| Publish signed release | Push a `v*` tag; `.github/workflows/release.yml` builds it |
| Clean | `./gradlew clean` |

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

## Local server testing

The dev server runs on port 3000. From the emulator, use `http://10.0.2.2:3000`;
from a physical device on the LAN, use the machine's LAN address. HTTP is accepted
with an in-app warning.

## Emulator

If the SDK emulator cannot find system X11 libraries, point `LD_LIBRARY_PATH` at the
libX11 package and start it headlessly:

```bash
emulator -avd <name> -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect
adb devices
```

## CI

`.github/workflows/ci.yml` runs the debug quality gate on push/PR. A `v*` tag triggers
`.github/workflows/release.yml`, which builds a signed release. Never commit signing
material; see `releasing.md`.
