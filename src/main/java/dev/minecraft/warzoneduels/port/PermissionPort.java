package dev.minecraft.warzoneduels.port;

import org.bukkit.entity.Player;

public interface PermissionPort {
    boolean has(Player player, String permission);
}
