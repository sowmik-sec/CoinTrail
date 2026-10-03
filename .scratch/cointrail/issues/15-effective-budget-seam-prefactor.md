# 15: Prefactor — one effective-budget seam

**What to build:** "Which budgets apply to month M" becomes a single pure domain decision. Today every budget is a default budget, so behaviour is identical: Home, the Monthly report and threshold planning read their limits through one effective-budget entry point that takes the target month, instead of consuming the raw budget list themselves. This makes the change easy before making the easy change.

**Blocked by:** None (can start immediately)

**Status:** done

- [x] A single domain entry point resolves the budgets governing a given month and feeds progress bars and threshold planning
- [x] Home passes the current month and the Monthly report passes the viewed month instead of consuming the raw budget list
- [x] All existing budget, alert and ViewModel tests stay green with unchanged behaviour
