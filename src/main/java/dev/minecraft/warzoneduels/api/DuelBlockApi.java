package dev.minecraft.warzoneduels.api;

import java.util.UUID;

/**
 * Personal duel blocks for other plugins (for example EnthusiaFriends' Block Everywhere), registered with Bukkit's
 * ServicesManager. Calls must be made on the main thread. Callers pass identities they have already authenticated;
 * this API performs no permission checks and sends no player messages. Changes are the same blocks as /duel block.
 */
public interface DuelBlockApi {

    /** Whether {@code owner} blocks duel challenges and party invitations involving {@code target}. */
    boolean isBlocked(UUID owner, UUID target);

    /**
     * Block or unblock {@code target} for {@code owner}. Setting the current state again changes nothing.
     *
     * @throws IllegalArgumentException if owner and target are the same player
     * @throws IllegalStateException if called off the main thread or the change could not be saved
     */
    void setBlocked(UUID owner, UUID target, boolean blocked);
}
