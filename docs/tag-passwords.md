# NTAG password protection

Supported chips: NXP **NTAG213, NTAG215, NTAG216**, identified exactly with
GET_VERSION (0x60). Any other chip — including NTAG210/212 and Ultralight
EV1 — is refused with "chip unsupported"; config pages differ and a wrong
write can break a tag.

## Key derivation (stable format)

`SHA-256(UTF-8 password)` → bytes 0..3 are PWD, bytes 4..5 are PACK.
Passwords must be 4–64 characters. This is **not** interoperable with other
apps: a password set elsewhere cannot be removed here and vice versa.

## Write order

Set: PWD page → PACK page → CFG1 (ACCESS.PROT = read protection on/off) →
CFG0 (AUTH0 = page 4) **last**, so the tag is never protected with a
half-written key. Success is then verified by authenticating (PWD_AUTH 0x1B)
and comparing PACK.

Remove: authenticate → CFG0 AUTH0 = 0xFF → clear PROT → reset PWD/PACK.

| Chip | CFG0 | CFG1 | PWD | PACK |
|---|---|---|---|---|
| NTAG213 | 0x29 | 0x2A | 0x2B | 0x2C |
| NTAG215 | 0x83 | 0x84 | 0x85 | 0x86 |
| NTAG216 | 0xE3 | 0xE4 | 0xE5 | 0xE6 |

Forgotten passwords cannot be recovered or reset by any app. The UI requires
double entry, an "I understand" checkbox and a final confirmation.

Hardware status: **not yet validated on a physical tag** (AGENTS rule 11).
