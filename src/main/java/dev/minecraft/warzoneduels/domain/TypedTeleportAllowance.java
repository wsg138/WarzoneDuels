package dev.minecraft.warzoneduels.domain;

import org.bukkit.Location;

import java.util.UUID;

public record TypedTeleportAllowance(
    TeleportAllowanceReason reason,
    UUID worldId,
    double x,
    double y,
    double z,
    long expiresAtEpochMs
) {
    private static final double MAX_DISTANCE_SQUARED = 0.01D;

    public static TypedTeleportAllowance forDestination(TeleportAllowanceReason reason, Location destination, long expiresAtEpochMs) {
        if (destination == null || destination.getWorld() == null) {
            throw new IllegalArgumentException("Teleport allowance destination must have a world");
        }
        return new TypedTeleportAllowance(reason, destination.getWorld().getUID(), destination.getX(), destination.getY(), destination.getZ(), expiresAtEpochMs);
    }

    public boolean matches(Location destination, long nowEpochMs) {
        return destination != null && destination.getWorld() != null
            && matches(destination.getWorld().getUID(), destination.getX(), destination.getY(), destination.getZ(), nowEpochMs);
    }

    public boolean matches(UUID destinationWorldId, double destinationX, double destinationY, double destinationZ, long nowEpochMs) {
        if (destinationWorldId == null || nowEpochMs > expiresAtEpochMs || !worldId.equals(destinationWorldId)) {
            return false;
        }
        double deltaX = x - destinationX;
        double deltaY = y - destinationY;
        double deltaZ = z - destinationZ;
        return (deltaX * deltaX) + (deltaY * deltaY) + (deltaZ * deltaZ) <= MAX_DISTANCE_SQUARED;
    }

    public boolean isWatcherAllowance() {
        return reason == TeleportAllowanceReason.WATCH_ENTRY
            || reason == TeleportAllowanceReason.WATCH_BOUNDARY_RETURN
            || reason == TeleportAllowanceReason.WATCH_RESTORE
            || reason == TeleportAllowanceReason.WATCH_RECOVERY;
    }
}
