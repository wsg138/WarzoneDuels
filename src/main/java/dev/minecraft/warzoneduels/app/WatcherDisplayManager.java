package dev.minecraft.warzoneduels.app;

import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.logging.Level;

final class WatcherDisplayManager {
    private final WarzoneDuelsPlugin plugin;
    private final List<WatcherDisplayHook> hooks;

    WatcherDisplayManager(WarzoneDuelsPlugin plugin) {
        this.plugin = plugin;
        this.hooks = List.of(new EnthusiaTagsWatcherDisplayHook(), new NotBountiesWatcherDisplayHook(plugin));
    }

    void suppress(Player watcher) {
        forEach(hook -> hook.hideForWatcher(watcher), "suppress", watcher);
    }

    void restore(Player watcher) {
        forEach(hook -> hook.restoreForWatcher(watcher), "restore", watcher);
    }

    void enforce(Player watcher) {
        forEach(hook -> hook.refreshActiveWatcher(watcher), "refresh", watcher);
    }

    private void forEach(HookOperation operation, String action, Player watcher) {
        for (WatcherDisplayHook hook : hooks) {
            try {
                operation.apply(hook);
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to " + action + " watcher display hook "
                    + hook.getClass().getSimpleName() + " for " + watcher.getUniqueId(), ex);
            }
        }
    }

    @FunctionalInterface
    private interface HookOperation {
        void apply(WatcherDisplayHook hook);
    }
}
