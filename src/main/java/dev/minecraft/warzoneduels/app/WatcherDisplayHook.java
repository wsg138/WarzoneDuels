package dev.minecraft.warzoneduels.app;

import org.bukkit.entity.Player;

interface WatcherDisplayHook {
    void hideForWatcher(Player watcher);

    void restoreForWatcher(Player watcher);

    void refreshActiveWatcher(Player watcher);
}
