# WarzoneDuels implementation

## Layer Dependency Rules

Dependency direction for new and substantially changed code is `domain <- application <- infrastructure`.

- `domain/**`: Java standard library and framework-free duel facts and policies only.
- `app/**`: application orchestration; may depend on domain and ports. Existing Bukkit-heavy `DuelService` is brownfield code to be decomposed incrementally.
- `port/**`: integration contracts. Existing Bukkit-typed ports are brownfield debt and must not be copied into new party APIs.
- `adapter/**` and `WarzoneDuelsPlugin`: Bukkit, Paper, persistence, command, GUI, and external-plugin integration.

Several pre-adoption domain types currently contain Bukkit values, including arena, loadout, spectator, teleport, and map representations. They are documented exceptions, not examples for new work. Architecture review evaluates the changed surface and requires any new Duel Party core type to remain framework-free.

## Forbidden Domain Annotations

```yaml
forbidden: []
```

SPEAR defaults apply: `org.springframework.*`, `jakarta.persistence.*`, `javax.persistence.*`, `com.fasterxml.jackson.*`, `io.micronaut.*`, and `lombok.*`. New party-domain code also forbids Bukkit, Paper, Adventure, and adapter imports.

## Competitive core

`MatchTeam` represents one to three unique participants. `ActiveDuel` keeps its legacy singleton accessors while exposing team-aware participant and opponent lookup. `DuelParty` owns an editable roster until a challenge-specific lock is acquired. `DuelChallenge` owns immutable roster and rule snapshots plus participant acceptance state.

The application layer owns registries, invitation expiry, player-to-party indexes, challenge lifecycle, command authorization, and conversion of a ready challenge into a match. Bukkit commands and scheduled expiry are adapters around those policies. Challenge creation releases expired participant contracts before checking roster locks. Multi-member active matches require PARTY; singleton matches preserve existing compatibility.

## Persistence and recovery

Analytics initialization disposes any opened connection if setup or schema creation fails. Store state is cleared before disposal; the original setup error and any suppressed close failure are logged together. A package-local opening seam permits real-H2 fault injection while the public constructor retains DriverManager, its existing URL and transaction semantics.

DuelCooldownPolicy stores per-player completion timestamps and unordered opposing-player UUID pairs. DuelCooldownService depends only on the framework-free policy and DuelCooldownStore port; the Bukkit YAML adapter is wired by WarzoneDuelsPlugin. The separate schema-1 `duel-cooldowns.yml` is read strictly and atomically replaced before match/challenge evidence is recorded. Malformed or inaccessible history blocks new duels instead of silently resetting protection. Stats and existing advancement totals are not reset.

`settings.duel-cooldown-seconds` defaults to 300; `settings.repeat-opponent-cooldown-seconds` defaults to 86400. Zero disables each independently; negative values clamp to disabled. Repeat-opponent protection blocks the rematch itself, because the advancement consumer reads ordinary win/streak totals, not an independent credited-wins stream. All cross-team pairs are considered, including non-leaders and eliminated members; teammate relationships are not considered rematches. Successful challenge-sent evidence has its own pair window so expired/declined requests cannot repeatedly award credit, without consuming the completed-duel cooldown. History survives configuration reload, relog, party recreation and plugin/server restart. Administrative aborts and restart interruptions do not count as completed duels; kills, draws and disconnect forfeits do.

Protection is checked before sending/accepting and at final/queued start and arena-preparation completion. Normal completion is synchronously persisted before statistics/mutual-draw evidence is emitted; an I/O failure withholds those counters, preserves normal cleanup/loot/analytics, and blocks new matchmaking. After repairing storage, an administrator can use configuration reload to retry the retained in-memory write (or the initial read if loading failed); duels remain blocked until persistence succeeds. Expired history is pruned only for enabled positive windows; disabled windows retain history. Existing earned advancements are not revoked, and this is cooldown-based anti-farming rather than proof against collusion among many different accounts.

Existing one-versus-one runtime files remain readable alongside version-2 full-team snapshots. Failure to load a team match must retain recovery data and must never synthesize a winner. Analytics writes commit the parent and participant rows together. Failed writes roll back together; a caller-owned transaction uses a savepoint without committing the caller's unrelated changes.

## Match execution

Arena team indexes are zero-based: team one uses 0 and team two uses 1. The participant index and countdown lock must be established before entry teleports. Party victory announcements name every snapshotted winning member, including eliminated teammates. Unlocked leader departure disbands the social roster; an already snapshotted match retains its participants and disconnect policy.

Arena configuration supports three positions per side while preserving legacy spawn1/spawn2 settings. Team preparation, deaths, disconnects, statistics, and cleanup iterate complete rosters. Party wagers remain rejected until a split policy is approved; existing one-versus-one wagers are retained.

The optional match-duration limit starts when combat is released after the opening countdown. Zero disables the limit and preserves death-only duels. A positive limit concludes an unfinished duel through the existing draw path so wagers refund and ordinary participant, spectator, analytics, and arena cleanup remain centralized.

Explosive damage attribution combines Paper's causing entity with duel-owned source tracking. Crystal detonation records the attacking participant, explosive minecarts retain the placing or attacking participant, and respawn-anchor activation retains the participant at the exploding block position. Party explosive damage with no participant attribution is cancelled because teammate safety cannot otherwise be proven.

An attributed participant can hurt themselves with explosives as in vanilla; the teammate protection applies to other members. All participant damage is canceled during entry and countdown.

Player deaths are accumulated until the next primary-thread tick before an outcome is selected. Ordinary partial eliminations still create one spoils entry per defeated inventory, with an attributed opposing participant preferred over the deterministic surviving-opponent fallback. If both complete teams were eliminated in that tick, the draw path is used, no spoils are created, and archived pre-duel loadouts are reapplied to the simultaneously defeated players after respawn.

## Guild boundary

No LumaGuilds dependency, guild model, champion role, active-war query, or war-settlement callback belongs in the current phases. A future integration must depend on WarzoneDuels match contracts without changing Duel Party ownership.

## Platform compatibility

Java 25 is the compile and runtime baseline for Minecraft/Paper 26.1 and newer. The ordinary Maven build pins the stable Paper 26.2 API and produces the testing artifact. The `paper-26.3` Maven profile replaces only the provided Paper API with a pinned 26.3 alpha API so the same sources and test suite can detect binary/API drift without producing a separate implementation branch. The stable 26.2 build is rerun after compatibility verification so the delivered JAR remains based on the stable API.

## SPEAR adoption

WarzoneDuels existed before adoption. The original 14 tests and the first 12 team-domain tests are baseline evidence, not retrospectively labeled red/green work. New tasks use the project-local skills and Windows-compatible state helper under `tools/spear`.
