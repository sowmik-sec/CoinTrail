# 21: Delete the dead query-side tails

**What to build:** Nothing calls the per-table "rows changed since a moment" queries, their repository wrappers, or the repository write that exists only so tests can plant a row verbatim — sync reads whole tables instead. Removing them leaves each interface saying only what the app actually does. No behaviour changes and nothing new is built.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] No production code reads expenses, categories, payment methods, budgets or recurring series by change time
- [ ] The recurring-series store carries only the operations the settings screen and the generator use
- [ ] The repository write used only by tests is gone, and those tests plant their rows through the real write path instead
- [ ] The assertions covering the deleted operations are deleted, and no other test changes
- [ ] The suite is green, and its size drops by exactly the deleted assertions
