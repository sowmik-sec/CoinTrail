# 07: Daily reminder

**What to build:** A daily local notification at a user-configurable time (default 21:30) nudging the user to log the day's expenses. It is suppressed on days where at least one expense is already logged (partial-logging days still get the nudge), and tapping it opens the quick-add screen directly. The reminder survives phone restarts and catches up if the phone was off at the scheduled time.

**Blocked by:** 02 Log an expense, see it on Today

**Status:** done

- [x] The reminder fires daily at the configured time (default 21:30), changeable in settings
- [x] It is suppressed when at least one expense is logged that day
- [x] Tapping the notification opens quick-add
- [x] It survives device restart and catches up after the phone was off at the scheduled time
- [x] The notification permission flow is handled on Android 13+
