# ADR 0001 — Opt-in auto-run for task profiles

Date: 2026-11-04 · Status: accepted (product owner decision)

## Context

AGENTS.md rule 6 and spec § 4.4 originally forbade executing anything because
a tag was scanned. The product owner asked for NFC-Tasks-style behaviour where
scanning a task tag runs its tasks. Android also restricts what an app may do
without the user (Wi-Fi/Bluetooth toggles are not available to normal apps
since Android 10/13).

## Decision

1. Profiles stay **app-side**. A *task tag* stores only a random UUID token in
   an NFC Forum external record `nfcstudio.dev:task`. No actions, parameters,
   code or URLs are read from tags.
2. A profile may opt in to **Run automatically when scanned**. It is off by
   default, requires a scan trigger, and is unavailable under the
   "confirm every action" policy. Migration v2→v3 adds the flag as `false`
   for all existing profiles.
3. In an auto-started run only *safe* actions skip confirmation: show text,
   show contact (in-app), open a system Settings screen. Opening links,
   drafting email/SMS, opening maps or apps **always** ask.
4. Auto-start happens only when exactly one auto-run profile matches. Several
   matches are offered as suggestions instead.
5. Task tags also launch the app through an `NDEF_DISCOVERED` filter. Because
   any app can send such an intent, the token is format-validated and can only
   select an existing profile; rule 3 bounds the effect.

## Consequences

- Rule 6 is amended, not removed: tag data is still never executed; only a
  user-configured profile is selected by a token.
- Tests: `TaskRunnerTest` (auto-run only for safe actions, policy precedence,
  opt-in required), `TaskValidatorTest` (exact token match), migration test
  `migrate2To3KeepsProfilesManual`.
