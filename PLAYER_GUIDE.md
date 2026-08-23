# WarzoneDuels — SMP Player Guide

This file documents the current player-facing Death Duel system on Enthusia SMP. The values below were checked against the live production configuration and current source on August 22, 2026.

## What a Death Duel is

WarzoneDuels runs consensual 1v1 fights in Enthusia's dedicated duel arena. A challenger chooses the rules before sending the request, and the challenged player gets a full review screen before accepting.

These are **not keep-inventory duels**. The loser risks the items they bring into the fight, and an optional wager can put additional currency at stake.

The server currently supports **one active duel at a time** in the global arena.

## Starting a duel

```text
/duel <player>
```

opens the duel builder against that player.

Both players must still satisfy the start checks when the request is sent/accepted:

- both must be online;
- neither can already be busy with another duel flow;
- neither can be combat-tagged;
- both must be inside the configured spawn/Warzone matchmaking area;
- the two players cannot be on the same IP under the current production setting;
- neither player can have unclaimed victor's spoils waiting in `/vault`;
- any wager must be affordable by both players;
- the selected arena map must have a saved terrain snapshot before the fight can begin.

A pending request lasts **120 seconds**.

The recipient can use:

```text
/duel review
/duel accept
/duel deny
```

Accepting opens/requires the request review rather than silently starting a fight with rules the recipient never saw.

## Duel setup flow

The challenger can configure the following before sending the request.

### 1. Arena map

The live configuration defines:

- **Flat Arena** — the default classic flat colosseum floor;
- **Forest** — configured as a terrain-oriented option;
- **Desert** — configured as a terrain-oriented option.

A configured option is not enough by itself: the duel startup path also requires a saved server-side terrain snapshot for the selected map. The production `config.yml` still labels Forest and Desert as future/placeholder terrain entries, so **Flat Arena is the only map that should be treated as confirmed public-ready from repository/config evidence alone**. Forest/Desert should not be advertised on the wiki until their saved production snapshots are independently confirmed.

### 2. Building rules

The challenge can choose one of three formats:

#### No Building or Breaking

No blocks may be placed or broken during the duel.

#### Limited Placement

Original arena terrain remains protected, but blocks placed during the duel may be broken.

Two limited-placement styles exist:

- **Utilities Only** — Cobwebs, Water Buckets, buttons, and pressure plates;
- **All Placeable Blocks** — normal block placement is allowed while original map blocks remain protected.

Utilities-only fights do not offer explosive configuration.

#### Place & Break

Allows full placement and original-terrain breaking, but only on a map that explicitly supports terrain breaking.

The default Flat Arena does **not** support original-terrain breaking.

### 3. Explosive rules

When the chosen build/map format supports explosives, the challenger can independently toggle:

- End Crystals + Respawn Anchors;
- TNT Minecarts;
- TNT / other explosive blocks.

Crystals/anchors can be allowed in supported protected-terrain formats. TNT Minecarts and ordinary TNT require the full Place & Break format.

Explosion handling is contained to the managed arena terrain and the arena is restored after the match rather than leaving permanent damage.

### 4. Combat-item rules

The challenger can independently allow/block:

- Ender Pearls;
- Wind Charges;
- Maces;
- Chorus Fruit;
- Spears;
- Elytras;
- Ender Chests.

Ender Pearls and Wind Charges also have optional duel-specific cooldowns. Right-clicking their rule cycles through:

```text
0s → 5s → 10s → 15s → 30s → 0s
```

A disabled item is blocked outright; an enabled Pearl/Wind Charge with a configured cooldown uses the corresponding Minecraft item cooldown during the duel.

Chorus Fruit is constrained to the playable arena. If its normal result would leave the managed arena terrain, the plugin attempts a playable fallback destination instead of allowing the player to escape the duel.

### 5. Wager

Wagers are enabled in production.

Each player can risk up to **100,000** units of the Vault economy. The builder shows the amount **per player** while the final review screen shows the combined pot.

For example, if the wager is 500:

- challenger stakes 500;
- recipient stakes 500;
- winner receives the 1,000 pot.

Both stakes are withdrawn before the duel begins. If the duel becomes a draw or is safely cancelled under a refund path, each player's held stake is returned.

The current duel UI labels wagers with `$`, even though Enthusia's main physical economy is Raw Gold-backed through EnthusiaCurrency.

## Fight start

Before the duel begins, both players are moved into the prepared arena and normalized for the fight:

- health restored to full;
- hunger and saturation restored;
- fire, freeze, arrows-in-body, fall distance, and active potion effects cleared;
- game mode set to Survival;
- external combat state cleared where supported.

The current code defaults to a **5-second start countdown** because production does not override that setting. During the countdown, movement, building, and combat are restricted until the fight releases.

## During the duel

The duel is tightly isolated from normal server activity:

- participant chat is disabled;
- almost all commands are blocked;
- participants cannot teleport out;
- participants cannot leave the arena bounds;
- outsiders cannot interfere with the duel's PvP;
- unauthorized players cannot enter the protected fighting footprint;
- environment/arena damage is contained and later reset.

The allowed participant commands are currently limited to duel control/info operations such as:

```text
/duel draw
/duel surrender
/duel cancel
/duel info
/duel settings
```

`/duel settings` / `/duel info` lets a participant inspect the active rules.

## Death and victor's spoils

A normal duel ends when one participant dies.

The loser's normal death drops are **not thrown onto the arena floor**. Instead they are captured into a durable **victor's spoils** vault belonging to the winner.

The winner can open it with:

```text
/vault
/duel vault
/spoils
/claimspoils
/duelvault
```

The vault can claim individual items or all items that fit. If an item will not fit in the winner's inventory, it remains in the vault rather than being deleted.

Unclaimed spoils expire after **24 hours**. A player with unclaimed spoils is blocked from starting or accepting another duel until those spoils are dealt with.

The plugin intentionally archives each participant's pre-duel loadout for staff recovery, but a normal loss does **not** automatically restore the loser's pre-duel gear. The archived copy is a safety/recovery mechanism, not keep-inventory gameplay.

### Experience on death

Current code clears the duel death's dropped XP (`0`) and does not set keep-level for an ordinary duel death. As implemented, this means a normal duel loser can lose their carried experience levels without dropping that XP for the winner to collect. This is current runtime behavior and should be treated as such unless the duel design is changed.

## Disconnecting during a duel

Disconnecting does not safely evade a loss.

The current grace period is **30 seconds**.

If the player rejoins within that window, their duel continues and they are returned to their assigned arena spawn.

If they do not return before the deadline:

- they forfeit;
- the opponent wins;
- the winner's spoils are created from the disconnecting player's saved loadout;
- the disconnecting player's inventory/armor/offhand are cleared when they next join;
- the plugin then forces the pending death/respawn flow so the disconnect cannot preserve the items that were put at risk.

## Draws and `/surrender`

```text
/duel draw
/draw
/surrender
```

currently all use the same **mutual draw request** behavior.

One player requests a draw; the duel ends as a draw only after **both players** have requested it. A draw refunds held wagers.

Important: despite its name, **`/surrender` does not currently concede/forfeit the duel**. It is an alias for requesting a draw. Likewise `/duel cancel` routes to the same draw/cancel handler: before a queued duel starts it can cancel that queue; during an active duel it participates in the mutual draw flow.

## Victory moment

For a normal kill win, the current code defaults to a short **6-second victory moment** because production does not override the setting. The winner is healed, receives the victory presentation/fireworks, and is then returned to the normal exit/spawn flow.

Victory fireworks are cosmetic and are prevented from damaging players.

## Watching a duel

Where the spectator permission is granted, players can use:

```text
/duel watch
/duel spectate
/duel stands
```

and leave with:

```text
/duel leave
/duel unwatch
```

When a duel begins, eligible non-participants can receive a clickable watch prompt.

This is a controlled **pseudo-spectator** mode rather than ordinary Minecraft Spectator mode. The plugin:

- safely stores the viewer's current state;
- moves them to the configured stands;
- uses Adventure mode with flight;
- empties/isolates their temporary watcher inventory;
- prevents interaction, item pickup, combat, teleports, and most commands;
- hides the watcher from the duel participants;
- confines the watcher around the stands (default 40-block horizontal / 25-block vertical boundary);
- restores the saved state when they leave or the duel ends;
- persists enough recovery data to restore interrupted watcher sessions after reconnect/reload.

Because `warzoneduels.spectate` defaults to false in `plugin.yml`, whether ordinary SMP ranks currently receive this permission should be confirmed from LuckPerms before the wiki advertises spectating to everyone.

## Stats

```text
/stats
/stats <player>
/duel stats
```

can show duel statistics where the corresponding permission is granted. The profile tracks:

- matches played;
- wins;
- losses;
- draws;
- current win streak;
- best win streak;
- disconnect-forfeit losses;
- win/loss ratio.

The GUI can also show other stored player profiles/leaderboard data when the viewer has the `stats.others` permission.

## Arena reset and map safety

The fighting area is backed by an exact managed arena footprint and terrain snapshots.

After a match, the plugin can:

- remove duel-placed blocks;
- clear fluids;
- clear non-player entities;
- restore the managed terrain/default map.

Original arena block drops are suppressed so a terrain-enabled duel cannot be used as a free resource farm.

The default map is also configured to restore when the server starts without a duel to resume.

## Current production summary

| Rule | Enthusia SMP |
| --- | --- |
| Active duels at once | 1 |
| Request lifetime | 120 seconds |
| Disconnect grace | 30 seconds |
| Maximum wager per player | 100,000 |
| Same-IP duels | Blocked |
| Wagers | Enabled |
| Spoils lifetime | 24 hours |
| Start countdown | 5 seconds (code default) |
| Kill victory moment | 6 seconds (code default) |
| Default map | Flat Arena |
| Flat Arena original terrain breaking | Disabled |
| Flat Arena limited placement | Supported |
| Combat chat | Disabled |
| Leaving/teleporting out | Blocked |

## Player commands

Depending on assigned permissions:

```text
/duel <player>
/duel review
/duel accept
/duel deny
/duel info
/duel settings
/duel draw
/duel surrender
/duel cancel
/duel vault
/duel stats [player]
/duel watch
/duel leave
/surrender
/draw
/vault
/stats [player]
```

Administrative arena setup, map snapshot, loadout recovery, watcher recovery, reload, and bypass commands are intentionally omitted from this player guide.

## Items to verify before public wiki publication

Repository + production-config evidence leaves two deployment-level questions that should be confirmed rather than assumed:

1. **Forest/Desert maps:** the config lists them but still labels their terrain hookup as future/placeholder, while current code requires a saved terrain snapshot to start them. Confirm their production snapshots before listing them as playable maps.
2. **Public spectating/stats-other permissions:** the plugin supports them, but their permission nodes default to false. Confirm current LuckPerms grants before describing them as universally available player commands.
