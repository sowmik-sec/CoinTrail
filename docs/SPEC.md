# CoinTrail — Product & Technical Spec

**Status:** FINAL — 2026-10-01, output of a complete design-tree grilling session.
**This file is the source of truth.** Any agent (or human) building or maintaining CoinTrail must read this before writing code. Implementation plans live in `docs/superpowers/plans/`, one per subsystem, and must not contradict this spec. If reality forces a change, update this file first.

---

## 1. Overview

CoinTrail is a personal expense tracker for Android. The core promise:

1. **Fast daily logging** — a nightly nudge and a 5-second quick-add screen.
2. **Data that survives a decade** — survives phone loss, phone changes, and app rewrites, via Google Drive sync + backups in open formats.

v1 is built for and used by its author. It may later be shared with friends and family, so per-account data isolation is designed in from day one.

## 2. Users & distribution

- **v1:** author only, sideloaded APK.
- **Later:** friends/family via Play Store (project kept Play-Store-ready: proper release signing; sharing is a distribution step, not a rewrite).
- **Isolation:** strictly per Google account. No household/shared ledgers.

## 3. Platform & stack

- Native Android: Kotlin, Jetpack Compose, Material 3.
- minSdk 26 (Android 8.0); compileSdk/targetSdk 35 (safe to bump).
- Single `:app` module; Gradle Kotlin DSL + version catalog; package/applicationId `com.cointrail`; repo root is the project root.
- Room (SQLite) for storage; WorkManager for reminders, recurring generation and backup jobs; coroutines + Flow.
- **No backend.** The only cloud dependency is Google (Sign-In + Drive).

## 4. Global conventions

- **Money:** integer paisa (`Long`) everywhere. Floating point is forbidden. Display: `৳1,250` (whole taka) unless paisa is non-zero → `৳1,250.50`. Entry accepts decimals.
- **Currency:** single, BDT (৳). No multi-currency, ever, in current scope.
- **Language:** English UI only.
- **Time:** `java.time` on device-local timezone. "Day" = local calendar day; "month" = local calendar month.
- **IDs:** UUID strings generated client-side. Presets use stable string IDs (`preset-food`, `pm-cash`, …) so sync across devices never duplicates them.

## 5. Data model (Room schema v1)

Five tables. All rows carry `updatedAt`; deletable tables carry `deletedAt` tombstones (for sync). Categories and payment methods are never deleted — they are hidden.

| Table | Columns |
|---|---|
| `expenses` | `id` (PK), `amountPaisa`, `categoryId`, `note?`, `paymentMethodId?`, `occurredAt`, `createdAt`, `updatedAt`, `deletedAt?` — indexed on `occurredAt`, `updatedAt` |
| `categories` | `id` (PK), `name`, `isPreset`, `isHidden`, `sortOrder`, `updatedAt` |
| `payment_methods` | same shape as `categories` |
| `budgets` | `id` (PK), `scopeKey` (categoryId or `__overall__`, unique), `monthlyLimitPaisa`, `updatedAt`, `deletedAt?` |
| `recurring_series` | `id` (PK), `amountPaisa`, `categoryId`, `note?`, `paymentMethodId?`, `dayOfMonth` (1–31), `startMonth` (ISO `YYYY-MM`), `lastGeneratedMonth?`, `isPaused`, `updatedAt`, `deletedAt?` |

Validation: expense amount and budget limits must be positive; expense must have a category; `dayOfMonth` in 1..31.

## 6. Features

### 6.1 Today screen (everyday screen)
Chronological (descending) list of today's expenses, running total at top, budget progress bars (overall + per category). Tap an expense to edit; swipe to delete with an undo snackbar.

### 6.2 Quick-add screen (reminder target)
Custom in-app numeric keypad + tappable category chips + optional payment-method selector + collapsed note field (tap to expand). Amount field focused on open; no system keyboard for the amount. Entry must be doable in ~5 seconds.

### 6.3 Edit expense
Amount, category, note, payment method, and datetime (for back-filling) are all editable. Delete via undo-snackbar semantics (Q28: gone after undo window; tombstoned internally for sync; no visible trash/audit).

### 6.4 Monthly view
Calendar-style heatmap grid (each day a cell with its total), monthly total, per-category breakdown as horizontal bars (donut available as a switch), and month-over-month comparison: total delta **and** per-category deltas vs previous month. Tapping a day opens that day's list.

### 6.5 Budgets
One overall monthly budget plus optional per-category monthly budgets. **No rollover** of unused budget. Progress bars on Today and Monthly screens. Notifications at threshold crossings: **80% (warning)** and **100% (exceeded)** — one notification per budget per month per threshold.

### 6.6 Daily reminder
Daily local notification at a configurable time (default 21:30), **suppressed if at least one expense is already logged that day** (partial-logging days still get the nudge). Tapping the notification opens the quick-add screen. Scheduled via WorkManager with catch-up if the device was off.

### 6.7 Recurring expenses
Auto-created monthly entries (rent, internet, subscriptions). Rules:
- Generate on `dayOfMonth`, clamping 31 → month end.
- Auto-created entries are ordinary expenses: editing/deleting one occurrence never affects the series.
- Each series can be paused/resumed. Deleting a series tombstones it but keeps its generated expenses.
- Monthly frequency only in v1 (weekly/yearly later).
- Generation runs on app open and via background job when `lastGeneratedMonth < current month`.

### 6.8 Categories & payment methods
Small preset lists plus custom entries. Operations: add, rename, hide. No hard delete (expenses reference them).

Preset categories (stable IDs): `preset-food` Food, `preset-groceries` Groceries, `preset-transport` Transport, `preset-utilities` Utilities, `preset-rent` Rent, `preset-health` Health, `preset-shopping` Shopping, `preset-entertainment` Entertainment, `preset-other` Other.

Preset payment methods: `pm-cash` Cash, `pm-bkash` bKash, `pm-nagad` Nagad, `pm-card` Card. The field is optional on an expense.

### 6.9 CSV export
Export any date range. Columns: `date,time,amount_taka,category,payment_method,note` (ISO date/time; amount with plain `.` decimal separator; UTF-8).

### 6.10 Settings
Reminder time, budget management, category & payment-method management, recurring series management, backup/restore, CSV export, sync-now + Google account section (sign in/out, "remove my data from this device").

## 7. Sync & backup (Drive-centric, no backend)

Chosen architecture (grilling Q13/Q21): **one substrate — Google Drive — does both sync and backup.**

- **Sign-in:** Google Sign-In (Credential Manager). Local-first (Q29): the app works with no account at all; signing in enables sync + Drive backups. Local data is namespaced per Google account; sign-out hides data but keeps a local copy; an explicit "remove my data from this device" action deletes it.
- **Transport:** the app's private Drive app folder (each user's data lands in *their own* Drive — per-account isolation for free).
- **Sync:** offline-first change journal — full row state + tombstones, **last-write-wins per record by `updatedAt`** (acceptable: expenses are append-mostly; Q21). Sync triggers: app open, periodic background job (~every 6h), manual "Sync now". Latency of seconds-to-minutes is accepted; no live mirroring.
- **Snapshots (backups):** timestamped full backups written to Drive weekly (and on demand via "Backup now"); an in-app list of snapshots with **restore = LWW-merge into local data** (idempotent, safe on fresh or existing devices).
- **Full backup file format:** versioned JSON (Q34) — `{"format": "cointrail-backup", "version": 1, "exportedAt": <ISO>, "tables": {...}}` with paisa integers and ISO timestamps; human-readable, forward-migratable. Manual export/import of this file anywhere via the system document picker ("save it wherever" — Q8). Format documented in `docs/BACKUP_FORMAT.md` when Plan 6 lands.
- **10-year rule:** backup formats must stay parseable by a future app or by hand. Never ship a binary-only backup.

## 8. Security & privacy

- No app lock (phone-level security is enough — Q9).
- Data lives in app-private storage and the user's own Drive app folder.
- No analytics, no crash reporting, no third-party SDKs other than Google auth/Drive.

## 9. Non-goals (v1)

No income records, no receipt photos, no home-screen widgets, no household/shared ledgers, no multi-currency, no Bangla UI, no visible trash/audit trail, no app lock, no Material You dynamic color, no real-time live mirroring.

## 10. Theme

Dark + light modes with a fixed calm green/teal palette. Dynamic color is deliberately off (it washes out budget progress colors).

## 11. Testing policy

Unit tests only where money or data durability is at risk (Q35):
- Money math and formatting/parsing
- Budget calculation and 80%/100% threshold logic
- Recurring generation incl. month-end clamping and series/occurrence independence
- Month-over-month math
- Sync journal merge (LWW, tombstones)
- Backup JSON round-trip and CSV correctness

No UI tests.

## 12. Plan series (one plan per subsystem, in `docs/superpowers/plans/`)

1. **Foundation & data layer** — Gradle scaffold, Money, domain models, Room schema v1, repositories, preset seeding. *(Plan 2026-10-01-foundation-and-data-layer.md — written.)*
2. **Core UI** — Today screen, quick-add keypad, edit/delete + undo, category & payment-method management screens.
3. **Budgets + reminder** — budget engine, WorkManager daily reminder, budget threshold notifications, progress UI.
4. **Reports** — monthly heatmap calendar, per-category bars/donut, month-over-month deltas.
5. **Recurring engine** — monthly generation, pause/resume, occurrence independence.
6. **Drive sync + backup** — sign-in, journal, LWW merge, snapshots, JSON backup/restore, CSV export.
7. **Release** — release signing, icon/branding, Play-Store-readiness checklist for sharing.

Each plan must produce working, testable software on its own and follow TDD with frequent commits.

## 13. Decision log (grilling session, 2026-10-01)

- Q1 durability is a core requirement: phone loss/change must never lose data → Drive.
- Q2 daily local notification at configurable time → quick-add screen.
- Q3 expense = amount + category + optional note + editable datetime (+ payment method Q26).
- Q4 categories: preset + custom add/rename/hide.
- Q5 reports: today + running total, daily totals per month, monthly total, per-category chart, MoM.
- Q6 budgets: monthly overall + per-category, no rollover (Q16), 80% + 100% notifications (Q16).
- Q7 single currency BDT. Q18 English UI only.
- Q8 CSV export (any range) + full backup/restore file + automatic + cloud; 10-year readability (Q34: versioned JSON).
- Q9/Q29 local-first, optional Google sign-in, per-account namespacing, explicit on-device removal.
- Q10 minSdk 26. Q11 Kotlin + Compose + Material 3. Q12 project root = this repo, name CoinTrail, package `com.cointrail`.
- Q13/Q21 Drive-centric sync+backup in one substrate; LWW per record; no backend. Q14 multi-device live use; friends/family later, isolated per account. Q22 no shared ledgers.
- Q23 sideload v1, Play-Store-ready signing. Q24 recurring: monthly, clamp to month end, occurrence-independent, pause/resume.
- Q25 expenses only. Q27 no receipt photos. Q28 undo snackbar + tombstones, no visible trash.
- Q30 custom keypad quick-add. Q31 integer paisa, whole-taka display unless paisa non-zero. Q32 dark+light fixed green/teal palette. Q33 calendar heatmap + horizontal bars (donut switchable). Q35 tests on money-at-risk logic only.
