# AGENTS.md — NFC Studio

Rules for AI agents and humans working in this repository. Source of truth for
product scope: `docs/spec.md` (exported from the original specification docx).

## Project

- Android NFC utility (reader, writer, tag inspector, task profiles).
- Kotlin + Jetpack Compose + Material 3 + Navigation Compose, single `:app`
  module for M1; package structure mirrors the spec section 7 so later module
  extraction is mechanical.
- Toolchain: AGP 9.4.1 with built-in Kotlin (no `org.jetbrains.kotlin.android`),
  Compose compiler plugin `org.jetbrains.kotlin.plugin.compose` pinned to
  `kotlin = 2.4.21`, Gradle wrapper 9.7.1, JDK 17.
- SDK: compileSdk 37, minSdk 26, targetSdk 36. All versions pinned in
  `gradle/libs.versions.toml`; do not bump without verifying AGP/compiler/JDK
  compatibility and documenting why.

## Commands

```bash
./gradlew assembleDebug        # debug APK (M1 exit gate)
./gradlew testDebugUnitTest    # JVM unit tests
./gradlew lintDebug            # Android Lint
./gradlew check                # aggregate
```

## Engineering rules

1. Follow Kotlin conventions and the package boundaries in `docs/spec.md` § 7.
2. Do not introduce dependencies without explaining why and verifying
   compatibility against the pinned toolchain.
3. Do not run a release build until the debug build and tests pass.
4. Never silently delete or rewrite user data; write Room migrations and test
   them (from M4 on).
5. All NFC technology connections must be closed safely; surface I/O errors as
   typed results, never as swallowed exceptions (from M2 on).
6. Treat every tag payload as untrusted input. Never auto-execute tag data,
   shell commands, or serialized objects; validate URL schemes before opening.
   Exception (ADR 0001): a profile the user explicitly marked "run
   automatically" may start when its task-tag token or trigger matches; only
   in-app display and Settings-screen actions skip confirmation.
7. Destructive tag operations (overwrite, format, lock) require explicit
   confirmation UI and an explanation that they may be irreversible.
8. Do not fake results: no mock tag data, no simulated scanner in production
   code paths, no fabricated success states. Uncapable hardware gets truthful
   status messaging.
9. Do not claim unsupported NFC technologies are implemented.
10. Every change ships with tests where testable, and with real verification
    evidence (commands + outcomes). "It should work" is not a test result.
11. Hardware-dependent features are only considered validated after a physical
    device test is recorded; emulator results must be labeled as such.
12. Keep secrets, signing keys and personal data out of prompts, logs and Git.
13. Redact payloads and identifiers from logs and crash reports (from M2 on).
14. Update `docs/` acceptance records when a milestone feature lands; keep
    commit messages conventional (< 72 char subject, no emojis).
