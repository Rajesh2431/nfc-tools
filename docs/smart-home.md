# Smart home

Tools › **Smart home** stores connections; workflows, Device Controls and
the in-app device browser use them.

| System | Setup | Workflow actions |
|---|---|---|
| Home Assistant | URL (e.g. `http://192.168.1.10:8123` or `https://ha.example.com`) + long-lived access token (HA › Profile › Security) | `ha.service` (any `domain.service`, entity, JSON data), `ha.state` (store state in a variable) |
| MQTT | Host, port (1883 / 8883 TLS), optional username/password | `mqtt.publish` (topic, payload, QoS 0/1, retain) |
| Philips Hue | Bridge IP, then press the link button and tap **Pair** | `hue.light` (on/off/keep, brightness), `hue.scene` |
| Shelly Gen 1 / Plus-Pro, Tasmota, WLED | Device IP, optional password | `device.switch` (on/off/toggle, channel, brightness for WLED/Tasmota) |

In workflows, the **Connection name** field refers to the name you gave the
connection (templates expect `Home`, `Hue`, `Broker`, `Desk`).

## Security

- Tokens and passwords are encrypted with AES-256-GCM using a non-exportable
  Android Keystore key (`core/security/SecretBox.kt`). They are never written
  to exports, backups or logs. Restoring app data on another phone makes them
  unreadable; re-enter them.
- Plain HTTP and unencrypted MQTT are allowed **only** to local-network
  addresses (RFC 1918, link-local, CGNAT, IPv6 ULA, `*.local`, `*.lan`,
  `*.home.arpa`), decided from the address text without DNS
  (`smarthome/LanPolicy.kt`). Remote servers need HTTPS / TLS.
- No redirects are followed; responses are capped at 512 KB; 8 s timeouts.
- The MQTT client is publish-only (MQTT 3.1.1, QoS 0/1), written in-app to
  avoid a dependency; it never subscribes or executes received data.

## Android Device Controls (Android 11+)

Long-press power (or the Quick Settings device panel) › add controls ›
Tap & Go. Home Assistant toggleable entities and Hue lights show live state;
Shelly/Tasmota/WLED appear as tap-to-toggle buttons because their state is not
read back; workflows appear as buttons (Premium). A failed state read shows
an error instead of a guessed state. Workflows started here run without a
screen, so actions that open screens report *Unsupported*.

## Widget

Home-screen widget *Tap & Go workflows*: a Scan button plus up to four
workflows chosen in Workflow › Triggers › *Show on widget* (Premium).

## Wi-Fi and location triggers

Workflow › Triggers › *Wi-Fi & location* (Premium): join/leave a Wi-Fi network,
enter/leave an area (50 m – 10 km). Implemented without Google Play Services:
a `ConnectivityManager` network callback delivered by PendingIntent and
`LocationManager.addProximityAlert`. Needs precise location and *Allow all the
time*. Limits: Android may delay area events by minutes; leaving Wi-Fi is
noticed at the next network change; registrations are restored after reboot.

## Templates

Workflows › **Templates**: Arrive home, Leave home, Bedtime, Car mode, Meeting,
Gym, Battery check, Garage door (MQTT), Desk lamp (Shelly/Tasmota/WLED),
Temperature alert (HA sensor → fan). Each is added as an editable copy.
