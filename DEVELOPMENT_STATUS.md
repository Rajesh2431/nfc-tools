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
| 2 | Tag library: names, descriptions, notes, icons, folders, labels, favourites, photos, search/filter/sort, duplicate detection, compare, restore to tag | IMPLEMENTED | schema v4 + MIGRATION_3_4; LibraryTest, OtherViewModelTest restore; DAO + migration instrumented tests written, NOT yet run (emulator stopped: host low on memory) |
| 2 | Encrypted backup (PBKDF2-SHA256 + AES-256-GCM) | TESTED | BackupCryptoTest (found + fixed missing envelope fields) |
| 3 | Copy/erase/lock/format/NTAG password/memory dump | TESTED | hardware pending |
| 3 | CSV/TXT export (library), tag comparison | TESTED | CSV formula-injection guard tested |
| 3 | Raw NDEF editor, hex editor | NOT_STARTED | |
| 4 | Task profiles, allowlisted actions, task tags, opt-in auto-run | TESTED | ADR 0001 |
| 4 | Workflow engine core: blocks (action/if/else-if/else/repeat/while/delay/set/break/continue/stop), ${VAR} interpolation + masking, and/or/not + comparison operators, step/loop/timeout limits, retries, continue/stop on error, allowlisted action registry | TESTED | automation/engine/*; WorkflowEngineTest (9). Not yet wired to UI, storage or Android actions |
| 4 | Built-in variables provider (battery, device, Wi-Fi/BT state, location, tag) | NOT_STARTED | |
| 4 | Visual workflow builder UI, workflow storage (TaskEntity/ActionEntity/ConditionEntity) | NOT_STARTED | |
| 4 | Device actions (flashlight, volume, ringer, brightness, alarm, TTS…) | NOT_STARTED | Wi-Fi/BT toggles need root on Android 10+/13+ |
| 5 | Premium entitlement abstraction | NOT_STARTED | no fake purchases |
| 5 | HCE NDEF Type 4 emulation | NOT_STARTED | |
| 6 | Root manager, RootExecutionService (allowlist, timeout) | NOT_STARTED | |
| 7 | QS tile, shortcuts, Tasker/intent API, webhooks | NOT_STARTED | |
| 8 | Hardening, hardware tests, release signing | IN_PROGRESS | see docs/acceptance-v1.md |
