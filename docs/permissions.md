# Permissions

| Permission | Why | When used |
|---|---|---|
| `NFC` | Read/write tags, reader mode | Read, Write, Other |
| `INTERNET` | Webhook workflow action only | When a workflow you built sends a request |
| `POST_NOTIFICATIONS` | *Show notification* action | Requested by Android 13+ at first use |
| `SET_ALARM` | *Set alarm*, *Start timer* | Normal permission |
| `ACCESS_NOTIFICATION_POLICY` | Ringer vibrate/silent | User grants DND access in Settings |
| `WRITE_SETTINGS` | Brightness, auto-rotate, timeout | User grants *Modify system settings* |
| `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `ACCESS_NETWORK_STATE` | Wi-Fi variables; Wi-Fi toggle on Android 9 and older | Automation |
| `BLUETOOTH`, `BLUETOOTH_ADMIN` (≤ API 30), `BLUETOOTH_CONNECT` | Bluetooth variables; toggle on Android 12 and older | Automation |

Not requested: location, contacts, SMS, call, storage, `QUERY_ALL_PACKAGES`,
accessibility. Dialer/SMS/email/calendar actions open the respective app with
a draft; the user completes the action.

Package visibility (`<queries>`): launcher apps (Open app), TTS engines,
dialer/SMS/email/maps handlers, and five root-manager package names for
detection only.

Services bound by the system: Quick Settings tile
(`BIND_QUICK_SETTINGS_TILE`), HCE (`BIND_NFC_SERVICE`, disabled until enabled).

Root (`su`) is not an Android permission: the root manager app asks you when
Tap & Go first requests it from Root Manager › Check access.
