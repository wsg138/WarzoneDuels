# Verification and traceability

Brownfield baseline before SPEAR adoption: Maven clean verify passed 26 tests with zero failures, errors, or skips and produced `WarzoneDuels-1.0.1.jar`.

| Requirements | Automated evidence | Live evidence still required |
| --- | --- | --- |
| REQ-001 | Existing permission, persistence, spectator, Plan, and teleport tests | Full one-versus-one Paper regression |
| REQ-002 | `MatchTeamTest`, `ActiveDuelTeamTest`, `DuelChallengeTest` | Invalid live challenge messages |
| REQ-003 | `DuelPartyTest` | Party command usability |
| REQ-004 | `DuelPartyServiceTest` covers leader authority, expiry, acceptance, membership indexing, transfer, kick, leave, and disband | Multi-client invitation flow |
| REQ-005 | `DuelPartyTest.challengeLockPreventsEveryRosterMutation`, `DuelPartyServiceTest.rosterLockRejectsEveryMembershipMutation` | Locked command feedback |
| REQ-006, REQ-007 | `DuelChallengeTest` and `DuelChallengeServiceTest` cover snapshots, equal rosters, locking, indexing, unanimous acceptance, decline, expiry, and completion | Live GUI integration and multi-client unanimous acceptance |
| REQ-008 | `DuelPartyCommandContractTest`, `PermissionPolicyTest`, and `PermissionParentsTest` verify routing, operations, composition, configuration, and permission metadata | Multi-client messages and tab-completion check |
| REQ-009 | `TeamMatchPolicyTest` covers friendly fire, partial elimination, whole-team victory, and unanimous surviving draw consent | 1v1, 2v2, and 3v3 arena combat |
| REQ-010 | `RuntimeStateTeamSchemaContractTest` verifies versioned full-team storage, legacy keys, and whole-roster restart/recovery enumeration | Disconnect and full restart staging |
| REQ-011 | `TeamOutcomePolicyTest`, `TeamSpoilsPolicyTest`, and `DuelAnalyticsTransactionTest` cover team outcomes, spoils selection, and actual H2 parent/participant persistence | Vault, economy, stats, Plan, and announcements |
| REQ-012 | Source scan for guild or LumaGuilds coupling | None until integration is approved |
| REQ-013 | Architecture review for every changed party-core file | Plugin coexistence staging |
| REQ-014 | EARS validation, SPEAR state history, linked task evidence, and clean Maven verification | None |
| REQ-015 | `DuelDurationPolicyTest` covers unlimited defaults, release-based deadlines, tick conversion, configuration wiring, cancellation, and runtime persistence | Enable a short limit on staging and verify an unfinished 1v1 and party match end as draws |
| REQ-016 | `ExplosiveTeamCombatPolicyTest` plus the focused combat/spoils suites cover attributed enemies, teammate cancellation, unattributed party explosions, and live adapter wiring | Crystal, anchor, and TNT-minecart combat with multiple real clients |
| REQ-017 | `ExplosiveTeamCombatPolicyTest` covers complete-team double elimination; source contracts verify next-tick batching and persistent archived-loadout restoration | Same-explosion final deaths, respawn, and restart-before-respawn staging |
| REQ-018 | Java 25 clean verification against pinned Paper 26.2 stable and Paper 26.3 alpha APIs; see the latest review verification logs for current test counts | Full startup and gameplay matrix on actual Paper 26.2 and 26.3 servers with dependencies |
| REQ-022 | `DuelAnalyticsTransactionTest`: complete write, partial-batch rollback/retry, duplicate parent, caller-owned transaction isolation, injected commit failure and failed-rollback connection disposal on real in-memory H2 | Disk/server interruption staging |
| REQ-023 | `DuelChallengeServiceTest.expiredChallengeCanBeReplacedWithoutAnIntermediateLookup` | Expiry under server load |
| REQ-024 | `ActiveDuelTeamTest.normalRejectsMultiMemberTeamsButBothTypesAllowSingletons` | Corrupt persisted match rejection on staging |
| REQ-025 | `tools/spear/tooling.test.mjs`: missing clauses, malformed JSON and state shape, same-directory rename, failure preservation and cleanup | None |

Automated tests and a clean package build do not approve production deployment. Paper/client behavior, plugin interoperability, restart recovery, and latency-sensitive combat remain staging checks.

## Current cooldown review delivery (2026-10-04)

Analytics initialization follow-up (2026-10-05): two real-H2 failure-injection assertions first failed on leaked connections and missing throwable diagnostics, then passed after scoped disposal. `DuelAnalyticsInitializationTest` covers successful retry/persistence and a suppressed close error. Both clean pinned Paper builds pass 145 Java tests; nine Node tests and EARS pass. The package-local opener is test infrastructure, not a new runtime option. Existing rollback/caller-owned transaction semantics remain unchanged. Review/CI and actual server acceptance remain separate gates.

The isolated review branch starts at fetched upstream main `480a365` and incorporates the existing team/admin/evidence work plus cooldown commit `9f2466a`, preserving the upstream regression suites. REQ-029 is covered by `DuelCooldownPolicyTest`, `DuelCooldownServiceTest`, `DuelCooldownAdmissionTest` and `DuelCooldownWiringTest`: UUID-pair history, independent configuration, party-member guards, exact expiry, restart persistence and fail-closed storage. The original 102-test result is historical evidence before the upstream tests were combined, not a claim for this review head.

Historical reconciliation verification at `e006881` passed 123 Java tests against each pinned Paper API, then six independent Node tooling tests and EARS. Stable 26.2 verification ran last; logs are `docs/evidence/cooldown-review-26.3.log` and `cooldown-review-26.2.log`. Existing PR #1's 14 review threads were resolved and CodeRabbit succeeded at `4cae280`, but neither is fresh review approval of the subsequent upstream PR #21 head.

Both workflows must use Java 25 and exact PR heads. CI's stable shaded JAR includes source-commit/checksum metadata and excludes the unshaded original. The previously uploaded `/plugins/chapter 2 staging/WarzoneDuels-1.0.5-cooldown-test.1.jar` remains an unmerged local test artifact from the older source, not a merged release or production activation.

Production delivery requires reviewed/merged source on the confirmed canonical main branch, a clean checkout of that exact merged commit, canonical clean build/CI verification, version/SHA-256 recording and explicit deployment authorization. No production action or PR merge is authorized by this delivery pass. No build-owning monorepo or dependency pin has been identified in this standalone repository; that must be verified if a combined build is later requested. Actual Paper/EnthusiaTags player-visible anti-farming acceptance remains open.

Historical review-fix baseline: 70 Java tests passed on each pinned Paper API (`review-final-26.2.log`, `review-final-26.3.log`), and six Node tooling tests passed. That stable build produced `target/WarzoneDuels-1.0.3.jar`. The 62-test result below is historical 1.0.2 evidence, not the latest count.

Historical pending-death and cooldown recovery verification at `a6b513c`: 134 Java tests passed on each pinned Paper API, seven Node tooling tests passed, and EARS passed. Death-drain tests check adapter ordering and reuse existing domain outcome tests; cooldown recovery/pruning tests execute fault injection against the service. Neither proves live Paper inventory restoration or administrative filesystem repair.

Latest runtime-source verification at `bbd1aad`: 143 Java tests pass on each pinned Paper API, nine Node tooling tests pass, and EARS passes. Added result accounting tests distinguish ordinary statistics from durable specialized evidence; spawn tests execute containment predicates, while default/readiness/offline-feedback tests are adapter contracts. Stable 26.2 clean verification ran last. Existing explicit spawn overrides are not migrated or removed; invalid positions reject arena readiness. No server/client acceptance, deployment or merge is claimed.

1.0.2 regression evidence: `PlaytestRegressionTest` executes all six DuelService spawn lookups against ArenaDefinition, checks complete-party victory labels, leader disband/index/invitation cleanup, and explosive self-versus-teammate damage (REQ-019 through REQ-021). Both pinned Paper API builds pass 62 tests. Live countdown, arrival, leader logout notification, and self-explosion checks remain in MANUAL_TESTING.md.

## Duel blocks (REQ-041, 2026-10-06)

Requested by the server owner so EnthusiaFriends' Block Everywhere can cover duel challenges. New framework-free `DuelBlockList` (domain), `DuelBlockStore` port, `DuelBlockService` and `DuelBlockApiService` (app), `YamlDuelBlockStore` (adapter) and public `DuelBlockApi`. Red evidence: docs/evidence/duel-blocks-red.log (12 failing). Green: 140 Java tests on each pinned Paper API (duel-blocks-26.3-verify.log ran first, duel-blocks-26.2-verify.log last and produced WarzoneDuels-1.0.5.jar), seven Node tooling tests and EARS. Not verified: live Paper behavior of the commands, refusal messages, party flows, restart persistence and the EnthusiaFriends connection; see MANUAL_TESTING.md. No deployment or merge is implied.

## Duel block review fixes (REQ-041 amended, TDD-032, 2026-10-06)

A code review of the merged duel blocks found four gaps, now fixed: a single failed save no longer pauses all duels (that change fails and the previous list stays in effect), /duel reload retries an unreadable block file, accepting a pending 1v1 request or a Duel Party invitation re-checks blocks, and duel name suggestions hide players the sender cannot see. Red: docs/evidence/duel-block-review-red.log. Green: 142 Java tests on each pinned Paper API (26.3 first, 26.2 last), seven Node tooling tests and EARS. Not verified in game.
