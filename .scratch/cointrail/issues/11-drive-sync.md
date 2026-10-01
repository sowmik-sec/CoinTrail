# 11: Drive sync

**What to build:** Two devices signed into the same Google account stay in step through the Drive app folder. Local changes (including deletions) queue while offline and merge with last-write-wins per record by updatedAt, tombstones respected. Sync runs on app open, periodically in the background, and on demand via "Sync now" with visible status. Conflicting edits converge deterministically (newest wins); nothing is lost after days offline.

**Blocked by:** 10 Google account + data isolation

**Status:** ready-for-agent

- [ ] A change on one device appears on a second device (same account) via the Drive app folder
- [ ] Deletions propagate via tombstones and never resurrect
- [ ] Last-write-wins per record by updatedAt resolves conflicting edits deterministically
- [ ] Sync runs on app open, periodically in the background, and manually via "Sync now" with visible status
- [ ] Fully offline usage queues changes; nothing is lost after days offline
