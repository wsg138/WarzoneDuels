# WarzoneDuels

[![Codacy Badge](https://app.codacy.com/project/badge/Grade/19df76ce35864dccb2f4533071af33e5)](https://app.codacy.com/gh/wsg138/WarzoneDuels/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)

Death duel plugin with arena setup, loadout restore, vault rewards, stats, and optional server integrations.

## Build

```powershell
mvn -q -DskipTests package
```

## Permissions

All non-administrative permissions default to `false`. Grant `warzoneduels.command` for ordinary duel commands, `warzoneduels.spectate` for controlled watch mode, or `warzoneduels.admin` for every command, setup action, and administrative bypass. Individual permissions are declared in `plugin.yml` under those three parents for LuckPerms assignment.

The older `warzoneduels.user`, command leaf, and bypass nodes remain as deprecated compatibility aliases. New permission assignments should use the current parent hierarchy.
