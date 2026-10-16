# 24: Catalogue — one store serves both lists

**What to build:** A category and a payment method are one concept in the code as they already are in the glossary, so the screens that treat them alike stop working in pairs. One catalog item type and one store, keyed by kind, serve both lists, and every screen reads them through it — the picker feeds and the management screen. Turning an id into a display name is deliberately left to the row projection rather than folded in here. The two tables, the two preset sets and the two journal keys are untouched, so nothing about stored data or the backup file changes. The old twin stays present underneath at the end of this slice, serving the one store rather than the screens.

**Blocked by:** 23 Prefactor — one journal-table registry

**Status:** ready-for-agent

- [ ] One store serves both lists: observing, adding, renaming, hiding and seeding behave identically for a category and for a payment method
- [ ] Hidden items stay out of every picker, and that filtering is declared in one place
- [ ] The management screen dispatches on the kind once rather than once per operation, and maps an item to its display form once
- [ ] The two tables, the two preset ID sets and the two journal keys are unchanged, and the codec, round-trip and backup tests pass unchanged
- [ ] The suite is green
