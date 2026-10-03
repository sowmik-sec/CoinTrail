# 19: Threshold alerts key on (scope, month, threshold)

**What to build:** Budget threshold alerts use the month's effective budget and fire at most one warning (80%) and one exceeded (100%) notification per scope per month. Changing a budget mid-month never re-arms a threshold already crossed that month; "no budget" months stay silent; each new month re-arms.

**Blocked by:** 18 Home and Monthly show effective budgets (shares the Home budget state path)

**Status:** ready-for-agent

- [ ] Alert identity is (scope, month, threshold): one notification per scope per month per threshold
- [ ] A mid-month budget change — editing a limit, adding or removing an override — never re-arms a threshold already crossed for that scope and month
- [ ] "No budget" months fire no alerts
- [ ] Thresholds re-arm at the start of each new month
- [ ] Alert bookkeeping remains device-local and never syncs
