# Hardware testing guide

Mocked and emulator tests are **not** proof of NFC compatibility. A feature
moves to HARDWARE_VERIFIED in DEVELOPMENT_STATUS.md only with a recorded run
below (device, Android version, tag, result, date).

## Equipment

- Android phone with NFC (and HCE for emulation), Android 8+.
- Tags: NTAG213, NTAG215, NTAG216; MIFARE Ultralight (EV1/C); a MIFARE Classic
  1K (reading only on NXP-chip phones); an ISO-DEP card (e.g. DESFire) for the
  APDU console; an NFC-V (ICODE SLIX) tag if available.
- Second phone or USB reader for HCE.
- Rooted test phone (Magisk or KernelSU) for Root Manager.

## Checklist

| # | Test | Expected |
|---|---|---|
| 1 | Read blank NTAG213 | Chip NTAG213, 144 B user memory, NDEF formattable/empty |
| 2 | Write URL, Text, vCard, Wi-Fi to NTAG215 | Size preview exact; read-back verified; phone opens URL on scan |
| 3 | Write > capacity to NTAG213 | Refused before writing with required vs available bytes |
| 4 | Remove tag mid-write | Typed error, no false success |
| 5 | Copy NTAG213 → NTAG215 | Same NDEF; UID differs (not cloned) |
| 6 | Erase, format, lock (spare tag) | Two confirmations for lock; tag read-only after |
| 7 | Set/remove NTAG password | Write blocked without password; read unaffected |
| 8 | Memory dump NTAG216 | 231 pages; matches another reader |
| 9 | MIFARE Classic on non-NXP phone | Truthful "not supported by this phone" |
| 10 | APDU console on ISO-DEP card | SELECT returns status word decoded |
| 11 | Task tag (profile + workflow) with app closed | App opens, confirm or auto-run as configured |
| 12 | Workflow shortcut + QS tile | Runs; history shows SHORTCUT / TILE |
| 13 | Scheduled workflow (15 min) | Runs within Android's window; history SCHEDULE |
| 14 | `adb shell am start … ExternalLaunch` | Refused until both switches on; then confirm/run |
| 15 | HCE: emulate URL, read with second phone | Reader sees Type 4 NDEF URL; APDU log shows SELECT/READ BINARY |
| 16 | Root: Check access on rooted phone | Prompt from manager; Granted, implementation shown |
| 17 | Root: Wi-Fi off/on, density, profile | Real state change; history entries |
| 18 | Root on unrooted phone | "Root not available"; actions locked |

Record results in `docs/acceptance-v1.md` under *Hardware runs*.
