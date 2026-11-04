# Tap & Go

[![Android build](https://github.com/Rajesh2431/nfc-tools/actions/workflows/android.yml/badge.svg)](https://github.com/Rajesh2431/nfc-tools/actions/workflows/android.yml)

Android NFC reader, writer, tag library and automation toolkit. Kotlin, Jetpack Compose, Material 3,
offline-first. Specification: `docs/spec.md`; agent rules: `AGENTS.md`.

## Features

| Tab | What it does |
|---|---|
| **Read** | Tag type and chip (NTAG213/215/216, Ultralight, ICODE…), technologies, serial number, ATQA/SAK, memory size, NDEF format, size, writable/lockable, decoded records (text, URI, Smart Poster, contact, Wi-Fi, Bluetooth, app launch, task tag, MIME), hex/ASCII views, safe link opening, diagnostics export, APDU console (Advanced mode). |
| **Write** | Guided records: text, URL, phone, SMS, email, contact (vCard), location, address, Wi-Fi (WSC), Bluetooth (OOB), app launch (AAR), custom MIME. Exact size preview, capacity/read-only checks, overwrite/format confirmation, same-session read-back verification, templates. |
| **Other** | Copy tag, erase, lock (two confirmations), format blank tags, set/remove NTAG password, raw memory dump. |
| **Tasks** | App-side profiles of allowlisted actions; scan triggers, task tags (token only), opt-in auto-run for safe actions (ADR 0001), execution log. |
| **History** | Search, filters, favourites, delete, export. Settings: theme, privacy, retention, advanced mode, backup/import. |

## Status

| Milestone | State |
|---|---|
| M1 Foundation | Done |
| M2 Reader · M3 Writer · M4 Persistence · M5 Tasks · M6 Advanced · M7 Hardening | Implemented and tested on JVM + emulator |
| Hardware validation | **Pending** — see `docs/test-plan.md` |
| M8 Release | Release build OK; signing needs your `keystore.properties` |

Evidence: `docs/acceptance-v1.md`.

## Build

CI (`.github/workflows/android.yml`) runs on every push and pull request:
debug build, unit tests, lint and an unsigned R8 release build, and uploads
the APKs and reports as workflow artifacts. Instrumented tests need a device
and are run locally (`connectedDebugAndroidTest`).

```bash
./gradlew assembleDebug                 # debug APK
./gradlew testDebugUnitTest             # 138 JVM tests
./gradlew lintDebug
./gradlew connectedDebugAndroidTest     # device/emulator tests
./gradlew assembleRelease               # signed if keystore.properties exists
```

Requires JDK 17 and Android SDK platform 37.
