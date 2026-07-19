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
    private final UUID storedPlayerId;
    private final String storedPlayerName;
    private final long storedCreatedAtEpochMs;
    private final SpectatorSessionPhase storedPhase;
    private final GameMode storedOriginalGameMode;
    private final StoredLocation storedOriginalLocation;
    private final boolean storedOriginalAllowFlight;
    private final boolean storedOriginalFlying;
    private final float storedOriginalFlySpeed;
    private final float storedOriginalWalkSpeed;
    private final boolean storedOriginalCollidable;
    private final boolean storedOriginalCanPickupItems;
    private final ItemStack[] storedContents;
    private final ItemStack[] storedArmor;
    private final ItemStack storedOffhand;
    private final ItemStack storedCursor;
    private final double storedHealth;
    private final int storedFoodLevel;
    private final float storedSaturation;
    private final int storedTotalExperience;
    private final int storedLevel;
    private final float storedExperienceProgress;
    private final int storedFireTicks;
    private final List<PotionEffect> storedPotionEffects;
    private final float storedFallDistance;
    private final Vector storedVelocity;

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
        this.storedPlayerId = playerId;
        this.storedPlayerName = playerName;
        this.storedCreatedAtEpochMs = createdAtEpochMs;
        this.storedPhase = phase;
        this.storedOriginalGameMode = originalGameMode;
        this.storedOriginalLocation = originalLocation;
        this.storedOriginalAllowFlight = originalAllowFlight;
        this.storedOriginalFlying = originalFlying;
        this.storedOriginalFlySpeed = originalFlySpeed;
        this.storedOriginalWalkSpeed = originalWalkSpeed;
        this.storedOriginalCollidable = originalCollidable;
        this.storedOriginalCanPickupItems = originalCanPickupItems;
        this.storedContents = cloneArray(contents);
        this.storedArmor = cloneArray(armor);
        this.storedOffhand = cloneItem(offhand);
        this.storedCursor = cloneItem(cursor);
        this.storedHealth = health;
        this.storedFoodLevel = foodLevel;
        this.storedSaturation = saturation;
        this.storedTotalExperience = totalExperience;
        this.storedLevel = level;
        this.storedExperienceProgress = experienceProgress;
        this.storedFireTicks = fireTicks;
        this.storedPotionEffects = Collections.unmodifiableList(new ArrayList<>(potionEffects == null ? List.of() : potionEffects));
        this.storedFallDistance = fallDistance;
        this.storedVelocity = velocity == null ? new Vector() : velocity.clone();
    }

    public SpectatorSession withPhase(SpectatorSessionPhase newPhase) {
        return new SpectatorSession(storedPlayerId, storedPlayerName, storedCreatedAtEpochMs, newPhase, storedOriginalGameMode, storedOriginalLocation,
            storedOriginalAllowFlight, storedOriginalFlying, storedOriginalFlySpeed, storedOriginalWalkSpeed, storedOriginalCollidable,
            storedOriginalCanPickupItems, storedContents, storedArmor, storedOffhand, storedCursor, storedHealth, storedFoodLevel, storedSaturation,
            storedTotalExperience, storedLevel, storedExperienceProgress, storedFireTicks, storedPotionEffects, storedFallDistance, storedVelocity);
    }

    public UUID playerId() { return storedPlayerId; }
    public String playerName() { return storedPlayerName; }
    public long createdAtEpochMs() { return storedCreatedAtEpochMs; }
    public SpectatorSessionPhase phase() { return storedPhase; }
    public GameMode originalGameMode() { return storedOriginalGameMode; }
    public StoredLocation originalLocation() { return storedOriginalLocation; }
    public boolean originalAllowFlight() { return storedOriginalAllowFlight; }
    public boolean originalFlying() { return storedOriginalFlying; }
    public float originalFlySpeed() { return storedOriginalFlySpeed; }
    public float originalWalkSpeed() { return storedOriginalWalkSpeed; }
    public boolean originalCollidable() { return storedOriginalCollidable; }
    public boolean originalCanPickupItems() { return storedOriginalCanPickupItems; }
    public ItemStack[] contents() { return cloneArray(storedContents); }
    public ItemStack[] armor() { return cloneArray(storedArmor); }
    public ItemStack offhand() { return cloneItem(storedOffhand); }
    public ItemStack cursor() { return cloneItem(storedCursor); }
    public double health() { return storedHealth; }
    public int foodLevel() { return storedFoodLevel; }
    public float saturation() { return storedSaturation; }
    public int totalExperience() { return storedTotalExperience; }
    public int level() { return storedLevel; }
    public float experienceProgress() { return storedExperienceProgress; }
    public int fireTicks() { return storedFireTicks; }
    public List<PotionEffect> potionEffects() { return storedPotionEffects; }
    public float fallDistance() { return storedFallDistance; }
    public Vector velocity() { return storedVelocity.clone(); }

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
