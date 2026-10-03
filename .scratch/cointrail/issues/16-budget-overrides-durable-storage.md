# 16: Budget overrides land durably (schema v2 + sync/backup keying)

**What to build:** A budget row can be a scope's default budget, a month's budget override (explicit limit), or a month's explicit "no budget". Existing budgets upgrade to default budgets invisibly. Per-month budgets survive sync and restore: journal and backup carry the month and merge per (scope, month), so two months' overrides for one scope never annihilate each other. No visible UI change yet — this slice is verifiable through tests.

**Blocked by:** 15 Prefactor — one effective-budget seam

**Status:** ready-for-agent

- [ ] A (scope, month) row supports three states — explicit limit, "no budget" (null limit), and absent (inherit default) — and a tombstone means "override removed", never "no budget"
- [ ] At most one row exists per (scope, month), the default included; default limits are always positive and override limits positive when present
- [ ] Existing budgets migrate to default budgets, keeping their ids
- [ ] Storage operations distinguish the four intents: set/replace default, set/replace a month's limit, set a month to "no budget", remove a month's override
- [ ] Journal and backup round-trip month, limit and "no budget" rows; LWW merge is keyed by (scope, month), with tombstone-beats-live ties as before
