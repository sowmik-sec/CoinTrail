# CoinTrail — Product & Technical Spec

**Status:** FINAL — 2026-10-01; amended 2026-10-02 (Home & navigation, §6.11–6.12; per-month budgets, §5–§7, §11–§13).
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
- **Time:** `java.time` on device-local timezone. "Day" = local calendar day; "month" = local calendar month. Times in the UI respect the system 12/24-hour setting.
- **IDs:** UUID strings generated client-side. Presets use stable string IDs (`preset-food`, `pm-cash`, …) so sync across devices never duplicates them.

## 5. Data model (Room schema v2)

Five tables. All rows carry `updatedAt`; deletable tables carry `deletedAt` tombstones (for sync). Categories and payment methods are never deleted — they are hidden.

| Table | Columns |
|---|---|
| `expenses` | `id` (PK), `amountPaisa`, `categoryId`, `note?`, `paymentMethodId?`, `occurredAt`, `createdAt`, `updatedAt`, `deletedAt?` — indexed on `occurredAt`, `updatedAt` |
| `categories` | `id` (PK), `name`, `isPreset`, `isHidden`, `sortOrder`, `updatedAt` |
| `payment_methods` | same shape as `categories` |
| `budgets` | `id` (PK), `scopeKey` (categoryId or `__overall__`), `month?` (ISO `YYYY-MM`; null = the default budget), `monthlyLimitPaisa?` (null only with a `month` = "no budget" for it), `updatedAt`, `deletedAt?` — unique on (`scopeKey`, `month`) |
| `recurring_series` | `id` (PK), `amountPaisa`, `categoryId`, `note?`, `paymentMethodId?`, `dayOfMonth` (1–31), `startMonth` (ISO `YYYY-MM`), `lastGeneratedMonth?`, `isPaused`, `updatedAt`, `deletedAt?` |

Validation: expense amount must be positive; a budget limit must be positive where present — every default budget has one, while a month override may instead be null ("no budget" for that month); at most one `budgets` row per (`scopeKey`, `month`), the default included; expense must have a category; `dayOfMonth` in 1..31.

Schema v2 (from v1): `budgets` gained `month` and its unique key became (`scopeKey`, `month`); the migration maps existing v1 rows to default budgets.

## 6. Features

### 6.1 Day screen (everyday screen)
Date-parameterized list of one day's expenses, chronological (descending), with that day's total at top. "Today" is the Day screen for the current date (titled "Today"); any other date is titled with the date. Tapping a day in the Monthly calendar (§6.4) opens the same screen for that date. Tap an expense to edit; swipe to delete with an undo snackbar.

### 6.2 Quick-add screen (reminder target)
Custom in-app numeric keypad + tappable category chips + optional payment-method selector + collapsed note field (tap to expand). Amount field focused on open; no system keyboard for the amount. Entry must be doable in ~5 seconds.

### 6.3 Edit expense
Amount, category, note, payment method, and datetime (for back-filling) are all editable. Delete via undo-snackbar semantics (Q28: gone after undo window; tombstoned internally for sync; no visible trash/audit).

### 6.4 Monthly view
Calendar-style heatmap grid (each day a cell with its total), monthly total, per-category breakdown as horizontal bars (donut available as a switch), and month-over-month comparison: total delta **and** per-category deltas vs previous month. Tapping a day opens the Day screen (§6.1) for that date.

### 6.5 Budgets
Each budget scope — overall or a category — is governed by a **default budget** (its standing monthly limit) and optional **budget overrides** for individual months (`docs/adr/0002-budget-defaults-and-overrides.md`; terms in `CONTEXT.md`):
- A month's **effective budget** is its override if it has one, else the default; a month with neither has no budget. An override is either an explicit limit or an explicit "no budget for this month" (no progress bars, no alerts). Removing an override returns the month to the default — it never means "no budget".
- Overrides can be set for any month, past or future; past months are freely restatable (no audit trail, cf. Q28). Changing the default retroactively restates every month without an override (Q46).

**No rollover** of unused budget. Progress bars on Home and Monthly screens show the month's effective budget (Home always shows the current month). Notifications at threshold crossings: **80% (warning)** and **100% (exceeded)** — one notification per scope per month per threshold; a mid-month budget change never re-arms a threshold already crossed for that scope and month (Q48).

Budget management (§6.10) is the only editing surface: a month picker selects the month being edited (or the default), with "use default for this month" and "no budget for this month" as distinct actions (Q47).

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

### 6.11 Home screen (landing)
The app's front door (Q36): at-a-glance overview instead of landing in a detail list. Top to bottom:
- **Hero:** month-to-date total ("Spent in {Month}"), with a month-over-month delta line underneath — hidden when there is no previous-month data.
- **Today block:** today's total + entry count + the last 5 entries. Tapping an entry opens Edit; "See all" opens the Day screen for today. When today has no entries: "No expenses yet today".
- **Budget progress section** (§6.5): hidden when no budgets are configured; tapping it opens budget management (§6.10).
- **First run:** when no expenses exist at all, Home shows a "Log your first expense" call-to-action that opens quick-add.

### 6.12 Navigation
Three bottom tabs — **Home, Monthly, Settings**. Drill-in screens (quick-add, Day, edit expense, budgets, category & payment-method management, daily reminder, recurring, account, backup, CSV export) are full-screen with back navigation. The quick-add FAB (+) is present on every tab. The daily reminder notification opens quick-add directly (§6.6). Implemented with Navigation-Compose (`docs/adr/0001-navigation-compose.md`).

## 7. Sync & backup (Drive-centric, no backend)

Chosen architecture (grilling Q13/Q21): **one substrate — Google Drive — does both sync and backup.**

- **Sign-in:** Google Sign-In (Credential Manager). Local-first (Q29): the app works with no account at all; signing in enables sync + Drive backups. Local data is namespaced per Google account; sign-out hides data but keeps a local copy; an explicit "remove my data from this device" action deletes it.
- **Transport:** the app's private Drive app folder (each user's data lands in *their own* Drive — per-account isolation for free).
- **Sync:** offline-first change journal — full row state + tombstones, **last-write-wins per record by `updatedAt`** (acceptable: expenses are append-mostly; Q21), records keyed by `id` except budgets, keyed by (`scopeKey`, `month`) so two months' overrides never annihilate each other. Sync triggers: app open, periodic background job (~every 6h), manual "Sync now". Latency of seconds-to-minutes is accepted; no live mirroring.
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
- Budget calculation (incl. effective-budget resolution: default vs override vs "no budget") and 80%/100% threshold logic
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
8. **Home & navigation restructure** — Home landing screen, 3-tab Navigation-Compose host, Day screen unification. *(Plan 2026-10-02-home-and-navigation.md — written.)*
9. **Per-month budgets** — default budgets + month overrides (incl. "no budget" months), effective-budget resolution, month picker in budget management, (scope, month) sync/backup keying.

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

### Decision log (grilling session, 2026-10-02 — Home & navigation)

- Q36 landing on a bare day list felt like a tool without a front door → a **Home** overview screen is the landing screen (§6.11); the old "Today screen" becomes the date-parameterized **Day** screen (§6.1).
- Q37 three bottom tabs: Home / Monthly / Settings (§6.12).
- Q38 one Day screen shared by Home's "See all" and the Monthly calendar's day taps; the separate reports-internal day list is removed.
- Q39 Navigation-Compose replaces the flag-based screen switching — `docs/adr/0001-navigation-compose.md`.
- Q40 quick-add FAB on every tab; the reminder → quick-add deep link is unchanged (Q2).
- Q41 Home content: month-to-date hero + MoM delta (hidden without prior-month data), today block with a last-5 preview (row → edit), budget section (tap → budget management; hidden when none), first-run CTA.

### Decision log (grilling session, 2026-10-02 — per-month budgets)

- Q42 users need different limits for different months → each scope keeps a **default budget** plus per-month **budget overrides** (incl. an explicit "no budget" state) over per-month-only rows or effective-dated limits — `docs/adr/0002-budget-defaults-and-overrides.md`.
- Q43 overrides apply uniformly to overall and per-category scopes.
- Q44 "no budget for this month" is an explicit override state, distinct from removing an override (= back to the default).
- Q45 any month — past or future — can get or change an override; past months are freely restatable (Q28's no-audit stance).
- Q46 changing the default retroactively restates every month without an override; no bulk "keep past months at their old limits" action in v1.
- Q47 budget management with a month picker is the only editing surface; no Monthly-report editing affordance.
- Q48 threshold alerts keyed by (scope, month, threshold): one per scope per month per threshold; a mid-month budget change never re-arms.
- Q49 journal/backup stay format v1: budget rows gain nullable `month` (absent = default); LWW merge key becomes (scope, month); a pre-override reader degrades overrides to one row per scope.
