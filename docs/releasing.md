# Releasing and updating

Read when: preparing an installable APK, rotating signing keys, or changing version
numbers.

## Update rules

Android will install a new APK over an existing install only when:

- the package name is unchanged (`net.pipebomb.android`),
- the APK is signed with the same certificate as the installed app,
- `versionCode` is not lower than the installed version,
- the device still satisfies `minSdk` and the APK ABI requirements.

The app has no in-app updater. Users install the signed APK from the GitHub Release.

## Signing key

The release key is long-lived and must never be committed. The local build reads, in
order:

1. `ANDROID_KEYSTORE_FILE`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and
   `ANDROID_KEY_PASSWORD` environment variables.
2. `keystore.properties` in the project root (ignored by Git).
3. `~/.config/pipebomb-android/keystore.properties`.

The properties file contains:

```properties
storeFile=/absolute/path/to/release.jks
storePassword=...
keyAlias=pipebomb
keyPassword=...
```

Back up the keystore and properties file. If the key is lost, future APKs cannot update
an existing install and users must uninstall, losing app data.

CI uses the repository secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Never print, commit, or upload the keystore outside the release workflow.

## Versioning

- Debug/local defaults: `versionCode = 2`, `versionName = 0.2.0`.
- Signed CI releases use `1000 + GitHub run number` for a monotonic `versionCode` and
  the tag without the leading `v` for `versionName`.
- Override locally with `-PversionCode=...` and `-PversionName=...`.

## Build a release

```bash
./gradlew lintDebug verifyRoborazziDebug koverHtmlReportDebug assembleRelease
```

The signed APK is `app/build/outputs/apk/release/app-release.apk`. Verify it:

```bash
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

## Publish

Create and push a tag:

```bash
git tag v0.2.0
git push origin v0.2.0
```

`.github/workflows/release.yml` runs the quality gate, builds a signed release, checks
the signature, and attaches the APK and SHA-256 file to the GitHub Release.

## One-time migration from debug builds

Debug builds use the Android debug certificate. The first release-signed install
requires uninstalling the debug app once, which deletes app data. After that, every
release signed with the same release key installs over the previous release.
