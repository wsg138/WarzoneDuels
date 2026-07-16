package dev.minecraft.warzoneduels.app;

import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import org.bukkit.Bukkit;
import org.enthusia.teleport.api.CancelReason;
import org.enthusia.teleport.api.TeleportApi;

import java.util.UUID;

/** Optional bridge to EnthusiaTeleport's stable Bukkit service API. */
final class WatcherTeleportCancellationHook {
    private final WarzoneDuelsPlugin plugin;

    WatcherTeleportCancellationHook(WarzoneDuelsPlugin plugin) {
        this.plugin = plugin;
    }

    void cancelInvolving(UUID playerId) {
        TeleportApi service = Bukkit.getServicesManager().load(TeleportApi.class);
        if (service == null) {
            plugin.getLogger().fine("EnthusiaTeleport is unavailable; no pending teleport state was cancelled for watcher " + playerId);
            return;
        }
        service.cancelAllRequestsInvolving(playerId, CancelReason.DUEL_SPECTATE);
    }
}
