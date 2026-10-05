# WarzoneDuels requirements

## Safety and behavior

### REQ-040 - Close failed analytics initialization

IF analytics initialization fails after opening a connection THEN THE SYSTEM SHALL close and discard that connection, preserve the original failure and any close failure in diagnostics, and permit a later initialization retry without changing transaction ownership or persisted records.

### REQ-038 - Safe party spawns

WHEN arena readiness is checked THE SYSTEM SHALL reject configured or derived team-member spawns outside the existing containment bounds, and SHALL ship optional secondary spawn overrides commented out so omitted positions follow moved primary spawns.

### REQ-039 - Offline roster feedback

IF a snapshotted party roster contains an offline member THEN THE SYSTEM SHALL notify the requester with the existing offline-target message before cancelling the challenge and releasing its roster locks.

### REQ-037 - Match finalization after cooldown failure

IF completed-match cooldown persistence fails THEN THE SYSTEM SHALL preserve ordinary wins, losses and draws while withholding new specialized advancement evidence, completing recovery, cleanup and loot distribution, and keeping new duels blocked until storage recovers.

### REQ-035 - Recover cooldown storage safely

WHEN an administrator reloads configuration after repairing cooldown storage THE SYSTEM SHALL retry persisting retained in-memory history if load previously succeeded, otherwise retry the initial load, and unblock duels only after persistence succeeds.

### REQ-036 - Bound enabled cooldown history

WHEN cooldown history is persisted THE SYSTEM SHALL prune expired entries only for positive configured windows while retaining all history for disabled windows and all timestamps still protected by backward clock movement.

### REQ-034 - Resolve captured deaths before lifecycle transitions

WHEN a duel concludes or the plugin disables with captured pending deaths THE SYSTEM SHALL resolve the existing death batch before marking the duel ended or persisting reload state, preserve its existing spoils and simultaneous-elimination policy, and cancel its scheduled callback to avoid duplicate resolution.

### REQ-033 - Preserve eliminated party participants on reload

WHEN an active duel is saved and resumed after a plugin reload THE SYSTEM SHALL persist and restore eliminated roster UUIDs before rebuilding the participant index, exclude eliminated players from recovery teleports, and interpret legacy saves without elimination data as having no eliminated players.

### REQ-032 - Truthful SPEAR history failure reporting

IF SPEAR history append fails after a state replacement succeeds THEN THE SYSTEM SHALL report a history warning without reporting that the persisted transition failed or weakening phase gates.

### REQ-031 - Party same-IP admission

WHEN party rosters are checked before admission THE SYSTEM SHALL enforce the configured same-IP restriction across every opposing participant pair while allowing shared teammate addresses and preserving the allow-same-IP opt-out and missing-address behavior.

### REQ-030 - Compatible historical analytics

IF a stored analytics row has an unknown match type THEN THE SYSTEM SHALL read that row as NORMAL without modifying persisted data or breaking recent and player-specific queries.

Date: 2026-09-17

This is a brownfield SPEAR adoption. Requirements describing behavior that predates adoption are baseline requirements and do not claim historical red/green evidence.

### REQ-001 - Preserve existing one-versus-one behavior

WHEN two individual players complete the existing challenge flow THE SYSTEM SHALL preserve the current one-versus-one rules, safety, persistence, statistics, spoils, spectator, and recovery behavior.

### REQ-002 - Equal competitive teams

IF a proposed match has empty, duplicate, oversized, overlapping, or unequal rosters THEN THE SYSTEM SHALL reject the match before arena preparation or economic mutation.

### REQ-003 - Duel Party ownership

THE SYSTEM SHALL provide lightweight Duel Parties with one leader, one to three unique members, and no dependency on guild membership.

### REQ-004 - Controlled party membership

WHEN a Duel Party leader invites, removes, or transfers leadership THE SYSTEM SHALL enforce leader authority, membership uniqueness, the three-player limit, and explicit invite acceptance.

### REQ-005 - Roster locking

WHILE a Duel Party participates in a pending or accepted challenge THE SYSTEM SHALL prevent joins, leaves, removals, disbanding, and leadership changes until that challenge terminates.

### REQ-006 - Challenge contracts

WHEN a leader challenges another equally sized Duel Party THE SYSTEM SHALL snapshot both rosters and selected rules into a separately expiring challenge contract.

### REQ-007 - Unanimous acceptance

WHEN a Duel Party challenge is pending THE SYSTEM SHALL start no match until every snapshotted participant has accepted and all start-time safety checks still pass.

### REQ-008 - Party commands

THE SYSTEM SHALL expose create, invite, accept, inspect, leave, kick, and disband operations through the `/duel party` command hierarchy with permission-aware help and tab completion.

### REQ-009 - Team-aware match execution

WHEN a valid one-versus-one, two-versus-two, or three-versus-three challenge becomes ready THE SYSTEM SHALL prepare every participant, assign a team spawn, prevent teammate damage, and declare victory only when an opposing team is fully eliminated.

### REQ-010 - Disconnect and interruption safety

IF a participant disconnects or the server interrupts a party match THEN THE SYSTEM SHALL apply the configured forfeit or recovery policy without duplicating rewards, losing archived loadouts, or recording an invented result.

### REQ-011 - Team-aware outcomes

WHEN a party match ends THE SYSTEM SHALL restore all participants and apply wagers, spoils, statistics, analytics, announcements, and cleanup according to an explicit team policy.

### REQ-012 - Guild separation

THE SYSTEM SHALL keep guild membership, champion appointments, wars, settlements, and war resolution outside the Duel Party core until a separately approved integration requirement exists.

### REQ-013 - Architectural isolation

THE SYSTEM SHALL keep new party, challenge, and match rules independent of Bukkit and Paper while adapters translate commands, events, persistence, and server state.

### REQ-014 - SPEAR workflow

WHEN WarzoneDuels behavior changes THE SYSTEM SHALL maintain EARS requirements, linked tasks, red and green evidence for new behavior, architectural review, and clean-build verification before producing a test artifact.

### REQ-015 - Configurable duel duration

WHEN a duel is released THE SYSTEM SHALL leave its duration unlimited when configured to zero and otherwise end it as a draw after the configured number of seconds.

### REQ-016 - Explosive combat attribution

WHEN a duel participant causes crystal, respawn-anchor, or explosive-minecart damage THE SYSTEM SHALL attribute that damage to the participant, prevent damage to their teammates, and use the attributed opposing participant for spoils selection.

### REQ-017 - Simultaneous team elimination

IF both duel teams become fully eliminated during the same server tick THEN THE SYSTEM SHALL conclude the match as a draw without awarding spoils and restore every simultaneously defeated participant's archived pre-duel loadout after respawn.

### REQ-018 - Paper 26 platform baseline

THE SYSTEM SHALL build and run on Java 25 against stable Paper 26.2 while providing a pinned Paper 26.3 compatibility verification profile.

### REQ-019 - Party playtest regressions

WHEN a duel starts THE SYSTEM SHALL send every participant to the correct team spawn before the opening countdown and identify the entire winning party in the victory announcement.

### REQ-020 - Leader departure

WHEN an unlocked Duel Party leader leaves the party or disconnects THE SYSTEM SHALL disband that party and remove its memberships and invitations.

### REQ-021 - Vanilla explosive self-damage

WHEN a participant damages themselves with an explosive THE SYSTEM SHALL allow self-damage while continuing to prevent explosive damage to other members of their team.

### REQ-022 - Atomic analytics records

WHEN a duel result is persisted THE SYSTEM SHALL store its parent and participant records atomically, roll back failed inserts, and preserve any caller-owned transaction and connection mode.

IF analytics rollback fails THEN THE SYSTEM SHALL discard the uncertain connection without restoring auto-commit or reusing it for later writes.

### REQ-023 - Expired roster reuse

WHEN a leader creates a challenge after an earlier challenge expires THE SYSTEM SHALL release expired roster locks before checking availability without requiring an intermediate lookup.

### REQ-024 - Match type consistency

IF a match contains multiple participants per team and its type is not PARTY THEN THE SYSTEM SHALL reject it while preserving valid singleton matches.

### REQ-025 - Reliable SPEAR validation and state

WHEN SPEAR tooling validates requirements or changes phase THE SYSTEM SHALL reject missing requirement clauses and malformed state, replace state through a same-directory temporary file, and preserve the prior state if replacement fails.

WHEN SPEAR tooling is imported without a CLI argument THE SYSTEM SHALL expose its validator without executing the CLI, and SHALL reject test records whose file, name or status is missing or invalid without changing stored state.

### REQ-026 - Advancement evidence counters

WHEN a valid duel challenge is sent, captured spoils are withdrawn, all surviving participants agree to a draw, or a duel winner satisfies an approved ruleset or low-health condition THE SYSTEM SHALL persist the corresponding per-player advancement evidence counter in stats.yml without changing ordinary match statistics.

### REQ-027 - Advancement evidence semantics

WHEN a one-versus-one challenger wins with a non-default ruleset THE SYSTEM SHALL record challenger-custom-rules evidence, WHEN a player wins with Ender Pearls and Wind Charges both disabled THE SYSTEM SHALL record restricted-mobility evidence, and WHEN a one-versus-one kill winner has less than four health points before post-duel healing THE SYSTEM SHALL record low-health evidence.

### REQ-028 - Advancement integration boundary

THE SYSTEM SHALL expose only durable WarzoneDuels evidence needed by the advancement consumer and SHALL NOT implement guild-war achievements, spectator-betting achievements, or reward payouts as part of this slice.

### REQ-029 - Persistent duel cooldowns and repeat-opponent prevention

WHEN a duel concludes normally THE SYSTEM SHALL persist the completion time of every participant and every cross-team UUID pair before recording advancement-bearing match statistics.

WHILE any participant has a remaining duel cooldown or any cross-team pair has a remaining repeat-opponent cooldown THE SYSTEM SHALL reject challenges, acceptance, and queued or immediate match starts with the affected players and remaining wait.

THE SYSTEM SHALL default settings.duel-cooldown-seconds to 300 and settings.repeat-opponent-cooldown-seconds to 86400, allow either setting to be disabled independently with zero, and apply configuration reloads without clearing history or interrupting active duels.

IF cooldown history cannot be read or written safely THEN THE SYSTEM SHALL refuse new duels and withhold new match-result and challenge-sent advancement evidence rather than reset protection.

WHEN a valid challenge is sent THE SYSTEM SHALL credit challenge-sent advancement evidence at most once per unordered cross-team UUID pair within the configured repeat-opponent window, including challenges that expire or are declined.

THE SYSTEM SHALL preserve existing statistics and earned advancements, exclude cancelled requests and interrupted server-shutdown matches from completed-duel cooldowns, and prevent relogging, party recreation, leader changes, or swapped challenger roles from clearing recent history.
