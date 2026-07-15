package dev.minecraft.warzoneduels.domain;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public record StoredLocation(
    UUID worldId,
    String worldName,
    double x,
    double y,
    double z,
    float yaw,
    float pitch
) {
    public static StoredLocation capture(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        World world = location.getWorld();
        return new StoredLocation(world.getUID(), world.getName(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    public Location resolve() {
        World world = worldId == null ? null : Bukkit.getWorld(worldId);
        if (world == null && worldName != null) {
            world = Bukkit.getWorld(worldName);
        }
        return world == null ? null : new Location(world, x, y, z, yaw, pitch);
    }
}
