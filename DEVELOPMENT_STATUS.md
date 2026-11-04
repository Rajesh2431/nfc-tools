# DEVELOPMENT_STATUS — Tap & Go

Statuses: NOT_STARTED · IN_PROGRESS · IMPLEMENTED · TESTED · HARDWARE_VERIFIED · BLOCKED.
No feature is HARDWARE_VERIFIED yet: no physical NFC test has been recorded.
Resume from the first IN_PROGRESS / NOT_STARTED row of the current phase.

| Phase | Feature | Status | Notes / tests |
|---|---|---|---|
| 1 | Rebrand (name, package com.tapandgo, logo, brand palette) | TESTED | 138 unit / 10 instrumented pass after rename |
| 1 | Hilt DI (2.60.1, verified on AGP 9.4.1) | TESTED | all view models @HiltViewModel |
| 1 | Home dashboard (customizable cards: reorder/hide/pin) | TESTED | DashboardLayoutTest (4); NavigationSmokeTest on emulator |
| 1 | Navigation Home/Read/Write/Tasks/Tools + Tools hub, About | TESTED | tag ops/history/APDU/settings/about under Tools; status-bar contrast fixed |
| 1 | Gradle modularization | NOT_STARTED | deferred: single :app keeps KSP/Room/Hilt simple while features land; packages are layered (core/data/domain/feature) so extraction is mechanical. Revisit in phase 8. |
| 2 | Reader, NDEF parser, chip identification, history | TESTED | from NFC Studio; hardware pending |
| 2 | Writer (text/URI/phone/SMS/email/vCard/geo/Wi-Fi/BT/AAR/MIME) | TESTED | hardware pending |
| 2 | Tag library (names, folders, photos, notes, compare) | NOT_STARTED | history + favourites exist |
| 3 | Copy/erase/lock/format/NTAG password/memory dump | TESTED | hardware pending |
| 3 | CSV/TXT export, scan comparison, raw NDEF editor | NOT_STARTED | JSON export exists |
| 4 | Task profiles, allowlisted actions, task tags, opt-in auto-run | TESTED | ADR 0001 |
| 4 | Workflow engine (conditions, loops, variables ${VAR}) | NOT_STARTED | |
| 4 | Device actions (flashlight, volume, ringer, brightness, alarm, TTS…) | NOT_STARTED | Wi-Fi/BT toggles need root on Android 10+/13+ |
| 5 | Premium entitlement abstraction | NOT_STARTED | no fake purchases |
| 5 | HCE NDEF Type 4 emulation | NOT_STARTED | |
| 6 | Root manager, RootExecutionService (allowlist, timeout) | NOT_STARTED | |
| 7 | QS tile, shortcuts, Tasker/intent API, webhooks | NOT_STARTED | |
| 8 | Hardening, hardware tests, release signing | IN_PROGRESS | see docs/acceptance-v1.md |
