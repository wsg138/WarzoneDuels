package dev.minecraft.warzoneduels.adapter.bukkit.integration;

import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import dev.minecraft.warzoneduels.app.DuelService;
import dev.minecraft.warzoneduels.port.CombatTagPort;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/** Optional CombatLogX bridge that avoids hard binary linkage to its API and BlueSlimeCore. */
public final class CombatLogXCombatTagPort implements CombatTagPort, Listener {
    private static final String COMBAT_LOG_X_API = "com.github.sirblobman.combatlogx.api.ICombatLogX";
    private static final String COMBAT_MANAGER_API = "com.github.sirblobman.combatlogx.api.manager.ICombatManager";
    private static final String PRE_TAG_EVENT = "com.github.sirblobman.combatlogx.api.event.PlayerPreTagEvent";
    private static final String UNTAG_REASON = "com.github.sirblobman.combatlogx.api.object.UntagReason";

    private final WarzoneDuelsPlugin plugin;
    private final DuelService duelService;

    private Object combatManager;
    private Object expireReason;
    private Method isInCombatMethod;
    private Method untagMethod;
    private Method eventPlayerMethod;
    private boolean registered;
    private boolean failClosed;
    private boolean failureLogged;

    public CombatLogXCombatTagPort(WarzoneDuelsPlugin plugin, DuelService duelService) {
        this.plugin = plugin;
        this.duelService = duelService;
    }

    @Override
    public void enable() {
        failClosed = false;
        Plugin other = Bukkit.getPluginManager().getPlugin("CombatLogX");
        if (other == null || !other.isEnabled()) {
            return;
        }
        try {
            ClassLoader loader = other.getClass().getClassLoader();
            Class<?> apiType = Class.forName(COMBAT_LOG_X_API, true, loader);
            Class<?> managerType = Class.forName(COMBAT_MANAGER_API, true, loader);
            if (!apiType.isInstance(other)) {
                throw new IllegalStateException("CombatLogX plugin does not implement its public API");
            }
            Object resolvedManager = apiType.getMethod("getCombatManager").invoke(other);
            if (resolvedManager == null || !managerType.isInstance(resolvedManager)) {
                throw new IllegalStateException("CombatLogX did not expose a compatible combat manager");
            }

            Class<?> untagReasonClass = Class.forName(UNTAG_REASON, true, loader);
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object resolvedExpireReason = Enum.valueOf((Class) untagReasonClass.asSubclass(Enum.class), "EXPIRE");
            Method resolvedIsInCombat = managerType.getMethod("isInCombat", Player.class);
            Method resolvedUntag = managerType.getMethod("untag", Player.class, untagReasonClass);

            Class<?> rawEventClass = Class.forName(PRE_TAG_EVENT, true, loader);
            if (!Event.class.isAssignableFrom(rawEventClass)) {
                throw new IllegalStateException("PlayerPreTagEvent is not a Bukkit event");
            }
            Method resolvedEventPlayer = rawEventClass.getMethod("getPlayer");

            combatManager = resolvedManager;
            expireReason = resolvedExpireReason;
            isInCombatMethod = resolvedIsInCombat;
            untagMethod = resolvedUntag;
            eventPlayerMethod = resolvedEventPlayer;

            if (!registered) {
                @SuppressWarnings("unchecked")
                Class<? extends Event> eventClass = (Class<? extends Event>) rawEventClass;
                Bukkit.getPluginManager().registerEvent(
                    eventClass,
                    this,
                    EventPriority.HIGHEST,
                    (listener, event) -> handlePreTagEvent(event),
                    plugin,
                    true
                );
                registered = true;
            }
            plugin.getLogger().info("Hooked WarzoneDuels into CombatLogX combat tagging.");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            markUnavailable("CombatLogX API is incompatible; new duel entry is blocked until the integration is restored.", ex);
        }
    }

    @Override
    public void disable() {
        clearBridgeState();
        failClosed = false;
        if (registered) {
            HandlerList.unregisterAll(this);
            registered = false;
        }
    }

    @Override
    public boolean isInCombat(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (failClosed) {
            return true;
        }
        if (combatManager == null || isInCombatMethod == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(isInCombatMethod.invoke(combatManager, player));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            markUnavailable("CombatLogX combat lookup failed; new duel entry is now blocked.", ex);
            return true;
        }
    }

    @Override
    public void clearCombatState(Player player) {
        if (failClosed || combatManager == null || untagMethod == null || expireReason == null || player == null || !player.isOnline()) {
            return;
        }
        try {
            if (Boolean.TRUE.equals(isInCombatMethod.invoke(combatManager, player))) {
                untagMethod.invoke(combatManager, player, expireReason);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            markUnavailable("CombatLogX untag failed; new duel entry is now blocked.", ex);
        }
    }

    private void handlePreTagEvent(Event event) {
        if (eventPlayerMethod == null || !(event instanceof Cancellable cancellable)) {
            return;
        }
        try {
            Object playerValue = eventPlayerMethod.invoke(event);
            if (playerValue instanceof Player player && duelService.isParticipantRestricted(player.getUniqueId())) {
                cancellable.setCancelled(true);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            markUnavailable("CombatLogX pre-tag interception failed; new duel entry is now blocked.", ex);
        }
    }

    private void markUnavailable(String message, Throwable throwable) {
        clearBridgeState();
        failClosed = true;
        logFailure(message, throwable);
    }

    private void clearBridgeState() {
        combatManager = null;
        expireReason = null;
        isInCombatMethod = null;
        untagMethod = null;
        eventPlayerMethod = null;
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
