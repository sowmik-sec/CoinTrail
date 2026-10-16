# 26: Expense draft behind both keypads

**What to build:** One place decides how an amount is entered, what a zero amount does, when a draft may be saved, and how tapping a selected payment method clears it — so quick-add and edit can no longer drift apart. Quick-add keeps saving and resetting for the next entry; edit keeps loading the expense, its timestamp, and writing the change back. The draft knows nothing about the catalogue, so the screens still supply the picker lists.

**Blocked by:** 24 Catalogue — one store serves both lists

**Status:** ready-for-agent

- [ ] One test suite covers the amount and selection rules for both screens
- [ ] Quick-add and edit each keep only what differs between them, with the shared rules stated once
- [ ] Behaviour is unchanged: at most two paisa digits, a single decimal point, backspace, a zero amount never saveable, a category required, the note starting collapsed, and tapping the selected payment method clearing it
- [ ] The assertions the two suites shared are replaced rather than duplicated
- [ ] The suite is green
