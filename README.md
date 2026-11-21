# WarzoneDuels

Death-duel plugin for Enthusia SMP with configurable arena rules, non-keep-inventory victor's spoils, optional Vault wagers, durable reconnect/recovery behavior, spectator/watch mode, terrain snapshots, and duel statistics.

For the current **player-facing Enthusia SMP behavior**—challenge setup, building/explosive/item rules, wagers, spoils, disconnect forfeits, draw behavior, spectating, and stats—see **[`PLAYER_GUIDE.md`](PLAYER_GUIDE.md)**.

The player guide is the preferred source for future public-wiki work. It also identifies deployment-level features that must be confirmed before advertising them, such as Forest/Desert terrain snapshots and ordinary-player spectator permissions.

## Build

```powershell
mvn -q -DskipTests package
```

For the optional EnthusiaTeleport and EnthusiaTags service integrations, install those two sibling projects into the local Maven repository first:

```powershell
Push-Location ..\EnthusiaTeleport; mvn -q -DskipTests install; Pop-Location
Push-Location ..\EnthusiaTags; mvn -q -DskipTests install; Pop-Location
```

## TNT carts in WorldGuard arenas

When an outer region denies vehicle placement, set `worldguard.allow-duel-tnt-carts` to `true` in WarzoneDuels configuration to honor the duel's **Explosive Minecarts** selection. The integration is off by default and is registered at plugin startup. The arena region must explicitly allow TNT and permit block placement for the player. Keep the existing vehicle placement flags.

The exception applies when a surviving participant right-clicks a rail in the arena footprint during an active Place & Break duel. Countdown, spectators, ordinary vehicles and disabled carts keep their existing rules. See the [Paper placement regression](qa/cart-placement/README.md).

## Permissions

All non-administrative permissions default to `false`. Grant `warzoneduels.command` for ordinary duel commands, `warzoneduels.spectate` for controlled watch mode, or `warzoneduels.admin` for every command, setup action, and administrative bypass. Individual permissions are declared in `plugin.yml` under those three parents for LuckPerms assignment.

The older `warzoneduels.user`, command leaf, and bypass nodes remain as deprecated compatibility aliases. New permission assignments should use the current parent hierarchy.
