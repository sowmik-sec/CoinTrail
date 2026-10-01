# 12: Snapshots, restore & JSON backup

**What to build:** Durable, open-format backups on top of sync: timestamped full snapshots written to Drive weekly and on demand ("Backup now"), an in-app list of snapshots, and restore that merges a snapshot idempotently into a fresh or existing device. Plus manual versioned-JSON export/import of the full database to anywhere via the document picker, with the format documented for the 10-year rule (paisa integers, ISO timestamps, forward-migratable).

**Blocked by:** 11 Drive sync

**Status:** ready-for-agent

- [ ] Snapshots are written to Drive weekly and on demand via "Backup now"
- [ ] The app lists available snapshots and restore merges one idempotently (safe on fresh or existing devices, run twice without damage)
- [ ] Full data can be exported to and imported from a versioned JSON file anywhere via the document picker
- [ ] The JSON backup format is documented (versioned schema, paisa integers, ISO timestamps) so a future app or a human can parse it
- [ ] Export → import round-trip is covered by a test asserting identical data
