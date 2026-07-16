package dev.minecraft.warzoneduels.app;

import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import dev.minecraft.warzoneduels.adapter.bukkit.persistence.SpectatorSessionStore;
import dev.minecraft.warzoneduels.domain.ArenaDefinition;
import dev.minecraft.warzoneduels.domain.SpectatorSession;
import dev.minecraft.warzoneduels.domain.SpectatorSessionPhase;
import dev.minecraft.warzoneduels.domain.StoredLocation;
import dev.minecraft.warzoneduels.domain.TeleportAllowanceReason;
import dev.minecraft.warzoneduels.domain.TypedTeleportAllowance;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;

public final class SpectatorManager {
    private static final Set<String> BUILT_IN_SAFE_COMMANDS = Set.of(
        "duel leave", "duel unwatch", "duel watch", "duel info", "duel settings"
    );
    private static final long TELEPORT_ALLOWANCE_MILLIS = 2_000L;

    private final WarzoneDuelsPlugin plugin;
    private final SpectatorSessionStore store;
    private final Supplier<Set<UUID>> participantIds;
    private final Supplier<ArenaDefinition> arenaSupplier;
    private final Supplier<Location> exitSupplier;
    private final NamespacedKey recoveryMarkerKey;
    private final WatcherTeleportCancellationHook teleportCancellationHook;
    private final WatcherDisplayManager displayManager;
    private final Map<UUID, SpectatorSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, TypedTeleportAllowance> teleportAllowances = new ConcurrentHashMap<>();
    private final Map<UUID, Long> actionMessageCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> teleportMessageCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> boundaryMessageCooldowns = new ConcurrentHashMap<>();

    private double horizontalRadius = 40D;
    private double verticalRadius = 25D;
    private long enforcementIntervalTicks = 1L;
    private long boundaryMessageCooldownMillis = 2_000L;
    private List<String> allowedCommands = List.of();
    private BukkitTask enforcementTask;

    public SpectatorManager(
        WarzoneDuelsPlugin plugin,
        SpectatorSessionStore store,
        Supplier<Set<UUID>> participantIds,
        Supplier<ArenaDefinition> arenaSupplier,
        Supplier<Location> exitSupplier
    ) {
        this.plugin = plugin;
        this.store = store;
        this.participantIds = participantIds;
        this.arenaSupplier = arenaSupplier;
        this.exitSupplier = exitSupplier;
        this.recoveryMarkerKey = new NamespacedKey(plugin, "pseudo_spectator_recovery");
        this.teleportCancellationHook = new WatcherTeleportCancellationHook(plugin);
        this.displayManager = new WatcherDisplayManager(plugin);
    }

    public void reload(FileConfiguration config) {
        horizontalRadius = Math.max(1D, config.getDouble("settings.spectator-boundary.horizontal-radius", 40D));
        verticalRadius = Math.max(1D, config.getDouble("settings.spectator-boundary.vertical-radius", 25D));
        enforcementIntervalTicks = Math.min(2L, Math.max(1L, config.getLong("settings.spectator-enforcement-interval-ticks", 1L)));
        boundaryMessageCooldownMillis = Math.max(250L,
            config.getLong("settings.spectator-boundary.return-message-cooldown-millis", 2_000L));
        allowedCommands = config.getStringList("settings.allowed-watcher-commands").stream()
            .map(value -> normalizeCommand(value))
            .filter(value -> !value.isBlank())
            .toList();
        restartEnforcementTask();
    }

    public void enable() {
        sessions.clear();
        sessions.putAll(store.loadAll());
        visibilitySafetyScrub();
        for (Player player : Bukkit.getOnlinePlayers()) {
            recoverOnJoin(player);
        }
        ensureEnforcementTask();
    }

    public void disable(String reason) {
        cancelEnforcementTask();
        restoreAllOnline(reason);
        visibilitySafetyScrub();
        teleportAllowances.clear();
    }

    public boolean enter(Player player) {
        requirePrimaryThread();
        UUID playerId = player.getUniqueId();
        if (isActiveWatcher(playerId)) {
            return restore(player, "watch-toggle", true);
        }
        if (sessions.containsKey(playerId) || store.exists(playerId) || hasRecoveryMarker(player)) {
            send(player, "messages.watcher-recovery-failure", "&cAn unfinished watcher session must be recovered before you can watch again.");
            recoverOnJoin(player);
            return false;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            send(player, "messages.duel-watch-incompatible", "&cYou cannot start watching while already in spectator mode.");
            return false;
        }

        teleportCancellationHook.cancelInvolving(playerId);
        player.closeInventory();
        dismount(player);
        SpectatorSession prepared = capture(player);
        if (!store.save(prepared)) {
            send(player, "messages.watcher-recovery-failure", "&cWatch mode could not start because your state could not be saved safely.");
            return false;
        }
        sessions.put(playerId, prepared);
        if (!addRecoveryMarker(player)) {
            sessions.remove(playerId);
            store.delete(prepared);
            send(player, "messages.watcher-recovery-failure", "&cWatch mode could not start because recovery state could not be recorded.");
            return false;
        }

        try {
            applyWatchState(player);
            Location spawn = spectatorSpawn();
            if (spawn == null || !teleport(player, spawn, TeleportAllowanceReason.WATCH_ENTRY)) {
                throw new IllegalStateException("Configured spectator spawn is unavailable or teleport failed");
            }
            applyVisibility(player);
            displayManager.suppress(player);
            SpectatorSession active = prepared.withPhase(SpectatorSessionPhase.ACTIVE);
            if (!store.save(active)) {
                throw new IllegalStateException("Failed to persist ACTIVE spectator session phase");
            }
            sessions.put(playerId, active);
            ensureEnforcementTask();
            send(player, "messages.duel-watch-started", "&aYou are now watching the duel. Use &e/duel leave&a to return.");
            return true;
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to activate spectator session for " + playerId + " (" + player.getName() + ")", ex);
            restore(player, "entry-failure", false);
            send(player, "messages.watcher-recovery-failure", "&cWatch mode failed and your previous state was restored.");
            return false;
        }
    }

    public boolean restore(Player player, String reason, boolean notify) {
        requirePrimaryThread();
        UUID playerId = player.getUniqueId();
        SpectatorSession session = sessions.get(playerId);
        if (session == null) {
            session = store.load(playerId).orElse(null);
        }
        if (session == null) {
            forceVisible(player);
            displayManager.restore(player);
            if (notify) {
                send(player, "messages.duel-watch-not-watching", "&7You are not currently watching a duel.");
            }
            return false;
        }

        SpectatorSession restoring = session.withPhase(SpectatorSessionPhase.RESTORING);
        boolean phaseSaved = store.save(restoring);
        sessions.put(playerId, restoring);
        forceVisible(player);
        displayManager.restore(player);
        sessions.remove(playerId);

        try {
            applyOriginalState(player, restoring);
            removeRecoveryMarker(player);
            player.saveData();
            if (!phaseSaved || !store.delete(restoring)) {
                addRecoveryMarker(player);
                sessions.put(playerId, restoring);
                plugin.getLogger().severe("Restored watcher state but retained recovery session for " + playerId + " (" + restoring.playerName() + ") because durable cleanup did not complete.");
                return false;
            }
            if (notify) {
                send(player, "messages.duel-watch-ended", "&aYou stopped watching and your previous state was restored.");
            }
            plugin.getLogger().info("Restored spectator session for " + playerId + " (" + restoring.playerName() + "), reason=" + reason);
            return true;
        } catch (RuntimeException ex) {
            sessions.put(playerId, restoring);
            addRecoveryMarker(player);
            plugin.getLogger().log(Level.SEVERE, "Failed to restore spectator session for " + playerId + " (" + restoring.playerName() + "), reason=" + reason, ex);
            send(player, "messages.watcher-recovery-failure", "&cYour watcher state could not be fully restored. Staff have been notified.");
            return false;
        } finally {
            if (sessions.values().stream().noneMatch(value -> value.phase() == SpectatorSessionPhase.ACTIVE)) {
                cancelEnforcementTask();
            }
        }
    }

    public void restoreAllOnline(String reason) {
        for (SpectatorSession session : List.copyOf(sessions.values())) {
            Player player = Bukkit.getPlayer(session.playerId());
            if (player != null && player.isOnline()) {
                restore(player, reason, false);
            }
        }
    }

    public boolean recoverOnJoin(Player player) {
        requirePrimaryThread();
        UUID playerId = player.getUniqueId();
        SpectatorSession session = sessions.get(playerId);
        if (session == null) {
            session = store.load(playerId).orElse(null);
            if (session != null) {
                sessions.put(playerId, session);
            }
        }
        if (session != null) {
            boolean restored = restore(player, "join-recovery", false);
            send(player, restored ? "messages.watcher-state-recovered" : "messages.watcher-recovery-failure",
                restored ? "&aYour unfinished watcher session was safely recovered." : "&cYour watcher session could not be fully recovered. Staff have been notified.");
            return restored;
        }
        if (hasRecoveryMarker(player)) {
            return applyMissingSessionFallback(player);
        }
        return true;
    }

    public boolean recoverByAdmin(Player administrator, Player target) {
        requirePrimaryThread();
        boolean found = sessions.containsKey(target.getUniqueId()) || store.exists(target.getUniqueId()) || hasRecoveryMarker(target);
        boolean result = found && (sessions.containsKey(target.getUniqueId()) || store.exists(target.getUniqueId())
            ? restore(target, "admin-recovery-by-" + administrator.getName(), false)
            : applyMissingSessionFallback(target));
        forceVisible(target);
        plugin.getLogger().info("Watcher recovery administrator=" + administrator.getName() + ", target=" + target.getUniqueId() + ", found=" + found + ", success=" + result);
        return found && result;
    }

    public boolean hasRecoverableSession(UUID playerId) {
        return sessions.containsKey(playerId) || store.exists(playerId);
    }

    public boolean isActiveWatcher(UUID playerId) {
        SpectatorSession session = playerId == null ? null : sessions.get(playerId);
        return session != null && session.phase() == SpectatorSessionPhase.ACTIVE;
    }

    public boolean shouldBlockAction(Player player) {
        return player != null && isActiveWatcher(player.getUniqueId());
    }

    public boolean isAllowedCommand(String rawCommand) {
        String normalized = normalizeCommand(rawCommand);
        if (BUILT_IN_SAFE_COMMANDS.contains(normalized)) {
            return true;
        }
        for (String allowed : allowedCommands) {
            if (normalized.equals(allowed) || normalized.startsWith(allowed + " ")) {
                return true;
            }
        }
        return false;
    }

    public boolean consumeTeleportAllowance(UUID playerId, Location destination) {
        TypedTeleportAllowance allowance = teleportAllowances.remove(playerId);
        return allowance != null && allowance.matches(destination, System.currentTimeMillis());
    }

    public boolean shouldBlockTeleport(Player player) {
        return shouldBlockAction(player);
    }

    public boolean isInsideBoundary(Location location) {
        Location center = spectatorSpawn();
        if (location == null || center == null || location.getWorld() == null || center.getWorld() == null
            || !location.getWorld().getUID().equals(center.getWorld().getUID())) {
            return false;
        }
        double x = location.getX() - center.getX();
        double z = location.getZ() - center.getZ();
        return (x * x) + (z * z) <= horizontalRadius * horizontalRadius
            && Math.abs(location.getY() - center.getY()) <= verticalRadius;
    }

    public void enforce(Player player) {
        if (!shouldBlockAction(player)) {
            return;
        }
        if (player.getGameMode() != GameMode.ADVENTURE) {
            player.setGameMode(GameMode.ADVENTURE);
        }
        if (!player.getAllowFlight()) {
            player.setAllowFlight(true);
        }
        if (!player.isFlying()) {
            player.setFlying(true);
        }
        player.setCollidable(false);
        player.setCanPickupItems(false);
        emptyWatcherInventory(player);
        dismount(player);
        applyVisibility(player);
        displayManager.enforce(player);
        if (!isInsideBoundary(player.getLocation())) {
            returnToBoundary(player);
        }
    }

    public void handleViewerJoin(Player viewer) {
        for (SpectatorSession session : sessions.values()) {
            if (session.phase() != SpectatorSessionPhase.ACTIVE) {
                continue;
            }
            Player watcher = Bukkit.getPlayer(session.playerId());
            if (watcher == null || !watcher.isOnline()) {
                continue;
            }
            if (participantIds.get().contains(viewer.getUniqueId())) {
                viewer.hidePlayer(plugin, watcher);
            } else {
                viewer.showPlayer(plugin, watcher);
            }
        }
    }

    public void applyVisibility(Player watcher) {
        Set<UUID> participants = participantIds.get();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (participants.contains(viewer.getUniqueId())) {
                viewer.hidePlayer(plugin, watcher);
            } else {
                viewer.showPlayer(plugin, watcher);
            }
        }
    }

    public void forceVisible(Player watcher) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.showPlayer(plugin, watcher);
        }
    }

    public void visibilitySafetyScrub() {
        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        for (Player viewer : players) {
            for (Player target : players) {
                viewer.showPlayer(plugin, target);
            }
        }
    }

    public void sendActionBlocked(Player player) {
        sendWithCooldown(player, actionMessageCooldowns, 1_500L, "messages.watcher-action-blocked", "&cYou cannot do that while watching a duel.");
    }

    public void sendTeleportBlocked(Player player) {
        sendWithCooldown(player, teleportMessageCooldowns, 1_500L, "messages.watcher-teleport-blocked", "&cLeave watch mode before teleporting.");
    }

    public void sendExternalTeleportIntoActiveDuelBlocked(Player player) {
        sendWithCooldown(player, teleportMessageCooldowns, 1_500L, "messages.teleport-active-duel-area-blocked",
            "&cYou cannot teleport into an active duel or spectator area.");
    }

    private SpectatorSession capture(Player player) {
        return new SpectatorSession(
            player.getUniqueId(), player.getName(), System.currentTimeMillis(), SpectatorSessionPhase.PREPARED,
            player.getGameMode(), StoredLocation.capture(player.getLocation()), player.getAllowFlight(), player.isFlying(),
            player.getFlySpeed(), player.getWalkSpeed(), player.isCollidable(), player.getCanPickupItems(),
            player.getInventory().getStorageContents(), player.getInventory().getArmorContents(),
            player.getInventory().getItemInOffHand(), player.getItemOnCursor(), player.getHealth(), player.getFoodLevel(),
            player.getSaturation(), player.getTotalExperience(), player.getLevel(), player.getExp(), player.getFireTicks(),
            new ArrayList<>(player.getActivePotionEffects()), player.getFallDistance(), player.getVelocity()
        );
    }

    private void applyWatchState(Player player) {
        emptyWatcherInventory(player);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setCollidable(false);
        player.setCanPickupItems(false);
        player.setVelocity(new Vector());
        player.setFallDistance(0F);
        player.setFireTicks(0);
    }

    private void applyOriginalState(Player player, SpectatorSession session) {
        player.leaveVehicle();
        player.setGameMode(session.originalGameMode() == GameMode.SPECTATOR ? GameMode.SURVIVAL : session.originalGameMode());
        player.setAllowFlight(session.originalAllowFlight());
        if (session.originalAllowFlight()) {
            player.setFlying(session.originalFlying());
        } else {
            player.setFlying(false);
        }
        player.setFlySpeed(session.originalFlySpeed());
        player.setWalkSpeed(session.originalWalkSpeed());
        player.setCollidable(session.originalCollidable());
        player.setCanPickupItems(session.originalCanPickupItems());
        SpectatorInventoryRestorer.replace(player, session);
        player.setHealth(Math.max(0.001D, Math.min(player.getMaxHealth(), session.health())));
        player.setFoodLevel(session.foodLevel());
        player.setSaturation(session.saturation());
        player.setTotalExperience(session.totalExperience());
        player.setLevel(session.level());
        player.setExp(session.experienceProgress());
        player.setFireTicks(session.fireTicks());
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        for (PotionEffect effect : session.potionEffects()) {
            player.addPotionEffect(effect, true);
        }
        player.setFallDistance(session.fallDistance());
        player.setVelocity(session.velocity());
        Location destination = safeRestoreDestination(session);
        if (destination == null || !teleport(player, destination, TeleportAllowanceReason.WATCH_RESTORE)) {
            throw new IllegalStateException("No safe watcher restoration destination was available");
        }
        player.updateInventory();
    }

    private Location safeRestoreDestination(SpectatorSession session) {
        Location original = session.originalLocation() == null ? null : session.originalLocation().resolve();
        ArenaDefinition arena = arenaSupplier.get();
        if (original != null && (arena == null || !arena.contains(original))) {
            return original;
        }
        return exitSupplier.get();
    }

    private void emptyWatcherInventory(Player player) {
        if (!hasWatcherItems(player)) {
            return;
        }
        player.getInventory().setStorageContents(new ItemStack[player.getInventory().getStorageContents().length]);
        player.getInventory().setArmorContents(new ItemStack[player.getInventory().getArmorContents().length]);
        player.getInventory().setItemInOffHand(null);
        player.setItemOnCursor(null);
        player.updateInventory();
    }

    private boolean hasWatcherItems(Player player) {
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && !item.getType().isAir()) {
                return true;
            }
        }
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item != null && !item.getType().isAir()) {
                return true;
            }
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        ItemStack cursor = player.getItemOnCursor();
        return offhand != null && !offhand.getType().isAir() || cursor != null && !cursor.getType().isAir();
    }

    private void dismount(Player player) {
        player.leaveVehicle();
        for (Entity passenger : List.copyOf(player.getPassengers())) {
            player.removePassenger(passenger);
        }
    }

    private void returnToBoundary(Player player) {
        Location spawn = spectatorSpawn();
        if (spawn == null) {
            restore(player, "missing-spectator-spawn", false);
            return;
        }
        player.setVelocity(new Vector());
        teleport(player, spawn, TeleportAllowanceReason.WATCH_BOUNDARY_RETURN);
        sendWithCooldown(player, boundaryMessageCooldowns, boundaryMessageCooldownMillis,
            "messages.watcher-boundary-return", "&eYou were returned to the spectator area.");
    }

    private boolean teleport(Player player, Location destination, TeleportAllowanceReason reason) {
        if (destination == null || destination.getWorld() == null) {
            return false;
        }
        TypedTeleportAllowance allowance = TypedTeleportAllowance.forDestination(reason, destination, System.currentTimeMillis() + TELEPORT_ALLOWANCE_MILLIS);
        teleportAllowances.put(player.getUniqueId(), allowance);
        boolean teleported = player.teleport(destination);
        if (!teleported) {
            teleportAllowances.remove(player.getUniqueId(), allowance);
        }
        return teleported;
    }

    private void ensureEnforcementTask() {
        if (sessions.values().stream().noneMatch(session -> session.phase() == SpectatorSessionPhase.ACTIVE) || enforcementTask != null) {
            return;
        }
        enforcementTask = Bukkit.getScheduler().runTaskTimer(plugin, this::enforceAll, 1L, enforcementIntervalTicks);
    }

    private void restartEnforcementTask() {
        cancelEnforcementTask();
        ensureEnforcementTask();
    }

    private void enforceAll() {
        for (SpectatorSession session : List.copyOf(sessions.values())) {
            if (session.phase() != SpectatorSessionPhase.ACTIVE) {
                continue;
            }
            Player player = Bukkit.getPlayer(session.playerId());
            if (player != null && player.isOnline()) {
                enforce(player);
            }
        }
    }

    private void cancelEnforcementTask() {
        if (enforcementTask != null) {
            enforcementTask.cancel();
            enforcementTask = null;
        }
    }

    private boolean addRecoveryMarker(Player player) {
        try {
            player.getPersistentDataContainer().set(recoveryMarkerKey, PersistentDataType.BYTE, (byte) 1);
            player.saveData();
            return hasRecoveryMarker(player);
        } catch (RuntimeException ex) {
            player.getPersistentDataContainer().remove(recoveryMarkerKey);
            plugin.getLogger().log(Level.SEVERE, "Failed to add spectator recovery marker for " + player.getUniqueId() + " (" + player.getName() + ")", ex);
            return false;
        }
    }

    private void removeRecoveryMarker(Player player) {
        player.getPersistentDataContainer().remove(recoveryMarkerKey);
    }

    private boolean hasRecoveryMarker(Player player) {
        PersistentDataContainer container = player.getPersistentDataContainer();
        return container.has(recoveryMarkerKey, PersistentDataType.BYTE);
    }

    private boolean applyMissingSessionFallback(Player player) {
        forceVisible(player);
        displayManager.restore(player);
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        if (player.getGameMode() != GameMode.CREATIVE) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
        player.setCollidable(true);
        player.setCanPickupItems(true);
        Location exit = exitSupplier.get();
        ArenaDefinition arena = arenaSupplier.get();
        boolean requiresEvacuation = arena != null && arena.contains(player.getLocation());
        if (requiresEvacuation && (exit == null || !teleport(player, exit, TeleportAllowanceReason.WATCH_RECOVERY))) {
            plugin.getLogger().severe("Could not evacuate missing-session watcher " + player.getUniqueId() + " (" + player.getName() + ") from the arena; recovery marker retained.");
            return false;
        }
        removeRecoveryMarker(player);
        try {
            player.saveData();
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save missing-session fallback for " + player.getUniqueId(), ex);
            return false;
        }
        plugin.getLogger().severe("A spectator recovery marker existed without a readable session for " + player.getUniqueId()
            + " (" + player.getName() + "). Visibility, movement, collision and pickup were normalized without changing inventory; inspect spectator-sessions and loadout-archives.yml manually.");
        send(player, "messages.watcher-recovery-failure", "&cWatcher recovery data was missing. Your inventory was left untouched and staff have been notified.");
        return true;
    }

    private Location spectatorSpawn() {
        ArenaDefinition arena = arenaSupplier.get();
        return arena == null ? null : arena.spectator();
    }

    private void sendWithCooldown(Player player, Map<UUID, Long> cooldowns, long cooldownMillis, String path, String fallback) {
        long now = System.currentTimeMillis();
        Long previous = cooldowns.put(player.getUniqueId(), now);
        if (previous == null || now - previous >= cooldownMillis) {
            send(player, path, fallback);
        }
    }

    private void send(Player player, String path, String fallback) {
        FileConfiguration config = plugin.getConfig();
        String prefix = color(config.getString("messages.prefix", "&6[Duel]&r "));
        String message = config.getString(path, fallback);
        player.sendMessage(prefix + color(message == null || message.isBlank() ? fallback : message));
    }

    private String normalizeCommand(String raw) {
        return raw == null ? "" : raw.toLowerCase(Locale.ROOT).replaceFirst("^/", "").trim().replaceAll("\\s+", " ");
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }

    private void requirePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Spectator state mutations must run on the primary thread.");
        }
    }
}
