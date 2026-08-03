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
    private boolean failureLogged;

    public CombatLogXCombatTagPort(WarzoneDuelsPlugin plugin, DuelService duelService) {
        this.plugin = plugin;
        this.duelService = duelService;
    }

    @Override
    public void enable() {
        Plugin other = Bukkit.getPluginManager().getPlugin("CombatLogX");
        if (other == null || !other.isEnabled()) {
            return;
        }
        try {
            ClassLoader loader = other.getClass().getClassLoader();
            Object resolvedManager = other.getClass().getMethod("getCombatManager").invoke(other);
            if (resolvedManager == null) {
                return;
            }

            Class<?> untagReasonClass = Class.forName(UNTAG_REASON, true, loader);
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object resolvedExpireReason = Enum.valueOf((Class) untagReasonClass.asSubclass(Enum.class), "EXPIRE");
            Method resolvedIsInCombat = resolvedManager.getClass().getMethod("isInCombat", Player.class);
            Method resolvedUntag = resolvedManager.getClass().getMethod("untag", Player.class, untagReasonClass);

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
            clearBridgeState();
            logFailure("CombatLogX API is incompatible; duel combat-tag integration is disabled.", ex);
        }
    }

    @Override
    public void disable() {
        clearBridgeState();
        if (registered) {
            HandlerList.unregisterAll(this);
            registered = false;
        }
    }

    @Override
    public boolean isInCombat(Player player) {
        if (combatManager == null || isInCombatMethod == null || player == null || !player.isOnline()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(isInCombatMethod.invoke(combatManager, player));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            logFailure("CombatLogX combat lookup failed; treating the player as not tagged.", ex);
            return false;
        }
    }

    @Override
    public void clearCombatState(Player player) {
        if (combatManager == null || untagMethod == null || expireReason == null || player == null || !player.isOnline()) {
            return;
        }
        try {
            if (Boolean.TRUE.equals(isInCombatMethod.invoke(combatManager, player))) {
                untagMethod.invoke(combatManager, player, expireReason);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            logFailure("CombatLogX untag failed; duel startup will continue without clearing the external tag.", ex);
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
            logFailure("CombatLogX pre-tag interception failed.", ex);
        }
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
