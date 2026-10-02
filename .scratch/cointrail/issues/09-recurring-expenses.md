# 09: Recurring expenses

**What to build:** The user creates monthly recurring series (rent, internet, subscriptions) with amount, category, note, payment method, and day of month. Ordinary expense entries appear automatically on that day each month (day 31 clamps to month end), with pause/resume per series. Generated entries are ordinary expenses: editing or deleting one never touches the series or other occurrences. Deleting a series tombstones it but keeps its generated expenses. No duplicates ever appear for a month.

**Blocked by:** 02 Log an expense, see it on Today

**Status:** done

- [x] Monthly series can be created, edited, paused, resumed, and deleted
- [x] An occurrence is generated on the series' day of month, clamping day 31 to the month's end
- [x] Editing or deleting one generated expense never affects the series or other occurrences
- [x] Deleting a series tombstones it while keeping its generated expenses
- [x] Generation runs on app open and in the background, and never duplicates a month's entry
