package dev.minecraft.warzoneduels.adapter.bukkit.integration;

import dev.minecraft.warzoneduels.port.PermissionPort;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class LuckPermsPermissionPort implements PermissionPort {
    private final JavaPlugin plugin;
    private final boolean luckPermsAvailable;

    public LuckPermsPermissionPort(JavaPlugin plugin) {
        this.plugin = plugin;
        this.luckPermsAvailable = Bukkit.getPluginManager().getPlugin("LuckPerms") != null;
        if (luckPermsAvailable) {
            plugin.getLogger().info("LuckPerms detected — deep permission integration enabled.");
        } else {
            plugin.getLogger().info("LuckPerms not found — falling back to Bukkit permissions.");
        }
    }

    @Override
    public boolean has(Player player, String permission) {
        if (luckPermsAvailable) {
            try {
                LuckPerms luckPerms = LuckPermsProvider.get();
                User user = luckPerms.getUserManager().getUser(player.getUniqueId());
                if (user != null) {
                    return user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "LuckPerms permission check failed for " + player.getName(), e);
            }
        }
        return player.hasPermission(permission);
    }
}
