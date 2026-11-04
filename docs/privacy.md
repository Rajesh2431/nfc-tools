# Privacy and data safety

- All data stays on the device. No network permission is requested; there is
  no analytics or crash reporting SDK.
- History stores a summary, technologies and time. Full payload bytes and tag
  UIDs are stored only if enabled in Settings › Privacy (off by default).
- Backups: excluded from Android cloud backup and device-to-device transfer.
  Users export JSON explicitly via the system file picker.
- Diagnostics exports contain device model, Android version, technologies,
  protocol metadata and error codes — never payloads or UIDs.
- Task log stores action type and outcome only.
- Play Data safety draft: no data collected, no data shared.
