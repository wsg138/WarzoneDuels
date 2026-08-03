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
    private final Object economy;
    private final boolean wagersEnabled;
    private final Logger logger;
    private final Method hasMethod;
    private final Method withdrawMethod;
    private final Method depositMethod;
    private final Method transactionSuccessMethod;
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
                Class<?> type = resolvedEconomy.getClass();
                resolvedHas = type.getMethod("has", OfflinePlayer.class, double.class);
                resolvedWithdraw = type.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
                resolvedDeposit = type.getMethod("depositPlayer", OfflinePlayer.class, double.class);
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
    }

    @Override
    public boolean isEnabled() {
        return wagersEnabled
            && economy != null
            && hasMethod != null
            && withdrawMethod != null
            && depositMethod != null
            && transactionSuccessMethod != null;
    }

    @Override
    public boolean has(Player player, double amount) {
        if (!isEnabled() || player == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(hasMethod.invoke(economy, player, amount));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            logFailure("Vault balance check failed; wagers are unavailable.", ex);
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
            logFailure("Vault withdrawal failed; the duel wager was not accepted.", ex);
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
            depositMethod.invoke(economy, player, amount);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            logFailure("Vault deposit failed; check the economy provider before enabling wagers.", ex);
        }
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
