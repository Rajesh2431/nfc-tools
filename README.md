# NFC Studio

Android-first NFC reader, writer, tag inspector and task-profile utility.
Kotlin + Jetpack Compose + Material 3, offline-first. See `docs/spec.md` for
the full specification and milestone plan.

## Status

| Milestone | Scope | State |
|---|---|---|
| M0 Discovery | Specification, architecture | Done (spec committed) |
| **M1 Foundation** | Toolchain, Compose shell, navigation, NFC state, tests | **Done** |
| M2 Reader | Tag discovery, NDEF decoding, tag details | Next |

## M1 contents

- Gradle 9.7.1 wrapper, version catalog (`gradle/libs.versions.toml`), AGP 9.4.1
  with built-in Kotlin (no `kotlin-android` plugin), Compose compiler plugin
  `2.4.21`.
- Single-activity Compose app: bottom navigation with Home / Scan / Write /
  Tasks / History / Settings.
- Real NFC adapter state (`NfcAdapter.getDefaultAdapter` / `isEnabled`),
  lifecycle-refreshed on every resume, with ready / disabled / no-hardware
  states and a system-settings deep link when disabled.
- Truthful placeholders for milestone screens; no mock tag data anywhere.
- JVM unit tests for the NFC state mapping; Android Lint clean of errors.

## Build

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest
./gradlew lintDebug
```

Requires JDK 17 and an Android SDK with platform 37 (`local.properties` or
`ANDROID_HOME`).

## Physical-device testing (M1)

Any Android 8.0+ phone can install the debug APK. Expected behavior:
- Device with NFC enabled: Home shows "NFC is ready".
- Device with NFC turned off: "NFC is turned off" plus a working settings
  deep link.
- Device without NFC: "No NFC hardware".

No tag scanning exists in M1 by design (that is M2).
