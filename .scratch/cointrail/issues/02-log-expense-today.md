# 02: Log an expense, see it on Today

**What to build:** The daily loop works end-to-end. From the quick-add screen (custom numeric keypad, tappable category chips, optional payment-method selector, expandable note) the user records an expense in about five seconds with no system keyboard for the amount, and sees it on the Today screen under a running total. Data survives app restart. The app ships its dark + light theme with the fixed calm green/teal palette.

**Blocked by:** 01 Foundation & data core

**Status:** ready-for-agent

- [ ] Quick-add records amount, category, optional payment method and note
- [ ] The amount field is focused on open and the amount is entered on the in-app keypad (no system keyboard popup)
- [ ] Today lists today's expenses newest-first with the running total at top
- [ ] A logged expense survives app restart
- [ ] Dark and light modes are both usable; fixed palette (no Material You dynamic color)
