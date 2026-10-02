# CoinTrail

Personal expense tracker: fast daily logging with data that survives a decade.

## Language

**Expense**:
A single money outflow at a point in time, with a category and optional note and payment method.

**Home**:
The landing screen: an at-a-glance summary of spending and budget health for the month.
_Avoid_: Dashboard, Overview

**Day list**:
The expenses of a single calendar date with that day's total. "Today" is the Day list for the current date.
_Avoid_: Today screen, Day expenses

**Quick-add**:
The keypad screen for logging an expense in seconds.
_Avoid_: Add-expense form

**Monthly report**:
A calendar heatmap, per-category breakdown, and month-over-month comparison for one month.
_Avoid_: Reports, Analytics

**Budget**:
A monthly spending limit, overall or per category. Unused budget never rolls over.

**Recurring series**:
A monthly rule that auto-creates expenses on a day of the month; occurrences are independent once created.
_Avoid_: Subscription, Template

**Catalog item**:
A category or a payment method. Hidden when unwanted, never deleted.
_Avoid_: Tag
