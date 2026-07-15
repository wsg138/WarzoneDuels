package dev.minecraft.warzoneduels.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypedTeleportAllowanceTest {
    @Test
    void matchesOnlyTypedDestinationBeforeExpiration() {
        UUID worldId = UUID.randomUUID();
        TypedTeleportAllowance allowance = new TypedTeleportAllowance(
            TeleportAllowanceReason.WATCH_BOUNDARY_RETURN, worldId, 10D, 64D, -4D, 2_000L);

        assertTrue(allowance.matches(worldId, 10.01D, 64D, -4D, 1_999L));
        assertFalse(allowance.matches(UUID.randomUUID(), 10D, 64D, -4D, 1_999L));
        assertFalse(allowance.matches(worldId, 11D, 64D, -4D, 1_999L));
        assertFalse(allowance.matches(worldId, 10D, 64D, -4D, 2_001L));
        assertTrue(allowance.isWatcherAllowance());
    }

    @Test
    void duelMovementIsNotAWatcherAllowance() {
        TypedTeleportAllowance allowance = new TypedTeleportAllowance(
            TeleportAllowanceReason.DUEL_MOVEMENT, UUID.randomUUID(), 0D, 0D, 0D, Long.MAX_VALUE);
        assertFalse(allowance.isWatcherAllowance());
    }
}
