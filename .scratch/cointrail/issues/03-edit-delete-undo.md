# 03: Edit & delete with undo

**What to build:** The user taps any expense to edit everything about it — amount, category, note, payment method, and datetime for back-filling earlier days — and swipes to delete with an undo snackbar. After the undo window the expense is gone from every view, kept only as an internal tombstone so future sync can propagate the deletion. No visible trash or audit trail.

**Blocked by:** 02 Log an expense, see it on Today

**Status:** done

- [x] Every expense field is editable, including the datetime
- [x] Swipe-delete shows an undo snackbar and undo restores the expense fully
- [x] After the undo window the expense disappears from all views but remains as a tombstone for sync
- [x] There is no visible trash or audit UI
