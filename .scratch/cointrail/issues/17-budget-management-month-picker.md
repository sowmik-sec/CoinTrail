# 17: Month picker in budget management

**What to build:** In budget management the user picks the default or any month (past or future) and sees each scope's state for it — explicit limit, "no budget", or inherited (with the inherited value). "Use default for this month" and "no budget for this month" are distinct actions from setting a limit.

**Blocked by:** 16 Budget overrides land durably (schema v2 + sync/backup keying)

**Status:** done

- [x] A month picker selects the default or any month, past or future
- [x] Each scope shows its state for the selection: explicit limit, no budget, or inherited from the default (with the value it inherits)
- [x] Setting a limit creates or updates the override for the picked month only
- [x] "Use default for this month" removes the override while "No budget for this month" sets the explicit no-budget state — the two are never conflated
- [x] Editing the default budget works as before and governs every month without an override
