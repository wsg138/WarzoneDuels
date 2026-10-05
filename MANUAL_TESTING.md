# WarzoneDuels Manual Test Plan

## Admin mode controls (1.0.4)

- As an operator and from the console, use `/duel mode`, `/duel mode 2v2 disable`, and `/duel mode 3v3 disable`; confirm ordinary players cannot use the control and tab completion respects permissions.
- Check disabled modes reject new party challenges, while 1v1 and party membership continue to work. Re-enable only one mode and verify it works independently.
- Send a party challenge, disable its mode before the final acceptance, and confirm accepting cancels it and releases both rosters. Re-enable and send a fresh challenge.
- Disable a mode while its match is preparing or fighting; confirm that match finishes normally and new challenges remain blocked.
- Confirm saved values survive `/duel reload` and restart. Edit each config toggle manually and reload to verify enforcement.

Run these checks on a non-production Leaf/Fuji server with disposable player data first. Keep a copy of the world, `plugins/WarzoneDuels`, and each test player's data file before crash tests.

## Platform matrix

For the 1.0.2 playtest regression pass, verify both teams appear on their configured sides, receive the five-second opening countdown, and cannot take damage during it. Test self-inflicted crystal, anchor, and TNT-minecart damage separately from teammate damage. A party win must list every winning roster member. Leader `/duel party leave` and leader logout must disband an unlocked party and invalidate invitations; a pending challenge must still enforce roster locks until canceled.

Use Java 25 for every test. Run the complete checklist first on stable Paper 26.2, then on the intended Paper 26.3 build. Confirm the server reaches `Done (...)!`, WarzoneDuels enables without linkage errors, and Vault, Plan, CombatLogX, EnthusiaTeleport, EnthusiaTags, NotBounties, and the arena reset path remain healthy. The packaged plugin declares `api-version: 26.2`; it is not intended for Paper 1.21.11 or a Java 21 runtime after this migration.

## Permission setup

All command and spectator permissions default to `false` while the feature is staged. When the feature is ready for general use, grant both `warzoneduels.command` and `warzoneduels.spectate` through the server permission manager; do not change individual plugin defaults unless unrestricted access is intended.

Test with separate accounts or temporary groups:

- No permissions: `/duel`, `/draw`, `/vault`, and `/stats` expose no usable features or inaccessible tab completions.
- Only `warzoneduels.command.challenge`: can run `/duel <player>` and complete/send the GUI, but cannot accept, inspect stats, or watch.
- Only `warzoneduels.spectate`: can run `/duel watch` during an active duel, but cannot create a challenge.
- Only `warzoneduels.command.stats`: `/stats` works; `/stats <other>` and leaderboard profile navigation do not.
- Only `warzoneduels.command.stats.others`: `/stats <other>` and target completions work; personal `/stats` still requires `warzoneduels.command.stats`.
- Grant each individual administrative node without `warzoneduels.admin`; verify only its exact subcommand and completion appears.
- Verify `warzoneduels.admin` grants command, spectator, administrative, build-bypass, arena-entry, and combat-entry behavior.
- Verify legacy `warzoneduels.bypass.enter` grants both `warzoneduels.admin.bypass.arena` and `warzoneduels.admin.bypass.combat` behavior.
- Remove a permission while its GUI is open; confirm the final click is denied and no duel, acceptance, vault claim, or stats navigation occurs.
- Remove `warzoneduels.spectate` and `warzoneduels.spectate.leave` from an active watcher; `/duel leave` and `/duel unwatch` must still restore the player.

## External teleport and display integrations

Use a watcher W, another player P, and both duel participants.

- Have W send `/tpahere P`, enter `/duel spectate`, then run `/tpaccept` as P. Confirm the request is gone and P never enters the arena or spectator boundary.
- Repeat with P sending `/tpa W`, then with an accepted request already in its warmup before W watches. Confirm request and warmup cancellation messages reach affected players.
- Call `Player#teleport` for P directly to W's watcher location. Confirm it is cancelled with the active-duel-area message; a destination immediately outside both the arena and spectator boundary must succeed.
- Confirm typed WarzoneDuels participant, watcher-entry, boundary-return, recovery, and exit teleports still succeed.
- With EnthusiaTags installed, confirm W's TextDisplay tag disappears on entry, remains absent through a tag refresh/reload, and returns on leave, duel end, disconnect recovery, and failed watcher entry. Confirm selected tag data is unchanged.
- With the verified NotBounties version installed and wanted tags enabled, confirm W's bounty tag disappears on entry and returns on restoration without changing the bounty amount. Confirm nonparticipants can still see W's body while duelists cannot see W or either external display.

## Watch mode entry and visibility

Use four players: duel participants A and B, watcher W, and ordinary player O.

1. Start a duel between A and B, then run `/duel watch` as W.
2. Confirm W is in Adventure mode and never enters Spectator mode.
3. Confirm W has flight allowed, is flying, has collision disabled, cannot pick up items, and has empty storage, armor, offhand, and cursor.
4. Confirm A and B cannot see W.
5. Confirm O can see W normally and W can see A and B.
6. Reconnect A; W must be hidden from A again immediately.
7. Reconnect O; O must still see W.
8. Add a second watcher and repeat visibility checks in both directions.

## Non-interference

As W, verify all of the following are blocked without repeated message spam:

- Break/place blocks; fill/empty buckets.
- Right-click blocks and entities; trigger pressure plates, farmland, buttons, doors, and tripwire.
- Manipulate armor stands, leash, shear, fish, or mount/control vehicles.
- Attack players, mobs, crystals, or other entities directly or with projectiles.
- Launch arrows, pearls, potions, lingering potions, wind charges, or other projectiles.
- Drop/pick up items, pick up experience, click/drag inventories, swap hands, or equip armor.
- Receive environmental, melee, projectile, or explosion damage.
- Become a mob target.
- Enter nether portals, end portals, or gateways.
- Change game mode or disable required flight.

Fire arrows and other projectiles through W toward a target. Confirm the projectile does not stop, redirect, damage W, or lose its intended hit because of W. Repeat with melee targeting and entity movement through W.

## Commands, teleports, and boundary

- Verify only `/duel leave`, `/duel unwatch`, `/duel watch`, `/duel info`, `/duel settings`, and explicitly configured safe watcher commands pass the watcher command filter.
- Verify `/tpa`, `/tpahere`, `/tpaccept`, `/home`, `/spawn`, `/back`, and staff teleport commands are blocked while W remains a watcher.
- Start a teleport warmup before `/duel watch`; confirm its delayed teleport is cancelled.
- Invoke `Player#teleport` from another plugin; confirm W does not leave the configured spectator boundary.
- Attempt pearls, chorus fruit, portals, gateways, vehicles, and plugin teleports at the boundary.
- Fly outside the horizontal and vertical limits; W must be returned to `arena.spectator` on the next enforcement tick with cleared velocity and a cooldown-limited message.
- Confirm no bypass permission permits an active watcher to escape containment.

## Exact restoration

Before each test, give W a distinctive inventory, armor, offhand, cursor item, potion effects, health, hunger, saturation, experience, fire ticks, game mode, flight flags/speeds, walk speed, collision setting, pickup setting, velocity, and location. Capture screenshots or command output.

Verify exact, single restoration after each path:

- `/duel leave`, `/duel unwatch`, and `/duel watch` toggle.
- Duel draw, kill, disconnect timeout, cancellation, and any duel start/terrain failure.
- Watcher quit and kick.
- Plugin reload and plugin disable.
- Clean server shutdown.
- `/duel recoverwatcher <player>`.

The normal policy returns W to the captured original location. If that location is inside the arena, W returns to the resolved server spawn or configured arena exit.

After every exit, verify W is visible to all players, inventory is replaced rather than appended, flight/collision/pickup match the snapshot, and no file remains under `plugins/WarzoneDuels/spectator-sessions` after a successful player save.

## Crash recovery

Perform process-kill tests only with backups and disposable player data.

- Kill immediately after the `PREPARED` file appears but before observable mutation; restart and join W.
- Kill immediately after inventory clear/Adventure flight begins; restart and join W.
- Kill while the session file says `ACTIVE`; restart and join W.
- Kill during `RESTORING`, including after inventory replacement but before session deletion; restart and join W twice to confirm no duplication.
- Keep W offline through restart; confirm the independent session file remains until W joins and restoration succeeds.
- Corrupt one session file; confirm other watcher files still recover independently and the corrupt file is retained with a severe UUID/name log.
- Remove a session file while leaving the player recovery marker. On join, confirm inventory is not cleared or replaced, visibility/collision/pickup are normalized, unauthorized flight is disabled, the player leaves the arena, and a severe log identifies the missing record.
- After every recovery case, have all test players reconnect and confirm nobody remains hidden by WarzoneDuels.

## Duel Parties and team matches

Use six disposable test accounts so both 2v2 and 3v3 can be exercised.

1. Create parties with `/duel party create`, invite with `/duel party invite <player>`, and join with `/duel party accept <leader>`.
2. Verify `/duel party info`, `leave`, `kick`, `transfer`, and `disband`, including the three-player cap and one-party-per-player rule.
3. Have one party leader run `/duel <other-leader>` and configure the contract. Confirm unequal party sizes are rejected and party wagers are rejected.
4. Confirm every snapshotted participant receives the challenge, `/duel accept` opens its review, and the match does not prepare until all players accept.
5. While acceptance is pending, verify join, leave, kick, transfer, and disband are rejected for both locked rosters. Confirm decline, expiry, logout, and a failed start release both locks.
6. Verify all four/six inventories are archived, every player receives a distinct configured team spawn, and every player remains frozen through the countdown.
7. Attack a teammate directly and with projectiles/explosives; friendly-fire damage must be cancelled. Opponents must remain damageable.
8. Eliminate one player from each team. The match must continue, eliminated players must respawn at the exit, and they must not re-enter after reconnecting.
9. Eliminate the final player on one team. Confirm all winners receive one win, all losers one loss, every surviving winner is healed, all players exit, spectators restore, and the arena resets once.
10. Repeat with a disconnect: reconnect within the grace period, then test timeout. A timeout eliminates only that player while teammates remain; the final team elimination ends the match.
11. Verify each defeated loadout creates exactly one spoils entry for the valid opposing killer, or the deterministic surviving opponent when no killer is available. A teammate must never receive it.
12. Have all surviving players request a draw. The match must remain active until every survivor has requested it.
13. Stop the server during a team match. Restart and verify every participant is recovered without a fabricated winner, duplicated stats, duplicated spoils, or lost archived loadouts.
14. Enable crystals/anchors and explosive minecarts. For each explosive type, verify enemy damage works, teammate damage is cancelled, and a defeated enemy's spoils are assigned to the attributed opposing player or the documented surviving-opponent fallback.
15. Kill the final living member of each team with the same explosion. Verify the result is a draw, no victor's-spoils entries are created, and both simultaneously defeated players receive their exact archived pre-duel loadouts after respawn. Repeat while restarting before one player respawns to verify the pending restore survives restart.

The additional arena spawn keys are `arena.team1-spawn2`, `arena.team1-spawn3`, `arena.team2-spawn2`, and `arena.team2-spawn3`. If omitted, deterministic offsets from the legacy `spawn1` and `spawn2` positions are used; configure explicit safe positions before production use.

## Optional duel duration

1. Leave `settings.duel-time-limit-seconds: 0`, start a duel, and confirm it remains active beyond several minutes until a death, disconnect forfeit, or unanimous draw.
2. Set a short positive value such as `30`, restart or reload before starting the next duel, and confirm the duration begins only after the opening countdown releases combat.
3. Let the limit expire in both a 1v1 and a party match. Confirm each ends as a draw, held 1v1 wagers refund, statistics record a draw for every participant, spectators restore, and arena cleanup runs once.
4. End a limited duel by death before expiry, then wait beyond the former deadline. Confirm no delayed second conclusion, broadcast, stat update, payout, or cleanup occurs.
5. Reload the plugin during a limited duel. Confirm the persisted deadline resumes with its remaining time rather than granting a new full duration.

## Duel anti-farming cooldowns (1.0.5)

Use disposable accounts on staging. Temporarily set `settings.duel-cooldown-seconds: 10` and `settings.repeat-opponent-cooldown-seconds: 30`, then `/duel reload`. Revert to the intended values after testing.

1. Finish a 1v1 kill, mutual draw and disconnect forfeit. Verify both players cannot start another duel for ten seconds, even against a different opponent. Check that the refusal identifies the player and remaining seconds.
2. After ten seconds, face a new opponent successfully; attempting the original opponent must remain blocked until thirty seconds after the prior duel ended. Swap challenger/recipient to verify the same wait applies.
3. Repeat in 2v2/3v3. Put former non-leaders on newly created teams with new leaders. Any cross-team player pair from the previous match must prevent the rematch; unrelated teammates are not opposing pairs.
4. Relog and restart the staging server between attempts. Inspect `plugins/WarzoneDuels/duel-cooldowns.yml`; waits must persist. Do not delete this file as part of routine upgrades or reloads.
5. Disable each setting independently with zero and reload. Confirm only the selected guard is disabled, previously recorded history survives re-enabling, and an already-active fight is unaffected. The separate duel-duration setting stays zero/unlimited.
6. Send valid requests, then decline/expire and resend to the same player. In stats.yml, `advancements.challenges-sent` must increase only once during the repeat window, including after reversing leaders or restarting. The request itself remains allowed if no completed-duel cooldown is active.
7. Increase the cooldown settings while a contract is pending or its arena is preparing. Acceptance/final start must recheck history and reject blocked participants; party cancellation must unlock both rosters. No entry teleport or wager payout should occur for a rejected start.
8. On a disposable copy, corrupt the history YAML or make its path unwritable. New duels must refuse to start and logs must identify the fault. Existing history must not be erased. Restore a backed-up valid history/permissions and re-enable the plugin before continuing. Never perform this fault injection on production.
9. Verify existing win/streak and special-condition advancements still advance on eligible matches with EnthusiaTags present. Existing earned advancements must remain; local Java/API tests do not replace this player-visible check.
10. Verify cancelled requests, administrative aborts and server-shutdown interruptions do not create completed-duel cooldowns or fabricate match results. Existing stats, spoils and inventory restoration must remain intact.

## Safe deployment order

1. Back up the plugin data directory, world, and player data.
2. Install the JAR with ordinary permissions still defaulting to `false`.
3. Start with no active duel; inspect startup logs for session or configuration errors.
4. Grant only the required individual nodes to test accounts.
5. Run permission and tab-completion checks.
6. Run entry, visibility, non-interference, external teleport, and boundary checks.
7. Run normal exit, disconnect, reload, and clean shutdown restoration checks.
8. Run crash recovery checks on a disposable copy of the server.
9. Grant `warzoneduels.command` and `warzoneduels.spectate` to the intended public group only after every live-server check passes.
