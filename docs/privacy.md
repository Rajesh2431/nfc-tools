# Privacy and data safety

- All data stays on the device. The only network use is the *Webhook (HTTPS)*
  workflow action, which sends exactly what the user configured to the URL the
  user entered. There is no analytics or crash reporting SDK, and tag contents
  are never uploaded on their own.
- History stores a summary, technologies and time. Full payload bytes and tag
  UIDs are stored only if enabled in Settings › Privacy (off by default).
- Backups: excluded from Android cloud backup and device-to-device transfer.
  Users export JSON explicitly via the system file picker.
- Diagnostics exports contain device model, Android version, technologies,
  protocol metadata and error codes — never payloads or UIDs.
- Task log stores action type and outcome only.
- Play Data safety draft: no data collected, no data shared.
- Root history stores command category and outcome, never output. System logs
  read in Root Manager are shown on screen only.
- Workflow trigger tokens are stored locally and never exported.
- HCE emulation logs APDU headers and status words in memory only.
