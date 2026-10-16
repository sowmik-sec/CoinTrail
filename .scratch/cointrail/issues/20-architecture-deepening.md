# 20: Architecture deepening

**Status:** ready-for-agent (slices 21–28)

## Problem Statement

CoinTrail's features work and are tested, but its author pays for the same decision in several places, and the signed-in account is tracked by relaunching the app rather than resolved where the stores are. Adding a table means editing several hand-written lists in lockstep, with nothing to catch a missed one. A category and a payment method are one concept in the glossary and in the settings screen that manages them, but two parallel families in the data layer. The rules for entering an amount are written twice, once per keypad. The same month's totals are assembled two different ways by two screens, so they can disagree. Each of these makes the next change to that area cost more than it should, and makes a change made in one place not reach the other.

## Solution

Deepen six modules, with no change to the schema, the journal or backup format, or any screen's behaviour:

1. The stores a caller holds resolve the active account scope themselves, so an account change no longer needs the Activity relaunch (ADR-0003).
2. One registry owns what a journal table is, so the merge, the codec and the local store stop naming tables one by one.
3. One catalogue module serves categories and payment methods, keyed by kind, so the parallel family disappears.
4. One expense draft owns the entry rules behind both keypads.
5. One month standing answers a month's numbers for Home and the Monthly report alike.
6. One projection turns expenses plus the catalogue into what a row shows.

Plus the subtraction that makes room for them: the query paths and the test-only repository write that nothing calls are deleted.

## User Stories

1. As a CoinTrail user, I want signing in or out to leave me where I was, so that changing account never throws away the screen I was reading.
2. As a CoinTrail user, I want an account change to reach every screen at once, so that no screen keeps showing the previous account's data.
3. As a CoinTrail user, I want the data under every screen to belong to the account I am signed in as, so that I can never see or edit the wrong account's expenses.
4. As a CoinTrail user, I want sync and snapshots to keep using the account I am signed in as, so that my data never lands in another account's Drive folder.
5. As the app's author, I want to add a table without hunting for every list it belongs in, so that a new record type cannot silently fail to sync.
6. As the app's author, I want a test to tell me when a table is half-wired, so that a forgotten list is caught before my data diverges between devices.
7. As the app's author, I want one place that defines what syncs and what backs up, so that the two cannot drift apart.
8. As a CoinTrail user, I want my journal and my backup files to stay readable exactly as they are today, so that a refactor never costs me my history.
9. As a CoinTrail user, I want two devices to go on reconciling to the same result, so that an edit made here never beats an edit made there by accident.
10. As the app's author, I want one place that decides the rules for categories and payment methods, so that a new rule is stated once rather than twice.
11. As the app's author, I want a category and a payment method to be one concept in the code as they already are in the glossary, so that the screens treating them alike stop paying for two parallel families.
12. As a CoinTrail user, I want renaming, adding or hiding a payment method to behave exactly as it does today, so that the refactor is invisible to me.
13. As the app's author, I want one place that decides how an amount is entered and when it can be saved, so that a keypad fix reaches both screens at once.
14. As a CoinTrail user, I want quick-add and edit to behave identically wherever they overlap, so that the same gesture never does two different things.
15. As a CoinTrail user, I want quick-add to stay fast, so that logging an expense still takes seconds.
16. As the app's author, I want one place that answers what a month amounts to, so that two screens cannot disagree about the same month.
17. As a CoinTrail user, I want Home and the Monthly report to state the same total for the same month, so that I can trust the numbers I am shown.
18. As a CoinTrail user, I want the budget bars on both screens to measure the month's effective budget, so that a month with an override is judged against its own limit.
19. As a CoinTrail user, I want my budget warnings to keep arriving at the same thresholds they do today, so that a refactor never changes when I am nagged.
20. As the app's author, I want month stepping and month naming declared once, so that a change to either reaches every screen that uses it.
21. As a CoinTrail user, I want Home to appear promptly, so that the landing screen never has to scan my whole history to decide what to show.
22. As the app's author, I want one place that turns expenses into what a row displays, so that two lists cannot render the same expense differently.
23. As the app's author, I want to delete code that nothing calls, so that each interface says only what the app actually does.
24. As the app's author, I want the existing tests to keep passing, so that the refactor is provably behaviour-preserving.
25. As the app's author, I want the work to land in slices I can verify one at a time, so that a mistake costs one slice to revert rather than the whole change.
26. As the app's author, I want no change to the database schema, so that installing this update carries no risk to my data.
27. As a CoinTrail user, I want the update to need no migration, so that a refactor cannot reshape or damage what I have already logged.
28. As another agent picking this repo up, I want the plan, the vocabulary and the decisions recorded in the repo, so that I can continue from the files rather than from a conversation.

## Implementation Decisions

- Vocabulary per `CONTEXT.md`: **Account scope**, **Journal table**, **Expense draft**, **Month standing** are new terms; **Catalog item** already existed and is what the catalogue module serves.
- **Account scope** (`docs/adr/0003-account-scoped-stores.md`): the five store interfaces are unchanged, and nothing about the ViewModels changes. The store a caller already holds resolves the active scope itself — re-resolving on each subscription for reads, at call time for writes — so an account change arrives as a flow emission. The Activity relaunch and its cleared-task restart are deleted. The account switcher the settings screen drives is unchanged.
- **Journal table**: one descriptor per table owns its name, its JSON shape, its merge key, and its read and write against the database. The merge, the codec and the local store iterate descriptors instead of naming tables. The journal type keeps its typed per-table lists so that a table added to the type is visible, and a completeness test asserts every list is covered by exactly one descriptor. Keys, envelope and version are untouched, and the backup file keeps sharing the one codec. The table declarations the database framework requires stay written out literally, so the registry covers the six places that enumerate tables rather than all seven.
- **Catalogue**: one item type covers a category and a payment method, behind one store keyed by the existing kind. The two tables, the two preset ID sets and the two journal keys all stay, so the schema and the backup file are unaffected. Hidden filtering, and the mapping of an item to the form a screen displays, are each declared once; turning an id into a name belongs to the display projection below, not here. The kind moves out of the settings screen that first needed it, since the store is now keyed by it.
- **Expense draft**: one module owns the amount, the category, the payment method, the note and the entry transitions — append a digit, a decimal, backspace, select, toggle the note — plus the rule for when the draft may be saved. It takes no catalogue dependency of its own, so its tests need no catalogue fake. Quick-add keeps save-and-reset; edit keeps loading, the timestamp and write-back.
- **Month standing**: one module answers a month's total, the previous month's total, the month-over-month move, the per-day totals, the per-category breakdown with names resolved, and budget progress against that month's effective budget. Home and the Monthly report both read it. Month stepping and month naming move in with it. The first-run check stops summing the entire expense history and asks an existence question instead.
- **Display projection**: one module turns expenses plus the catalogue into display rows — category name, payment-method name, note, time, amount — and resolves a catalog id to a name in one place, which is what the screens and the breakdowns consume.
- **Deletions**: the incremental change queries on the DAOs, their repository wrappers and the repository write that exists only for tests are removed, along with the assertions covering them.
- **Order**: prefactors first (make the change easy, then make the easy change). The deletions, then the account scope, then the table registry, then the catalogue, then the draft, then month standing, then the projection. Some edges between slices are hard dependencies and some only avoid rework; the Further Notes record which is which.

## Testing Decisions

Good tests here assert external behaviour only: what a store emits after an account change, what a month's numbers are, what a merge decides, what a row shows for a given expense — never the shape of the code that produces it. Every test runs at a seam that already exists, with one exception noted below.

- **The store interfaces** are the highest existing seam and the one every ViewModel test already uses. The account scope is tested here and nowhere else: no new seam, no ViewModel change, and no screen's construction of its view model changes. Prior art: the existing store fakes and the ViewModel tests.
- **The journal codec and merge seam** is where the registry is verified: the same data still encodes, decodes, merges and round-trips through a backup to exactly the same result, plus the new completeness assertion. Prior art: the journal codec, journal merge and backup round-trip tests.
- **The pure domain seam** is unchanged by this work: effective-budget resolution, budget progress, alert planning, recurring generation and last-write-wins merge stay as they are. Their tests are the regression guard for the slices that touch what feeds them.
- **The catalog store seam** — today two seams, one per list, which this work merges into one — is where the catalogue is verified: both lists observe, add, rename, hide and seed identically, and hidden items stay out of pickers. Prior art: the catalog repository and DAO tests, merged into one file.
- **No new seam is introduced anywhere in this work.** The only addition is a fixture at the store seam for the account scope: two in-memory databases behind a fake session, asserting that a single store instance emits the second scope's rows after a switch. Robolectric is already set up for in-memory databases, so this stays a JVM unit test. Prior art: the DAO tests and the database factory test.
- No UI tests (SPEC §11). Evidence for the slices that change what a screen shows is the ViewModel tests plus the manual checklist handed over with each slice.

## Out of Scope

- Any schema change or migration, and any change to the journal or backup format or its version.
- Any change to what a screen displays or says, with the single exception of the account switch no longer relaunching the app.
- Splitting the two catalogue tables into one, or giving them a shared id space.
- New features of any kind, including the release and Play-Store-readiness work in SPEC §12 plan 7.
- Reworking the sync transport, the Drive access, the snapshot naming or the backup envelope.
- The budget rules themselves: effective-budget resolution, the tri-state override semantics, threshold identity and alert bookkeeping are unchanged.
- Introducing a dependency-injection framework, a new navigation model, or a module split.
- Re-arming threshold alerts, or any change to when notifications fire.

## Further Notes

- Sources of truth: SPEC §12 (Plan 10), `docs/adr/0003-account-scoped-stores.md`, and `CONTEXT.md` for the four new terms.
- Slices: numbered from 21 in dependency order — eight of them for six deepenings plus the deletions. Six are one deepening each: the deletions, the account scope, the registry, the draft, the projection and month standing. The catalogue takes two, sliced expand-then-contract because its blast radius crosses every layer at once: the first puts one store in front of both lists with the old twin still standing beneath it, and the second deletes the twin. Both stay green, which no single-pass version of that change could.
- Hard edges versus sequencing: the deletions precede the registry because they edit the same files; the registry precedes the catalogue because the catalogue's two tables become descriptors; the catalogue precedes the draft and the projection because they read the one store; the projection precedes month standing because both resolve catalog names, and building Home's rows once avoids building them twice. By contrast the account scope gates nothing — it is placed early because it is the one slice that changes observable behaviour, so it lands on an otherwise untouched tree where a regression is unambiguous.
- Expected consequences that are deliberately not acceptance criteria: Home's dependency list narrows once a month's numbers come from elsewhere, and the journal keeps its five typed per-table lists. Both follow from the decisions above, and asserting either would pin the shape of the code rather than its behaviour.
- Verification: the unit suite plus a debug build, run against the final commit of each slice, with a manual click-through checklist handed over for the slices whose effect a test cannot show. Google sign-in is unavailable without a client ID in `local.properties`, so the account scope is verified by the two-scope test rather than by hand.
- Behaviour preservation is the acceptance bar for the whole plan: every existing assertion that describes a behaviour survives, and only assertions about deleted code or replaced test surface are removed.
