package dev.minecraft.warzoneduels.app;

import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Compatibility hook for NotBounties WantedTags as implemented by upstream
 * commit 2382587. It invokes only the confirmed public static lifecycle methods.
 */
final class NotBountiesWatcherDisplayHook implements WatcherDisplayHook {
    private static final String PLUGIN_NAME = "NotBounties";
    private static final String WANTED_TAGS_CLASS = "me.jadenp.notbounties.features.settings.display.WantedTags";

    private final WarzoneDuelsPlugin plugin;
    private Plugin resolvedPlugin;
    private Method removeWantedTag;
    private Method addWantedTag;

    NotBountiesWatcherDisplayHook(WarzoneDuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void hideForWatcher(Player watcher) {
        invoke(removeWantedTag, watcher.getUniqueId());
    }

    @Override
    public void restoreForWatcher(Player watcher) {
        invoke(addWantedTag, watcher);
    }

    @Override
    public void refreshActiveWatcher(Player watcher) {
        if (resolvedPlugin != Bukkit.getPluginManager().getPlugin(PLUGIN_NAME)) {
            clearCachedMethods();
        }
        hideForWatcher(watcher);
    }

    private void invoke(Method method, Object argument) {
        if (!resolveMethods()) {
            return;
        }
        try {
            (method == null ? (argument instanceof UUID ? removeWantedTag : addWantedTag) : method).invoke(null, argument);
        } catch (ReflectiveOperationException ex) {
            clearCachedMethods();
            plugin.getLogger().fine("NotBounties wanted-tag compatibility hook failed: " + ex.getMessage());
        }
    }

    private boolean resolveMethods() {
        Plugin notBounties = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (notBounties == null || !notBounties.isEnabled()) {
            clearCachedMethods();
            return false;
        }
        if (notBounties == resolvedPlugin && removeWantedTag != null && addWantedTag != null) {
            return true;
        }
        try {
            Class<?> wantedTags = Class.forName(WANTED_TAGS_CLASS, true, notBounties.getClass().getClassLoader());
            removeWantedTag = wantedTags.getMethod("removeWantedTag", UUID.class);
            addWantedTag = wantedTags.getMethod("addWantedTag", Player.class);
            resolvedPlugin = notBounties;
            return true;
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().fine("NotBounties WantedTags API is unavailable: " + ex.getMessage());
            clearCachedMethods();
            return false;
        }
    }

    private void clearCachedMethods() {
        resolvedPlugin = null;
        removeWantedTag = null;
        addWantedTag = null;
    }
}
