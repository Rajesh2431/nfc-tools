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
