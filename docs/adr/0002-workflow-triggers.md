# ADR 0002 — Workflow triggers (tag, shortcut, tile, schedule, other apps)

Date: 2026-11-13 · Status: accepted

## Context

The Tap & Go spec (§ 10.3, § 16) asks for workflows started by an NFC tag,
home-screen shortcuts, a Quick Settings tile, schedules and other apps
(Tasker, MacroDroid). Workflows run allowlisted device actions the user built
in the app, so the question is when a run may start without another tap.

## Decision

1. Every trigger carries only a random UUID **token** stored in
   `workflows.triggerToken` (schema v6, unique). Tokens are never exported,
   duplicated with a workflow or written to logs. Workflows still come from the
   app's database; no action, parameter or code is read from a tag or intent.
2. **Tag** (`NDEF_DISCOVERED`, external type `tapandgo.app:task`): asks before
   running unless the workflow opted in to *Run without asking* (off by
   default). Profiles are matched first, so existing task tags keep working.
3. **Shortcut / tile**: go through the non-exported activity alias
   `.InternalLaunch`; only this app and the launcher/System UI acting for it can
   use it. The user's tap is the confirmation, so the run starts directly.
4. **Other apps**: only through the exported alias `.ExternalLaunch` with action
   `com.tapandgo.action.RUN_WORKFLOW` and the secret token. Refused unless the
   global setting *Allow other apps to run workflows* (off by default) **and**
   the workflow's own *Allow other apps* switch are on. A caller cannot claim
   to be a shortcut: the source is derived from the resolved component, which
   other apps cannot choose.
5. **Schedule**: WorkManager periodic work, minimum 15 minutes, timing decided
   by Android. Scheduled runs are marked as background runs, and actions that
   would open a screen report `Unsupported` instead of claiming success.
6. Imported workflows never have triggers; the user must add them.

## Consequences

- AGENTS.md rule 6's exception now covers workflows the user explicitly set up
  with a trigger. Tag payloads remain untrusted and are never executed.
- Anyone who can read a trigger tag can copy its token, and any app can send a
  forged `NDEF_DISCOVERED` intent with it. For workflows with *Run without
  asking* this is the same trust model as any NFC automation tag. The UI says so
  and the default is to confirm.
- No hilt-work dependency: the worker uses a Hilt entry point.
