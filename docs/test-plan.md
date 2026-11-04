# Test plan

## Automated

- `./gradlew testDebugUnitTest` — JVM: codecs, parser, rich records, chip
  parsing, validators, view-model state machines (fake gateway, test sources
  only), export/import, task runner.
- `./gradlew connectedDebugAndroidTest` — emulator/device: Room DAO queries,
  migrations v1→v2→v3, Compose UI navigation, writer validation, Wi-Fi record
  builder, task create→run→log. These do **not** exercise NFC hardware.

## Physical-device checklist (required before claiming hardware support)

Record phone model, Android version, tag chip and result for each line.

1. NFC on/off/no-adapter status, quick-settings toggle refresh.
2. Read: NTAG213/215/216 (type, memory, ATQA/SAK, records), ISO 15693, MIFARE
   Classic (metadata only), IsoDep card.
3. Write + verify: text, URL, phone, SMS, email, contact, Wi-Fi (scan with
   stock Android afterwards), Bluetooth, app launch, multi-record; tag full;
   read-only tag; tag removed mid-write.
4. Other: copy tag A→B; erase; format blank NdefFormatable tag; lock
   (disposable tag!); memory dump on NTAG and ICODE.
5. Password (disposable NTAG): set (write-only), confirm write refused without
   password, remove; set with read protection.
6. Tasks: task tag with auto-run Show text + Open Wi-Fi settings — app closed
   and app open; profile without auto-run asks; link action asks.
7. APDU console on an IsoDep card: SELECT, blocked UPDATE BINARY.
8. History, templates, export/import round trip, retention pruning.
