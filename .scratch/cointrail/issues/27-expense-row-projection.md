# 27: One projection for expense rows and catalog names

**What to build:** One place turns expenses plus the catalogue into what a row displays — the category, the payment-method name, the note, the time and the amount — and into the id-to-name lookups the lists and breakdowns need. Home and the Day screen therefore render the same expense the same way, and the screens stop each resolving catalog names in their own fashion.

**Blocked by:** 24 Catalogue — one store serves both lists

**Status:** ready-for-agent

- [ ] A display row is built in exactly one place
- [ ] The screens that display an expense no longer derive catalog names themselves
- [ ] Home and the Day screen show identical rows for the same expenses
- [ ] The suite is green
