package dev.minecraft.warzoneduels.domain;

import org.bukkit.GameMode;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class SpectatorSession {
    private final UUID playerId;
    private final String playerName;
    private final long createdAtEpochMs;
    private final SpectatorSessionPhase phase;
    private final GameMode originalGameMode;
    private final StoredLocation originalLocation;
    private final boolean originalAllowFlight;
    private final boolean originalFlying;
    private final float originalFlySpeed;
    private final float originalWalkSpeed;
    private final boolean originalCollidable;
    private final boolean originalCanPickupItems;
    private final ItemStack[] contents;
    private final ItemStack[] armor;
    private final ItemStack offhand;
    private final ItemStack cursor;
    private final double health;
    private final int foodLevel;
    private final float saturation;
    private final int totalExperience;
    private final int level;
    private final float experienceProgress;
    private final int fireTicks;
    private final List<PotionEffect> potionEffects;
    private final float fallDistance;
    private final Vector velocity;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public SpectatorSession(
        UUID playerId,
        String playerName,
        long createdAtEpochMs,
        SpectatorSessionPhase phase,
        GameMode originalGameMode,
        StoredLocation originalLocation,
        boolean originalAllowFlight,
        boolean originalFlying,
        float originalFlySpeed,
        float originalWalkSpeed,
        boolean originalCollidable,
        boolean originalCanPickupItems,
        ItemStack[] contents,
        ItemStack[] armor,
        ItemStack offhand,
        ItemStack cursor,
        double health,
        int foodLevel,
        float saturation,
        int totalExperience,
        int level,
        float experienceProgress,
        int fireTicks,
        List<PotionEffect> potionEffects,
        float fallDistance,
        Vector velocity
    ) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.createdAtEpochMs = createdAtEpochMs;
        this.phase = phase;
        this.originalGameMode = originalGameMode;
        this.originalLocation = originalLocation;
        this.originalAllowFlight = originalAllowFlight;
        this.originalFlying = originalFlying;
        this.originalFlySpeed = originalFlySpeed;
        this.originalWalkSpeed = originalWalkSpeed;
        this.originalCollidable = originalCollidable;
        this.originalCanPickupItems = originalCanPickupItems;
        this.contents = cloneArray(contents);
        this.armor = cloneArray(armor);
        this.offhand = cloneItem(offhand);
        this.cursor = cloneItem(cursor);
        this.health = health;
        this.foodLevel = foodLevel;
        this.saturation = saturation;
        this.totalExperience = totalExperience;
        this.level = level;
        this.experienceProgress = experienceProgress;
        this.fireTicks = fireTicks;
        this.potionEffects = Collections.unmodifiableList(new ArrayList<>(potionEffects == null ? List.of() : potionEffects));
        this.fallDistance = fallDistance;
        this.velocity = velocity == null ? new Vector() : velocity.clone();
    }

    public SpectatorSession withPhase(SpectatorSessionPhase newPhase) {
        return new SpectatorSession(playerId, playerName, createdAtEpochMs, newPhase, originalGameMode, originalLocation,
            originalAllowFlight, originalFlying, originalFlySpeed, originalWalkSpeed, originalCollidable,
            originalCanPickupItems, contents, armor, offhand, cursor, health, foodLevel, saturation,
            totalExperience, level, experienceProgress, fireTicks, potionEffects, fallDistance, velocity);
    }

    public UUID playerId() { return playerId; }
    public String playerName() { return playerName; }
    public long createdAtEpochMs() { return createdAtEpochMs; }
    public SpectatorSessionPhase phase() { return phase; }
    public GameMode originalGameMode() { return originalGameMode; }
    public StoredLocation originalLocation() { return originalLocation; }
    public boolean originalAllowFlight() { return originalAllowFlight; }
    public boolean originalFlying() { return originalFlying; }
    public float originalFlySpeed() { return originalFlySpeed; }
    public float originalWalkSpeed() { return originalWalkSpeed; }
    public boolean originalCollidable() { return originalCollidable; }
    public boolean originalCanPickupItems() { return originalCanPickupItems; }
    public ItemStack[] contents() { return cloneArray(contents); }
    public ItemStack[] armor() { return cloneArray(armor); }
    public ItemStack offhand() { return cloneItem(offhand); }
    public ItemStack cursor() { return cloneItem(cursor); }
    public double health() { return health; }
    public int foodLevel() { return foodLevel; }
    public float saturation() { return saturation; }
    public int totalExperience() { return totalExperience; }
    public int level() { return level; }
    public float experienceProgress() { return experienceProgress; }
    public int fireTicks() { return fireTicks; }
    public List<PotionEffect> potionEffects() { return potionEffects; }
    public float fallDistance() { return fallDistance; }
    public Vector velocity() { return velocity.clone(); }

    private static ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
    }

    private static ItemStack[] cloneArray(ItemStack[] source) {
        if (source == null) {
            return new ItemStack[0];
        }
        ItemStack[] copy = new ItemStack[source.length];
        for (int index = 0; index < source.length; index++) {
            copy[index] = cloneItem(source[index]);
        }
        return copy;
    }
}
