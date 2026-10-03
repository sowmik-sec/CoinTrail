# Budgets are defaults plus per-month overrides

Real spending varies by month (Eid, travel, salary changes), but a budget was one limit per scope —
overall or a category — stamped on every month. That limit now stays as the **default budget** (the
standing monthly limit), and per-month **budget overrides** can replace it: a row for one month
holding either an explicit limit or an explicit "no budget". A month without an override inherits the
default (terms in `CONTEXT.md`; rules in SPEC §6.5).

Considered: (a) one budget row per (scope, month) with no default — the cleanest schema, but it turns
budgeting into a monthly chore and loses the standing-limit UX; (b) effective-dated versions of a
single limit, stamping the old value onto history on every change — history preserves itself, but a
past month can never be restated and a splurge month cannot be planned ahead.

Consequences: changing the default retroactively restates every month without an override —
deliberate, CoinTrail keeps no audit history (the Q28 stance). An override with a null limit means
"no budget for that month" and is distinct from a tombstone, which removes the override and lets the
default apply again. Sync merges budgets last-write-wins by (scope, month) so two months' overrides
never annihilate each other, and budget rows in journal and backup gain a nullable `month` (absent =
default); a reader from before per-month budgets degrades overrides to one row per scope, but the
file stays fully parseable.
