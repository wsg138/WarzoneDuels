package dev.minecraft.warzoneduels.permission;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class PermissionMessages {
    private PermissionMessages() {
    }

    public static void sendNoPermission(JavaPlugin plugin, CommandSender sender) {
        String prefix = plugin.getConfig().getString("messages.prefix", "&6[Duel]&r ");
        String message = plugin.getConfig().getString("messages.no-permission", "&cYou do not have permission.");
        sender.sendMessage(color(prefix) + color(message));
    }

    private static String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }
}
