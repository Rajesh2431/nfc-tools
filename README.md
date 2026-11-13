# Tap & Go

[![Android build](https://github.com/Rajesh2431/nfc-tools/actions/workflows/android.yml/badge.svg)](https://github.com/Rajesh2431/nfc-tools/actions/workflows/android.yml)

Android NFC reader, writer, tag library and automation toolkit. Kotlin, Jetpack Compose, Material 3,
offline-first. Specification: `docs/spec.md`; agent rules: `AGENTS.md`.

## Features

| Area | What it does |
|---|---|
| **Home** | Customizable dashboard (reorder/hide/pin): NFC status, quick actions, recent scans and runs, profiles, favourites, device info with root and Premium status. |
| **Read** | Tag type and chip (NTAG213/215/216, Ultralight, ICODE…), technologies, UID when exposed, ATQA/SAK, memory, NDEF format/size/writable/lockable, decoded records (text, URI, Smart Poster, contact, Wi-Fi, Bluetooth, app launch, task tag, MIME, external), hex/ASCII, diagnostics export, save to library. |
| **Write** | Text, URL, phone, SMS, email, vCard, location, address, Wi-Fi (WSC), Bluetooth (OOB), app launch (AAR), MIME, raw records from NDEF Lab. Exact size preview, capacity checks, confirmation, read-back verification, templates. |
| **Tag operations** | Copy NDEF (not UID), erase, format, lock (two confirmations), NTAG password set/remove, memory dump. |
| **Library** | Names, notes, icons, folders, labels, favourites, photos, search/filter/sort, duplicates, compare, restore to tag, CSV/TXT export, encrypted backup. |
| **Tasks** | Profiles with task tags and opt-in auto-run (ADR 0001); visual **workflows** with if/else-if/else, and/or/not, loops, delays, variables, retries, 30 allowlisted actions, execution history, JSON import/export. |
| **Triggers** | NFC tag, home-screen shortcut, Quick Settings tile, schedule (WorkManager), other apps via a token-protected intent (ADR 0002, `docs/integration-api.md`). |
| **Developer** | APDU console, NDEF Lab (hex/TLV decoder, raw record builder, hexdump), HCE NDEF tag emulation. |
| **Root Manager** | Optional: detection (Magisk/KernelSU/APatch), access check, allowlisted connectivity/display/power commands, profiles, console with consent, diagnostics, history. |
| **Premium** | Entitlement seam; no billing configured in this build, debug-only testing switch (`docs/premium.md`). |

## Status

See `DEVELOPMENT_STATUS.md` for per-feature status. All features build and
pass JVM tests; **no feature is hardware-verified yet**
(`docs/hardware-testing.md`). Release signing needs your `keystore.properties`.

## Documentation

`docs/architecture.md` · `docs/permissions.md` · `docs/integration-api.md` ·
`docs/premium.md` · `docs/hardware-testing.md` · `docs/privacy.md` ·
`docs/threat-model.md` · `docs/adr/`

## Build

CI (`.github/workflows/android.yml`) runs on every push and pull request:
debug build, unit tests, lint and an unsigned R8 release build, and uploads
the APKs and reports as workflow artifacts. Instrumented tests need a device
and are run locally (`connectedDebugAndroidTest`).

```bash
./gradlew assembleDebug                 # debug APK
./gradlew testDebugUnitTest             # JVM unit tests
./gradlew lintDebug
./gradlew connectedDebugAndroidTest     # device/emulator tests
./gradlew assembleRelease               # signed if keystore.properties exists
```

Requires JDK 17 and Android SDK platform 37.

## Release

1. Create a keystore: `keytool -genkeypair -v -keystore release.jks -keyalg RSA -keysize 4096 -validity 10000 -alias tapandgo`
2. Add an untracked `keystore.properties` in the project root:
   `storeFile=release.jks`, `storePassword=…`, `keyAlias=tapandgo`, `keyPassword=…`
3. `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
   (R8-minified, resources shrunk). Without the file the APK is unsigned.
4. Install: `adb install -r app/build/outputs/apk/release/app-release.apk`

Release builds have no development Premium switch.
