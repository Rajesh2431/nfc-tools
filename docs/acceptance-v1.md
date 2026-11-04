# Acceptance record — M2 to M8 and v1.1 feature set

Date: 2026-11-04. Toolchain as pinned in `gradle/libs.versions.toml`
(AGP 9.4.1, Kotlin 2.4.21, Gradle 9.7.1, JDK 17, compileSdk 37 / target 36 /
min 26). Added dependencies, each verified by building on this toolchain:
Room 2.8.5 + KSP 2.3.13, DataStore 1.3.0, kotlinx.serialization 1.12.0,
kotlinx-coroutines-test 1.9.0 (matches runtime), androidx.test runner 1.7.0 /
ext-junit 1.3.0 / espresso-core 3.7.0, Compose ui-test (BOM).

## Commands and outcomes (clean build)

| Command | Outcome |
|---|---|
| `./gradlew clean assembleDebug assembleRelease` | BUILD SUCCESSFUL; release is R8-minified and **unsigned** |
| `./gradlew testDebugUnitTest` | 138 tests, 0 failures, 0 errors |
| `./gradlew lintDebug` | 0 errors, 6 warnings (version pins/targetSdk, documented in m1-acceptance) |
| `./gradlew connectedDebugAndroidTest` | 10 tests, 0 failures on emulator `Pixel_10a` (Android 17, API 37) |

Instrumented coverage: Room DAO search/filter/pruning, migrations 1→2→3
(`MigrationTestHelper`, data preserved, existing profiles stay manual),
Compose navigation of all tabs, invalid writer input, Wi-Fi record builder,
task profile create → run → log.

Emulator walkthrough: all tabs render, 0 `FATAL EXCEPTION` in logcat.
This emulator image declares `android.hardware.nfc` but runs no `nfc`
service, so `NfcAdapter` is null and the app truthfully shows "No NFC
hardware". **No tag was read or written in this record.**

## Defects found and fixed during verification

- URI prefix table was wrong from code 0x18 (now matches NFC Forum table).
- Write verification compared against the discovery cache (now same-session RF read-back).
- Reader mode toggled off mid-write (now armed for the whole screen).
- Invalid language code crashed composition (now validated on entry).
- Home tab restored the previous tab's stack (start-destination restoreState).
- `URLEncoder.encode(String, Charset)` is API 33 on a minSdk 26 app (lint NewApi; switched overload).
- Espresso 3.5.0 (from Compose ui-test) fails on current Android; pinned 3.7.0.

## Hardware validation — PENDING (AGENTS rule 11)

Nothing hardware-dependent is validated yet. Run `docs/test-plan.md`
"Physical-device checklist" on at least two phones and two tag families and
record results here before claiming support.

## Release (M8) — partially done

- Version 1.0.0 (versionCode 2); release build minifies and shrinks.
- Signing: create `keystore.properties` (untracked) with `storeFile`,
  `storePassword`, `keyAlias`, `keyPassword`; `assembleRelease` then signs.
  No signed artifact has been produced or installed yet.
- Privacy and data-safety notes: `docs/privacy.md`. Threat model:
  `docs/threat-model.md`. Auto-run decision: `docs/adr/0001-opt-in-task-auto-run.md`.

## Known limitations

- Wi-Fi/Bluetooth cannot be toggled by apps on modern Android; tasks open the
  Settings screen instead.
- MIFARE Classic: metadata only (sector keys required for content).
- Tag passwords: NTAG213/215/216 only; not interoperable with other apps.
- Chip identification is skipped while writing to avoid halting tags.
