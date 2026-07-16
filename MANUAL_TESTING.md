# WarzoneDuels Manual Test Plan

Run these checks on a non-production Leaf/Fuji server with disposable player data first. Keep a copy of the world, `plugins/WarzoneDuels`, and each test player's data file before crash tests.

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
