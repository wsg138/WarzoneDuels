# WarzoneDuels testing

WarzoneDuels uses JUnit 5 for deterministic unit/integration coverage. Run the automated suite with:

```bash
mvn test
```

For a clean verification pass:

```bash
mvn clean test
```

Surefire output is written under `target/surefire-reports/`. `MANUAL_TESTING.md` remains the separate live-server acceptance checklist for gameplay that requires real Paper/world/player behavior.

## Automated coverage currently protecting

The repository now has direct regression evidence for:

- duel settings, build modes, explosive visibility, item cooldown formatting and copy independence;
- win/loss/draw/disconnect-forfeit statistics and streak accounting;
- arena footprint bounds and packed block membership, including negative coordinates;
- spoils metadata, null filtering and defensive `ItemStack` copying;
- typed teleport allowances;
- spectator session persistence and inventory restoration;
- permission namespaces, parent relationships and policy;
- optional integration binary isolation and Plan API compatibility.

`FullFeatureCoverageContractTest` is an inventory guard. It ensures established regression suites are not silently removed or moved. It does not replace behavioral assertions.

## Important remaining gaps

WarzoneDuels is still far from exhaustive. High-value remaining automated coverage includes:

- `DuelService` request, wager, start, finish, disconnect and recovery paths;
- `SpectatorManager` lifecycle and teleport cancellation behavior;
- arena terrain snapshot/reset and map persistence;
- `RuntimeStateStore`, `LoadoutArchiveStore`, `PlayerStatsStore`, `SpoilsStore`, and duel analytics persistence;
- GUI command/listener navigation and authorization;
- CombatLogX, Vault, Spawn, Tags and other integration behavior at their real boundaries;
- listener enforcement for damage, death, block rules, item restrictions and protected explosives;
- plugin enable/disable wiring and real multi-player match flow.

Use pure unit tests where possible. Use mocks only around genuine boundaries; do not mock an optional plugin into existence simply to claim compatibility. Gameplay behavior that depends on Paper internals, real worlds, networking, or multiple live players belongs in the manual/live-server acceptance lane unless a trustworthy integration harness exists.

## Change workflow

For bug fixes or feature changes:

1. add a regression test that demonstrates the intended behavior;
2. run `mvn test` and inspect the specific Surefire failure if it fails;
3. update `FullFeatureCoverageContractTest` only after concrete behavioral evidence exists for a new major feature family;
4. keep production server data, credentials and live databases out of tests;
5. keep manual gameplay acceptance documented separately in `MANUAL_TESTING.md`.
