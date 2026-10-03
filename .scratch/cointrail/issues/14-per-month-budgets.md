# 14: Per-month budgets (default budgets + budget overrides)

**Status:** ready-for-agent

## Problem Statement

Spending varies by month — Eid shopping, a trip, a salary change — but CoinTrail applies one monthly limit per budget scope to every month. There is no way to say "this month is different", so budget progress bars and threshold alerts measure months that genuinely differ against the wrong limit.

## Solution

Each budget scope (overall or a category) keeps a **default budget** — its standing monthly limit — and can carry **budget overrides** for individual months. A month's **effective budget** is its override if it has one, else the default; an override can explicitly mean "no budget for this month". All editing happens in budget management via a month picker: pick the default or any month (past or future), then give each scope an explicit limit, an explicit "no budget", or "use default". Home and Monthly measure against the month's effective budget, and threshold alerts respect it too. Per-month budgets sync and back up like everything else.

## User Stories

1. As a CoinTrail user, I want each budget scope to have a default budget, so that most months are budgeted without any per-month work.
2. As a CoinTrail user, I want to give a specific month its own limit for a scope, so that an unusual month (Eid, travel, a wedding) is measured against a realistic budget.
3. As a CoinTrail user, I want different limits for different months, so that my budget reflects how my spending actually varies.
4. As a CoinTrail user, I want to set a month's budget ahead of time, so that a planned splurge month is budgeted before it arrives.
5. As a CoinTrail user, I want to restate a past month's budget, so that a month that was genuinely different is remembered accurately.
6. As a CoinTrail user, I want to mark a specific month as "no budget", so that an unbudgeted month shows no progress bars and no alerts.
7. As a CoinTrail user, I want "use default for this month" and "no budget for this month" as distinct actions, so that removing an override never accidentally un-budgets a month.
8. As a CoinTrail user, I want to see each scope's state for the picked month — explicit limit, no budget, or inherited — so that I always know which budget governs a month.
9. As a CoinTrail user, I want one month picker in budget management, so that all budget editing lives in a single place.
10. As a CoinTrail user, I want Home's budget progress to use the current month's effective budget, so that today's progress reflects the budget that actually governs this month.
11. As a CoinTrail user, I want the Monthly report's budget bars to use the viewed month's effective budget, so that each month is judged against its own limit.
12. As a CoinTrail user, I want "no budget" months to show no budget bars, so that un-budgeted months are not judged.
13. As a CoinTrail user, I want threshold alerts to use the month's effective budget, so that 80%/100% warnings match the budget that governs the month.
14. As a CoinTrail user, I want at most one warning and one exceeded notification per scope per month, so that changing a budget mid-month never re-nags me.
15. As a CoinTrail user, I want "no budget" months to never fire budget alerts, so that un-budgeted months stay quiet.
16. As a CoinTrail user, I want my per-month budgets to sync and back up like all other data, so that no phone change ever loses them.
17. As a CoinTrail user, I want two months' overrides for the same scope to survive sync intact, so that multi-device use never collapses my per-month budgets.
18. As a CoinTrail user, I want my existing budgets to carry over unchanged as default budgets when the app updates, so that the upgrade is invisible to me.
19. As a CoinTrail user, I want changing the default budget to restate months without an override, so that the default remains the standing limit everywhere I haven't said otherwise.
20. As a CoinTrail user, I want unused budget to still never roll over, so that each month starts clean regardless of overrides.
21. As a CoinTrail user, I want my backup files to stay plain human-readable JSON, so that a decade from now my per-month budgets can still be recovered by hand or by a future app.

## Implementation Decisions

- Model (see `docs/adr/0002-budget-defaults-and-overrides.md`): default budget + per-month budget overrides, chosen over per-month-only rows and over effective-dated limits. Applies uniformly to overall and per-category scopes.
- Vocabulary per `CONTEXT.md`: **Budget**, **Default budget**, **Budget override**, **Effective budget**.
- Schema v2: the budgets row gains a nullable `month` (`YYYY-MM`; null = the default budget) and a nullable limit (null only with a month = the explicit "no budget" state); the unique key becomes (scope, month), default included; existing rows migrate to default budgets keeping their ids.
- Tri-state override semantics: absent row = inherit default; row with limit = override; row with null limit = "no budget"; tombstone = override removed (back to default). A tombstone never means "no budget".
- Effective-budget resolution is a single pure domain decision taking the target month and the budget rows; storage layers stay dumb.
- The budget store gains month-aware operations that distinguish four intents: set/replace the default, set/replace a month's limit, set a month to "no budget", remove a month's override. Validation: a default limit is always positive; an override limit is positive when present.
- Budget management with a month picker is the only editing surface; Home and Monthly only display. The picker offers the default and any month, past or future.
- Alert identity is (scope, month, threshold): one notification per scope per month per threshold; a mid-month budget change never re-arms a threshold already crossed for that scope and month; "no budget" months fire nothing; thresholds re-arm each new month. Alert bookkeeping stays device-local.
- Sync and backup stay format v1: budget rows carry a nullable `month` (absent = default), LWW merge is keyed by (scope, month) so two months' overrides never annihilate each other, and a pre-override reader degrades overrides to one row per scope (documented in `docs/BACKUP_FORMAT.md`).
- Changing the default retroactively restates every month without an override (accepted consequence, Q46); there is no bulk "keep past months at their old limits" action in v1.

## Testing Decisions

Good tests assert external behavior — which limit governs a month, what the progress and thresholds are, what wins a merge, what round-trips through a backup — never implementation details. All tests run at existing seams; no new seam is introduced.

- **Domain budget seam** (pure): effective-budget resolution (override vs default vs "no budget"), progress against effective budgets, threshold planning. Prior art: the existing budget progress and alert evaluator tests.
- **Alert bookkeeping seam**: keying by (scope, month, threshold), no re-arm on mid-month change, re-arm each new month, silence for "no budget" months. Prior art: the alert tracker and alert store tests.
- **Repository/DAO seam**: month-aware tri-state writes with replacement semantics, tombstone ≠ "no budget", migration of existing rows to default budgets. Prior art: the catalog repository and DAO tests.
- **Sync/backup seam**: LWW by (scope, month), nullable-month round-trip (missing = default), "no budget" row surviving a merge distinctly from a tombstone. Prior art: the sync journal codec/merge and backup round-trip tests.
- ViewModel tests extend the existing ViewModel prior art for the month picker tri-state actions and month-aware display. No UI tests (SPEC §11).

## Out of Scope

- Bulk "keep past months at their old limits", start-dated/effective-dated defaults.
- Editing budgets from the Monthly report (budget management is the single editing surface).
- Budget rollover (never, per SPEC §6.5), income, shared ledgers, multi-currency.
- Re-arming threshold alerts after budget edits; evaluation outside the existing Home evaluation path.
- Journal/backup format v2.

## Further Notes

- Sources of truth: SPEC §5, §6.5, §7 and §13 (decision log Q42–Q49), `docs/adr/0002-budget-defaults-and-overrides.md`, `docs/BACKUP_FORMAT.md`, `CONTEXT.md`.
- Implementation is broken into tracer-bullet tickets 15–19 (blockers first).
- The migration must be invisible: at first launch after the update every existing budget is simply its scope's default budget.
