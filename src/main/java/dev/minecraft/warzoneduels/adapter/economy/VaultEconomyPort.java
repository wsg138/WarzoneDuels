package dev.minecraft.warzoneduels.adapter.economy;

import dev.minecraft.warzoneduels.port.EconomyPort;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

/** Optional Vault bridge that avoids hard binary linkage to VaultAPI. */
public final class VaultEconomyPort implements EconomyPort {
    private static final String ECONOMY_API = "net.milkbowl.vault.economy.Economy";

    private final Object economy;
    private final boolean wagersEnabled;
    private final Logger logger;
    private final Method hasMethod;
    private final Method withdrawMethod;
    private final Method depositMethod;
    private final Method transactionSuccessMethod;
    private boolean operational;
    private boolean failureLogged;

    public VaultEconomyPort(Object economy, boolean wagersEnabled, Logger logger) {
        this.wagersEnabled = wagersEnabled;
        this.logger = logger;

        Object resolvedEconomy = economy;
        Method resolvedHas = null;
        Method resolvedWithdraw = null;
        Method resolvedDeposit = null;
        Method resolvedTransactionSuccess = null;
        if (resolvedEconomy != null) {
            try {
                Class<?> economyApi = findNamedType(resolvedEconomy.getClass(), ECONOMY_API);
                if (economyApi == null) {
                    throw new IllegalStateException("Vault provider does not implement the Economy API");
                }
                resolvedHas = economyApi.getMethod("has", OfflinePlayer.class, double.class);
                resolvedWithdraw = economyApi.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
                resolvedDeposit = economyApi.getMethod("depositPlayer", OfflinePlayer.class, double.class);
                resolvedTransactionSuccess = resolvedWithdraw.getReturnType().getMethod("transactionSuccess");
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
                logFailure("Vault economy API is incompatible; wagers are disabled.", ex);
                resolvedEconomy = null;
            }
        }
        this.economy = resolvedEconomy;
        this.hasMethod = resolvedHas;
        this.withdrawMethod = resolvedWithdraw;
        this.depositMethod = resolvedDeposit;
        this.transactionSuccessMethod = resolvedTransactionSuccess;
        this.operational = resolvedEconomy != null
            && resolvedHas != null
            && resolvedWithdraw != null
            && resolvedDeposit != null
            && resolvedTransactionSuccess != null;
    }

    @Override
    public boolean isEnabled() {
        return wagersEnabled && operational;
    }

    @Override
    public boolean has(Player player, double amount) {
        if (!isEnabled() || player == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(hasMethod.invoke(economy, player, amount));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            markUnavailable("Vault balance check failed; wagers are now disabled.", ex);
            return false;
        }
    }

    @Override
    public boolean withdraw(Player player, double amount) {
        if (!isEnabled() || player == null) {
            return false;
        }
        try {
            Object response = withdrawMethod.invoke(economy, player, amount);
            return response != null && Boolean.TRUE.equals(transactionSuccessMethod.invoke(response));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            markUnavailable("Vault withdrawal failed; wagers are now disabled.", ex);
            return false;
        }
    }

    @Override
    public void deposit(Player player, double amount) {
        if (player != null) {
            deposit((OfflinePlayer) player, amount);
        }
    }

    @Override
    public void deposit(UUID playerId, double amount) {
        if (playerId != null) {
            deposit(Bukkit.getOfflinePlayer(playerId), amount);
        }
    }

    private void deposit(OfflinePlayer player, double amount) {
        if (!isEnabled() || player == null) {
            return;
        }
        try {
            Object response = depositMethod.invoke(economy, player, amount);
            if (response == null || !Boolean.TRUE.equals(transactionSuccessMethod.invoke(response))) {
                markUnavailable("Vault rejected a payout or refund; wagers are now disabled.",
                    new IllegalStateException("Vault deposit transaction was unsuccessful"));
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            markUnavailable("Vault deposit failed; wagers are now disabled.", ex);
        }
    }

    private Class<?> findNamedType(Class<?> type, String expectedName) {
        if (type == null) {
            return null;
        }
        if (type.getName().equals(expectedName)) {
            return type;
        }
        for (Class<?> implemented : type.getInterfaces()) {
            Class<?> match = findNamedType(implemented, expectedName);
            if (match != null) {
                return match;
            }
        }
        return findNamedType(type.getSuperclass(), expectedName);
    }

    private void markUnavailable(String message, Throwable throwable) {
        operational = false;
        logFailure(message, throwable);
    }

    private void logFailure(String message, Throwable throwable) {
        if (failureLogged || logger == null) {
            return;
        }
        failureLogged = true;
        String detail = throwable.getMessage();
        logger.warning(message + (detail == null || detail.isBlank() ? "" : " " + detail));
    }
}
