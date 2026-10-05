# WarzoneDuels SPEAR tasks

- [x] **TDD-030** - Close opened analytics connections when initialization fails.
  Tag: TDD
  References: REQ-040, REQ-022; docs/implementation.md persistence-and-recovery.
  Acceptance: setup/DDL failure disposes the opened connection and clears store state; failed close is attached to the setup error; retry can initialize and persist a real record. The public constructor retains the existing JDBC URL/driver and no production operation occurs.
  Evidence:
  - Exact import-evidence audit identified shorthand omissions for java.lang.reflect.InvocationTargetException, java.lang.reflect.Proxy and java.util.logging.LogRecord; these use the existing reflective JDBC fixture and standard logging handler. Rechecked exact import paths after expanding this evidence; no new gameplay-layer coupling.
  - Import gate: java.util.ArrayList, org.junit.jupiter.api.Assertions, dev.minecraft.warzoneduels.domain.DuelEndReason, dev.minecraft.warzoneduels.domain.DuelMatchType, dev.minecraft.warzoneduels.domain.analytics.DuelRecord, dev.minecraft.warzoneduels.domain.analytics.DuelRecordParticipant, java.util.UUID and java.util.List are existing JDK/domain/JUnit dependencies; java.util.logging.Level supplies throwable diagnostics in the persistence adapter.
  - DuelAnalyticsStore.enable opens java.sql.Connection through java.sql.DriverManager then sets auto-commit and initializes schema; its catch currently only clears connection. Existing rollback disposal is correct and remains unchanged. A package-local connection-opening seam enables fault injection without changing the production path or adding a dependency.
  - Existing DuelAnalyticsTransactionTest uses dev.minecraft.warzoneduels.WarzoneDuelsPlugin, org.bukkit.plugin.java.JavaPlugin, org.junit.jupiter.api.Test/assertions, java.lang.reflect.Field/Proxy/InvocationTargetException, sun.misc.Unsafe, java.sql.Connection/DriverManager and java.util.logging.Logger. H2 2.2.224 is the real database. New tests use existing org.junit.jupiter.api.io.TempDir and java.nio.file.Path plus java.sql.SQLException, java.util.concurrent.atomic.AtomicInteger, java.util.logging.Handler/LogRecord and existing DuelRecord/DuelRecordParticipant/DuelEndReason/DuelMatchType/UUID/List for retry and diagnostic evidence.

  Validation: both focused assertions failed before the disposal fix and pass after it. Clean verify passes 145 Java tests against each pinned Paper profile (stable last), nine Node tests and EARS. Real H2 tests prove disposal, retry with persisted records and suppressed-close diagnostics; public JDBC construction and transaction handling remain unchanged. No live server acceptance, deployment or merge is claimed.

- [x] **DOC-005** - Align architecture and completion gates with current project contracts.
  Tag: DOC
  References: REQ-025; docs/implementation.md layer dependency rules and SPEAR adoption.
  Acceptance: application may use ports; changed domain imports reject frameworks; unrelated staged paths stop before task completion/state clear; verification history identifies exact source revisions and latest counts.
  Evidence:
  - Existing docs/implementation.md permits domain/port application dependencies and records brownfield exceptions. Current skill instructions and tools/spear/state.mjs define resumable refine state. Prior evidence is associated with e006881, a6b513c and bbd1aad, not relabeled as current approval.
  Validation: documentation only; no behavior prove/engine or production imports apply. EARS, nine Node tests, clean stable Maven verify (143 Java tests) and diff checks pass; no unrelated staged paths exist. Actual runtime source verification for both API profiles remains separately identified at bbd1aad. No production change or review approval is claimed.

- [x] **TDD-029** - Reject unsafe party spawns and explain offline roster cancellation.
  Tag: TDD
  References: REQ-038, REQ-039; docs/implementation.md match-execution.
  Acceptance: every team-member position lies within the existing containment volume before admission; defaults allow derived offsets to follow primary spawn edits; offline roster rejection sends feedback before cancellation.
  Evidence:
  - ArenaDefinition.contains/isReady/fallbackSpawnGroup already own bounds and legacy Bukkit Location coupling; retain that boundary without adding framework imports. Existing ArenaTeamSpawnTest provides Location and JUnit fixtures; java.lang.reflect.Proxy supplies the org.bukkit.World getName fixture without running a server. Readiness source wiring is separate from live Paper containment acceptance.
  - Existing org.bukkit.configuration.file.YamlConfiguration/File plus java.nio.file.Files/Path and JUnit APIs cover default resource contracts. DuelService.sendPartyRequest already receives a null online roster and has MSG_TARGET_OFFLINE plus challengeService.cancel for failure handling.
  Validation: three spawn-predicate and two adapter/default contract assertions failed before implementation and pass after it; all seven focused tests pass. Both pinned Paper profiles pass 143 Java tests; nine Node tests and EARS pass. Production imports add no framework coupling. Existing explicit installed overrides remain preserved but invalid arenas are now rejected; defaults affect new installs only. Actual multi-client containment and requester feedback remain test-server acceptance gates.

- [x] **TDD-028** - Separate ordinary match results from durable advancement evidence.
  Tag: TDD
  References: REQ-037, REQ-029; docs/implementation.md persistence-and-recovery.
  Acceptance: cooldown failure retains ordinary win/loss/draw counters; specialized evidence requires durable cooldown storage; existing result API remains compatible; terminal cleanup/loot stays outside the storage gate.
  Evidence:
  - StatsService.recordMatchResult and PlayerDuelStats record ordinary and specialized counters; DuelService.concludeDuel currently gates both on cooldownService.recordCompletion. Existing ActiveDuel, DuelSettings, DuelMatchType, DuelEndReason, MatchParticipant and MatchTeam define the result input.
  - Existing org.junit.jupiter.api.Test/JUnit assertions, java.nio.file.Files/Path, java.util.List/Map/UUID and java.util.function.Consumer support a constructor-free Bukkit-independent persistence sink and direct service tests. Reflective boundary lookup avoids compile-failure red; the package-local sink is testability infrastructure, not a new runtime persistence profile.
  Validation: all four focused assertions failed before the flag-bearing result boundary and pass after implementation. Three tests execute result accounting through a package-local persistence sink; one checks terminal adapter wiring. Both pinned Paper profiles pass 138 Java tests, nine Node tests and EARS pass. Existing public three-argument API delegates with evidence enabled; the production constructor still uses PlayerStatsStore.saveAsync. No database or live Paper loot/cleanup acceptance is claimed.

- [x] **TDD-027** - Preserve validator imports and require identified test evidence.
  Tag: TDD
  References: REQ-025; docs/implementation.md SPEAR adoption.
  Acceptance: importing the EARS validator from node -e works; empty test file/name and invalid status are rejected before state writes; valid identified red/green records still work.
  Evidence:
  - Existing tools/spear/tooling.test.mjs uses node:test, node:assert/strict, node:fs, node:os, node:path, node:url and node:child_process for actual process and state-file checks. Existing state.mjs owns phase/test gates and ears.mjs owns validator CLI routing.
  Validation: both new process/state assertions failed on the original helpers and pass after the guards. Nine Node tests, EARS, clean stable Maven verify (134 Java tests) and diff checks pass. No gameplay imports or source changed; state remains untouched on rejected evidence.

- [x] **DOC-004** - Correct reviewed SPEAR routing and historical planning guidance.
  Tag: DOC
  References: REQ-025; docs/implementation.md SPEAR adoption.
  Acceptance: actual Java package paths receive layer checks; prove retries retain state; missing state reports idle; Kotlin scaffolding requires actual build support; final checks follow the project stack; commits reject unrelated staged paths; settled party behavior is documented.
  Evidence:
  - Existing tools/spear/state.mjs, Maven manifest, Java source layout and phase procedures define the supported workflow; current implementation documents disconnect grace, rejected party wagers and cancelled friendly fire.
  Validation: documentation-only corrections; no new runtime behavior, imports, or behavioral prove/engine steps apply. EARS, seven Node tests, clean stable Maven verify (134 Java tests) and diff checks pass. Reviewed each instruction against source layout, manifest and state transitions; architecture inspection has no changed gameplay source in this task.

- [x] **TDD-026** - Recover repaired cooldown storage and prune expired enabled history.
  Tag: TDD
  References: REQ-035, REQ-036, REQ-029; docs/implementation.md persistence-and-recovery.
  Acceptance: admin reload retries failed writes without replacing unpersisted in-memory evidence; initial read failure retries loading; failed recovery remains blocked; persistence prunes expired positive-window entries but preserves disabled-window and future timestamps.
  Evidence:
  - DuelCooldownService.enable/persist/isHealthy and DuelCooldownPolicy.snapshot/remaining own the existing fail-closed state and timestamp semantics; DuelService.reloadFromCommand is permission-gated to ADMIN_RELOAD.
  - Existing DuelCooldownServiceTest uses dev.minecraft.warzoneduels.port.DuelCooldownStore, dev.minecraft.warzoneduels.domain.DuelCooldownPolicy, java.io.IOException, java.util.List/UUID/Map, java.util.concurrent.atomic.AtomicLong, org.junit.jupiter.api.Test and JUnit assertions for fault injection without Bukkit. Reflection on java.lang.Class.getMethod invokes the demanded recovery boundary without a compile-failure red.
  Validation: three executable recovery/pruning assertions failed before implementation and pass afterward; 17 focused policy/service tests pass. Both pinned Paper profiles pass 134 Java tests, seven Node tooling tests and EARS pass. Failed recovery remains fail-closed; retry preserves unpersisted memory instead of replacing it from disk. Administrative reload uses the existing permission gate. Live filesystem repair/reload acceptance remains open; no production changes.

- [x] **TDD-025** - Drain captured deaths before terminal and reload transitions.
  Tag: TDD
  References: REQ-034, REQ-017; docs/implementation.md match-execution and persistence-and-recovery.
  Acceptance: disable resolves the pending death batch before cancelling tasks/saving reload state; terminal transitions resolve before duelEnding and stop if resolution already concluded; scheduled resolution is cancelled before synchronous execution; existing spoils/draw policy stays authoritative.
  Evidence:
  - DuelService.handleDeath captures drops in pendingDeaths; resolvePendingDeaths creates spoils or preserves archived loads for simultaneous elimination. disable cancels deathResolutionTask then persists only elimination UUIDs; concludeDuel sets duelEnding before cleanup clears pendingDeaths.
  - Existing org.junit.jupiter.api.Test, java.nio.file.Files/Path and JUnit assertions in ExplosiveTeamCombatPolicyTest support adapter ordering contracts. These checks establish source wiring, not end-to-end Paper scheduler or inventory acceptance; existing TeamMatchPolicy tests exercise batch outcomes.
  Validation: after normalizing CRLF in the test fixture, all three source-order assertions failed before implementation and pass after the shared drain. Both pinned Paper profiles pass 131 Java tests; seven Node tests and EARS pass. No new production imports or domain coupling. Source wiring and existing domain batch tests are not end-to-end Paper inventory/reload acceptance. No production changes.

- [x] **TDD-024** - Preserve eliminated party members through runtime persistence and reload.
  Tag: TDD
  References: REQ-033; docs/implementation.md persistence-and-recovery and match-execution.
  Acceptance: real YAML save/load preserves only eliminated roster UUIDs and ignores malformed/foreign IDs; legacy files and existing save signatures remain supported. Recovery restores elimination state before participant indexing and skips eliminated players in its teleport loop.
  Evidence:
  - RuntimeStateStore serializes team rosters and returns PersistedRuntime, but currently omits DuelService.eliminatedParticipantIds; queueActiveDuelSave and saveActiveDuelSync are the existing snapshot boundaries.
  - DuelService.handleDeath and disconnect timeout add eliminated IDs before queued saves. disable flushes an active duel synchronously with a reload marker; recoverActiveDuelIfNeeded currently restores only the duel then indexes/teleports the full roster.
  - Existing persistence tests use sun.misc.Unsafe and java.lang.reflect.Field to supply a WarzoneDuelsPlugin/JavaPlugin fixture without booting Paper; org.bukkit.configuration.file.YamlConfiguration supports real temporary-file round trips. Domain ActiveDuel, MatchTeam, MatchParticipant, DuelMatchType, DuelSettings and TeamMatchPolicy define roster and survivor semantics.
  - JUnit Jupiter Test/TempDir, java.nio.file.Files/Path, java.util.Set/List/UUID, java.util.logging.Logger and existing source-wiring tests provide isolated persistence and integration-contract coverage. These tests do not claim live Paper reload acceptance.
  Validation: after correcting a test fixture API argument, both new assertions failed on the missing elimination snapshot/recovery behavior. Real YAML round-trip now preserves eliminated roster IDs, ignores foreign/malformed UUIDs and supports legacy missing data and the original synchronous save signature. Source-contract coverage confirms restoration before indexing and the recovery teleport guard; it is not an end-to-end Paper reload test. Both pinned Paper 26.3 and 26.2 clean verification profiles pass 128 Java tests; seven Node tests and EARS pass. Old queue/save signatures and the two-argument PersistedRuntime constructor remain available; no new runtime imports or domain coupling.

- [x] **TDD-023** - Report history append failures separately from persisted transitions.
  Tag: TDD
  References: REQ-032, REQ-025; docs/implementation.md SPEAR adoption.
  Acceptance: a persisted phase change exits successfully with an explicit history warning when history append fails; subsequent invalid transitions still fail.
  Evidence:
  - tools/spear/state.mjs save replaces the state using renameSync before appendFileSync; append failure currently propagates after the transition has succeeded.
  - tools/spear/tooling.test.mjs already uses isolated node:fs fault injection, node:child_process spawnSync and temporary state files to exercise real command outcomes. No new imports or dependencies are required.
  Validation: the injected append failure reproduced a nonzero command result after state persistence; the fix passes all seven Node tests, including preserved phase gates and failed-rename protection. EARS passes and clean Java 25 / Paper 26.2 verify passes all 126 Java tests. No Java imports, architecture boundaries or gameplay behavior changed.

- [x] **TDD-022** - Enforce same-IP admission across complete party rosters.
  Tag: TDD
  References: REQ-031, REQ-007; docs/implementation.md competitive-core and match-execution.
  Acceptance: Opposing non-leaders with matching IPs are blocked when configured; shared teammate IPs, missing addresses and the opt-out remain allowed; the common send/accept roster guard invokes the check.
  Evidence: DuelService.onlinePartyParticipants concatenates challenger then opponent players; rejectPartyRoster is called during request and acceptance; sameIp already defines null/unresolved-address behavior. Existing DuelModeControlsTest uses java.lang.reflect.Proxy, Field, java.util.List, java.nio.file.Files/Path, org.bukkit.entity.Player, org.junit.jupiter.api.Test/assertions and an Unsafe fixture for the real service. Test addresses use JDK java.net.InetSocketAddress; no production imports or dependencies are added.
  Validation: Two assertions failed before implementation (missing roster guard and helper); seven focused service tests pass after implementation, including opposing non-leaders, teammate-only sharing, null addresses and configured opt-out. This is a proxy-based service check plus source-wiring contract, not an end-to-end Paper proof. Paper 26.3 and stable 26.2 clean verify each pass 126 Java tests, zero failures/errors/skips; six Node tests and EARS pass. No new production imports or framework coupling; the small existing Bukkit service adapter uses its existing sameIp semantics. Hosted review/checks and live-player acceptance remain pending. No production changes.

- [x] **TDD-021** - Read unknown historical analytics match types safely.
  Tag: TDD
  References: REQ-030; docs/implementation.md persistence-and-recovery.
  Acceptance: Real H2 recent and player-specific queries retain an unknown-type row as NORMAL and preserve its participants and stored value; valid PARTY values stay unchanged.
  Evidence: DuelAnalyticsStore.readRecord currently invokes DuelMatchType.valueOf without a fallback. Existing DuelAnalyticsTransactionTest uses java.sql.Connection, java.sql.DriverManager, java.util.UUID and org.junit.jupiter.api.Test/assertions against the real H2 schema; no new imports or dependencies are required. RuntimeStateStore already uses safe enum fallback for legacy state. Canonical origin/main 480a365 is included in current PR head e006881, and the clean isolated ongoing branch is preserved.
  Validation: Real H2 regression failed one assertion on the prior PR head (unknown OLD_PARTY threw from findRecent); all seven analytics tests pass after the minimal adapter fallback. Both Paper 26.3 and stable 26.2 clean verify pass 124 Java tests with no failures/errors/skips; six Node tests and EARS pass. No new imports, framework coupling, permissions or data writes. SPEAR phases recorded through refine. Hosted exact-head checks/review remain separate; no staging or production change.

- [x] **INFRA-004** - Reconcile current upstream tests and traceable review delivery.
  Tag: INFRA
  References: REQ-014, REQ-018, REQ-029; docs/implementation.md platform-compatibility and SPEAR adoption.
  Acceptance: Preserve upstream tests from 480a365 and existing feature commits including 9f2466a in an isolated worktree. Both hosted workflows use Java 25 and exact PR heads; the stable test artifact excludes the unshaded original JAR and includes source commit/checksum metadata. Re-run both pinned Paper API suites, Node tooling and EARS; document local, CI, review, staging and production status separately. No automatic merge, production upload or activation.
  Evidence:
  - Fetched origin/main at 480a365; its changes since 09d048f are TESTING.md, tests.yml and five deterministic test suites. fork/main remains 09d048f; existing FainNeito/WarzoneDuels PR #1 is open at 4cae280. The original checkout/untracked logs and staged test JAR are preserved.
  - pom.xml, docs/tech-stack.md and verify.yml pin Java 25, Paper 26.2.build.123-stable and Paper 26.3.build.8-alpha. Newly merged tests.yml still selects Java 21, conflicting with release 25; reuse the inspected immutable checkout/setup-java pins from verify.yml.
  - Maven Shade leaves original-WarzoneDuels-1.0.5.jar next to the shaded deliverable. Restrict the hosted artifact glob to target/WarzoneDuels-*.jar and include git rev-parse HEAD plus sha256sum metadata after the stable build.
  - This task changes CI/documentation and combines previously verified source with upstream tests, not gameplay semantics: prove/engine are intentionally skipped. TDD-020 retains its original red/green evidence; new results will be recorded separately without fabricating another red.
  Validation: docs/evidence/cooldown-review-26.3.log and cooldown-review-26.2.log each pass clean verify with 123 Java tests and zero failures/errors/skips; stable build last. Six separate Node tests, EARS and diff whitespace checks pass. This task adds no Java imports or domain annotations; workflow/documentation-only architecture inspection passes. All 14 existing PR #1 review threads are resolved; CodeRabbit status succeeds only on the older 4cae280 head, not the new cooldown source. Publication is held pending the user's canonical-repository choice; new exact-head GitHub CI/review is not yet available. Local build results are not production/client acceptance.

- [x] **TDD-020** - Persist configurable duel and repeat-opponent cooldowns.
  Tag: TDD
  References: REQ-029; docs/implementation.md persistence-and-recovery and match-execution.
  Acceptance: Defaults are 300/86400 seconds; zero disables independently. Every participant and unordered opposing UUID pair is enforced at send, acceptance and start. Normal completion is persisted before advancement statistics; declined/expired challenge evidence is pair-limited. Reload/relog/restart preserve history; unreadable or unwritable history fails closed. Existing stats remain intact.
  Evidence:
  - DuelService.sendRequest, rejectRequestPlayers, rejectPartyRoster, rejectAcceptedRequest, startDuel and concludeDuel are the inspected matchmaking/result boundaries; statsService.recordMatchResult follows normal conclusion, not shutdown interruption.
  - Existing MatchTeam.participants, MatchParticipant.playerId, ActiveDuel.teamOne/teamTwo expose immutable complete rosters; org.junit.jupiter.api.Test, org.junit.jupiter.api.io.TempDir and org.bukkit.configuration.file.YamlConfiguration are existing test dependencies.
  - EnthusiaTags WarzoneStatsReader reads wins/best-win-streak directly from stats.yml; rejecting repeat matches preserves its contract without another plugin deployment. Challenge-sent evidence currently records every valid request.
  - PlayerStatsStore uses same-directory temporary files and java.nio.file.Files atomic move with fallback; new persistence uses strict YamlConfiguration.load rather than forgiving loadConfiguration so invalid history cannot silently reset protection.
  - dev.minecraft.warzoneduels.domain.DuelCooldownPolicy implements the UUID/pair history and immutable snapshots tested by DuelCooldownPolicyTest; dev.minecraft.warzoneduels.port.DuelCooldownStore carries snapshots and IOException without Bukkit coupling.
  - dev.minecraft.warzoneduels.app.DuelCooldownService coordinates the port, injected clock and failure reporting; dev.minecraft.warzoneduels.adapter.bukkit.persistence.YamlDuelCooldownStore uses org.bukkit.configuration.ConfigurationSection and org.bukkit.configuration.InvalidConfigurationException for strict schema/timestamp/UUID validation, tested with real temporary YAML files.
  - Admission tests follow existing DuelModeControlsTest reflection/proxy fixtures using dev.minecraft.warzoneduels.WarzoneDuelsPlugin, org.bukkit.entity.Player, org.bukkit.plugin.java.JavaPlugin and sun.misc.Unsafe; no new third-party dependency is introduced.
  Validation: duel-cooldown-red.log records two meaningful assertion failures (missing defaults and live guards), with no compilation/test errors. duel-cooldown-green.log passes 21 new cooldown tests plus 5 existing mode-control tests. Initial architecture scan incorrectly included JUnit test imports; restricting layer rules to production sources passed, as did import evidence and annotation gates. An initial unquoted PowerShell profile argument was corrected before compatibility verification. duel-cooldown-26.3-verify.log and duel-cooldown-26.2-verify.log each pass clean verify with 102 Java tests, zero failures/errors/skips; six separate Node tooling tests and EARS pass. Stable 26.2 build ran last. Packaged 1.0.5 JAR is 3,662,138 bytes, SHA-256 9A2513DBE3A296C750D3811E175E6030D3431ED1A341F845A22F853866B6A03E. MANUAL_TESTING.md records remaining live Paper/EnthusiaTags checks. No production mutation, push or PR is part of this task.

- [x] **TDD-018** - Prevent implicit commits after a rollback failure.
  Tag: TDD
  References: REQ-022; `docs/implementation.md#persistence-and-recovery`
  Acceptance: A failed rollback closes/discards the uncertain connection without setting auto-commit true; ordinary commit failures roll back and permit retry; the original SQL failure retains rollback/close diagnostic causes.
  Evidence:
  - TDD-016 real H2 regression fixture and DuelAnalyticsStore.insertAtomically; Connection.setAutoCommit(true) commits an active transaction, so it must not follow a failed rollback.
  - Existing java.sql.Connection, java.sql.SQLException and JDK java.lang.reflect.Proxy / java.lang.reflect.InvocationTargetException provide deterministic JDBC fault injection without new dependencies.
  Validation: review-rollback-red.log reproduces the unsafe auto-commit restoration after rollback failure; review-rollback-green.log passes six H2 transaction tests. review-final-26.3.log and review-final-26.2.log each pass all 70 Java tests; six Node tests and EARS pass. Persistence-only changes add no layer dependency. Stable build last; SPEAR state returns to idle.

- [x] **DOC-002** - Align local SPEAR instructions, attribution, and testing documentation.
  Tag: DOC
  References: REQ-014, REQ-018, REQ-022, REQ-023, REQ-024, REQ-025; `docs/implementation.md#spear-adoption`
  Acceptance: Skills use local helpers; the initial evidence gate precedes entering prove and later import-gate retries remain supported; platform and verification documents reflect the current baseline; preserve the full verified upstream notice with explicit provenance; distinguish the updated testing artifact as 1.0.3.
  Evidence:
  - Existing tools/spear/state.mjs and ears.mjs, AGENTS.md, pom.xml and plugin.yml define local commands and pinned platform versions.
  - Upstream BadgersMC/spear-plugin revision 2c91bae README declares MIT; its reference-implementations/ts-spear/LICENSE contains Copyright (c) 2026 BadgersMC and the full MIT text. No separate root/helpers notice exists in that snapshot; attribution provenance must remain explicit.
  - review-runtime-verify.log and review-tooling-green.log record the current Java and Node regression results.
  Validation: Manual gate-order inspection and helper-reference scan pass; later import-gate retry remains Step 7. EARS and six Node tests pass. review-26.3-verify.log and review-26.2-verify.log each pass 68 Java tests. Stable API built last as WarzoneDuels-1.0.3.jar. Upstream attribution uncertainty is documented rather than inventing a separate copyright.

- [x] **TDD-017** - Harden requirement validation and state-file replacement.
  Tag: TDD
  References: REQ-025; `docs/implementation.md#spear-adoption`
  Acceptance: Missing clauses at the next header and EOF fail; blank responses remain rejected; malformed JSON and non-object state report useful errors; successful transitions persist through a same-directory rename and failed replacements preserve previous state without temporary-file residue.
  Evidence:
  - Existing tools/spear/ears.mjs validate and CLI shim; tools/spear/state.mjs load, save and transition map.
  - Node built-in node:test, node:assert/strict, node:fs, node:os, node:path, node:url, node:child_process, node:module and node:crypto APIs supply isolated subprocess fixtures, filesystem fault injection, same-directory rename, and unique temporary names. No npm dependencies.
  Validation: review-tooling-red.log records five genuine failures; review-tooling-green.log passes six tests, including actual rename failure injection and all JSON primitive cases. Blank EARS responses were already rejected and the existing regexes were retained. EARS and review-tooling-verify.log pass (68 Java tests); no production layer changes or third-party imports.

- [x] **TDD-016** - Correct verified analytics, challenge expiry, and match-type review findings.
  Tag: TDD
  References: REQ-022, REQ-023, REQ-024; `docs/implementation.md#persistence-and-recovery`
  Acceptance: H2 parent/participant writes are atomic on success and failure; caller transactions remain owned by callers; expired challenges can be replaced directly; NORMAL rejects multi-member teams but singleton and PARTY matches remain valid.
  Evidence:
  - Existing DuelAnalyticsStore.insert, initializeSchema, insertParticipants and java.sql.Connection transaction/savepoint APIs; H2 2.2.224 resolved by pom.xml.
  - Existing DuelChallengeService.ensureRosterAvailable, challengeForParticipant, DuelChallengeServiceTest and ActiveDuelTeamTest.
  - Existing org.junit.jupiter.api.Test, org.junit.jupiter.api.Assertions, java.util.List, java.util.UUID, java.lang.reflect.Field and sun.misc.Unsafe fixtures; org.bukkit.plugin.java.JavaPlugin logger inspected in the pinned Paper JAR; dev.minecraft.warzoneduels.WarzoneDuelsPlugin, dev.minecraft.warzoneduels.domain.DuelEndReason, dev.minecraft.warzoneduels.domain.DuelMatchType, dev.minecraft.warzoneduels.domain.analytics.DuelRecord and dev.minecraft.warzoneduels.domain.analytics.DuelRecordParticipant supply existing types.
  - java.sql.Connection, java.sql.DriverManager, java.util.logging.Logger provide in-memory H2 execution and isolated logging; no new runtime dependencies.
  Validation: review-runtime-red.log reproduces four failing scenarios on the real H2 store and competitive core; review-runtime-green.log passes 13 focused tests; review-runtime-verify.log passes all 68 tests and clean packaging. EARS passes. No new production imports/annotations; the ActiveDuel change is a small constructor invariant within documented brownfield debt, not a framework expansion.

- [x] **TDD-015** - Correct playtest spawn/countdown, party announcements, explosive self-damage, and leader departure regressions.
  Tag: TDD
  References: REQ-009, REQ-016, REQ-019, REQ-020, REQ-021; `docs/implementation.md#match-execution`
  Acceptance: All six roster slots resolve to their configured sides; countdown begins after entry; victory names the complete winning party; leader departure clears memberships and invitations while locks remain respected; explosive self-damage follows the confirmed player preference.
  Evidence:
  - Existing `DuelService.spawnFor`, `ArenaDefinition.teamSpawn`, `startCountdown`, `DuelPartyService.leaveParty`, `DuelListener.onGenericDamage`, and `ExplosiveCombatPolicy` expose the reported failures.
  - Existing org.junit.jupiter.api.Test and org.junit.jupiter.api.Assertions support executable regression tests; java.lang.reflect and sun.misc.Unsafe provide an isolated DuelService fixture without starting Bukkit or invoking plugin constructors.
  - Existing dev.minecraft.warzoneduels.domain classes and org.bukkit.Location supply actual roster and arena values for spawn tests.
  Validation: `playtest-red.log` reproduces the wrong spawn side, missing party announcement, leader-leave rejection, and canceled self-damage. `playtest-green.log` passes 13 focused tests. `playtest-26.3-verify.log` and `playtest-26.2-verify.log` each pass all 62 tests on Java 25; EARS and changed-domain architecture checks pass. The final stable-API artifact is `target/WarzoneDuels-1.0.2.jar` (3,642,458 bytes). The old preparation source assertions encoded the wrong indexes and were replaced by executable six-slot service regression coverage. Live client confirmation remains outstanding.

## Brownfield baseline

The repository began SPEAR adoption with 14 passing tests. Initial team, party, and challenge domain seams added another 12 tests before adoption. No historical red/green claim is made for these 26 tests.

- [x] **TDD-001** - Establish the framework-free team, Duel Party, and unanimous challenge domain seam.
  Tag: TDD
  References: REQ-002, REQ-003, REQ-005, REQ-006, REQ-007, REQ-012, REQ-013; `docs/implementation.md#competitive-core`
  Acceptance: Existing one-versus-one construction remains compatible; teams and parties contain one to three unique players; challenges reject unequal or overlapping teams, snapshot rules and rosters, expire, and require unanimous acceptance.
  Evidence:
  - Existing `ActiveDuel`, `MatchParticipant`, `DuelSettings`, and `DuelService` sources established the one-versus-one compatibility surface.
  - JDK `java.util` collections and UUID types are the only dependencies added to the competitive core.
  - `ActiveDuelTeamTest`, `MatchTeamTest`, `DuelPartyTest`, and `DuelChallengeTest` pass as brownfield baseline evidence; no pre-implementation red is claimed.
  Validation: `mvn clean verify` passed 26 tests and produced the shaded 1.0.1 JAR before SPEAR adoption.

- [x] **INFRA-001** - Adopt project-local SPEAR workflow and traceability.
  Tag: INFRA
  References: REQ-014; `docs/implementation.md#spear-adoption`
  Acceptance: Project-local skills, EARS validator, Windows state helper, requirements, implementation notes, tasks, verification mapping, and contributor instructions exist; requirements validate and the clean build stays green.
  Evidence:
  - Upstream BadgersMC/spear-plugin revision `2c91bae` project-local snapshot: `spear-using-spear`, phase skills, and EARS validator.
  - Existing WarzoneDuels `pom.xml` identifies Java 21, Maven, Paper 1.21.11, H2, and JUnit 5.
  - Existing source and test inventory supplies the brownfield package and verification baseline.
  Validation: EARS validation passed all 14 requirements; the architecture scan found no framework imports or forbidden annotations in the new competitive-core types; Maven clean verify passed all 26 tests and produced the shaded JAR.

- [x] **TDD-002** - Add session Duel Party registry and invitation lifecycle.
  Tag: TDD
  References: REQ-003, REQ-004, REQ-005, REQ-013; `docs/implementation.md#competitive-core`
  Acceptance: A player belongs to at most one party; only leaders invite or remove; invitations expire and require target acceptance; leave, transfer, kick, and disband maintain indexes; roster locks reject every membership mutation.
  Evidence:
  - Existing `DuelParty` defines the framework-free roster and challenge-lock invariants.
  - Existing `DuelCommand` is the Bukkit command adapter and `PermissionPolicy` owns command authorization.
  - JUnit 5 and JDK collections are already present in `pom.xml` and existing tests.
  Validation: `party-service-red.log` records the missing application service; `party-service-green.log` passes five focused lifecycle tests; the application import scan found only domain and JDK dependencies; `party-service-verify.log` passes all 31 tests and packages the shaded JAR.

- [x] **TDD-003** - Add the roster-locking challenge coordinator and participant acceptance lifecycle.
  Tag: TDD
  References: REQ-001, REQ-002, REQ-005, REQ-006, REQ-007, REQ-013; `docs/implementation.md#competitive-core`
  Acceptance: Party leaders create equal-roster challenges; both rosters lock; every participant is indexed and must accept; decline, cancellation, expiry, and completion unlock both rosters; busy or unequal rosters cannot create partial locks.
  Evidence:
  - Existing `DuelRequest` and `DuelService.pendingRequest` identify the current single-request boundary.
  - Existing `DuelChallenge` supplies roster snapshots, expiration, and unanimous acceptance.
  - Existing request expiry, combat, spawn, and online checks in `DuelService` are the safety baseline.
  Validation: `challenge-service-red.log` records the missing coordinator; `challenge-service-green.log` passes four focused roster-lock and lifecycle tests; the application coupling scan found no server-framework dependency; `challenge-service-verify.log` passes all 37 tests and packages the shaded JAR.

- [x] **TDD-007** - Integrate party challenge contracts into the live request and GUI flow.
  Tag: TDD
  References: REQ-001, REQ-005, REQ-006, REQ-007, REQ-013; `docs/implementation.md#competitive-core`
  Acceptance: Individual challenges retain the current review flow; challenging a party leader snapshots both parties; all participants receive the contract; each acceptance is recorded; decline, expiry, logout, or failed combat/location/online checks release locks; only a ready challenge reaches match preparation.
  Evidence:
  - Existing `DuelService.pendingRequest`, builder GUI callbacks, request expiry, accept, deny, and start validation define the live individual flow.
  - `DuelChallengeService` owns party challenge indexing, roster locks, unanimous acceptance, expiry, and completion.
  - `DuelGui` and `DuelGuiListener` define the existing request-review and confirmation adapter.
  Validation: `party-challenge-live-red.log` records the absent composition and GUI callback flow; `party-challenge-live-green.log` passes the live contract and coordinator suites; `party-challenge-live-verify.log` passes all 50 tests and packages the shaded JAR. Party wagers are rejected, roster checks are repeated before start, and decline, expiry, logout, or failed start releases both rosters.

- [x] **TDD-006** - Expose Duel Party lifecycle through Bukkit commands.
  Tag: TDD
  References: REQ-004, REQ-005, REQ-008, REQ-013; `docs/implementation.md#competitive-core`
  Acceptance: `/duel party` supports create, invite, accept, inspect, leave, kick, transfer, and disband; help and tab completion reflect permissions and party state; domain failures produce actionable player messages without bypassing the application service.
  Evidence:
  - Existing `DuelCommand` is the Bukkit command router and tab completer.
  - Existing `PermissionPolicy` and `plugin.yml` define the permission hierarchy and parent tests.
  - TDD-002 supplies the framework-free application service consumed by the adapter.
  Validation: `party-command-red.log` records missing routing and composition; `party-command-green.log` passes the focused command-contract and permission suites; the competitive-core coupling scan is clean; `party-command-verify.log` passes all 33 tests and packages the shaded JAR.

- [x] **TDD-004** - Define framework-free team combat and elimination policy.
  Tag: TDD
  References: REQ-001, REQ-002, REQ-009, REQ-010, REQ-013; `docs/implementation.md#match-execution`
  Acceptance: Team membership is authoritative for friendly-fire checks; eliminating one member does not end a multi-player match; victory is returned only when every opponent is eliminated; draw readiness requires every non-eliminated participant.
  Evidence:
  - Existing `ArenaDefinition`, `DuelService`, and `DuelListener` define current two-spawn preparation, containment, and death boundaries.
  - Existing `RuntimeStateStore` and `LoadoutArchiveStore` define recovery and inventory safety requirements.
  - Paper 1.21.11 API is the provided server contract in `pom.xml`.
  Validation: `team-policy-red.log` records the missing policy; `team-policy-green.log` passes friendly-fire, partial elimination, team victory, and draw-consent tests; the domain coupling scan is clean; `team-policy-verify.log` passes all 41 tests and packages the shaded JAR.

- [x] **TDD-008** - Add backward-compatible arena spawn groups.
  Tag: TDD
  References: REQ-001, REQ-002, REQ-009, REQ-013; `docs/implementation.md#match-execution`
  Acceptance: Arena configuration supplies three stable positions per team while preserving `spawn1` and `spawn2`; missing configured positions use deterministic safe offsets from the legacy spawn; callers resolve a spawn by team and roster slot without exposing mutable location state.
  Evidence:
  - Existing `ArenaDefinition`, `DuelService.startDuel`, `spawnFor`, and `startCountdown` define the current two-player flow.
  - Existing `config.yml` `arena.spawn1` and `arena.spawn2` values must remain valid after upgrade.
  - Paper `Location.clone` and vector offsets are already used by the arena code.
  Validation: `team-spawns-red.log` records the absent grouped-spawn API; `team-spawns-green.log` passes slot resolution and defensive-copy tests; the existing Bukkit-coupled arena value remains documented brownfield debt; `team-spawns-verify.log` passes all 43 tests and packages the shaded JAR.

- [x] **TDD-010** - Generalize match preparation and countdown to complete rosters.
  Tag: TDD
  References: REQ-001, REQ-002, REQ-009, REQ-013; `docs/implementation.md#match-execution`
  Acceptance: Match preparation archives, clears combat state, prepares, teleports, warns, and freezes every participant; each participant receives the stable team-slot spawn; failed terrain preparation restores wager state and notifies every participant; existing one-versus-one behavior is unchanged.
  Evidence:
  - Existing `DuelService.startDuel`, `prepareCombatant`, `spawnFor`, `startCountdown`, and loadout archive calls define the current two-player flow.
  - TDD-008 supplies backward-compatible spawn groups.
  - `ActiveDuel.participants` and `MatchTeam.participants` supply stable roster order.
  Validation: `team-preparation-red.log` records the absent live roster preparation contract; `team-preparation-green.log` verifies party entry, roster iteration, grouped spawns, and complete-roster countdown; `team-preparation-verify.log` passes all 48 tests and packages the shaded JAR.

- [x] **TDD-009** - Apply team policy to live combat, disconnects, containment, and cleanup.
  Tag: TDD
  References: REQ-001, REQ-009, REQ-010, REQ-013; `docs/implementation.md#match-execution`
  Acceptance: Friendly fire is cancelled; death and disconnect timeout eliminate one member; a match continues while that member has a surviving teammate; a winner is declared only after a whole team is eliminated; every participant is contained, messaged, restored, and cleaned exactly once.
  Evidence:
  - Existing `DuelListener.onDamage` and `DuelService.shouldCancelDamage`, `handleDeath`, disconnect monitor, containment monitor, and conclusion paths define the live combat boundary.
  - TDD-004 supplies the framework-free policy used by the Bukkit orchestration.
  - Existing loadout archive and recovery markers protect participant inventories and restart exits.
  Validation: `team-combat-red.log` records the absent live team-policy contract; `team-combat-green.log` verifies friendly-fire, whole-team victory, unanimous surviving-player draws, and killer-aware death handling; `team-combat-verify.log` passes all 49 tests and packages the shaded JAR.

- [x] **TDD-005** - Version runtime persistence for complete teams and safe interruption recovery.
  Tag: TDD
  References: REQ-001, REQ-010, REQ-011, REQ-013; `docs/implementation.md#persistence-and-recovery`
  Acceptance: Team runtime state writes match type, team identity, and every participant; the reader accepts both legacy two-participant files and the new team schema; corrupt or incomplete teams produce no active duel; shutdown/restart recovery enumerates every participant and creates no invented winner.
  Evidence:
  - Existing `RuntimeStateStore`, `StatsService`, `SpoilsService`, `DuelAnalyticsService`, and `DuelService` define the current one-versus-one outcome behavior.
  - Existing shutdown, resume-marker, loadout archive, and spectator tests establish the recovery baseline.
  Validation: `team-runtime-schema-red.log` records absent schema and whole-roster recovery; `team-runtime-schema-green.log` passes the schema and recovery contract; the version-2 writer retains legacy participant keys while storing full teams; `team-runtime-schema-verify.log` passes all 45 tests and packages the shaded JAR.

- [x] **TDD-011** - Apply explicit team outcome policies to wagers, statistics, and analytics.
  Tag: TDD
  References: REQ-001, REQ-010, REQ-011, REQ-013; `docs/implementation.md#persistence-and-recovery`
  Acceptance: Party wagers are rejected until a split policy is approved; all winners receive one win and all losers one loss; analytics retain team size and participant membership without corrupting existing records.
  Evidence:
  - Existing `StatsService`, `SpoilsService`, `DuelAnalyticsService`, `DuelAnalyticsStore`, and `DuelRecord` define one-versus-one outcomes.
  - Existing economy hold/refund/payout paths assume two contributors and one recipient.
  - `TeamMatchPolicy` defines authoritative winning and losing rosters.
  Validation: `team-outcome-red.log` records the missing team outcome policy; `team-outcome-green.log` passes roster outcome and wager rejection tests; the domain import scan is clean; `team-outcome-verify.log` passes all 47 tests and packages the shaded JAR. The analytics schema preserves legacy leader columns and adds match type, team size, and indexed participant membership.

- [x] **TDD-012** - Capture defeated inventories once under an explicit team spoils policy.
  Tag: TDD
  References: REQ-001, REQ-010, REQ-011, REQ-013; `docs/implementation.md#persistence-and-recovery`
  Acceptance: Each defeated inventory is captured once for a valid opposing killer; when no valid killer exists, the winning roster receives a deterministic recipient; teammate or unrelated damage never receives spoils; legacy one-versus-one ownership is unchanged.
  Evidence:
  - Existing `SpoilsService` creates one vault from a winner and defeated participant snapshot.
  - Existing `DuelService.handleDeath` and disconnect timeout paths select the current one-versus-one winner.
  - `TeamMatchPolicy` defines authoritative team membership and surviving participants.
  Validation: `team-spoils-red.log` records the missing policy; `team-spoils-green.log` verifies valid opposing-killer preference and deterministic surviving-opponent fallback; the domain import scan is clean; `team-spoils-verify.log` passes all 52 tests and packages the shaded JAR.

- [x] **DOC-001** - Complete live Paper acceptance checklist and produce a testing JAR.
  Tag: DOC
  References: REQ-001, REQ-007, REQ-009, REQ-010, REQ-011, REQ-014; `docs/implementation.md#match-execution`
  Acceptance: Automated verification is green, the JAR is checksum-recorded, and manual steps cover 1v1 regression, 2v2, 3v3, friendly fire, deaths, disconnects, restart interruption, inventory restoration, wagers, spectators, arena reset, and dependent-plugin coexistence.
  Evidence:
  - Existing `MANUAL_TESTING.md` and `PLAYER_GUIDE.md` define the current operator and player behavior.
  - Maven Shade output is the existing deployable artifact format.
  Validation: `final-testing-jar-verify.log` records the original acceptance build; the historical Java 25 stable build at that stage is recorded in `paper-26.2-java25-verify.log` with all 58 tests passing. That historical shaded testing JAR was `target/WarzoneDuels-1.0.1.jar` (3,641,773 bytes), SHA-256 `9C396553B20F2F5B6A3D3E3227AB05892E99F5CF2D3E96AB688C34D76D230A3F`. Its class-file major version is 69 and packaged `api-version` is `26.2`. `MANUAL_TESTING.md` includes 1v1, complete 2v2/3v3, explosive/double-KO, duel-duration, and Paper 26.x live-server checks; live Paper/client testing remains for the operator.

- [x] **TDD-013** - Add an optional duel-duration limit that defaults to unlimited.
  Tag: TDD
  References: REQ-001, REQ-009, REQ-010, REQ-011, REQ-014, REQ-015; `docs/implementation.md#match-execution`
  Acceptance: `settings.duel-time-limit-seconds` defaults to zero; zero schedules no timeout; a positive value starts when combat is released, concludes an unfinished 1v1 or party match as a draw, and is cancelled on every ordinary conclusion or shutdown path.
  Evidence:
  - Existing `DuelService.reloadConfig`, `startCountdown`, `concludeDuel`, `disable`, and Bukkit task cancellation methods define the configuration, combat-release, draw, and lifecycle boundaries.
  - Existing `DuelEndReason.DRAW`, `StatsService`, and wager refund behavior define the safe timeout result without introducing a second conclusion path.
  - Existing JUnit 5 source-contract tests verify Bukkit orchestration where a live scheduler is unavailable in the local unit-test harness.
  Validation: `duel-duration-red.log` records the missing duration policy; `duel-duration-green.log` passes the focused deadline, rounding, configuration, scheduling, cancellation, and persistence contracts; `duel-duration-verify.log` passes EARS validation and all 54 tests, then packages the shaded JAR. Zero remains unlimited by default, while positive limits persist their active deadline across plugin reloads and conclude through the existing draw/refund/cleanup path.

- [x] **TDD-014** - Make explosive party combat attributable and resolve same-tick double knockouts safely.
  Tag: TDD
  References: REQ-001, REQ-009, REQ-010, REQ-011, REQ-013, REQ-016, REQ-017; `docs/implementation.md#match-execution`
  Acceptance: Crystal, respawn-anchor, and explosive-minecart damage retains a participant source; attributed teammate damage and unattributed party explosion damage are cancelled; attributed enemy damage supplies the spoils killer; deaths resolve together on the next primary-thread tick; eliminating both complete teams in that batch produces a draw, no spoils, and an archived-loadout restore after respawn.
  Evidence:
  - Paper 1.21.11 `EntityDamageEvent.getDamageSource`, `DamageSource.getCausingEntity`, `EntityDamageByBlockEvent`, `TNTPrimed.getSource`, `EntityPlaceEvent`, and Bukkit scheduler APIs define the adapter evidence available in the locally resolved `paper-api` JAR.
  - Existing `DuelListener.onDamage`, `handleCrystalDamage`, entity/block explosion handlers, and `PlayerDeathEvent` handling define the current event boundary.
  - Existing `DuelService.handleDeath`, `TeamMatchPolicy`, `TeamSpoilsPolicy`, `LoadoutArchiveStore`, and draw conclusion path define the elimination, spoils, restore, and outcome boundaries.
  - JUnit 5 and JDK collection/UUID APIs are already present in `pom.xml` and the framework-free domain test suite.
  Validation: `explosive-combat-red.log` records the absent attribution and batch-outcome policies; `explosive-combat-green.log` passes 13 focused combat, spoils, and recovery tests; the new domain policies import only JDK types; `explosive-combat-verify.log` passes EARS validation and all 58 tests. Explosion owners now flow through friendly-fire and spoils decisions, while a same-tick complete-team knockout draws without spoils and persists archived-loadout restoration across restart.

- [x] **INFRA-002** - Move the supported platform baseline to Java 25 and verify Paper 26.2/26.3.
  Tag: INFRA
  References: REQ-014, REQ-018; `docs/implementation.md#platform-compatibility`
  Acceptance: Maven emits Java 25 bytecode; the default provided API is pinned to stable Paper 26.2; `plugin.yml` declares API 26.2; a pinned `paper-26.3` profile compiles and passes the same suite; the stable build is rerun last and recorded as the testing JAR.
  Evidence:
  - Paper's official getting-started documentation specifies Java 25 for Paper 26.1 and newer.
  - Paper's official project-setup documentation defines the `26.2.build.<build>-stable` Maven coordinate format and Java 25 toolchain.
  - Paper's official downloads page identifies Paper 26.2 build 123 as the current stable build consulted for this task.
  - Paper's official API documentation identifies the currently consulted Paper 26.3 API as `26.3.build.8-alpha`.
  - Existing `pom.xml`, `plugin.yml`, Maven compiler, Surefire, Shade, and the 58-test suite define the local build and compatibility surface.
  Validation: `paper-26.2-java25-verify.log` passes EARS validation and all 58 tests against `26.2.build.123-stable`; `paper-26.3-java25-verify.log` passes all 58 tests against `26.3.build.8-alpha`. The stable 26.2 build was run last, emits Java class-file major version 69, packages `api-version: 26.2`, and produced the checksum-recorded testing JAR.

- [x] **TDD-019** - Persist provider-owned advancement evidence for the remaining non-guild duel achievements.
  Tag: TDD
  References: REQ-026, REQ-027, REQ-028; docs/implementation.md persistence-and-recovery and match-execution.
  Acceptance: stats.yml durably records counters for valid challenges sent, spoils withdrawals, mutual draws, challenger wins under non-default rules, wins with pearls and wind charges disabled, and 1v1 kill wins below four health before healing. Existing wins/streaks remain unchanged. Guild-war and spectator-betting achievements remain absent.
  Evidence:
  - DuelService.sendRequest creates a valid DuelRequest only after request safety and wager checks; DuelRequest.requesterId identifies the challenger.
  - SpoilsService.claimSingleItem and claimAll are the successful withdrawal boundaries.
  - DuelService.requestDraw marks participant consent and concludes only when TeamMatchPolicy.allSurvivorsRequestedDraw is true.
  - DuelSettings carries exact item/ruleset toggles; ActiveDuel preserves the challenger as team one for normal challenges; concludeDuel receives the online winner before healAfterDuel.
  - PlayerStatsStore already owns atomic stats.yml persistence and is the stable provider file consumed read-only by EnthusiaTags.
  - Current repository has participant wagers but no spectator-betting subsystem; WarzoneDuels REQ-012 keeps guild integration outside the core.
  Validation: focused advancement evidence tests pass 6/6; full Java 25 / Paper 26.2 Maven verify passes 76 Java tests (separate from six Node tooling tests) with zero failures/errors/skips and packages target/WarzoneDuels-1.0.3.jar.

## PR cleanup: workflow retries and verification reporting

- [x] **DOC-003** - Make engine/architecture/refine retryable without bypassing gates.
  Tag: DOC
  References: REQ-014, REQ-025; docs/implementation.md#spear-adoption
  Acceptance: resumes retain their current phase; refine requires EARS, Node tests, and clean Maven verify; historical counts remain labeled rather than overwritten.
  Evidence:
  - Existing local phase helpers and phase skill procedures; no gameplay or domain changes.
  - Actual clean verification logs from this PR-cleanup pass will be recorded separately from earlier acceptance builds.
  Validation: PR-cleanup clean verify passed 76 Java tests; the separate Node tooling run passed 6 tests; EARS passed. This is later than the historical 70-Java/6-Node baseline, not a relabeling of that baseline. Current local artifact is WarzoneDuels-1.0.3.jar. No gameplay files changed.

- [x] **INFRA-003** - Exercise the pinned 26.3 profile before stable artifact verification in hosted CI.
  Tag: INFRA
  References: REQ-014, REQ-018; docs/implementation.md#platform-compatibility
  Acceptance: Both pinned Paper profiles run clean verification on the exact head; preserve separate test reports; the uploaded JAR is produced only by the final stable 26.2 build.
  Evidence:
  - Existing pom.xml pins stable 26.2.build.123-stable and compatibility profile paper-26.3 at 26.3.build.8-alpha.
  - Existing immutable GitHub Actions checkout, setup-java and artifact actions in .github/workflows/verify.yml; no gameplay sources or server configuration changes.
  Validation: round2-warzone-tooling.log passed six Node tests and EARS passed. round2-warzone-26.3-verify.log and round2-warzone-26.2-verify.log each passed 76 Java tests with zero failures/errors/skips. The pinned 26.3 compatibility run ran first; stable 26.2 clean verification ran last, producing the final 1.0.3 artifact. Hosted CI now preserves both report sets separately. No gameplay or deployment changes.
