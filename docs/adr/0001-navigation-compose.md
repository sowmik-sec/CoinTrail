# Adopt Navigation-Compose for screen navigation

When CoinTrail gained a three-tab structure (Home, Monthly, Settings) with drill-in screens and a
reminder-notification deep link into quick-add, the hand-rolled `when`-over-flags navigation in
`CoinTrailApp` no longer scaled: eleven mutually exclusive flags, no per-tab back stack, no route
model. We replaced it with androidx navigation-compose — one NavHost, nested graphs per tab.

Considered: (a) extending the flag state machine with a hand-rolled back stack — no new dependency,
but bespoke stack code to maintain indefinitely; (b) bolting tabs onto the existing flags — smallest
diff, but no per-tab state preservation and the flag list keeps growing.

Consequences: the reminder notification now navigates to the quick-add destination instead of
flipping a start flag; screen routes are strings in one sealed destination hierarchy.
