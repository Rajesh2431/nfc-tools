# DEVELOPMENT_STATUS — Tap & Go

Statuses: NOT_STARTED · IN_PROGRESS · IMPLEMENTED · TESTED · HARDWARE_VERIFIED · BLOCKED.
No feature is HARDWARE_VERIFIED yet: no physical NFC test has been recorded.
Unit tests: 196 passing (2026-11-13). Instrumented tests need an emulator/device run.
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
| 3 | NDEF Lab: hex/TLV decoder, raw record builder (any TNF 0–5, type/id/payload hex), hexdump, import from last tag, write via Writer | TESTED | core/ndef/NdefLab.kt, RecordSpec.Raw + structural validation; NdefLabTest (5). Premium-gated |
| 4 | Task profiles, allowlisted actions, task tags, opt-in auto-run | TESTED | ADR 0001 |
| 4 | Workflow engine core: blocks (action/if/else-if/else/repeat/while/delay/set/break/continue/stop), ${VAR} interpolation + masking, and/or/not + comparison operators, step/loop/timeout limits, retries, continue/stop on error, allowlisted action registry | TESTED | automation/engine/*; WorkflowEngineTest (9). Not yet wired to UI, storage or Android actions |
| 4 | Device actions (flashlight, volume, ringer, brightness, rotation, timeout, alarm, timer, TTS, notification, URL, app, settings panels, share; Wi-Fi/BT → Unsupported on 10+/13+) | IMPLEMENTED | automation/actions/AndroidActions.kt; ActionParamsTest; hardware/device run pending |
| 4 | Built-in variables (date/time, battery, device, Wi-Fi/BT/network, storage) | IMPLEMENTED | BuiltinVariables.kt; location/SSID not offered (permission) |
| 4 | Workflow storage (schema v5: workflows + workflow_runs with FK cascade) and execution history | IMPLEMENTED | tree stored as validated JSON instead of separate Action/Condition tables (deliberate: atomic edits, versionable import); MIGRATION_4_5 needs instrumented run |
| 4 | Visual workflow builder (nested tree, add/edit/reorder/enable/duplicate/delete, condition editor all/any/not) and manual run with result log | IMPLEMENTED | WorkflowEditTest (5); UI test pending |
| 4 | Workflow triggers: NFC tag (token), pinned shortcut, Quick Settings tile, WorkManager schedule (≥15 min), other apps (exported alias + token + 2 opt-ins); launch screen with confirm/auto-run; run source recorded | TESTED | ADR 0002; schema v6 + MIGRATION_5_6 (instrumented test written, not run: emulator stopped); WorkflowTriggerTest (6). Background runs report screen-opening actions as Unsupported. Device run pending |
| 4 | Workflow import/export (JSON, strict validation, no tokens/triggers) | TESTED | WorkflowTransfer; WorkflowTriggerTest |
| 5 | Premium entitlement abstraction (BillingProvider seam, NotConfiguredBilling, debug-only DEVELOPMENT entitlement labelled testing-only), comparison screen, gates (workflows > 3, schedule, shortcuts/tile, other apps, emulation, NDEF Lab) | TESTED | billing/Entitlements.kt; resolver test. No Play Billing configured, so release builds cannot unlock Premium (stated in UI). Features that shipped free stay free |
| 5 | HCE NDEF Type 4 emulation (read-only, NDEF AID only, component disabled until enabled, APDU session log) | TESTED | nfc/hce/*; Type4EmulatorTest (4). Needs HCE-capable phone + reader for HARDWARE_VERIFIED |
| 6 | Root detection (su paths, Magisk/KernelSU/APatch packages, test-keys) and access check via `su` (uid=0), implementation + version, SELinux | TESTED | root/RootExecution.kt; RootExecutionTest with fake shell. Never assumes root: no su → NOT_ROOTED, refused grant → DENIED |
| 6 | RootExecutionService: allowlisted commands built from constants/validated numbers, stdin script, 15 s timeout, exit code + bounded stdout/stderr, redaction, history (category/outcome only) | TESTED | single mutex; not reachable from exported components |
| 6 | Root Manager UI: connectivity (airplane/data/Wi-Fi/BT/network reset), display (density, resolution, animation scale, brightness), power (reboot targets, power off, restart SystemUI) with confirmation, 7 profiles with per-step SDK checks, console with per-run consent, diagnostics, history | IMPLEMENTED | needs a rooted device for HARDWARE_VERIFIED |
| 6 | Root workflow actions (airplane, mobile data, Wi-Fi, BT) | IMPLEMENTED | opt-in setting; refused for runs started by other apps |
| 4 | More actions: dialer, SMS draft, email draft, map, app info, calendar event, HTTPS webhook | TESTED | params tested; intents only (no SMS sending, no CALL_PHONE); INTERNET permission only for webhook |
| 7 | External intent API for Tasker/MacroDroid (exported `.ExternalLaunch`, token, global + per-workflow opt-in, confirm unless auto-run) | TESTED | docs/integration-api.md; WorkflowTriggerTest. adb/Tasker run pending |
| 7 | Quick Settings tile, pinned workflow shortcuts, static launcher shortcuts, `tapandgo://` deep links, HTTPS webhook action | IMPLEMENTED | shortcuts.xml, NavHost deep links; device run pending |
| 7 | Plugin architecture | IMPLEMENTED (in-app only) | allowlisted ActionRegistry; third-party code/action loading deliberately not supported (security), integration via intent API — see docs/integration-api.md |
| 8 | Docs: architecture, permissions, integration API, Premium, hardware testing guide, release/signing in README, threat model + privacy updated | IMPLEMENTED | docs/ |
| 8 | UI smoke test for Premium/Root/NDEF Lab/Emulation/Workflows | IMPLEMENTED | NewToolsSmokeTest written, NOT run (emulator stopped for host memory) |
| 8 | Hardening, hardware tests, release signing | BLOCKED | needs physical NFC device + tags (docs/hardware-testing.md), a rooted phone, an emulator run for instrumented tests, and your keystore.properties. assembleDebug, 192 unit tests, lintDebug (0 errors) and unsigned R8 assembleRelease pass locally |
| 8 | App-wide reader mode: owned by MainActivity while resumed, so tags tapped inside the app are never dispatched to other apps/system viewer; screens register via NfcTagRouter (owner-scoped, fixes transition race that disabled reader mode); unhandled tags open Read, task tags run in-app; paused on the Emulation screen (reader mode blocks HCE) | TESTED | TagRouterTest (4); needs device confirmation |
| 8 | GitHub Actions CI (build, unit tests, lint, unsigned release, artifacts) | IMPLEMENTED | .github/workflows/android.yml |
