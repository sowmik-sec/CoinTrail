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
A spending limit for a scope — overall or a category. Each month is measured against its effective budget. Unused budget never rolls over.

**Default budget**:
The standing monthly limit for a scope; governs any month without a budget override.
_Avoid_: standing budget

**Budget override**:
A month's own limit for a scope: either an explicit limit or an explicit "no budget". Beats the default budget for that month.
_Avoid_: monthly budget, custom budget

**Effective budget**:
The budget a month actually uses: its budget override if it has one, else the default budget.
_Avoid_: actual budget, resolved budget

**Recurring series**:
A monthly rule that auto-creates expenses on a day of the month; occurrences are independent once created.
_Avoid_: Subscription, Template

**Catalog item**:
A category or a payment method. Hidden when unwanted, never deleted.
_Avoid_: Tag
