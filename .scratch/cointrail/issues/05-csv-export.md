# 05: CSV export

**What to build:** The user picks any date range and saves a CSV of that range's expenses anywhere via the system save dialog — readable in Excel or by hand a decade from now.

**Blocked by:** 02 Log an expense, see it on Today

**Status:** done

- [x] Any date range can be exported and saved anywhere via the document picker
- [x] Columns are exactly `date,time,amount_taka,category,payment_method,note` with ISO date/time, plain-dot decimal amounts, UTF-8 encoding
- [x] Only non-deleted expenses inside the range are exported
