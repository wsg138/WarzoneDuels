package dev.minecraft.warzoneduels.app;

import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;

final class EnthusiaTagsWatcherDisplayHook implements WatcherDisplayHook {
    private static final String PLUGIN_NAME = "EnthusiaTags";
    private static final String SERVICE_CLASS = "org.enthusia.tags.api.TagVisibilityService";
    private static final String OWNER = "WarzoneDuels:duel-watcher";

    private final WarzoneDuelsPlugin plugin;
    private boolean failureLogged;

    EnthusiaTagsWatcherDisplayHook(WarzoneDuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void hideForWatcher(Player watcher) {
        invoke("suppress", watcher);
    }

    @Override
    public void restoreForWatcher(Player watcher) {
        invoke("unsuppress", watcher);
    }

    @Override
    public void refreshActiveWatcher(Player watcher) {
        hideForWatcher(watcher);
    }

    private void invoke(String methodName, Player watcher) {
        if (watcher == null) {
            return;
        }
        Plugin other = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (other == null || !other.isEnabled()) {
            return;
        }
        try {
            Class<?> serviceType = Class.forName(SERVICE_CLASS, true, other.getClass().getClassLoader());
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object service = Bukkit.getServicesManager().load((Class) serviceType);
            if (service == null) {
                return;
            }
            Method method = findOwnerMethod(serviceType, methodName);
            method.invoke(service, watcher.getUniqueId(), OWNER);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            logFailure("EnthusiaTags watcher visibility integration is incompatible; continuing without it.", ex);
        }
    }

    private Method findOwnerMethod(Class<?> serviceType, String methodName) throws NoSuchMethodException {
        for (Method method : serviceType.getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (method.getName().equals(methodName)
                && parameters.length == 2
                && parameters[0] == UUID.class
                && parameters[1].isAssignableFrom(String.class)) {
                return method;
            }
        }
        throw new NoSuchMethodException(serviceType.getName() + "." + methodName + "(UUID, owner)");
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
