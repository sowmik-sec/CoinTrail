# 23: Prefactor — one journal-table registry

**What to build:** One place knows what a **journal table** is. Each table's descriptor carries its name, its JSON shape, the key that decides which of two rows is newer, and how to read it and write it — so the merge, the codec and the local store stop naming tables one by one, and adding a table becomes one descriptor instead of an edit in every place that enumerates them. The table declarations the database framework requires stay written out literally. Journal and backup keys, envelope and version are untouched, and the backup file keeps sharing the one codec.

**Blocked by:** 21 Delete the dead query-side tails — the deletions edit the same query and repository files, so the two cannot be worked side by side

**Status:** done

- [ ] The merge, the JSON codec and the local store name no table individually
- [ ] A test asserts that every table the journal carries has exactly one descriptor, so a table added to the journal cannot go unnoticed
- [ ] The same data still encodes, decodes, merges and restores through a backup to exactly the same result — the existing codec, merge and round-trip tests pass unchanged
- [ ] Adding a table needs no edit to the merge, the codec or the local store
- [ ] The suite is green
