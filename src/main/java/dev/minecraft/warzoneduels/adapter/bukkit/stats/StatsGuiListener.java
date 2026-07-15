package dev.minecraft.warzoneduels.adapter.bukkit.stats;

import dev.minecraft.warzoneduels.app.StatsService;
import dev.minecraft.warzoneduels.domain.stats.PlayerDuelStats;
import dev.minecraft.warzoneduels.permission.PermissionMessages;
import dev.minecraft.warzoneduels.permission.PermissionPolicy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

public final class StatsGuiListener implements Listener {
    private static final int PAGE_SIZE = 21;
    private static final int SLOT_PROFILE_LEADERBOARD = 40;
    private static final int SLOT_CLOSE = 44;
    private static final int SLOT_LEADERBOARD_PROFILE = 36;
    private static final int SLOT_PREVIOUS_PAGE = 38;
    private static final int SLOT_NEXT_PAGE = 42;
    private static final int[] LEADERBOARD_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final StatsService statsService;
    private final PlayerHeadCache headCache;
    private final JavaPlugin plugin;

    public StatsGuiListener(JavaPlugin plugin, StatsService statsService, PlayerHeadCache headCache) {
        this.plugin = plugin;
        this.statsService = statsService;
        this.headCache = headCache;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof StatsGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);
        boolean viewingOthers = holder.type() == StatsGuiType.LEADERBOARD
            || holder.targetPlayerId() != null && !holder.targetPlayerId().equals(player.getUniqueId());
        String requiredPermission = viewingOthers ? PermissionPolicy.STATS_OTHERS : PermissionPolicy.STATS_SELF;
        if (!player.hasPermission(requiredPermission)) {
            player.closeInventory();
            PermissionMessages.sendNoPermission(plugin, player);
            return;
        }
        switch (holder.type()) {
            case PROFILE -> handleProfile(player, holder, event.getRawSlot());
            case LEADERBOARD -> handleLeaderboard(player, holder, event.getRawSlot());
        }
    }

    private void handleProfile(Player player, StatsGuiHolder holder, int slot) {
        if (slot == SLOT_PROFILE_LEADERBOARD) {
            if (!requireOthers(player)) {
                return;
            }
            player.openInventory(StatsGuiFactory.buildLeaderboard(statsService.topByWins(0, PAGE_SIZE), headCache, 0, holder.targetPlayerId()));
            return;
        }
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
        }
    }

    private void handleLeaderboard(Player player, StatsGuiHolder holder, int slot) {
        if (!requireOthers(player)) {
            player.closeInventory();
            return;
        }
        int page = holder.page();
        if (slot == SLOT_LEADERBOARD_PROFILE) {
            openCurrentProfile(player, holder);
            return;
        }
        if (slot == SLOT_PREVIOUS_PAGE && page > 0) {
            openLeaderboard(player, page - 1, holder.targetPlayerId());
            return;
        }
        if (slot == SLOT_NEXT_PAGE) {
            List<PlayerDuelStats> next = statsService.topByWins(page + 1, PAGE_SIZE);
            if (!next.isEmpty()) {
                player.openInventory(StatsGuiFactory.buildLeaderboard(next, headCache, page + 1, holder.targetPlayerId()));
            }
            return;
        }
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        openLeaderboardEntry(player, page, slot);
    }

    private void openCurrentProfile(Player player, StatsGuiHolder holder) {
        PlayerDuelStats profileStats = holder.targetPlayerId() == null
            ? statsService.stats(player.getUniqueId(), player.getName())
            : statsService.findById(holder.targetPlayerId());
        if (profileStats == null) {
            profileStats = statsService.stats(player.getUniqueId(), player.getName());
        }
        player.openInventory(StatsGuiFactory.buildProfile(profileStats, headCache));
    }

    private void openLeaderboard(Player player, int page, UUID targetPlayerId) {
        player.openInventory(StatsGuiFactory.buildLeaderboard(statsService.topByWins(page, PAGE_SIZE), headCache, page, targetPlayerId));
    }

    private void openLeaderboardEntry(Player player, int page, int slot) {
        for (int i = 0; i < LEADERBOARD_SLOTS.length; i++) {
            if (slot != LEADERBOARD_SLOTS[i]) {
                continue;
            }
            List<PlayerDuelStats> entries = statsService.topByWins(page, PAGE_SIZE);
            if (i >= entries.size()) {
                return;
            }
            player.openInventory(StatsGuiFactory.buildProfile(entries.get(i), headCache));
            return;
        }
    }

    private boolean requireOthers(Player player) {
        if (player.hasPermission(PermissionPolicy.STATS_OTHERS)) {
            return true;
        }
        PermissionMessages.sendNoPermission(plugin, player);
        return false;
    }
}
