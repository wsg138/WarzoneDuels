package dev.minecraft.warzoneduels.app;

import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import dev.minecraft.warzoneduels.adapter.bukkit.persistence.SpoilsStore;
import dev.minecraft.warzoneduels.domain.LoadoutSnapshot;
import dev.minecraft.warzoneduels.domain.spoils.SpoilsEntry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SpoilsService {
    private final WarzoneDuelsPlugin plugin;
    private final SpoilsStore spoilsStore;

    private final Map<UUID, SpoilsEntry> entriesById = new HashMap<>();
    private final Set<UUID> pendingForcedDeathOnJoin = new HashSet<>();

    private String prefix;
    private long expiryMillis;
    private long cleanupIntervalTicks;
    private BukkitTask cleanupTask;

    public SpoilsService(WarzoneDuelsPlugin plugin, SpoilsStore spoilsStore) {
        this.plugin = plugin;
        this.spoilsStore = spoilsStore;
    }

    public void enable() {
        reloadConfig();
        SpoilsStore.State state = spoilsStore.load();
        entriesById.clear();
        entriesById.putAll(state.entries());
        pendingForcedDeathOnJoin.clear();
        pendingForcedDeathOnJoin.addAll(state.clearOnJoin());
        cleanupExpiredEntries();
        startCleanupTask();
    }

    public void disable() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        spoilsStore.shutdown();
        saveSync();
    }

    public void reloadConfig() {
        prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "&6[Duel]&r "));
        int expiryHours = Math.max(1, plugin.getConfig().getInt("spoils.expiry-hours", 24));
        int cleanupMinutes = Math.max(1, plugin.getConfig().getInt("spoils.cleanup-interval-minutes", 5));
        expiryMillis = Duration.ofHours(expiryHours).toMillis();
        cleanupIntervalTicks = cleanupMinutes * 60L * 20L;
    }

    public boolean hasUnclaimedSpoils(UUID playerId) {
        cleanupExpiredEntries();
        return entriesById.values().stream().anyMatch(entry -> entry.ownerId().equals(playerId) && !entry.isEmpty());
    }

    public int unclaimedEntryCount(UUID playerId) {
        cleanupExpiredEntries();
        int count = 0;
        for (SpoilsEntry entry : entriesById.values()) {
            if (entry.ownerId().equals(playerId) && !entry.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    public List<SpoilsEntry> getEntriesFor(UUID playerId) {
        cleanupExpiredEntries();
        return entriesById.values().stream()
            .filter(entry -> entry.ownerId().equals(playerId) && !entry.isEmpty())
            .sorted(Comparator.comparingLong(SpoilsEntry::createdAtEpochMs).reversed())
            .toList();
    }

    public SpoilsEntry getEntry(UUID entryId) {
        cleanupExpiredEntries();
        return entriesById.get(entryId);
    }

    public void createSpoils(UUID winnerId, String winnerName, UUID loserId, String loserName, List<ItemStack> items) {
        List<ItemStack> normalized = sanitizeItems(items);
        if (winnerId == null || winnerName == null || normalized.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        SpoilsEntry entry = new SpoilsEntry(
            UUID.randomUUID(),
            winnerId,
            winnerName,
            loserId,
            loserName,
            now,
            now + expiryMillis,
            normalized
        );
        entriesById.put(entry.entryId(), entry);
        save();
        Player onlineWinner = Bukkit.getPlayer(winnerId);
        if (onlineWinner != null) {
            sendPlayerMessage(onlineWinner, plugin.getConfig().getString("messages.spoils-created", "&aVault captured from &e{player}&a. Use &e/vault&a.").replace("{player}", loserName));
        }
    }

    public void createSpoils(Player winner, UUID loserId, String loserName, List<ItemStack> items) {
        createSpoils(winner.getUniqueId(), winner.getName(), loserId, loserName, items);
    }

    public void createSpoilsFromSnapshot(UUID winnerId, String winnerName, UUID loserId, String loserName, LoadoutSnapshot snapshot) {
        createSpoils(winnerId, winnerName, loserId, loserName, toItemList(snapshot));
    }

    public void markForcedDeathOnJoin(UUID playerId) {
        pendingForcedDeathOnJoin.add(playerId);
        save();
    }

    public boolean prepareForcedDeathIfPending(Player player) {
        if (!pendingForcedDeathOnJoin.remove(player.getUniqueId())) {
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(null);
        inventory.setItemInOffHand(null);
        player.updateInventory();
        save();
        return true;
    }

    public boolean claimSingleItem(Player player, UUID entryId, int itemIndex) {
        SpoilsEntry entry = entriesById.get(entryId);
        if (entry == null || !entry.ownerId().equals(player.getUniqueId())) {
            return false;
        }
        if (itemIndex < 0 || itemIndex >= entry.mutableItems().size()) {
            return false;
        }
        ItemStack item = entry.mutableItems().get(itemIndex);
        if (item == null || item.getType().isAir()) {
            return false;
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item.clone());
        if (!leftovers.isEmpty()) {
            sendPlayerMessage(player, plugin.getConfig().getString("messages.spoils-inventory-full", "&cYou need more inventory space to claim that item."));
            return false;
        }
        entry.mutableItems().remove(itemIndex);
        cleanupEmptyEntry(entry.entryId());
        player.updateInventory();
        save();
        if (plugin.statsService() != null) {
            plugin.statsService().recordSpoilsClaim(player.getUniqueId(), player.getName());
        }
        return true;
    }

    public int claimAll(Player player, UUID entryId) {
        SpoilsEntry entry = entriesById.get(entryId);
        if (entry == null || !entry.ownerId().equals(player.getUniqueId())) {
            return 0;
        }
        int claimed = 0;
        List<ItemStack> remaining = new ArrayList<>();
        for (ItemStack item : entry.mutableItems()) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item.clone());
            if (leftovers.isEmpty()) {
                claimed++;
            } else {
                remaining.addAll(leftovers.values());
            }
        }
        entry.mutableItems().clear();
        entry.mutableItems().addAll(remaining);
        cleanupEmptyEntry(entry.entryId());
        player.updateInventory();
        save();
        if (claimed > 0 && plugin.statsService() != null) {
            plugin.statsService().recordSpoilsClaim(player.getUniqueId(), player.getName());
        }
        return claimed;
    }

    public boolean deleteRemaining(Player player, UUID entryId) {
        SpoilsEntry entry = entriesById.get(entryId);
        if (entry == null || !entry.ownerId().equals(player.getUniqueId())) {
            return false;
        }
        entriesById.remove(entryId);
        save();
        return true;
    }

    public void sendNoSpoilsMessage(CommandSender sender) {
        sendSenderMessage(sender, plugin.getConfig().getString("messages.no-spoils", "&7You do not have any unclaimed spoils."));
    }

    public String formatRemainingTime(SpoilsEntry entry) {
        long remaining = Math.max(0L, entry.expiresAtEpochMs() - System.currentTimeMillis());
        long hours = remaining / 3_600_000L;
        long minutes = (remaining % 3_600_000L) / 60_000L;
        return hours + "h " + minutes + "m";
    }

    public Material displayMaterial(SpoilsEntry entry) {
        for (ItemStack item : entry.items()) {
            if (item != null && !item.getType().isAir()) {
                return item.getType();
            }
        }
        return Material.CHEST;
    }

    private void cleanupExpiredEntries() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (SpoilsEntry entry : new ArrayList<>(entriesById.values())) {
            if (entry.expiresAtEpochMs() <= now) {
                entriesById.remove(entry.entryId());
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }

    private void cleanupEmptyEntry(UUID entryId) {
        SpoilsEntry entry = entriesById.get(entryId);
        if (entry == null || !entry.isEmpty()) {
            return;
        }
        entriesById.remove(entryId);
    }

    private void startCleanupTask() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupExpiredEntries, cleanupIntervalTicks, cleanupIntervalTicks);
    }

    private List<ItemStack> toItemList(LoadoutSnapshot snapshot) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : snapshot.contents()) {
            if (item != null && !item.getType().isAir()) {
                items.add(item.clone());
            }
        }
        for (ItemStack item : snapshot.armor()) {
            if (item != null && !item.getType().isAir()) {
                items.add(item.clone());
            }
        }
        ItemStack offHand = snapshot.offHand();
        if (offHand != null && !offHand.getType().isAir()) {
            items.add(offHand.clone());
        }
        return items;
    }

    private List<ItemStack> sanitizeItems(List<ItemStack> rawItems) {
        List<ItemStack> sanitized = new ArrayList<>();
        for (ItemStack item : rawItems) {
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                continue;
            }
            sanitized.add(item.clone());
        }
        return sanitized;
    }

    private void save() {
        spoilsStore.saveAsync(entriesById.values(), pendingForcedDeathOnJoin);
    }

    private void saveSync() {
        spoilsStore.save(entriesById.values(), pendingForcedDeathOnJoin);
    }

    private void sendPlayerMessage(Player player, String message) {
        player.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', message));
    }

    private void sendSenderMessage(CommandSender sender, String message) {
        sender.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', message));
    }
}
