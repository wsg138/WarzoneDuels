package dev.minecraft.warzoneduels.adapter.bukkit.spoils;

import dev.minecraft.warzoneduels.app.SpoilsService;
import dev.minecraft.warzoneduels.domain.spoils.SpoilsEntry;
import dev.minecraft.warzoneduels.permission.PermissionMessages;
import dev.minecraft.warzoneduels.permission.PermissionPolicy;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

public final class SpoilsGuiListener implements Listener {
    private static final int CONTENT_SLOTS = 45;
    private static final int SLOT_OVERVIEW_PREVIOUS = 45;
    private static final int SLOT_OVERVIEW_CLOSE = 49;
    private static final int SLOT_OVERVIEW_NEXT = 53;
    private static final int SLOT_DETAIL_BACK = 45;
    private static final int SLOT_DETAIL_PREVIOUS = 46;
    private static final int SLOT_DETAIL_NEXT = 47;
    private static final int SLOT_DETAIL_CLAIM_ALL = 50;
    private static final int SLOT_DETAIL_DELETE_REMAINING = 51;

    private final SpoilsService spoilsService;
    private final JavaPlugin plugin;

    public SpoilsGuiListener(JavaPlugin plugin, SpoilsService spoilsService) {
        this.plugin = plugin;
        this.spoilsService = spoilsService;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof AbstractSpoilsHolder holder)) {
            return;
        }
        if (!player.hasPermission(PermissionPolicy.VAULT)) {
            event.setCancelled(true);
            player.closeInventory();
            PermissionMessages.sendNoPermission(plugin, player);
            return;
        }
        if (!holder.ownerId().equals(player.getUniqueId())) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }
        event.setCancelled(true);

        if (holder instanceof SpoilsOverviewHolder overviewHolder) {
            handleOverviewClick(player, overviewHolder, event.getRawSlot());
            return;
        }
        if (holder instanceof SpoilsDetailHolder detailHolder) {
            handleDetailClick(player, detailHolder, event.getRawSlot());
        }
    }

    private void handleOverviewClick(Player player, SpoilsOverviewHolder holder, int slot) {
        List<SpoilsEntry> entries = spoilsService.getEntriesFor(player.getUniqueId());
        if (closeIfNoEntries(player, entries)) {
            return;
        }
        int page = holder.page();
        if (slot >= 0 && slot < CONTENT_SLOTS) {
            int index = (page * CONTENT_SLOTS) + slot;
            if (index >= entries.size()) {
                return;
            }
            SpoilsEntry entry = entries.get(index);
            player.openInventory(SpoilsGuiFactory.detail(player.getUniqueId(), entry, spoilsService, 0));
            return;
        }
        if (slot == SLOT_OVERVIEW_PREVIOUS && page > 0) {
            player.openInventory(SpoilsGuiFactory.overview(player.getUniqueId(), entries, spoilsService, page - 1));
            return;
        }
        if (slot == SLOT_OVERVIEW_NEXT && hasNextPage(page, entries.size())) {
            player.openInventory(SpoilsGuiFactory.overview(player.getUniqueId(), entries, spoilsService, page + 1));
            return;
        }
        if (slot == SLOT_OVERVIEW_CLOSE) {
            player.closeInventory();
        }
    }

    private void handleDetailClick(Player player, SpoilsDetailHolder holder, int slot) {
        SpoilsEntry entry = spoilsService.getEntry(holder.entryId());
        if (entry == null || entry.isEmpty()) {
            openOverviewOrClose(player);
            return;
        }

        int page = holder.page();
        if (slot >= 0 && slot < CONTENT_SLOTS) {
            int index = (page * CONTENT_SLOTS) + slot;
            if (spoilsService.claimSingleItem(player, holder.entryId(), index)) {
                reopenAfterClaim(player, holder.entryId(), page);
            }
            return;
        }
        if (slot == SLOT_DETAIL_BACK) {
            openOverviewOrClose(player);
            return;
        }
        if (slot == SLOT_DETAIL_PREVIOUS && page > 0) {
            player.openInventory(SpoilsGuiFactory.detail(player.getUniqueId(), entry, spoilsService, page - 1));
            return;
        }
        if (slot == SLOT_DETAIL_NEXT && hasNextPage(page, entry.items().size())) {
            player.openInventory(SpoilsGuiFactory.detail(player.getUniqueId(), entry, spoilsService, page + 1));
            return;
        }
        if (slot == SLOT_DETAIL_CLAIM_ALL) {
            int claimed = spoilsService.claimAll(player, holder.entryId());
            if (claimed <= 0) {
                player.sendMessage(ChatColor.RED + "No items were claimed.");
            }
            reopenAfterClaim(player, holder.entryId(), page);
            return;
        }
        if (slot == SLOT_DETAIL_DELETE_REMAINING) {
            if (spoilsService.deleteRemaining(player, holder.entryId())) {
                openOverviewOrClose(player);
            }
        }
    }

    private void reopenAfterClaim(Player player, UUID entryId, int page) {
        SpoilsEntry refreshed = spoilsService.getEntry(entryId);
        if (refreshed == null || refreshed.isEmpty()) {
            openOverviewOrClose(player);
            return;
        }
        int newPage = Math.min(page, Math.max(0, (refreshed.items().size() - 1) / CONTENT_SLOTS));
        player.openInventory(SpoilsGuiFactory.detail(player.getUniqueId(), refreshed, spoilsService, newPage));
    }

    private boolean hasNextPage(int page, int itemCount) {
        return ((page + 1) * CONTENT_SLOTS) < itemCount;
    }

    private void openOverviewOrClose(Player player) {
        List<SpoilsEntry> entries = spoilsService.getEntriesFor(player.getUniqueId());
        if (!closeIfNoEntries(player, entries)) {
            player.openInventory(SpoilsGuiFactory.overview(player.getUniqueId(), entries, spoilsService, 0));
        }
    }

    private boolean closeIfNoEntries(Player player, List<SpoilsEntry> entries) {
        if (!entries.isEmpty()) {
            return false;
        }
        player.closeInventory();
        spoilsService.sendNoSpoilsMessage(player);
        return true;
    }
}
