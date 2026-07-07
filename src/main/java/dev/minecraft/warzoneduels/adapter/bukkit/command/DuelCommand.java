package dev.minecraft.warzoneduels.adapter.bukkit.command;

import dev.minecraft.warzoneduels.adapter.bukkit.spoils.SpoilsGuiFactory;
import dev.minecraft.warzoneduels.adapter.bukkit.gui.DuelGui;
import dev.minecraft.warzoneduels.app.DuelService;
import dev.minecraft.warzoneduels.app.SpoilsService;
import dev.minecraft.warzoneduels.domain.BuilderSession;
import dev.minecraft.warzoneduels.domain.spoils.SpoilsEntry;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DuelCommand implements CommandExecutor, TabCompleter {
    private static final String ADMIN_PERMISSION = "warzoneduels.admin";
    private static final String VAULT_PERMISSION = "warzoneduels.duel.vault";
    private static final String DRAW_COMMAND = "draw";
    private static final String SURRENDER_COMMAND = "surrender";
    private static final String RELOAD_COMMAND = "reload";
    private static final String RESTORE_LOADOUT_COMMAND = "restoreloadout";
    private static final String STATS_COMMAND = "stats";
    private static final String MAP_SAVE_COMMAND = "mapsave";
    private static final String MAP_LOAD_COMMAND = "mapload";
    private static final String TARGET_OFFLINE_MESSAGE = "messages.target-offline";
    private static final int ROOT_ARGUMENT_COUNT = 1;
    private static final int TWO_ARGUMENTS = 2;
    private static final int LOCATION_ARGUMENT_COUNT = 4;
    private static final List<String> DEFAULT_MAP_IDS = List.of("flat_arena", "forest", "desert");

    private final DuelService duelService;
    private final SpoilsService spoilsService;

    public DuelCommand(DuelService duelService, SpoilsService spoilsService) {
        this.duelService = duelService;
        this.spoilsService = spoilsService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (isDrawCommandAlias(command)) {
            duelService.requestDraw(player);
            return true;
        }
        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        handleSubcommand(player, args[0].toLowerCase(Locale.ROOT), args);
        return true;
    }

    private boolean isDrawCommandAlias(Command command) {
        String commandName = command.getName();
        return SURRENDER_COMMAND.equalsIgnoreCase(commandName) || DRAW_COMMAND.equalsIgnoreCase(commandName);
    }

    private void handleSubcommand(Player player, String sub, String[] args) {
        switch (sub) {
            case "accept" -> duelService.acceptRequest(player);
            case "deny" -> duelService.denyRequest(player);
            case DRAW_COMMAND, SURRENDER_COMMAND, "cancel" -> duelService.requestDraw(player);
            case "review" -> openPendingRequestReview(player);
            case "watch", "spectate", "stands" -> duelService.watchDuel(player);
            case "vault" -> openSpoils(player);
            case STATS_COMMAND -> player.performCommand(args.length >= TWO_ARGUMENTS ? STATS_COMMAND + " " + args[1] : STATS_COMMAND);
            case "info", "settings" -> duelService.showSettings(player);
            case RELOAD_COMMAND -> handleReload(player);
            case RESTORE_LOADOUT_COMMAND -> handleRestoreLoadout(player, args);
            case MAP_SAVE_COMMAND -> handleMapSave(player, args);
            case MAP_LOAD_COMMAND -> handleMapLoad(player, args);
            case "mapstatus" -> handleMapStatus(player);
            case "setpos1", "setpos2", "setspawn1", "setspawn2", "setspectator", "setspectatorlocation", "setexit" -> handleArenaLocation(player, sub, args);
            default -> handleTargetDuelStart(player, args);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == ROOT_ARGUMENT_COUNT) {
            addRootCompletions(sender, result, args[0].toLowerCase(Locale.ROOT));
            return result;
        }
        if (shouldCompleteOnlinePlayers(sender, args)) {
            addOnlinePlayerCompletions(sender, result, args[1].toLowerCase(Locale.ROOT));
            return result;
        }
        if (shouldCompleteMapIds(sender, args)) {
            addMatchingOptions(result, DEFAULT_MAP_IDS, args[1].toLowerCase(Locale.ROOT));
        }
        return result;
    }

    private void addRootCompletions(CommandSender sender, List<String> result, String typed) {
        addVisibleCommandSuggestions(sender, result, typed);
        addOnlinePlayerCompletions(sender, result, typed);
    }

    private void addVisibleCommandSuggestions(CommandSender sender, List<String> result, String typed) {
        boolean admin = sender.hasPermission(ADMIN_PERMISSION);
        for (String option : duelService.commandSuggestions()) {
            if (isHiddenAdminSuggestion(option, admin)) {
                continue;
            }
            if (matchesTyped(option, typed)) {
                result.add(option);
            }
        }
    }

    private boolean isHiddenAdminSuggestion(String option, boolean admin) {
        return !admin && (option.startsWith("set") || RELOAD_COMMAND.equals(option) || RESTORE_LOADOUT_COMMAND.equals(option) || option.startsWith("map"));
    }

    private boolean shouldCompleteOnlinePlayers(CommandSender sender, String[] args) {
        if (args.length != TWO_ARGUMENTS) {
            return false;
        }
        if (RESTORE_LOADOUT_COMMAND.equalsIgnoreCase(args[0])) {
            return sender.hasPermission(ADMIN_PERMISSION);
        }
        return STATS_COMMAND.equalsIgnoreCase(args[0]);
    }

    private boolean shouldCompleteMapIds(CommandSender sender, String[] args) {
        return args.length == TWO_ARGUMENTS
            && (MAP_SAVE_COMMAND.equalsIgnoreCase(args[0]) || MAP_LOAD_COMMAND.equalsIgnoreCase(args[0]))
            && sender.hasPermission(ADMIN_PERMISSION);
    }

    private void addOnlinePlayerCompletions(CommandSender sender, List<String> result, String typed) {
        sender.getServer().getOnlinePlayers().forEach(player -> {
            String name = player.getName();
            if (matchesTyped(name.toLowerCase(Locale.ROOT), typed)) {
                result.add(name);
            }
        });
    }

    private void addMatchingOptions(List<String> result, List<String> options, String typed) {
        for (String option : options) {
            if (matchesTyped(option, typed)) {
                result.add(option);
            }
        }
    }

    private boolean matchesTyped(String value, String typed) {
        return typed.isEmpty() || value.startsWith(typed);
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Usage: /duel <player|accept|deny|review|watch|draw|surrender|cancel|vault|stats|info|settings>");
        if (player.hasPermission(ADMIN_PERMISSION)) {
            player.sendMessage(ChatColor.GRAY + "Admin: /duel <mapsave|mapload|mapstatus|reload|restoreloadout|setpos1|setpos2|setspawn1|setspawn2|setspectator|setspectatorlocation|setexit>");
        }
    }

    private String formatLocation(Location location) {
        return location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    private void handleReload(Player player) {
        if (!requireAdmin(player)) {
            return;
        }
        duelService.reloadFromCommand(player);
    }

    private void handleRestoreLoadout(Player player, String[] args) {
        if (!requireAdmin(player)) {
            return;
        }
        if (args.length < TWO_ARGUMENTS) {
            player.sendMessage(ChatColor.RED + "Usage: /duel restoreloadout <player>");
            return;
        }
        Player target = player.getServer().getPlayer(args[1]);
        if (target == null) {
            duelService.sendMessage(player, TARGET_OFFLINE_MESSAGE);
            return;
        }
        duelService.restoreLatestLoadout(player, target);
    }

    private void handleMapSave(Player player, String[] args) {
        if (!requireAdmin(player)) {
            return;
        }
        if (args.length < TWO_ARGUMENTS) {
            player.sendMessage(ChatColor.RED + "Usage: /duel mapsave <mapId>");
            return;
        }
        duelService.saveMapSnapshot(player, args[1]);
    }

    private void handleMapLoad(Player player, String[] args) {
        if (!requireAdmin(player)) {
            return;
        }
        if (args.length < TWO_ARGUMENTS) {
            player.sendMessage(ChatColor.RED + "Usage: /duel mapload <mapId>");
            return;
        }
        duelService.loadMapSnapshot(player, args[1]);
    }

    private void handleMapStatus(Player player) {
        if (!requireAdmin(player)) {
            return;
        }
        duelService.showMapStatus(player);
    }

    private void handleArenaLocation(Player player, String subcommand, String[] args) {
        if (!requireAdmin(player)) {
            return;
        }
        Location location = parseLocationArgument(player, args);
        if (location == null) {
            player.sendMessage(ChatColor.RED + "Invalid coordinates.");
            return;
        }
        duelService.updateArenaLocation(subcommand, location);
        player.sendMessage(ChatColor.GREEN + "Updated " + subcommand + " to " + formatLocation(location));
    }

    private Location parseLocationArgument(Player player, String[] args) {
        if (args.length != LOCATION_ARGUMENT_COUNT) {
            return player.getLocation();
        }
        try {
            return new Location(
                player.getWorld(),
                Double.parseDouble(args[1]),
                Double.parseDouble(args[2]),
                Double.parseDouble(args[3])
            );
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void handleTargetDuelStart(Player player, String[] args) {
        if (args.length != ROOT_ARGUMENT_COUNT) {
            sendUsage(player);
            return;
        }
        Player target = player.getServer().getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            duelService.sendMessage(player, TARGET_OFFLINE_MESSAGE);
            return;
        }
        duelService.startBuilder(player, target);
        BuilderSession builder = duelService.getBuilder(player.getUniqueId());
        if (builder != null) {
            player.openInventory(DuelGui.buildMapGui(duelService.mapOptions(), builder.settings()));
        }
    }

    private boolean requireAdmin(Player player) {
        if (player.hasPermission(ADMIN_PERMISSION)) {
            return true;
        }
        duelService.sendMessage(player, "messages.no-permission");
        return false;
    }

    private void openSpoils(Player player) {
        if (!player.hasPermission(VAULT_PERMISSION)) {
            duelService.sendMessage(player, "messages.no-permission");
            return;
        }
        List<SpoilsEntry> entries = spoilsService.getEntriesFor(player.getUniqueId());
        if (entries.isEmpty()) {
            spoilsService.sendNoSpoilsMessage(player);
            return;
        }
        player.openInventory(SpoilsGuiFactory.overview(player.getUniqueId(), entries, spoilsService, 0));
    }

    private void openPendingRequestReview(Player player) {
        duelService.openPendingRequestReview(player);
    }
}
