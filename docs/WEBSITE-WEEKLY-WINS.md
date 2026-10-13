# Public weekly match wins

Requirement: export the top three players by counted WarzoneDuels match wins for a UTC Monday week. Use the authoritative persisted match ledger, group by winner UUID, use the latest winning name and break score ties by UUID. Exclude draws, cancelled/non-counted matches and records outside [start,end). Do not expose wagers, opponents, locations or internal match records. Missing/unavailable ledger is an error, not a zero leaderboard.

SPEAR: current main is 480a365188b38b130ae384ea8495293f7428d77f; no local EARS/state tooling was found. The existing ledger stores winner identity, ended_at and counted_as_match, but no bounded weekly winner projection exists. Add a read-only JDBC query, application delegate and optional public plugin bridge consumed asynchronously by WarzoneRotator. Preserve all gameplay/stats behavior. Verify against an in-memory H2 ledger, including rename grouping, boundaries, ties, draws and cancellations.

No PR, production write, restart or upload is requested at this stage. Both companion source changes must reach canonical main and the owning monorepo pins before activation.

Verification: Maven verify passes on JDK 21, 36 tests, zero failures/errors/skips. The H2 fixture checks UTC interval boundaries, draws (including erroneous non-null winner), non-counted cancellations, name changes grouped by identity, stable tied rankings, top-three limit, public projection and an empty interval. Public bridge signature compiles into the companion artifact. This is an unmerged local test artifact; runtime installation and canonical monorepo pins remain unverified.
