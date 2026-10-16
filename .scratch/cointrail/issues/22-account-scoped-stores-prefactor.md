# 22: Prefactor — account-scoped stores, no Activity restart

**What to build:** A change of signed-in account reaches every screen without the app relaunching itself. The stores a caller already holds resolve the active **account scope** on their own: reads re-resolve when the scope changes, writes resolve when they run. Signing in or out therefore changes what is on screen in place — the reading position, the scroll and any half-finished edit survive it. No screen or view model changes, and `docs/adr/0003-account-scoped-stores.md` records why no restart comes back.

**Blocked by:** 21 Delete the dead query-side tails — sequencing only, not a dependency: worked second so the one slice that changes observable behaviour starts from a smaller tree

**Status:** ready-for-agent

- [ ] An account change changes what a store already held by a screen emits, with no relaunch of the Activity and no task cleared
- [ ] A single store instance, held across the switch, emits the newly signed-in scope's rows — asserted over two in-memory scopes behind a fake session
- [ ] Data written after the switch lands in the newly signed-in scope
- [ ] The account switcher the settings screen drives is unchanged, so sign-in, sign-out and "remove my data from this device" behave exactly as before
- [ ] No screen or view model signature changes: the same store interfaces reach the same constructors
- [ ] The suite is green. Google sign-in is unavailable without a client ID configured locally, so this slice is verified by the two-scope test rather than by hand
