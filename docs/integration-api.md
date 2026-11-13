# Integration API (Tasker, MacroDroid, other apps)

Tap & Go exposes **one** entry point for other apps: a request to run a saved
workflow. It never accepts commands, scripts, URLs or actions from the caller;
the caller only names an existing workflow by its secret token. Root actions
inside that workflow are skipped for these runs.

## Enable

1. Settings › Workflows › **Allow other apps to run workflows** (off by default).
2. Workflow › Triggers › **Other apps** › *Allow other apps to run this workflow*.
3. Copy the workflow token shown there. Treat it like a password.
4. Optional: *Run without asking*. Without it, Tap & Go shows the workflow and
   asks before running.

Both switches require Premium (see `docs/premium.md`).

## Intent

| Field | Value |
|---|---|
| Target | Activity |
| Package | `com.tapandgo` |
| Class | `com.tapandgo.ExternalLaunch` |
| Action | `com.tapandgo.action.RUN_WORKFLOW` |
| Extra | `com.tapandgo.extra.WORKFLOW_TOKEN` = the token (String) |

`adb` test:

```bash
adb shell am start -n com.tapandgo/.ExternalLaunch \
  -a com.tapandgo.action.RUN_WORKFLOW \
  --es com.tapandgo.extra.WORKFLOW_TOKEN 0f8fad5b-d9cb-469f-a165-70867728950e
```

**Tasker:** Task › System › Send Intent: Action as above, Extra
`com.tapandgo.extra.WORKFLOW_TOKEN:<token>`, Package `com.tapandgo`,
Class `com.tapandgo.ExternalLaunch`, Target **Activity**.

**MacroDroid:** Action › Applications › Send Intent: Target *Activity*, same
action, package, class and extra.

Outcomes: the run screen shows *Run …?* (confirmation), the result log, or
*refused* (switches off, unknown token, or no Premium). Every run is recorded
in Workflows › Execution history with source `EXTERNAL`.

## Other entry points

| Entry point | How | Notes |
|---|---|---|
| NFC task tag | Triggers › Write trigger tag | Tag holds only the token; confirms unless *Run without asking* |
| Home-screen shortcut | Triggers › Add to home screen | Non-exported alias `.InternalLaunch`; runs on tap |
| Quick Settings tile | Triggers › Use for tile, then add the *Tap & Go workflow* tile | One workflow at a time |
| Schedule | Triggers › Schedule (15 min – 24 h) | WorkManager; Android decides exact timing; screen-opening actions report Unsupported |
| Deep links | `tapandgo://read`, `write`, `tasks`, `workflows`, `library`, `tools` | Open screens only |
| Launcher shortcuts | Long-press the app icon: Scan, Write, Workflows, Library | Deep links |
| Webhook | Workflow action *Webhook (HTTPS)* | Outgoing only; https, GET/POST, no redirects, 10 s timeout |

## Plugin architecture

Actions live in an allowlisted `ActionRegistry` (`automation/engine/Engine.kt`):
an id maps to a Kotlin handler compiled into the app. Tap & Go does **not**
load third-party code or accept action definitions from other apps; that
would let any installed app run arbitrary operations with Tap & Go's
permissions. New actions are added in source (`automation/actions`), with a
UI description in `feature/automation/ActionCatalog.kt` and unit tests.
Third-party automation apps integrate through the intent above instead.
