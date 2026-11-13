# Architecture

Single `:app` module, layered by package (extraction to Gradle modules is
mechanical; see DEVELOPMENT_STATUS.md for why it is deferred).

```
com.tapandgo
├── core/          pure Kotlin + thin Android wrappers
│   ├── ndef/      NDEF codec, parser, URI/text codecs, rich records, NdefLab, validation
│   ├── nfc/       TagGateway (reader mode, typed NfcResult), chip ID, memory, passwords
│   ├── apdu/      APDU model + status words
│   ├── common/    hex, redaction
│   └── security/  BackupCrypto (PBKDF2-SHA256 + AES-256-GCM)
├── nfc/hce/       Type4Emulator (pure APDU state machine), NdefHceService, EmulationStore
├── data/          Room (v6, migrations 1→6), DAOs, repositories, DataStore settings, export
├── domain/task/   task profiles, validator, runner
├── automation/
│   ├── engine/    Workflow model, WorkflowEngine, conditions, variables, edit ops, JSON
│   ├── actions/   AndroidActions (allowlisted registry), params, built-in variables
│   └── *.kt       WorkflowRepository, triggers (launch policy, scheduler, tile, shortcuts), transfer
├── root/          RootExecutionService (su, allowlist, timeouts), commands, profiles, diagnostics
├── billing/       Entitlement seam (BillingProvider, resolver, repository)
├── feature/*      Compose screens + Hilt view models per feature
├── navigation/    NavHost, top-level destinations, deep links
└── di/            Hilt modules
```

## Data flow

UI (Compose) → `@HiltViewModel` → repository/service → Room/DataStore/platform.
Platform-dependent parts sit behind interfaces (`TagGateway`, `ShellRunner`,
`WorkflowScheduler`, `BillingProvider`) so the logic is unit-tested on the JVM.

## Security boundaries

| Boundary | Rule |
|---|---|
| Tag payloads | Parsed with bounds checks; never executed (AGENTS rule 6) |
| Trigger tokens | Select an existing workflow only (ADR 0002) |
| Exported components | `MainActivity` (launcher, deep links, task tags), `.ExternalLaunch` (token + 2 opt-ins), tile and HCE services (system-bound permissions) |
| Root | Only from in-app UI or opted-in workflows; never for EXTERNAL runs; allowlisted scripts; console needs per-run confirmation |
| Network | Only the user's webhook action |
| Premium | Single `EntitlementRepository`; no purchase is simulated |

## Schema

| Version | Change |
|---|---|
| 1 | scan history, templates |
| 2 | task profiles, task log |
| 3 | profile auto-run |
| 4 | tag library |
| 5 | workflows, workflow runs |
| 6 | workflow triggers (token, auto-run, external, schedule), run source |

The workflow block tree is stored as validated JSON (`WorkflowJson`) instead
of separate action/condition/variable tables: edits are atomic, imports are
validated as a whole, and the format is versioned.
