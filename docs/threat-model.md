# Threat model (summary)

| Threat | Mitigation | Evidence |
|---|---|---|
| Malformed / hostile NDEF (huge lengths, chunking, truncation) | Bounds-checked pure parser, no allocation beyond input, 255-record cap, chunked records rejected, previews capped at 4 KiB | `NdefMessageCodecTest`, `NdefParserTest` |
| Deceptive links (`user@host`, homographs, RTL overrides, `javascript:`/`intent:`/`file:`) | Scheme allowlist on read *and* write, control/bidi characters rejected, host shown prominently with warnings, explicit tap to open | `UriPolicyTest` |
| Tag-driven code execution | Tags never carry actions; task tags carry a UUID token only; auto-run limited to in-app/Settings actions (ADR 0001) | `TaskRunnerTest`, `TaskValidatorTest` |
| Forged `NDEF_DISCOVERED` intents from other apps | Token format check, selects only existing profiles, same auto-run bounds | ADR 0001 |
| Destructive tag operations | Confirmation after showing current content; lock = two confirmations; password = double entry + acknowledgement; format only for blank tags; unknown content treated as data | `WriteViewModelTest`, `OtherViewModelTest` |
| Chip bricking by config writes | Password ops only for exactly identified NTAG213/215/216; AUTH0 written last; verified by PWD_AUTH | `ChipParsingTest` |
| APDU misuse | Validation, state-changing and proprietary commands blocked by default | `ApduTest` |
| Privacy leaks (history, logs, backups, diagnostics) | Payload/UID storage off by default; logs carry no payloads; cloud backup and device transfer excluded; diagnostics exclude payload and UID | `ScanEntryFactoryTest`, `DiagnosticsTest`, `data_extraction_rules.xml` |
| Untrusted import files | 5 MiB cap, strict schema (unknown keys rejected), version check, per-item validation, insert-only | `ExportCodecTest` |
| Wi-Fi password on a tag | Shown masked until revealed; warning that anyone reading the tag sees it | UI |
| Other apps triggering workflows | Exported alias needs global switch + per-workflow opt-in + secret token; source derived from resolved component (cannot claim SHORTCUT); root actions refused for EXTERNAL runs (ADR 0002) | `WorkflowTriggerTest` |
| Imported workflows running unexpectedly | Imports never carry tokens, auto-run, schedule or external access; strict JSON, allowlisted action ids, size/depth limits | `WorkflowTriggerTest`, `WorkflowEditTest` |
| Background runs claiming success | Scheduled runs mark screen-opening actions Unsupported | `AndroidActions.start` |
| Root shell injection / silent root | Allowlisted scripts from constants and range-checked numbers; console only from UI with per-run confirmation; su requested only on user action; 15 s timeout; output redacted; history without output | `RootExecutionTest` |
| HCE emulating sensitive cards | Only the NDEF AID registered, read-only Type 4 file, writes refused, component disabled until enabled | `Type4EmulatorTest`, `apduservice.xml` |
| Fake Premium | Single entitlement source; no billing → no purchase; dev switch debug-only and labelled | `Type4EmulatorTest` (resolver) |
| Webhook data exfiltration | https only, no redirects, no credentials in URL, 16 KB body, user-entered URL, warning in action note | `RootExecutionTest` (params) |
| Smart-home credentials theft | Keystore AES-GCM secrets, never exported/logged; plain HTTP/MQTT refused for non-LAN hosts; no redirects; response caps | `SmartHomeTest` |
| Malicious control ids / device responses | Ids parsed against strict patterns; JSON parsed defensively; errors shown, not guessed state | `ControlIdsTest`, `SmartHomeTest` |
| Background triggers doing hidden UI work | CONTEXT/DEVICE_CONTROL runs marked background; screen-opening actions Unsupported | `ContextRulesTest`, `AndroidActions.start` |
