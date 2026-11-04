# NFC STUDIO
Full Project Specification, Technical Architecture & Development Playbook
Android-first NFC reader, writer, tag inspector and task automation utility
Version 1.0  |  Prepared: 19 October 2026
Implementation baseline: Kotlin · Jetpack Compose · Android NFC APIs · Room · OpenCode

# Document Control

| Field | Value |
|---|---|
| Project working name | NFC Studio (rename before public release if desired) |
| Product type | Android NFC utility and task-profile application |
| Primary platform | Android phones/tablets with NFC hardware |
| Primary users | Developers, NFC hobbyists, makers, QA/test engineers, everyday users |
| Delivery strategy | Offline-first, native Android, modular architecture, incremental releases |
| AI coding workflow | OpenCode with plan/build/review loops; human approval for risky actions |


## How to use this specification
Treat this document as the project source of truth. Implement one milestone at a time. Keep the scope register and acceptance criteria updated as the application evolves. A feature is not complete merely because code compiles: it must be tested against supported hardware and documented limitations.

# 1. Executive Summary
NFC Studio is an Android-first application inspired by the broad utility category of NFC Tools. It combines NDEF tag reading and writing, structured record editing, tag diagnostics, reusable task profiles, local history, and an advanced inspector for supported NFC technologies. The product should be original in branding and interface, and should not copy another application's proprietary assets or code.
The product is offline-first. Basic read/write operations do not require an account, backend, or internet connection. Cloud sync, account management, remote template sharing, and analytics are optional future additions, not MVP dependencies.

## Product goals
Make common NFC tasks understandable to non-experts while exposing advanced data to technical users.
Reliably read, decode, compose, write, and verify supported NDEF messages.
Represent actual tag capabilities and limitations instead of implying universal support.
Provide repeatable task profiles that launch permitted Android actions after an NFC scan.
Keep personal tag data on-device by default and make export/sharing explicit.
Use a maintainable architecture that can be extended without rewriting NFC I/O.

## Non-goals and explicit limitations
Do not promise universal tag compatibility, arbitrary card cloning, payment-card data extraction, or access-control bypass.
Do not promise that Android will allow unrestricted toggling of Wi-Fi, Bluetooth, mobile data, or other protected system settings.
Do not implement stealth scanning, covert tracking, or automatic execution of untrusted tag payloads.
Do not make cloud infrastructure mandatory for local NFC functionality.
Do not treat a UID as a reliable universal unique identifier; some tags use random or privacy-preserving identifiers.

# 2. Target users and use cases

| User segment | Main jobs to be done | Priority |
|---|---|---|
| Everyday user | Read a URL/text tag, save useful scans, write a link to a sticker | P0 |
| Productivity user | Use tags to launch a website, navigation destination, app or task profile | P1 |
| Maker / hobbyist | Inspect technologies, view raw bytes, test NDEF compatibility | P1 |
| Developer / QA | Inspect ISO-DEP tags, issue supported APDU commands, export diagnostics | P2 |
| Power user | Build templates, batch-write tags, import/export records and profiles | P1/P2 |


# 3. Feature catalogue and priorities
Priority definitions: P0 = MVP launch blocker; P1 = first major release; P2 = advanced capability; P3 = optional later expansion.

| Module | Features | Priority |
|---|---|---|
| Home & navigation | Scan, Write, Tasks, History, Templates, Tag Library, Settings; NFC state; quick actions | P0 |
| Tag reader | Discover tag, parse NDEF, text/URI/MIME records, metadata, technologies, error states | P0 |
| NDEF writer | Text, URL, multiple records, preview, capacity check, write, read-back verification | P0 |
| History | Local scans, timestamps, search, filters, favourites, delete/export | P0 |
| Tag templates | Save/reuse common record sets, duplicate template, edit before writing | P1 |
| Task profiles | Named profiles with allowlisted actions, confirmation policy, logs | P1 |
| Advanced inspector | Supported technologies, raw payload view, tag diagnostics | P1 |
| Low-level protocols | ISO-DEP transceive/APDU console with timeout and response parsing | P2 |
| Batch workflow | Queue records/templates, repeated writes, per-tag result log | P2 |
| Backup/import/export | JSON/text exports, schema versioning, optional encrypted backup | P1/P2 |
| Polish & accessibility | Dark/light theme, large text, TalkBack labels, localization, tablet layouts | P1 |
| Cloud capabilities | Optional sync, account, shared templates, remote backup | P3; separate project |


# 4. Detailed functional requirements

## 4.1 Home and onboarding
Show whether the device has NFC hardware, whether NFC is enabled, and whether the app can start scanning.
Provide prominent Scan and Write entry points plus recent scans and saved templates.
Explain first-use scanning: unlock the phone if required, hold the tag near the NFC antenna, and keep it steady.
Show contextual empty states instead of fake tag data.
Offer an optional guided test with a sample NDEF payload for parser/UI tests; clearly label it as simulated.

## 4.2 Reader and tag details
Use Android's NFC discovery/lifecycle APIs to receive a Tag and inspect the technologies exposed by the platform.
Attempt NDEF reading when Ndef is available; handle non-NDEF technologies separately.
Display decoded records: TNF/type, text, language code where applicable, URI, MIME type, payload length and readable summary.
Show raw bytes in hex and printable ASCII where safe; provide copy/export controls.
Show UID only when Android provides it. Explain that identifiers can be absent, randomised, or unsuitable for tracking/identity.
Display capacity and writable status only when the API exposes meaningful values. Avoid guessing.
Handle malformed NDEF, unsupported TNF, tag loss, I/O errors, timeouts and unsupported technology without crashing.
Require user action before opening external links; sanitize display and use Android's normal intent resolver where appropriate.

## 4.3 Writer and record builder
Support text and URI records first; then add MIME, Smart Poster and other well-defined NDEF record types where appropriate.
Support ordered multi-record messages and show a final encoded-size estimate before writing.
Validate input length, URI scheme, MIME type, text encoding and record fields.
Check NDEF availability, read-only state and available capacity when the platform exposes them.
Connect to Ndef, call connect/writeNdefMessage/close safely, and handle IOException and TagLostException.
Read the tag back after writing when supported and compare the decoded result with the requested message.
For non-NDEF blank tags, only offer formatting through NdefFormatable when supported and clearly explain the risk.
Provide explicit confirmations for overwrite, format, erase, and permanent read-only locking. Explain that locking may be irreversible.
Do not claim that clearing an NDEF message securely erases every physical memory location.

## 4.4 Task profiles and action engine
Allow users to create a named profile and add ordered actions from an explicit allowlist.
Initial actions: open URL, open supported app/deep link, display text, open map/navigation URI, create a draft email/SMS when supported, and show contact information.
Treat task profiles as app-side rules associated with a tag or scan workflow; do not imply the tag itself can perform unrestricted phone automation.
Require confirmation for actions with external effects, such as sending messages, placing calls, or opening unknown domains.
Validate URLs and schemes; block dangerous schemes and never execute tag-supplied shell commands or arbitrary code.
Log the action result locally and provide retry/cancel controls.
Amendment 2026-11-04 (ADR 0001): profiles may opt in to auto-run on a matching scan or task tag; only in-app display and Settings-screen actions skip confirmation in that case.
System settings changes should use permitted Android APIs or open the relevant Settings screen; document platform restrictions.

## 4.5 History, templates and data management
Persist scan timestamp, decoded summary, supported metadata, and optionally the full payload according to a user setting.
Search by record content/type and filter by scan date, tag technology, favourite status or result.
Provide delete-one, clear-history and export actions with confirmation where appropriate.
Store reusable NDEF templates independently from scan history.
Version exported JSON schemas and validate imported files before saving.
Avoid retaining sensitive personal information unless the user chooses to save it.

## 4.6 Advanced inspection
Show the technologies exposed by Android: examples include NfcA, NfcB, NfcF, NfcV, IsoDep, Ndef, NdefFormatable and MifareClassic where supported.
Do not assume every Android device supports every technology or that a technology name guarantees access to protected data.
For IsoDep, expose an advanced APDU console with command hex validation, maximum-length checks, timeout controls and response bytes/status words.
Keep raw protocol operations behind an Advanced mode with clear warnings and no destructive commands by default.
Do not include card-emulation, payment-card cloning, credential extraction, or access-control bypass as promised features.
Add diagnostics export with device/API level, exposed technologies and error codes, excluding payloads by default.

# 5. Technical stack

| Layer | Recommended technology | Reason / guidance |
|---|---|---|
| Language | Kotlin | Native Android ecosystem and type-safe code. |
| UI | Jetpack Compose + Material 3 | Declarative UI, reusable components, theming and accessibility. |
| Navigation | Navigation Compose | Typed/structured screen navigation; keep route arguments small. |
| Architecture | MVVM + repositories + use cases | Testable separation between UI, domain and platform I/O. |
| Async | Kotlin Coroutines + Flow | Lifecycle-aware asynchronous state and I/O. |
| NFC | Android NFC framework | NfcAdapter, Tag, Ndef, NdefFormatable, IsoDep and supported technology APIs. |
| DI | Hilt | Constructor injection and clear module boundaries; use only if project complexity warrants it. |
| Database | Room | Structured local history, templates and task profiles. |
| Preferences | DataStore | Theme, scan preferences and non-sensitive app settings. |
| Serialization | kotlinx.serialization | Versioned import/export JSON; validate untrusted input. |
| Testing | JUnit, kotlinx-coroutines-test, Compose UI test, AndroidX test | Unit, coroutine, UI and device-level coverage. |
| Static quality | Android Lint, detekt or ktlint | Catch Android-specific issues and enforce consistent Kotlin style. |
| Build | Gradle Kotlin DSL + version catalog | Reproducible dependency management. |
| Version control | Git | Milestone checkpoints and rollback. |
| AI coding | OpenCode | Planning, implementation, refactoring, tests and terminal build loops. |
| Optional UI icons | Material Symbols / Material Icons | Consistent icons; avoid excessive decorative icon libraries. |
| Optional encrypted secrets | Android Keystore + vetted crypto library only if needed | Protect encryption keys; never invent custom cryptography. |

Version policy: use stable compatible releases selected at project start. Record exact versions in libs.versions.toml and Gradle files. Do not blindly accept an AI-generated version number. Confirm compatibility with the current Android Gradle Plugin, Kotlin, Compose compiler/plugin, JDK and target SDK.

# 6. Android components, permissions and platform integration

| Component / declaration | Purpose | Notes |
|---|---|---|
| NFC hardware feature | Declare NFC feature in manifest | Choose required=true only if the app cannot provide meaningful non-NFC functionality without it; otherwise make it optional. |
| android.permission.NFC | Access NFC hardware | Declare in manifest; not a normal runtime permission prompt. |
| NfcAdapter | Check adapter availability and enablement | Handle devices without an adapter and disabled NFC. |
| Foreground dispatch or reader mode | Receive tag discovery while app is active | Choose based on app flow; handle lifecycle start/stop correctly. |
| Tag / TagTechnology | Inspect discovered tag and connect to supported tech | Always close technology connections in finally/use blocks. |
| Ndef | Read/write NDEF messages | Check read-only/capacity support and handle tag loss. |
| NdefFormatable | Format compatible blank tags | Only when exposed by tag; warn user before formatting. |
| IsoDep | ISO-DEP transceive | Protocol-specific, advanced feature; set timeouts and validate APDU input. |
| Android intents | Open browser, maps, contacts or app links | Use safe URI validation and user confirmation. |
| Room migrations | Evolve local schema | Test migration paths; do not drop user history silently. |

The exact manifest and NFC dispatch implementation must follow the Android documentation for the chosen minimum/target SDK. Android's NFC feature set and behavior vary by device, OS version and tag technology. Do not rely on deprecated Android Beam APIs.

# 7. Suggested module and package structure
app/: Application class, MainActivity, navigation graph, top-level dependency wiring.
core/nfc/: NFC adapter state, discovery controller, tag capability model, technology adapters.
core/ndef/: NDEF parser, record models, builder, size estimator and validation.
core/common/: Result/error types, dispatchers, logging redaction and shared utilities.
domain/model/: TagSnapshot, NdefRecordModel, NdefDocument, TaskProfile, TaskAction, ScanEntry.
domain/usecase/: ReadTag, WriteNdef, VerifyWrite, SaveScan, CreateTemplate, RunTaskProfile.
data/database/: Room entities, DAO interfaces and database migrations.
data/repository/: Repository implementations for history, templates, profiles and preferences.
feature/home/: Home UI and NFC status.
feature/scanner/: Scan state, decoded record list, raw bytes view and diagnostics.
feature/writer/: Record editor, preview, write progress and verification.
feature/tasks/: Profile list/editor, action allowlist, confirmation and execution log.
feature/history/: Search, filter, detail, delete and export.
feature/settings/: Theme, privacy, defaults, export/import and about.
docs/: Architecture decisions, compatibility matrix, threat model, test plan and release checklist.

# 8. Data model proposal

| Model | Important fields | Notes |
|---|---|---|
| TagSnapshot | scanId, timestamp, technologies, uidIfExposed, ndefAvailable, readOnlyIfKnown, capacityIfKnown | Represent unknown values as unknown/null, not false or zero. |
| NdefDocument | records[], encodedSize, schemaVersion | Domain representation independent from Android UI. |
| NdefRecordModel | id, recordKind, tnf, type, payload, text, language, uri, mimeType | Only applicable decoded fields should be populated. |
| ScanEntry | id, createdAt, summary, technologyNames, optionalPayload, favourite | Store payload only under the selected privacy policy. |
| NdefTemplate | id, name, description, recordsJson, createdAt, updatedAt | Validate before write. |
| TaskProfile | id, name, enabled, actions[], confirmationPolicy, createdAt | Do not deserialize executable code. |
| TaskAction | type, validatedParameters, requiresConfirmation | Use a sealed class / allowlisted action type. |
| AppSettings | theme, historyRetention, savePayload, defaultScanMode | Use DataStore for simple preferences. |


# 9. UI/UX requirements
Use a clean, utility-first interface. Prefer clear labels and compact data presentation over gradients, excessive cards, or decorative animations.
Bottom navigation or a simple navigation rail should expose the primary sections without crowding the screen.
Every operation needs idle, scanning/working, success, recoverable error, unsupported and permission/hardware-disabled states.
Use a single consistent component for tag status, technology chips, record rows, destructive confirmation and result banners.
For writing, show a record preview and estimated payload size before the user taps Write.
For advanced views, make hex/ASCII data selectable and copyable, with line wrapping or horizontal scrolling that remains usable.
Ensure TalkBack labels, adequate touch targets, scalable typography, contrast and dark/light themes.
Do not auto-open URLs or execute tasks immediately on every scan unless the user explicitly configures that behavior and the action is safe.

# 10. Security, privacy and safety model
Treat all tag contents as untrusted input. NFC tags can contain malformed payloads, deceptive URLs and unexpected record types.
Parse with bounds checks; avoid allocating based on untrusted length fields without limits.
Never execute scripts, shell commands, arbitrary code, or serialized object graphs from a tag.
Validate URL schemes and display the host/domain before opening external destinations.
Require confirmation for overwrite, formatting, permanent locking, message/call actions and unfamiliar external URLs.
Keep local history on-device by default. Export and share only after explicit user action.
Redact payloads, identifiers and personal content from logs and crash reports.
If encrypted backups are added, use Android Keystore-backed key handling and a vetted cryptographic implementation; do not design custom crypto.
Document that NFC range is short but not a security guarantee. Sensitive protocols still require protocol-level authentication and secure design.
Do not market the app as a tool to clone payment cards, bypass access control, or extract protected credentials.

# 11. Testing strategy and acceptance criteria

## Unit tests
NDEF text/URI/MIME record parsing and encoding round trips.
Malformed records, empty payloads, invalid encodings, large payload limits and unknown record types.
URI allowlist/validation and task action parameter validation.
JSON export/import schema validation and unsupported schema versions.
Repository behavior and Room migration tests.
Task execution policy: confirmation-required actions cannot run without approval.

## Device and integration tests
Phone with NFC hardware and NFC enabled.
Phone without NFC hardware or with NFC disabled.
Known NDEF URL and text tags.
Writable tag with sufficient capacity; tag with insufficient capacity; read-only tag.
Compatible blank tag exposing NdefFormatable, tested only with explicit confirmation.
Tag removed during read/write and I/O timeout/failure.
At least two tag families/chip types and more than one Android OS/device where possible.
Verify written records by reading back and comparing expected content.
Confirm no crashes or false success states when a technology is unsupported.

## Definition of Done
The feature compiles with the project's pinned toolchain.
Automated tests pass and relevant lint checks are resolved or documented.
A real-device test has been recorded for hardware-dependent features.
Success and failure states are implemented; no fake production results remain.
Security-sensitive actions have appropriate confirmation and logging redaction.
Documentation and acceptance criteria are updated.
Changes are committed to Git with a focused commit message.

# 12. Development process and milestone plan

| Milestone | Deliverables | Exit gate |
|---|---|---|
| M0 — Discovery | Scope, requirements, architecture decisions, NFC compatibility matrix, wireframes | Scope and P0 features approved |
| M1 — Foundation | Gradle project, Compose theme/navigation, manifest, CI/build command, Git baseline | Clean debug build |
| M2 — Reader | NFC state, tag discovery, NDEF decoding, tag details and error states | Reads known real tags reliably |
| M3 — Writer | Record builder, preview, capacity checks, write/format safeguards, read-back verification | Writes and verifies supported tags |
| M4 — Persistence | Room history, search, favourites, templates, DataStore preferences | Data survives process restart; migrations tested |
| M5 — Task profiles | Allowlisted action model, profile editor, confirmation and logs | Actions validated and safe by default |
| M6 — Advanced tools | Technology inspector, raw payload viewer, optional ISO-DEP/APDU console | Tested on compatible tags; unsupported states correct |
| M7 — Hardening | Security review, accessibility, performance, test coverage, compatibility fixes | Release checklist passes |
| M8 — Release | App icon, privacy disclosures, signing, APK/AAB, versioned documentation | Signed artifact installed and regression-tested |


## Suggested work cadence
Start each milestone in OpenCode Plan mode; ask it to inspect the current codebase and propose a small implementation plan.
Review the plan and acceptance criteria before allowing code edits.
Implement a vertical slice: UI + domain logic + platform integration + tests.
Run the exact Gradle build/test commands and inspect the output rather than trusting a summary.
Test NFC behavior on a physical phone before marking hardware-dependent work complete.
Review the diff, run static checks, commit, and only then begin the next milestone.

# 13. OpenCode setup and AI-agent operating rules
OpenCode is an AI coding agent that can inspect files, edit source, run terminal commands and support a plan/build workflow. It accelerates implementation but is not a substitute for real-device validation or engineering review.
Create the project directory and initialize Git before generating large amounts of code.
Open the project in OpenCode and run /init to generate an AGENTS.md file; customize it with the project's architecture, coding conventions, test commands and safety rules.
Use Plan mode for architecture and each major feature; switch to Build mode only after reviewing the plan.
Ask for small, verifiable milestones instead of one giant request to implement everything.
Require the agent to report files changed, commands run, test results, known limitations and manual test steps.
Keep secrets, signing keys, personal data and production credentials out of prompts and version control.
Configure shell/edit permissions conservatively. Require approval for destructive commands, external directory access, package installation and Git push.
Never accept claims such as 'tested' unless the agent supplies the command and actual result.

## Recommended AGENTS.md rules
Follow Kotlin conventions and the agreed package/module boundaries.
Do not introduce dependencies without explaining why and verifying compatibility.
Do not run a release build until the debug build and tests pass.
Do not silently delete or rewrite user data; write migrations and test them.
All NFC connections must be closed safely and all I/O errors must be surfaced as typed results.
All tag payloads are untrusted; do not auto-execute tag data.
For each feature, update tests and documentation and report real verification evidence.
Do not claim unsupported NFC technologies are implemented.

# 14. Build, debugging and release commands
Commands below are examples; adjust the Gradle wrapper tasks to the generated project and installed Android SDK.
./gradlew tasks
./gradlew assembleDebug
./gradlew test
./gradlew lint
./gradlew connectedAndroidTest
adb devices
adb logcat
git status --short
git diff --check
Use connectedAndroidTest only when a suitable device/emulator is connected and the required instrumentation configuration exists. An emulator is useful for UI testing but is not a substitute for physical NFC testing.

# 15. Release checklist
Confirm application ID, app name, versionCode and versionName.
Confirm minSdk/targetSdk policy against current Google Play requirements.
Review manifest permissions and exported components.
Run unit tests, lint and release build.
Install and test the signed artifact on physical hardware.
Test all P0 flows and key failure modes.
Review privacy policy, data safety declarations and any SDK data collection.
Verify no debug endpoints, test payloads, secrets or verbose payload logging remain.
Back up signing material securely and document recovery procedures.
Produce release notes and a compatibility/limitations page.

# 16. Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Tag/device incompatibility | Feature works on one phone/tag but fails elsewhere | Maintain a compatibility matrix; detect capabilities dynamically; test multiple devices. |
| AI-generated incorrect APIs | Build failures or runtime bugs | Pin versions, consult official docs, compile and test each milestone. |
| Destructive writes/locking | Permanent data loss on tag | Preview, explicit confirmation, clear warnings and separate dangerous actions. |
| Malformed/untrusted tag data | Crashes, deceptive URLs or unsafe behavior | Bounds-check parsers, validate schemes, never execute payloads. |
| Scope creep | Project stalls before a usable app exists | Protect P0 scope; defer advanced protocols and cloud features. |
| Platform restrictions | Automation cannot toggle a setting directly | Use supported intents/settings screens and explain limits. |
| Privacy leakage | Tag content or identifiers exposed in logs/backups | Local-first defaults, redaction, explicit export and opt-in diagnostics. |


# 17. Suggested 8-week schedule

| Week | Focus | Expected outcome |
|---|---|---|
| 1 | Project setup, architecture, navigation and UI skeleton | Compiling shell and NFC capability states |
| 2 | Reader and NDEF parser | Reliable real-tag scanning and decoded record view |
| 3 | Writer and verification | Text/URL write and read-back verification |
| 4 | Room history and templates | Persistent history and reusable record templates |
| 5 | Task profiles and safe Android intents | Working action builder and execution log |
| 6 | Advanced inspection | Technology diagnostics; optional ISO-DEP/APDU tools |
| 7 | Testing, accessibility and security review | Stable feature set and compatibility notes |
| 8 | Release preparation and final validation | Signed APK/AAB and release documentation |

This is a planning baseline for a solo developer assisted by AI. The advanced inspector, APDU console, broad tag compatibility and polished release may require additional time depending on available tags and device access.

# 18. Official references
Android NFC overview: https://developer.android.com/develop/connectivity/nfc
NFC basics: https://developer.android.com/develop/connectivity/nfc/nfc
Advanced NFC: https://developer.android.com/develop/connectivity/nfc/advanced-nfc
NfcAdapter API reference: https://developer.android.com/reference/android/nfc/NfcAdapter
Jetpack Compose documentation: https://developer.android.com/develop/ui/compose
Android app architecture: https://developer.android.com/topic/architecture
Room persistence library: https://developer.android.com/training/data-storage/room
DataStore: https://developer.android.com/topic/libraries/architecture/datastore
OpenCode documentation: https://docs.opencode.ai/docs/
OpenCode agents and permissions: https://docs.opencode.ai/docs/agents/
OpenCode configuration: https://docs.opencode.ai/docs/config/

# 19. First implementation prompt for OpenCode
You are the senior Android engineer for NFC Studio. Read the project specification in NFC_Studio_Full_Project_Specification.docx (or the exported Markdown version) before changing code.

First inspect the repository and development environment. Use Plan mode and return:
1. current project status,
2. proposed module/package structure,
3. dependency/version plan with official documentation links,
4. milestone M1 implementation plan,
5. build and test commands,
6. assumptions and risks.

Do not implement the entire app at once. After approval, implement M1 only: a compiling native Android application using Kotlin, Jetpack Compose, Material 3, Navigation Compose, a clean theme, Home/Scan/Write/Tasks/History/Settings navigation, and NFC availability/enabled-state handling. Do not fake tag results or implement a mock scanner as production behavior.

Use stable compatible dependencies, the project's version catalog, and the official Android NFC API. Run the Gradle build and relevant tests. Report exact commands and outcomes, files changed, unresolved issues, and manual steps needed to test on a physical NFC phone. Do not claim hardware validation without a physical device test.

# Appendix A — MVP acceptance checklist
App launches and clearly reports NFC hardware availability/enabled state.
Scan screen receives a real tag and presents decoded NDEF records.
Unknown or malformed records do not crash the app.
Writer creates a text or URL NDEF message and writes it to a compatible writable tag.
App reads back and verifies a successful write where supported.
Unsupported, read-only, full and lost tags display truthful error states.
History and templates persist across process restarts.
External actions require appropriate confirmation and use safe Android intents.
Automated tests pass; release build succeeds.
Manual hardware test results and known limitations are documented.
