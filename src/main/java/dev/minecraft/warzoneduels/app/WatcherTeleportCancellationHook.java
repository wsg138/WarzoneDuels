package dev.minecraft.warzoneduels.app;

import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;

/** Optional bridge to EnthusiaTeleport's Bukkit service without hard binary linkage. */
final class WatcherTeleportCancellationHook {
    private static final String PLUGIN_NAME = "EnthusiaTeleport";
    private static final String SERVICE_CLASS = "org.enthusia.teleport.api.TeleportApi";
    private static final String REASON_CLASS = "org.enthusia.teleport.api.CancelReason";

    private final WarzoneDuelsPlugin plugin;
    private boolean failureLogged;

    WatcherTeleportCancellationHook(WarzoneDuelsPlugin plugin) {
        this.plugin = plugin;
    }

    void cancelInvolving(UUID playerId) {
        if (playerId == null) {
            return;
        }
        Plugin other = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (other == null || !other.isEnabled()) {
            plugin.getLogger().fine("EnthusiaTeleport is unavailable; no pending teleport state was cancelled for watcher " + playerId);
            return;
        }
        try {
            ClassLoader loader = other.getClass().getClassLoader();
            Class<?> serviceType = Class.forName(SERVICE_CLASS, true, loader);
            Class<?> reasonType = Class.forName(REASON_CLASS, true, loader);
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object service = Bukkit.getServicesManager().load((Class) serviceType);
            if (service == null) {
                return;
            }
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object reason = Enum.valueOf((Class) reasonType.asSubclass(Enum.class), "DUEL_SPECTATE");
            Method cancellation = serviceType.getMethod("cancelAllRequestsInvolving", UUID.class, reasonType);
            cancellation.invoke(service, playerId, reason);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            logFailure("EnthusiaTeleport watcher cancellation integration is incompatible; watcher recovery safeguards remain active.", ex);
        }
    }

    private void logFailure(String message, Throwable throwable) {
        if (failureLogged) {
            return;
        }
        failureLogged = true;
        String detail = throwable.getMessage();
        plugin.getLogger().warning(message + (detail == null || detail.isBlank() ? "" : " " + detail));
    }
}
