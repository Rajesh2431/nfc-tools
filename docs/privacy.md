# Privacy and data safety

- All data stays on the device. Network use is limited to the *Webhook (HTTPS)*
  action and smart-home connections the user configures, sending exactly what
  the user set up. There is no analytics or crash reporting SDK, and tag contents
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
- Smart-home connections talk only to the systems you configure. Tokens and
  passwords are Keystore-encrypted and never exported.
- Wi-Fi names and trigger locations are stored on the device only; location
  is read only for triggers you create.
