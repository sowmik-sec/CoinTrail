# Memory

## Project Overview
See @README.md for project overview and @package.json for available npm/pnpm commands for this project.

## Specs & Plans (read before any code)
- @docs/SPEC.md — FINAL product & technical spec (source of truth; includes the full decision log).
- docs/superpowers/plans/ — implementation plans, one per subsystem, executed in series order listed in SPEC §12. Follow exactly; do not contradict the spec.

## Code Style Guidelines
- Use descriptive variable names
- Follow existing patterns in the codebase
- Extract complex conditions into meaningful boolean variables

## Architecture Notes
Add important architectural decisions and patterns here.

## Common Workflows
- Builds need **JDK 17** (AGP 8.7 / Gradle 8.10). The default JDK on this machine is 25, which fails
  with a cryptic `IllegalArgumentException: 25.0.2`. Prefix commands:
  `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew ...`
- Test: `./gradlew :app:testDebugUnitTest` — unit tests only (SPEC §11); no UI tests.
- Release APK: `./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
  (signed when `keystore.properties` exists, else `*-unsigned.apk`).
- Play upload bundle: `./gradlew :app:bundleRelease`.
- Release signing reads the gitignored `keystore.properties` (see `keystore.properties.example`);
  keystores and credentials are never committed. Publishing steps: `docs/PLAY_STORE_CHECKLIST.md`.
