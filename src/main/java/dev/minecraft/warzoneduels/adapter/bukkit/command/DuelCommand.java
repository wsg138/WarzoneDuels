package dev.minecraft.warzoneduels.adapter.bukkit.command;

import dev.minecraft.warzoneduels.adapter.bukkit.spoils.SpoilsGuiFactory;
import dev.minecraft.warzoneduels.adapter.bukkit.gui.DuelGui;
import dev.minecraft.warzoneduels.app.DuelService;
import dev.minecraft.warzoneduels.app.DuelPartyService;
import dev.minecraft.warzoneduels.app.SpoilsService;
import dev.minecraft.warzoneduels.domain.BuilderSession;
import dev.minecraft.warzoneduels.domain.DuelParty;
import dev.minecraft.warzoneduels.domain.spoils.SpoilsEntry;
import dev.minecraft.warzoneduels.permission.PermissionPolicy;
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
import java.util.Optional;
import java.util.UUID;

public final class DuelCommand implements CommandExecutor, TabCompleter {
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
    private static final List<String> PARTY_OPERATIONS = List.of(
        "create", "invite", "accept", "decline", "info", "leave", "kick", "transfer", "disband"
    );

    private final DuelService duelService;
    private final SpoilsService spoilsService;
    private final DuelPartyService partyService;

    public DuelCommand(DuelService duelService, SpoilsService spoilsService, DuelPartyService partyService) {
        this.duelService = duelService;
        this.spoilsService = spoilsService;
        this.partyService = partyService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!isDrawCommandAlias(command) && args.length > 0 && "mode".equalsIgnoreCase(args[0])) {
            handleModeCommand(sender, args);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (isDrawCommandAlias(command)) {
            if (!requirePermission(player, PermissionPolicy.DRAW)) {
                return true;
            }
            duelService.requestDraw(player);
            return true;
        }
        if (args.length == 0) {
            if (!requirePermission(player, PermissionPolicy.COMMAND_DUEL)) {
                return true;
            }
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
        if (isSafetyLeave(player, args)) {
            duelService.leaveWatchMode(player, "command-" + sub, true);
            return;
        }
        String permission = STATS_COMMAND.equals(sub) && args.length >= TWO_ARGUMENTS
            ? PermissionPolicy.STATS_OTHERS
            : PermissionPolicy.permissionForSubcommand(sub);
        if (permission != null && !player.hasPermission(permission)) {
            duelService.sendMessage(player, PermissionPolicy.SPECTATE_USE.equals(permission)
                ? "messages.no-spectate-permission" : "messages.no-permission");
            return;
        }
        switch (sub) {
            case "accept" -> duelService.acceptRequest(player);
            case "deny" -> duelService.denyRequest(player);
            case DRAW_COMMAND, SURRENDER_COMMAND, "cancel" -> duelService.requestDraw(player);
            case "review" -> openPendingRequestReview(player);
            case "watch", "spectate", "stands" -> duelService.watchDuel(player);
            case "leave", "unwatch" -> duelService.leaveWatchMode(player, "command-" + sub, true);
            case "vault" -> openSpoils(player);
            case STATS_COMMAND -> player.performCommand(args.length >= TWO_ARGUMENTS ? STATS_COMMAND + " " + args[1] : STATS_COMMAND);
            case "info", "settings" -> duelService.showSettings(player);
            case "party" -> handlePartyCommand(player, args);
            case RELOAD_COMMAND -> handleReload(player);
            case RESTORE_LOADOUT_COMMAND -> handleRestoreLoadout(player, args);
            case MAP_SAVE_COMMAND -> handleMapSave(player, args);
            case MAP_LOAD_COMMAND -> handleMapLoad(player, args);
            case "mapstatus" -> handleMapStatus(player);
            case "recoverwatcher" -> handleRecoverWatcher(player, args);
            case "setpos1", "setpos2", "setspawn1", "setspawn2", "setspectator", "setexit" -> handleArenaLocation(player, sub, args);
            default -> handleTargetDuelStart(player, args);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (isDrawCommandAlias(command)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        if (sender.hasPermission(PermissionPolicy.ADMIN_MODES)) {
            if (!(sender instanceof Player) && args.length == 1 && "mode".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                result.add("mode");
            } else if (args.length == 2 && "mode".equalsIgnoreCase(args[0])) {
                addMatchingOptions(result, List.of("2v2", "3v3"), args[1].toLowerCase(Locale.ROOT));
                return result;
            } else if (args.length == 3 && "mode".equalsIgnoreCase(args[0])
                && ("2v2".equalsIgnoreCase(args[1]) || "3v3".equalsIgnoreCase(args[1]))) {
                addMatchingOptions(result, List.of("enable", "disable", "status"), args[2].toLowerCase(Locale.ROOT));
                return result;
            }
        }
        if (args.length == ROOT_ARGUMENT_COUNT) {
            addRootCompletions(sender, result, args[0].toLowerCase(Locale.ROOT));
            return result;
        }
        if (shouldCompleteOnlinePlayers(sender, args)) {
            addOnlinePlayerCompletions(sender, result, args[1].toLowerCase(Locale.ROOT));
            return result;
        }
        if (sender instanceof Player player && args.length >= TWO_ARGUMENTS && "party".equalsIgnoreCase(args[0])) {
            addPartyCompletions(player, result, args);
            return result;
        }
        if (shouldCompleteMapIds(sender, args)) {
            addMatchingOptions(result, DEFAULT_MAP_IDS, args[1].toLowerCase(Locale.ROOT));
        }
        return result;
    }

    private void addRootCompletions(CommandSender sender, List<String> result, String typed) {
        if (!(sender instanceof Player)) {
            return;
        }
        addVisibleCommandSuggestions(sender, result, typed);
        if (sender.hasPermission(PermissionPolicy.CHALLENGE)) {
            addOnlinePlayerCompletions(sender, result, typed);
        }
    }

    private void handleModeCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PermissionPolicy.ADMIN_MODES)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission.");
            return;
        }
        if (args.length == 1) {
            sendModeStatus(sender, 2);
            sendModeStatus(sender, 3);
            return;
        }
        int teamSize = switch (args[1].toLowerCase(Locale.ROOT)) {
            case "2v2" -> 2;
            case "3v3" -> 3;
            default -> 0;
        };
        String action = args.length == 2 ? "status" : args[2].toLowerCase(Locale.ROOT);
        if (teamSize == 0 || args.length > 3 || !List.of("enable", "disable", "status").contains(action)) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /duel mode <2v2|3v3> <enable|disable|status>");
            return;
        }
        if (!"status".equals(action)) {
            try {
                duelService.setDuelModeEnabled(teamSize, "enable".equals(action));
            } catch (java.io.IOException ex) {
                sender.sendMessage(ChatColor.RED + "Could not save duel modes. The previous setting was retained.");
                return;
            }
        }
        sendModeStatus(sender, teamSize);
    }

    private void sendModeStatus(CommandSender sender, int teamSize) {
        sender.sendMessage(ChatColor.YELLOW + "Duel mode " + teamSize + "v" + teamSize + ": "
            + (duelService.isDuelModeEnabled(teamSize) ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled"));
    }

    private void addVisibleCommandSuggestions(CommandSender sender, List<String> result, String typed) {
        boolean activeWatcher = sender instanceof Player player && duelService.isActiveWatcher(player.getUniqueId());
        for (String option : PermissionPolicy.visibleRootSuggestions(permission -> sender.hasPermission(permission)
            || PermissionPolicy.STATS_SELF.equals(permission) && sender.hasPermission(PermissionPolicy.STATS_OTHERS), activeWatcher)) {
            if (matchesTyped(option, typed)) {
                result.add(option);
            }
        }
    }

    private boolean shouldCompleteOnlinePlayers(CommandSender sender, String[] args) {
        if (args.length != TWO_ARGUMENTS) {
            return false;
        }
        if (RESTORE_LOADOUT_COMMAND.equalsIgnoreCase(args[0])) {
            return sender.hasPermission(PermissionPolicy.ADMIN_RESTORE_LOADOUT);
        }
        if ("recoverwatcher".equalsIgnoreCase(args[0])) {
            return sender.hasPermission(PermissionPolicy.ADMIN_RECOVER_WATCHER);
        }
        return STATS_COMMAND.equalsIgnoreCase(args[0]) && sender.hasPermission(PermissionPolicy.STATS_OTHERS);
    }

    private boolean shouldCompleteMapIds(CommandSender sender, String[] args) {
        return args.length == TWO_ARGUMENTS
            && (MAP_SAVE_COMMAND.equalsIgnoreCase(args[0]) || MAP_LOAD_COMMAND.equalsIgnoreCase(args[0]))
            && sender.hasPermission(MAP_SAVE_COMMAND.equalsIgnoreCase(args[0]) ? PermissionPolicy.ADMIN_MAP_SAVE : PermissionPolicy.ADMIN_MAP_LOAD);
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
        boolean activeWatcher = duelService.isActiveWatcher(player.getUniqueId());
        List<String> visible = PermissionPolicy.visibleRootSuggestions(player::hasPermission, activeWatcher);
        String challenge = player.hasPermission(PermissionPolicy.CHALLENGE) ? "player|" : "";
        player.sendMessage(ChatColor.YELLOW + "Usage: /duel <" + challenge + String.join("|", visible) + ">");
    }

    private void handlePartyCommand(Player player, String[] args) {
        String operation = args.length < TWO_ARGUMENTS ? "info" : args[1].toLowerCase(Locale.ROOT);
        try {
            switch (operation) {
                case "create" -> createParty(player);
                case "invite" -> inviteToParty(player, args);
                case "accept" -> acceptPartyInvite(player, args);
                case "decline" -> declinePartyInvite(player, args);
                case "info" -> showParty(player);
                case "leave" -> leaveParty(player);
                case "kick" -> kickPartyMember(player, args);
                case "transfer" -> transferPartyLeadership(player, args);
                case "disband" -> disbandParty(player);
                default -> sendPartyUsage(player);
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            player.sendMessage(ChatColor.RED + ex.getMessage());
        }
    }

    private void createParty(Player player) {
        partyService.createParty(player.getUniqueId(), player.getName());
        player.sendMessage(ChatColor.GREEN + "Created a Duel Party. You are the leader.");
    }

    private void inviteToParty(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /duel party invite <player>");
            return;
        }
        Player target = player.getServer().getPlayer(args[2]);
        if (target == null || !target.isOnline()) {
            duelService.sendMessage(player, TARGET_OFFLINE_MESSAGE);
            return;
        }
        partyService.invite(player.getUniqueId(), target.getUniqueId(), target.getName(), System.currentTimeMillis());
        player.sendMessage(ChatColor.GREEN + "Invited " + target.getName() + " to your Duel Party.");
        target.sendMessage(ChatColor.GOLD + player.getName() + " invited you to a Duel Party. "
            + ChatColor.YELLOW + "Use /duel party accept " + player.getName() + " to join.");
    }

    private void acceptPartyInvite(Player player, String[] args) {
        DuelParty party = requireInvitingParty(player, args, "accept");
        partyService.acceptInvite(player.getUniqueId(), party.id(), System.currentTimeMillis());
        notifyParty(party, ChatColor.GREEN + player.getName() + " joined the Duel Party.");
    }

    private void declinePartyInvite(Player player, String[] args) {
        DuelParty party = requireInvitingParty(player, args, "decline");
        partyService.declineInvite(player.getUniqueId(), party.id());
        player.sendMessage(ChatColor.YELLOW + "Declined the Duel Party invitation from " + leaderName(party) + ".");
    }

    private DuelParty requireInvitingParty(Player player, String[] args, String operation) {
        if (args.length < 3) {
            throw new IllegalArgumentException("Usage: /duel party " + operation + " <leader>");
        }
        DuelParty party = findPartyByLeaderName(args[2])
            .orElseThrow(() -> new IllegalArgumentException("No Duel Party was found for that leader."));
        if (partyService.invitationFor(player.getUniqueId(), party.id(), System.currentTimeMillis()).isEmpty()) {
            throw new IllegalStateException("You do not have an active invitation from that Duel Party.");
        }
        return party;
    }

    private void showParty(Player player) {
        Optional<DuelParty> current = partyService.partyOf(player.getUniqueId());
        if (current.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "You are not in a Duel Party. Use /duel party create to start one.");
            return;
        }
        DuelParty party = current.get();
        player.sendMessage(ChatColor.GOLD + "Duel Party (" + party.size() + "/3)"
            + (party.isRosterLocked() ? ChatColor.RED + " [ROSTER LOCKED]" : ""));
        for (DuelParty.DuelPartyMember member : party.members()) {
            String suffix = member.playerId().equals(party.leaderId()) ? ChatColor.GOLD + " [Leader]" : "";
            player.sendMessage(ChatColor.YELLOW + "- " + member.name() + suffix);
        }
    }

    private void leaveParty(Player player) {
        DuelParty party = partyService.partyOf(player.getUniqueId()).orElseThrow(
            () -> new IllegalStateException("Player is not in a Duel Party."));
        boolean leaderLeaving = party.leaderId().equals(player.getUniqueId());
        partyService.leaveParty(player.getUniqueId());
        if (leaderLeaving) {
            notifyParty(party, ChatColor.YELLOW + "The Duel Party was disbanded because its leader left.");
        } else {
            player.sendMessage(ChatColor.YELLOW + "You left your Duel Party.");
        }
    }

    private void kickPartyMember(Player player, String[] args) {
        UUID memberId = requirePartyMember(player, args, "kick");
        partyService.kickMember(player.getUniqueId(), memberId);
        player.sendMessage(ChatColor.YELLOW + "Removed that player from your Duel Party.");
        Player removed = player.getServer().getPlayer(memberId);
        if (removed != null) {
            removed.sendMessage(ChatColor.RED + "You were removed from the Duel Party.");
        }
    }

    private void transferPartyLeadership(Player player, String[] args) {
        UUID memberId = requirePartyMember(player, args, "transfer");
        partyService.transferLeadership(player.getUniqueId(), memberId);
        DuelParty party = partyService.partyOf(player.getUniqueId()).orElseThrow();
        notifyParty(party, ChatColor.GOLD + memberName(party, memberId) + " is now the Duel Party leader.");
    }

    private UUID requirePartyMember(Player player, String[] args, String operation) {
        if (args.length < 3) {
            throw new IllegalArgumentException("Usage: /duel party " + operation + " <player>");
        }
        DuelParty party = partyService.partyOf(player.getUniqueId())
            .orElseThrow(() -> new IllegalStateException("You are not in a Duel Party."));
        return party.members().stream()
            .filter(member -> member.name().equalsIgnoreCase(args[2]))
            .map(DuelParty.DuelPartyMember::playerId)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("That player is not in your Duel Party."));
    }

    private void disbandParty(Player player) {
        DuelParty party = partyService.partyOf(player.getUniqueId())
            .orElseThrow(() -> new IllegalStateException("You are not in a Duel Party."));
        List<UUID> memberIds = party.members().stream().map(DuelParty.DuelPartyMember::playerId).toList();
        partyService.disbandParty(player.getUniqueId());
        for (UUID memberId : memberIds) {
            Player member = player.getServer().getPlayer(memberId);
            if (member != null) {
                member.sendMessage(ChatColor.RED + "The Duel Party was disbanded.");
            }
        }
    }

    private Optional<DuelParty> findPartyByLeaderName(String name) {
        return partyService.parties().values().stream()
            .filter(party -> leaderName(party).equalsIgnoreCase(name))
            .findFirst();
    }

    private String leaderName(DuelParty party) {
        return memberName(party, party.leaderId());
    }

    private String memberName(DuelParty party, UUID playerId) {
        return party.members().stream()
            .filter(member -> member.playerId().equals(playerId))
            .map(DuelParty.DuelPartyMember::name)
            .findFirst()
            .orElse("Unknown");
    }

    private void notifyParty(DuelParty party, String message) {
        for (DuelParty.DuelPartyMember member : party.members()) {
            Player online = org.bukkit.Bukkit.getPlayer(member.playerId());
            if (online != null) {
                online.sendMessage(message);
            }
        }
    }

    private void sendPartyUsage(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Usage: /duel party <" + String.join("|", PARTY_OPERATIONS) + "> [player]");
    }

    private void addPartyCompletions(Player player, List<String> result, String[] args) {
        if (args.length == TWO_ARGUMENTS) {
            addMatchingOptions(result, PARTY_OPERATIONS, args[1].toLowerCase(Locale.ROOT));
            return;
        }
        if (args.length != 3) {
            return;
        }
        String operation = args[1].toLowerCase(Locale.ROOT);
        String typed = args[2].toLowerCase(Locale.ROOT);
        if ("invite".equals(operation)) {
            addOnlinePlayerCompletions(player, result, typed);
            return;
        }
        if ("kick".equals(operation) || "transfer".equals(operation)) {
            partyService.partyOf(player.getUniqueId()).ifPresent(party -> party.members().stream()
                .map(DuelParty.DuelPartyMember::name)
                .filter(name -> matchesTyped(name.toLowerCase(Locale.ROOT), typed))
                .forEach(result::add));
            return;
        }
        if ("accept".equals(operation) || "decline".equals(operation)) {
            long now = System.currentTimeMillis();
            partyService.parties().values().stream()
                .filter(party -> partyService.invitationFor(player.getUniqueId(), party.id(), now).isPresent())
                .map(this::leaderName)
                .filter(name -> matchesTyped(name.toLowerCase(Locale.ROOT), typed))
                .forEach(result::add);
        }
    }

    private String formatLocation(Location location) {
        return location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    private void handleReload(Player player) {
        duelService.reloadFromCommand(player);
    }

    private void handleRestoreLoadout(Player player, String[] args) {
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
        if (args.length < TWO_ARGUMENTS) {
            player.sendMessage(ChatColor.RED + "Usage: /duel mapsave <mapId>");
            return;
        }
        duelService.saveMapSnapshot(player, args[1]);
    }

    private void handleMapLoad(Player player, String[] args) {
        if (args.length < TWO_ARGUMENTS) {
            player.sendMessage(ChatColor.RED + "Usage: /duel mapload <mapId>");
            return;
        }
        duelService.loadMapSnapshot(player, args[1]);
    }

    private void handleMapStatus(Player player) {
        duelService.showMapStatus(player);
    }

    private void handleArenaLocation(Player player, String subcommand, String[] args) {
        Location location = parseLocationArgument(player, args);
        if (location == null) {
            player.sendMessage(ChatColor.RED + "Invalid coordinates.");
            return;
        }
        duelService.updateArenaLocation(player, subcommand, location);
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
        if (!requirePermission(player, PermissionPolicy.CHALLENGE)) {
            return;
        }
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

    private boolean requirePermission(Player player, String permission) {
        if (player.hasPermission(permission)) {
            return true;
        }
        duelService.sendMessage(player, "messages.no-permission");
        return false;
    }

    private void openSpoils(Player player) {
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

    private void handleRecoverWatcher(Player player, String[] args) {
        if (args.length < TWO_ARGUMENTS) {
            player.sendMessage(ChatColor.RED + "Usage: /duel recoverwatcher <player>");
            return;
        }
        Player target = player.getServer().getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            duelService.sendMessage(player, TARGET_OFFLINE_MESSAGE);
            return;
        }
        boolean found = duelService.isActiveWatcher(target.getUniqueId()) || duelService.hasRecoverableWatcherSession(target.getUniqueId());
        boolean success = duelService.recoverWatcher(player, target);
        duelService.sendMessage(player, success ? "messages.admin-watcher-recovery-success"
            : found ? "messages.admin-watcher-recovery-failure" : "messages.admin-watcher-recovery-none", "{player}", target.getName());
    }

    private boolean isSafetyLeave(Player player, String[] args) {
        if (!duelService.isActiveWatcher(player.getUniqueId()) || args.length == 0) {
            return false;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return sub.equals("leave") || sub.equals("unwatch");
    }
}
