package dev.minecraft.warzoneduels.permission;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class PermissionPolicy {
    public static final String USER = "warzoneduels.user";
    public static final String ADMIN = "warzoneduels.admin";
    public static final String COMMAND_DUEL = "warzoneduels.command.duel";
    public static final String CHALLENGE = "warzoneduels.challenge";
    public static final String ACCEPT = "warzoneduels.accept";
    public static final String DENY = "warzoneduels.deny";
    public static final String REVIEW = "warzoneduels.review";
    public static final String SPECTATE = "warzoneduels.spectate";
    public static final String SPECTATE_LEAVE = "warzoneduels.spectate.leave";
    public static final String DRAW = "warzoneduels.draw";
    public static final String VAULT = "warzoneduels.vault";
    public static final String STATS_SELF = "warzoneduels.stats.self";
    public static final String STATS_OTHERS = "warzoneduels.stats.others";
    public static final String INFO = "warzoneduels.info";
    public static final String ADMIN_RELOAD = "warzoneduels.admin.reload";
    public static final String ADMIN_RESTORE_LOADOUT = "warzoneduels.admin.restoreloadout";
    public static final String ADMIN_MAP_SAVE = "warzoneduels.admin.map.save";
    public static final String ADMIN_MAP_LOAD = "warzoneduels.admin.map.load";
    public static final String ADMIN_MAP_STATUS = "warzoneduels.admin.map.status";
    public static final String ADMIN_ARENA_SET_POS = "warzoneduels.admin.arena.setpos";
    public static final String ADMIN_ARENA_SET_SPAWN = "warzoneduels.admin.arena.setspawn";
    public static final String ADMIN_ARENA_SET_SPECTATOR = "warzoneduels.admin.arena.setspectator";
    public static final String ADMIN_ARENA_SET_EXIT = "warzoneduels.admin.arena.setexit";
    public static final String ADMIN_RECOVER_WATCHER = "warzoneduels.admin.recoverwatcher";
    public static final String BYPASS_BUILD = "warzoneduels.bypass.build";
    public static final String BYPASS_ARENA_ENTRY = "warzoneduels.bypass.arena-entry";
    public static final String BYPASS_COMBAT_ENTRY = "warzoneduels.bypass.combat-entry";
    public static final String BYPASS_ENTER_LEGACY = "warzoneduels.bypass.enter";

    private static final Map<String, String> SUBCOMMAND_PERMISSIONS = buildSubcommandPermissions();
    private static final List<String> ROOT_ORDER = List.of(
        "accept", "deny", "review", "watch", "spectate", "stands", "leave", "unwatch", "draw", "surrender", "cancel",
        "vault", "stats", "info", "settings", "mapsave", "mapload", "mapstatus", "reload", "restoreloadout",
        "recoverwatcher", "setpos1", "setpos2", "setspawn1", "setspawn2", "setspectator", "setexit"
    );

    private PermissionPolicy() {
    }

    public static String permissionForSubcommand(String subcommand) {
        return SUBCOMMAND_PERMISSIONS.get(subcommand == null ? "" : subcommand.toLowerCase(java.util.Locale.ROOT));
    }

    public static List<String> visibleRootSuggestions(Predicate<String> hasPermission, boolean activeWatcher) {
        return ROOT_ORDER.stream()
            .filter(option -> activeWatcher && (option.equals("leave") || option.equals("unwatch") || option.equals("watch"))
                || hasPermission.test(permissionForSubcommand(option)))
            .toList();
    }

    public static Map<String, String> subcommandPermissions() {
        return SUBCOMMAND_PERMISSIONS;
    }

    private static Map<String, String> buildSubcommandPermissions() {
        Map<String, String> permissions = new LinkedHashMap<>();
        permissions.put("accept", ACCEPT);
        permissions.put("deny", DENY);
        permissions.put("review", REVIEW);
        permissions.put("watch", SPECTATE);
        permissions.put("spectate", SPECTATE);
        permissions.put("stands", SPECTATE);
        permissions.put("leave", SPECTATE_LEAVE);
        permissions.put("unwatch", SPECTATE_LEAVE);
        permissions.put("draw", DRAW);
        permissions.put("surrender", DRAW);
        permissions.put("cancel", DRAW);
        permissions.put("vault", VAULT);
        permissions.put("stats", STATS_SELF);
        permissions.put("info", INFO);
        permissions.put("settings", INFO);
        permissions.put("reload", ADMIN_RELOAD);
        permissions.put("restoreloadout", ADMIN_RESTORE_LOADOUT);
        permissions.put("mapsave", ADMIN_MAP_SAVE);
        permissions.put("mapload", ADMIN_MAP_LOAD);
        permissions.put("mapstatus", ADMIN_MAP_STATUS);
        permissions.put("setpos1", ADMIN_ARENA_SET_POS);
        permissions.put("setpos2", ADMIN_ARENA_SET_POS);
        permissions.put("setspawn1", ADMIN_ARENA_SET_SPAWN);
        permissions.put("setspawn2", ADMIN_ARENA_SET_SPAWN);
        permissions.put("setspectator", ADMIN_ARENA_SET_SPECTATOR);
        permissions.put("setexit", ADMIN_ARENA_SET_EXIT);
        permissions.put("recoverwatcher", ADMIN_RECOVER_WATCHER);
        return Map.copyOf(permissions);
    }
}
