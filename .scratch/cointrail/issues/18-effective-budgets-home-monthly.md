# 18: Home and Monthly show effective budgets

**What to build:** Home's budget progress uses the current month's effective budget, and the Monthly report's budget bars use the viewed month's effective budget — an override wins, the inherited default otherwise. A "no budget" month shows no budget bars at all.

**Blocked by:** 16 Budget overrides land durably (schema v2 + sync/backup keying) — ticket 17 is needed only to demo the effect

**Status:** done

- [x] Home's budget progress measures the current month's spending against that month's effective budget
- [x] The Monthly report's budget bars use the viewed month's effective budget, which may differ from the current month's
- [x] A "no budget" month shows no progress bars on either screen
- [x] A month with no effective budget shows nothing, as today
