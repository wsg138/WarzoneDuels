package dev.minecraft.warzoneduels.app;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.enthusia.tags.api.TagVisibilityService;

final class EnthusiaTagsWatcherDisplayHook implements WatcherDisplayHook {
    private static final String OWNER = "WarzoneDuels:duel-watcher";

    @Override
    public void hideForWatcher(Player watcher) {
        TagVisibilityService service = Bukkit.getServicesManager().load(TagVisibilityService.class);
        if (service != null) {
            service.suppress(watcher.getUniqueId(), OWNER);
        }
    }

    @Override
    public void restoreForWatcher(Player watcher) {
        TagVisibilityService service = Bukkit.getServicesManager().load(TagVisibilityService.class);
        if (service != null) {
            service.unsuppress(watcher.getUniqueId(), OWNER);
        }
    }

    @Override
    public void refreshActiveWatcher(Player watcher) {
        hideForWatcher(watcher);
    }
}
