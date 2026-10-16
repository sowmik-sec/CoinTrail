# 25: Catalogue — the payment-method family goes

**What to build:** The duplicate family that shadowed catalog items is deleted: the payment-method model, its storage row, its queries, its store and its test fake, and the second repository implementation sitting behind the one store. Nothing observable changes — this slice only removes the code the previous one left standing.

**Blocked by:** 24 Catalogue — one store serves both lists

**Status:** ready-for-agent

- [ ] No payment-method-specific model, storage row, query set, store or fake remains
- [ ] One implementation serves both lists behind the single store
- [ ] The catalog tests are one file, and no assertion describes the deleted twin
- [ ] The suite is green
