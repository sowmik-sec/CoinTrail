# CoinTrail — Play-Store-readiness checklist

CoinTrail **v1 is sideloaded** by its author. This checklist exists so that sharing it with friends
and family later is a *distribution* step — an upload and a listing — rather than a rewrite (SPEC §2,
§12 Plan 7). Nothing here changes app behaviour; it is a list of things to do and to verify against
the current Google Play requirements (which do change — check the Play Console for the latest).

## 1. Release signing

The release build signs with an **upload key** whose credentials never enter version control.

- [ ] Generate the upload keystore once and keep it (and its passwords) safe and backed up:
  ```bash
  keytool -genkeypair -v \
    -keystore cointrail-release.jks \
    -alias cointrail \
    -keyalg RSA -keysize 2048 -validity 10000
  ```
- [ ] Copy `keystore.properties.example` to `keystore.properties` and fill in the values.
- [ ] Confirm the credentials are ignored by git: `git check-ignore keystore.properties` prints the path.
- [ ] Confirm `git status` shows no keystore or `keystore.properties` staged.
- [ ] Enroll in **Play App Signing** so Google holds the real app-signing key (the local keystore
      becomes the *upload* key). If the upload key is ever lost, it can be reset in the Play Console —
      this is why Play App Signing matters.

Without `keystore.properties` the release build is left unsigned (so a fresh clone still builds);
`assembleRelease` then emits `app-release-unsigned.apk`.

## 2. Build the release artifact

```bash
# AGP 8.7 needs JDK 17. On this machine the default is 25, and `/usr/libexec/java_home -v 17` will
# NOT find 17 (only JDK 25 is registered) — use the Homebrew keg instead:
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :app:assembleRelease                     # sideloadable APK
./gradlew :app:bundleRelease                       # Play upload (AAB)
```

- APK: `app/build/outputs/apk/release/app-release.apk`
- AAB: `app/build/outputs/bundle/release/app-release.aab`

- [ ] `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk` reports
      *Verified* (v2 scheme) with your certificate.
- [ ] R8/minification is intentionally **off** in `release` for v1 (`isMinifyEnabled = false`); the
      APK is byte-for-byte behaviour of the debug build minus debuggability. Re-enable later only with
      ProGuard rules for Room, Compose, WorkManager and Google Sign-In, and re-test the full list below.

## 3. Sideload + smoke test (the v1 distribution path)

- [ ] Put your Google OAuth **web client ID** in the gitignored `local.properties`
      (`GOOGLE_WEB_CLIENT_ID=...`) before testing. It is machine-specific; without it the build still
      succeeds, but sign-in — and therefore Drive sync/backup — is unavailable (SPEC §7). Rebuild after
      adding it.
- [ ] Install on a physical device (transfer the APK, allow install from unknown sources).
- [ ] Run the acceptance smoke test end-to-end:
  - [ ] Log an expense and see it on Today with the running total.
  - [ ] Quick-add from the daily reminder notification (5-second flow, custom keypad).
  - [ ] Edit an expense; swipe-delete with the undo snackbar.
  - [ ] Set an overall + per-category budget; confirm the 80% and 100% alerts fire.
  - [ ] Monthly reports: heatmap, per-category bars/donut, month-over-month deltas.
  - [ ] Create a recurring series; confirm month-end clamping and pause/resume.
  - [ ] Add/rename/hide a category and a payment method.
  - [ ] CSV export for a date range.
  - [ ] Sign in with Google; sync between two devices; deletions propagate and never resurrect.
  - [ ] Weekly/on-demand Drive snapshot; restore into a fresh install.
  - [ ] JSON export → import round-trip.

## 4. App identity & branding

- [ ] Adaptive launcher icon ships: `mipmap-anydpi-v26/ic_launcher(.xml/_round.xml)` over
      `drawable/ic_launcher_foreground` (and a `monochrome` layer for themed icons), backed by
      `color/ic_launcher_background`.
- [ ] App name comes from `@string/app_name` ("CoinTrail"), not a hardcoded literal.
- [ ] Prepare Play **listing** assets (not part of the APK):
  - [ ] 512×512 app icon (32-bit PNG, ≤1 MB).
  - [ ] 1024×500 feature graphic.
  - [ ] At least 2 phone screenshots (to satisfy the "full-scope" listing rules).

## 5. Store listing

- [ ] App name (≤30 chars), short description (≤80 chars), full description (≤4000 chars).
- [ ] Category (Finance), contact email, support/privacy URLs.
- [ ] Content rating questionnaire.
- [ ] Data safety form (see §6).
- [ ] Countries/regions and pricing (free).

## 6. Data safety & privacy

- [ ] Publish a privacy policy (Play requires a URL even for a personal app).
- [ ] Data safety form — CoinTrail's honest answers (SPEC §7, §8):
  - Data collected: the Google account email (for sign-in) and the expense data the user chooses to
    sync — stored only in the user's **own** Google Drive app folder, never on a project server.
  - No data shared with third parties; **no analytics, no crash reporting, no third-party SDKs**
    beyond Google Sign-In / Drive.
  - Data encrypted in transit (TLS to Google APIs); no server-side storage to breach.
- [ ] Account deletion: CoinTrail has no server-side account. Document the path clearly —
      **sign out** hides data, **"remove my data from this device"** deletes the local copy, and the
      user can delete the app's folder in their own Drive. State this in the listing and privacy
      policy so the Play "account deletion" requirement is satisfiable without a backend.
- [ ] Permissions justified: `INTERNET` and `ACCESS_NETWORK_STATE` (Google Drive sync),
      `POST_NOTIFICATIONS` (the daily reminder) — all core features, no others requested.

## 7. Rollout tracks

- [ ] **Internal testing** first (your own device).
- [ ] **Closed testing** with friends/family (email list or Google Group).
- [ ] **Production** (optionally staged rollout).

## 8. Versioning

- [ ] Bump `versionCode` (integer) on **every** upload — Play rejects a reused code.
- [ ] Keep `versionName` human-readable — the current value lives in `app/build.gradle.kts`.

## 9. After launch

- [ ] Review the pre-launch report for device-specific crashes.
- [ ] Keep the upload key backed up; a lost upload key needs a Play Console reset.
- [ ] Remember the ten-year rule (SPEC §7): backup formats stay open (versioned JSON) — never a
      store-only or binary format.
