# M1 acceptance record

Date: 2026-10-19. Environment: Linux x86_64, OpenJDK 17.0.20.1, Android SDK at
`/home/dog/Android/sdk` (platforms 35/36/37.0, build-tools 35/36/37.0.0).

## Toolchain pins

| Component | Version | Verified against |
|---|---|---|
| AGP | 9.4.1 | Max API 37, needs Gradle >= 9.6.0, Build Tools 36, JDK 17 (developer.android.com/build/releases/agp-9-4-0-release-notes) |
| Gradle | 9.7.1 wrapper | Cached locally; >= AGP minimum 9.6.0 |
| Kotlin | built-in (AGP 9), effective KGP 2.4.21 via compose plugin | developer.android.com/build/migrate-to-built-in-kotlin |
| Compose BOM | 2026.09.00 (material3 1.4.0, ui 1.12.1) | Requires compileSdk 37 + AGP 9 (developer.android.com/develop/ui/compose/compiler) |
| Navigation Compose | 2.10.2 | Maven metadata (stable line) |
| Activity Compose / Lifecycle / Core | 1.13.0 / 2.11.0 / 1.19.1 | Maven metadata |
| compileSdk / target / min | 37 / 36 / 26 | Platform 37.0 installed; Play target policy re-check scheduled for M8 |

## Commands and outcomes

| Command | Outcome |
|---|---|
| `gradle wrapper --gradle-version 9.7.1 --distribution-type bin` | BUILD SUCCESSFUL (first run, 1m 11s) |
| `./gradlew assembleDebug` | BUILD SUCCESSFUL; `app/build/outputs/apk/debug/app-debug.apk` (18.9 MB, debug, unminified) |
| `./gradlew testDebugUnitTest` | BUILD SUCCESSFUL; 4 tests, 0 failures, 0 ignored (`NfcAvailabilityTest`) |
| `./gradlew lintDebug` | BUILD SUCCESSFUL; 0 errors; 4 informational findings (below) |

Compile-fix iterations during M1: two Kotlin compile errors (a leftover
parameter reference and a missing icon import) were fixed; no warnings
suppressed.

## Lint findings (informational, build passes)

1. `OldTargetApi` x2 — targetSdk 36 is a deliberate, documented choice
   (Play-compliant as of 2026-10); re-evaluate at M8 release checklist.
2. `AndroidGradlePluginVersion` — Gradle 9.8.1 exists; 9.7.1 kept as the
   AGP-documented compatible, locally cached version.
3. `ObsoleteSdkInt` — suggests merging `mipmap-anydpi-v26` into
   `mipmap-anydpi`; AAPT2 resource linking rejects that rename (adaptive-icon
   root requires the v26 qualifier even at minSdk 26), so the folder is kept.
   False positive, documented here instead of disabled.

## Emulator smoke test (NOT a hardware validation)

Device: `emulator-5554`, Android 17 (SDK 37), x86_64. Emulators do not prove
NFC behavior on real tags.

- `adb install -r app-debug.apk` → Success.
- `adb shell am start -W -n dev.nfcstudio.app/.MainActivity` → Status: ok,
  LaunchState: COLD, TotalTime: 1002 ms.
- Screenshot review: Home renders title, real NFC status card, quick actions,
  6-item bottom bar; Scan screen renders the truthful "Scanner arrives with
  M2 / Not implemented yet" state; tab navigation switches destinations.
- `logcat` grep for `FATAL EXCEPTION` / app `AndroidRuntime` crashes: 0 hits.

Observation: this emulator image exposes a virtual NFC adapter, so the app
reports "NFC is ready" — that is the genuine platform state as read through
`NfcAdapter`, exercising the READY code path. The no-hardware path is covered
by unit tests; the disabled path needs a physical device.

## Physical-device steps (pending — required for hardware claims)

1. Enable "Install unknown apps" for the transfer method; install
   `app-debug.apk`.
2. Confirm the Home status card matches reality: NFC on → "ready"; NFC off →
   "turned off" (tap the settings deep link, toggle NFC, return — the card
   must refresh on resume); no-NFC device → "no NFC hardware".
3. Walk all six tabs; confirm placeholders state their milestone and nothing
   simulates tag data.

## Exit gate

Spec M1 gate: "Clean debug build" — met (assembleDebug + tests + lint pass).
Git baseline created; conventional commits only.

## Known limitations / unresolved issues

- No release build verified in M1 (per AGENTS rule 3 ordering; release
  pipeline is M8). `isMinifyEnabled` is configured but unverified.
- No Compose UI or instrumented tests yet (added with M2 screen work).
- Launcher icon is a vector placeholder; final icon design is M8.
- Lint informational findings listed above are accepted, not suppressed.
