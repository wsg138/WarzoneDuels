package dev.minecraft.warzoneduels.adapter.bukkit.spoils;

import dev.minecraft.warzoneduels.app.SpoilsService;
import dev.minecraft.warzoneduels.domain.spoils.SpoilsEntry;
import dev.minecraft.warzoneduels.permission.PermissionMessages;
import dev.minecraft.warzoneduels.permission.PermissionPolicy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class SpoilsCommand implements CommandExecutor, TabCompleter {
    private final SpoilsService spoilsService;
    private final JavaPlugin plugin;

    public SpoilsCommand(JavaPlugin plugin, SpoilsService spoilsService) {
        this.plugin = plugin;
        this.spoilsService = spoilsService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission(PermissionPolicy.VAULT)) {
            PermissionMessages.sendNoPermission(plugin, player);
            return true;
        }
        List<SpoilsEntry> entries = spoilsService.getEntriesFor(player.getUniqueId());
        if (entries.isEmpty()) {
            spoilsService.sendNoSpoilsMessage(player);
            return true;
        }
        player.openInventory(SpoilsGuiFactory.overview(player.getUniqueId(), entries, spoilsService, 0));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
