# 01: Foundation & data core

**What to build:** The app installs and its data foundation is proven: a tested money type (integer paisa, BDT display), validated domain models (expense, category, payment method, budget, recurring series), a local database with the full schema (tombstones and updatedAt everywhere for later sync), repositories exposing tested totals queries, and preset categories/payment methods seeded with stable IDs. The database is namespaced per account key ("local" by default) so per-account isolation later needs no rework. Screens beyond a placeholder are out of scope.

**Blocked by:** None (can start immediately)

**Status:** done

- [x] Unit test suite green covering money math/format/parsing, model validation, DAO behavior (incl. tombstones, daily and per-category totals), repositories, and preset seeding
- [x] Money is integer paisa everywhere; no floating point in any money path
- [x] Preset categories and payment methods use the stable IDs from the spec and seed exactly once
- [x] Database instances are created from an account key; the "local" namespace is used when no Google account is signed in
- [x] Schema v1 is exported and committed for future migrations
