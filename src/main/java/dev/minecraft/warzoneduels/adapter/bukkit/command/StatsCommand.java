package dev.minecraft.warzoneduels.adapter.bukkit.command;

import dev.minecraft.warzoneduels.app.StatsService;
import dev.minecraft.warzoneduels.adapter.bukkit.stats.PlayerHeadCache;
import dev.minecraft.warzoneduels.adapter.bukkit.stats.StatsGuiFactory;
import dev.minecraft.warzoneduels.domain.stats.PlayerDuelStats;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class StatsCommand implements CommandExecutor, TabCompleter {
    private static final String STATS_PERMISSION = "warzoneduels.stats";
    private final StatsService statsService;
    private final PlayerHeadCache headCache;

    public StatsCommand(StatsService statsService, PlayerHeadCache headCache) {
        this.statsService = statsService;
        this.headCache = headCache;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(STATS_PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission.");
            return true;
        }
        PlayerDuelStats stats;
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Usage: /stats <player>");
                return true;
            }
            stats = statsService.stats(player.getUniqueId(), player.getName());
        } else {
            stats = statsService.findByNameOrOffline(args[0]);
            if (stats == null) {
                sender.sendMessage(ChatColor.RED + "No duel stats found for " + args[0] + ".");
                return true;
            }
        }

        if (sender instanceof Player player) {
            player.openInventory(StatsGuiFactory.buildProfile(stats, headCache));
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "Death Duel Stats: " + ChatColor.YELLOW + stats.lastKnownName());
        sender.sendMessage(ChatColor.GRAY + "Matches: " + ChatColor.WHITE + stats.matchesPlayed());
        sender.sendMessage(ChatColor.GRAY + "Wins: " + ChatColor.GREEN + stats.wins());
        sender.sendMessage(ChatColor.GRAY + "Losses: " + ChatColor.RED + stats.losses());
        sender.sendMessage(ChatColor.GRAY + "Draws: " + ChatColor.AQUA + stats.draws());
        sender.sendMessage(ChatColor.GRAY + "Current Streak: " + ChatColor.WHITE + stats.currentWinStreak());
        sender.sendMessage(ChatColor.GRAY + "Best Streak: " + ChatColor.GOLD + stats.bestWinStreak());
        sender.sendMessage(ChatColor.GRAY + "Disconnect Forfeit Losses: " + ChatColor.WHITE + stats.disconnectForfeitLosses());
        sender.sendMessage(ChatColor.GRAY + "Win/Loss Ratio: " + ChatColor.AQUA + formatRatio(stats));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String typed = args[0].toLowerCase(Locale.ROOT);
        List<String> results = new ArrayList<>();
        if (sender.getServer() != null) {
            sender.getServer().getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> typed.isEmpty() || name.toLowerCase(Locale.ROOT).startsWith(typed))
                .forEach(results::add);
        }
        statsService.all().stream()
            .map(PlayerDuelStats::lastKnownName)
            .filter(name -> name != null && !name.isBlank())
            .filter(name -> typed.isEmpty() || name.toLowerCase(Locale.ROOT).startsWith(typed))
            .filter(name -> results.stream().noneMatch(existing -> existing.equalsIgnoreCase(name)))
            .sorted(Comparator.naturalOrder())
            .forEach(results::add);
        return results;
    }

    private String formatRatio(PlayerDuelStats stats) {
        if (stats.losses() <= 0) {
            return stats.wins() > 0 ? "Perfect" : "0.00";
        }
        return String.format(java.util.Locale.US, "%.2f", (double) stats.wins() / (double) stats.losses());
    }
}
