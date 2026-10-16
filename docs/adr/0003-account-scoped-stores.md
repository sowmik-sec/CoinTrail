# The store layer resolves the active account scope

Each Google account keeps its own database, and every screen must follow whichever account is signed in
(SPEC §7). That promise only held because the app relaunched its Activity with a cleared task on every account
change: each ViewModel captured its stores once at construction, so without the relaunch it would keep reading
the previous account's database. The stores a caller holds are now account-scoped themselves — reads re-resolve
the active **account scope** per subscription and writes resolve at call time — so the five store interfaces and
all twelve ViewModel factories are unchanged, and the relaunch is gone.

Considered: (a) an explicit `StateFlow` of the signed-in account's stores that every ViewModel collects with
`flatMapLatest` — the dependency becomes visible in twelve constructor signatures, so every screen pays for a
decision none of them makes; (b) keeping the relaunch — blunt but working, except it discards the back stack and
every ViewModel, and it cannot be exercised at all without a configured Google client ID.

Consequences: an account change now arrives as a flow emission, so a screen part-way through an edit survives a
sign-in or sign-out and must tolerate the data under it changing. Nothing outside the store layer may assume the
signed-in account holds still for the life of a ViewModel, and re-introducing a restart is a regression rather
than a simplification.
