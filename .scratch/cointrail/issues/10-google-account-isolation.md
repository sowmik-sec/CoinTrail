# 10: Google account + data isolation

**What to build:** Google sign-in is optional — the app is fully usable with no account at all. Signing in switches to that Google account's data namespace; signing out hides that data while keeping a local copy; an explicit "remove my data from this device" action deletes it. Two accounts on one device never see each other's expenses. Settings shows the account section (sign in/out, remove data).

**Blocked by:** 02 Log an expense, see it on Today (and 01's account-keyed database factory)

**Status:** done

- [x] The app works end-to-end with no Google account signed in
- [x] Signing in switches to the account's namespace; signing out hides that data but keeps the local copy
- [x] "Remove my data from this device" explicitly and completely deletes that namespace's local data
- [x] Two Google accounts on one device never see each other's data
