# Home & Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give CoinTrail a **Home** landing screen (month-to-date overview, budget health, today preview) and a three-tab navigation structure (Home / Monthly / Settings) built on Navigation-Compose, with a single date-parameterized **Day** screen shared by Home and the Monthly calendar.

**Architecture:** One `NavHost` with nested graphs per tab (per-tab back stacks). Drill-in destinations (quick-add, Day, edit expense, budgets, catalog management, reminder, recurring, account, backup, CSV export) are full-screen with back. Existing `XxxRoute`/`XxxScreen` composables are reused; navigation state replaces the `when`-over-flags in `CoinTrailApp`. Home aggregates month-to-date and month-over-month totals via a shared pure-Kotlin summary function fed from `ExpenseRepository` — money math, so TDD applies.

**Tech Stack:** As `2026-10-01-foundation-and-data-layer.md`, plus `androidx.navigation:navigation-compose` 2.8.5. (Do not switch libraries.)

**Spec:** `docs/SPEC.md` §6.1 (Day), §6.11 (Home), §6.12 (Navigation); `docs/adr/0001-navigation-compose.md`. Glossary: `CONTEXT.md`.

## Global Constraints

- Money is integer paisa (`Long`) everywhere; display `৳1,250` / `৳1,250.50` (spec §4). English UI only.
- Times respect the system 12/24-hour setting (spec §4).
- Sparse-data rules (spec §6.11): first-run CTA when no expenses exist; MoM line hidden without prior-month data; budget section hidden when no budgets; today-empty message in the today block.
- Budget progress lives on **Home** and Monthly (spec §6.5), not on the Day screen.
- One task = one green test cycle = one commit. Never commit red.
- Tests only for money-at-risk logic (spec §11) — the Home month-summary math. No UI tests.

## File Structure

- `docs/SPEC.md`, `CONTEXT.md`, `docs/adr/0001-navigation-compose.md`, `docs/superpowers/plans/2026-10-02-home-and-navigation.md` — this plan's deliverables (Task 1).
- `gradle/libs.versions.toml`, `app/build.gradle.kts` — add navigation-compose.
- `app/src/main/java/com/cointrail/ui/navigation/Destinations.kt` — sealed destination hierarchy (tabs + drill-ins).
- `app/src/main/java/com/cointrail/ui/CoinTrailApp.kt` — rewritten: NavHost + NavigationBar + quick-add FAB.
- `app/src/main/java/com/cointrail/MainActivity.kt` — reminder deep link → initial navigation to QuickAdd.
- `app/src/main/java/com/cointrail/ui/day/DayScreen.kt` — generalized Day screen (replaces `ui/today/TodayScreen.kt`).
- `app/src/main/java/com/cointrail/ui/home/HomeScreen.kt` — `HomeRoute` + `HomeScreen` + `HomeViewModel`.
- `app/src/main/java/com/cointrail/domain/reports/MonthSummary.kt` — shared month totals + MoM math.
- `app/src/test/java/com/cointrail/domain/reports/MonthSummaryTest.kt` — unit tests.
- `app/src/main/java/com/cointrail/ui/reports/MonthlyReportsScreen.kt` — `DayExpensesScreen` removed; day taps navigate to the Day destination.

---

### Task 1: Spec & docs updates (no code)

**Files:**
- Create: `CONTEXT.md`, `docs/adr/0001-navigation-compose.md`, `docs/superpowers/plans/2026-10-02-home-and-navigation.md`
- Modify: `docs/SPEC.md` (§6.1 retitle + reword, §6.4/§6.5 cross-refs, new §6.11–§6.12, §12 item 8, §13 dated block Q36–Q41)

**Interfaces:**
- Consumes: nothing.
- Produces: updated source of truth consumed by Tasks 2–5 and any future agent.

- [ ] **Step 1: Update `docs/SPEC.md`** — §6.1 becomes "Day screen" (date-parameterized); add §6.11 Home screen and §6.12 Navigation; §6.5 budget progress bars move Today → Home; §12 adds plan 8; §13 appends the 2026-10-02 decision block.
- [ ] **Step 2: Create `CONTEXT.md`** — glossary (Expense, Home, Day list, Quick-add, Monthly report, Budget, Recurring series, Catalog item). Glossary only, no implementation details.
- [ ] **Step 3: Create `docs/adr/0001-navigation-compose.md`** — decision + considered alternatives + consequences (template per domain-modeling ADR format).
- [ ] **Step 4: Create this plan file.**
- [ ] **Step 5: Commit all four files.**

### Task 2: Navigation-Compose host (blocked by Task 1)

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts` — add `androidx.navigation:navigation-compose` (version catalog entry `navigation-compose`; do not switch other libraries).
- Create: `app/src/main/java/com/cointrail/ui/navigation/Destinations.kt` — sealed destinations: top-level `Home`, `Monthly`, `Settings`; drill-ins `QuickAdd`, `Day(dateIso)`, `Edit(id)`, `Budgets`, `ManageCatalog(kind)`, `Reminder`, `Recurring`, `Account`, `Backup`, `CsvExport`.
- Rewrite: `app/src/main/java/com/cointrail/ui/CoinTrailApp.kt` — `Scaffold` { `NavHost` + `NavigationBar` (Home/Monthly/Settings, shown only on top-level destinations) + `+` FAB ("Add expense") on every tab → QuickAdd }. Delete the 11 `rememberSaveable` flags and their `BackHandler`s (nav owns back).
- Modify: `app/src/main/java/com/cointrail/MainActivity.kt` — `startInQuickAdd` becomes an initial navigation to QuickAdd (reminder path, spec §6.6).

**Interfaces:**
- Consumes: existing `XxxRoute` composables unchanged (`ui/today`, `ui/quickadd`, `ui/edit`, `ui/reports`, `ui/settings`, `ui/backup`, `ui/export`) wired via nav lambdas.
- Produces: app builds; every existing screen reachable via tabs or drill-ins; back pops the nav stack.

- [ ] **Step 1: Add the dependency** (catalog + `app/build.gradle.kts`).
- [ ] **Step 2: Define `Destinations.kt`.**
- [ ] **Step 3: Rewrite `CoinTrailApp.kt` around `NavHost`.**
- [ ] **Step 4: Wire `MainActivity` deep link → QuickAdd.**
- [ ] **Step 5: Build green, commit.**

### Task 3: Day screen (blocked by Task 2)

**Files:**
- Rename/generalize: `app/src/main/java/com/cointrail/ui/today/TodayScreen.kt` → `app/src/main/java/com/cointrail/ui/day/DayScreen.kt` — `DayRoute(date: LocalDate)` + `DayViewModel(date)` (from `TodayViewModel`). Title "Today" when `date` is today, else the formatted date. Keep swipe-to-delete + undo snackbar, row → Edit, 12/24-hour-aware times.
- Modify: `app/src/main/java/com/cointrail/ui/reports/MonthlyReportsScreen.kt` — delete `DayExpensesScreen`; calendar day taps navigate to `Day(date)`.

**Interfaces:**
- Consumes: `DayViewModel` state (day total + rows) and existing repositories.
- Produces: the single Day destination consumed by Home ("See all") and Monthly.

- [ ] **Step 1: Generalize Today → Day (date-parameterized).**
- [ ] **Step 2: Point Monthly day taps at Day; remove `DayExpensesScreen`.**
- [ ] **Step 3: Build green, commit.**

### Task 4: Home screen (blocked by Task 3)

**Files:**
- Create: `app/src/main/java/com/cointrail/ui/home/HomeScreen.kt` — `HomeRoute` + `HomeScreen` + `HomeViewModel` (Route/Screen pattern).
- Create: `app/src/main/java/com/cointrail/domain/reports/MonthSummary.kt` — shared pure functions: month-to-date total, previous-month total, MoM delta (absolute + %), `null` when no prior-month data. `MonthComparisonSection` and Home both consume it (no duplicated money math).
- Create: `app/src/test/java/com/cointrail/domain/reports/MonthSummaryTest.kt` — TDD unit tests (month boundary, empty month, no prior month → null, delta %).
- Reuse: `BudgetProgressSection` (`ui/components/BudgetProgressBars.kt`), `Money` formatting (`core/Money.kt`), Day-row rendering pattern.

**Interfaces:**
- Consumes: `ExpenseRepository` totals queries, `BudgetProgress` domain, `MonthSummary`.
- Produces: Home destination with state: `monthTotalPaisa`, `todayTotalPaisa`, `todayCount`, `recent` (last 5), `momDelta?`, `budgets`, `hasAnyExpenses`.

- [ ] **Step 1: Write `MonthSummaryTest`, implement `MonthSummary` (red → green).**
- [ ] **Step 2: Build `HomeViewModel` over repositories + `MonthSummary`.**
- [ ] **Step 3: Build `HomeScreen` layout: hero + MoM line, today block (preview rows → Edit, "See all" → Day(today), empty message), budget section (tap → Budgets, hidden when none), first-run CTA → QuickAdd.**
- [ ] **Step 4: Tests green, commit.**

### Task 5: Cleanup + verification (blocked by Task 4)

- [ ] **Step 1: Grep-clean** — no remaining `TodayRoute`, `TodayScreen`, `DayExpensesScreen`, flag-navigation symbols (keep the `startInQuickAdd` intent contract).
- [ ] **Step 2: Run the suite + debug build** (see Verification).
- [ ] **Step 3: Mark all checkboxes done, commit, push, leave the working tree clean.**

## Verification

```bash
cd /Users/md.ahsanhabibsowmik/Documents/projects/CoinTrail
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :app:testDebugUnitTest
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :app:assembleDebug
```

Manual click-through (hand to the user with exact steps):
1. Cold launch lands on **Home**; hero shows month-to-date in ৳.
2. Tabs Home/Monthly/Settings switch and each keeps its state; back from a root tab exits the app.
3. `+` on each tab opens quick-add; saving updates Home totals immediately.
4. Home recent row → Edit; "See all" → Day titled "Today"; swipe-delete + undo works on Day.
5. Monthly calendar day tap → Day titled with that past date; edit/back-fill works.
6. Budget section tap → Budget settings; hidden when no budgets set.
7. No expenses: first-run CTA opens quick-add. No previous month: MoM line hidden.
8. Reminder notification opens quick-add directly (`startInQuickAdd` path).
9. Day times respect system 12/24-hour format.
