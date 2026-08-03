package dev.minecraft.warzoneduels.adapter.bukkit.listener;

import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import dev.minecraft.warzoneduels.app.DuelService;
import dev.minecraft.warzoneduels.domain.ArenaDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps a defeated duel participant outside the arena while the winner's
 * celebration is still using the active duel state.
 */
public final class DefeatedRespawnGuard implements Listener {
    private static final long RELEASE_CHECK_PERIOD_TICKS = 1L;
    private static final long RELEASE_TIMEOUT_PADDING_TICKS = 40L;

    private final WarzoneDuelsPlugin plugin;
    private final DuelService duelService;
    private final Set<UUID> guardedPlayerIds = ConcurrentHashMap.newKeySet();

    public DefeatedRespawnGuard(WarzoneDuelsPlugin plugin, DuelService duelService) {
        this.plugin = plugin;
        this.duelService = duelService;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDuelDeath(PlayerDeathEvent event) {
        UUID playerId = event.getEntity().getUniqueId();
        if (!duelService.isInActiveDuel(playerId)) {
            return;
        }
        guardedPlayerIds.add(playerId);
        startReleaseMonitor(playerId);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onGuardedMove(PlayerMoveEvent event) {
        if (isGuardActive(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onGuardedTeleport(PlayerTeleportEvent event) {
        if (!isGuardActive(event.getPlayer().getUniqueId())) {
            return;
        }
        ArenaDefinition arena = duelService.arena();
        Location destination = event.getTo();
        if (arena != null && destination != null && arena.contains(destination)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGuardedJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (!isGuardActive(playerId)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = event.getPlayer();
            if (player.isOnline() && isGuardActive(playerId)) {
                teleportToExit(player);
            }
        });
    }

    private boolean isGuardActive(UUID playerId) {
        if (!guardedPlayerIds.contains(playerId)) {
            return false;
        }
        if (duelService.hasActiveDuel()) {
            return true;
        }
        guardedPlayerIds.remove(playerId);
        return false;
    }

    private void startReleaseMonitor(UUID playerId) {
        long configuredVictoryTicks = Math.max(0L,
            plugin.getConfig().getLong("settings.victory-moment-seconds", 6L) * 20L);
        long timeoutTicks = configuredVictoryTicks + RELEASE_TIMEOUT_PADDING_TICKS;

        new BukkitRunnable() {
            private long elapsedTicks;

            @Override
            public void run() {
                if (!guardedPlayerIds.contains(playerId)) {
                    cancel();
                    return;
                }
                if (!duelService.hasActiveDuel() || elapsedTicks >= timeoutTicks) {
                    guardedPlayerIds.remove(playerId);
                    cancel();
                    return;
                }
                elapsedTicks += RELEASE_CHECK_PERIOD_TICKS;
            }
        }.runTaskTimer(plugin, RELEASE_CHECK_PERIOD_TICKS, RELEASE_CHECK_PERIOD_TICKS);
    }

    private void teleportToExit(Player player) {
        ArenaDefinition arena = duelService.arena();
        if (arena != null) {
            duelService.teleportSafe(player, arena.exit());
        }
    }
}
