package dev.minecraft.warzoneduels.domain;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DuelCooldownPolicyTest {
    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID();
    private DuelCooldownPolicy policy() { return new DuelCooldownPolicy(DuelCooldownPolicy.Snapshot.empty()); }

    @Test void allPlayersWaitAgainstNewOpponentsUntilExactBoundary() {
        var policy = policy();
        policy.recordCompletion(List.of(a, c), List.of(b, d), 1000);
        assertEquals(300, policy.block(List.of(c), List.of(UUID.randomUUID()), 1001, 300_000, 86_400_000).remainingSeconds());
        assertNull(policy.block(List.of(a), List.of(UUID.randomUUID()), 301_000, 300_000, 86_400_000));
    }
    @Test void reversedPairAndRecreatedPartiesCannotBypassRematchWindow() {
        var policy = policy();
        policy.recordCompletion(List.of(a, c), List.of(b, d), 1000);
        assertNotNull(policy.block(List.of(d), List.of(c), 301_000, 300_000, 86_400_000));
        assertEquals(DuelCooldownPolicy.pairKey(a, b), DuelCooldownPolicy.pairKey(b, a));
        assertNull(policy.block(List.of(d), List.of(c), 86_401_000, 300_000, 86_400_000));
        assertNull(policy.block(List.of(a), List.of(c), 301_000, 300_000, 86_400_000));
    }
    @Test void everyCrossTeamPairIncludingNonLeadersIsRemembered() {
        var policy = policy();
        var first = List.of(a, c, UUID.randomUUID());
        var second = List.of(b, d, UUID.randomUUID());
        policy.recordCompletion(first, second, 1000);
        assertEquals(6, policy.snapshot().players().size());
        assertEquals(9, policy.snapshot().opponents().size());
        for (UUID player : first) for (UUID opponent : second) {
            assertNotNull(policy.block(List.of(opponent), List.of(player), 301_000, 300_000, 86_400_000));
        }
    }
    @Test void independentlyDisabledLimitsDoNotClearHistory() {
        var policy = policy();
        policy.recordCompletion(List.of(a), List.of(b), 1000);
        assertNull(policy.block(List.of(a), List.of(b), 1001, 0, 0));
        assertNotNull(policy.block(List.of(a), List.of(b), 1001, 0, 1000));
        assertNotNull(policy.block(List.of(a), List.of(c), 1001, 1000, 0));
        assertNotNull(policy.block(List.of(a), List.of(b), 1001, 1000, 1000));
    }
    @Test void expiredChallengesCannotBeFarmedAndLeaderSwappingDoesNotHelp() {
        var policy = policy();
        assertTrue(policy.creditChallenge(List.of(a, c), List.of(b, d), 1000, 86_400_000));
        assertFalse(policy.creditChallenge(List.of(d), List.of(c), 1001, 86_400_000));
        assertTrue(policy.creditChallenge(List.of(a), List.of(UUID.randomUUID()), 1001, 86_400_000));
        assertTrue(policy.creditChallenge(List.of(b), List.of(a), 86_401_000, 86_400_000));
        assertNull(policy.block(List.of(a), List.of(b), 1001, 300_000, 86_400_000), "A request is not a completed duel");
    }
    @Test void negativeAndHugeSettingsAndBackwardsClockAreSafe() {
        var policy = policy();
        policy.recordCompletion(List.of(a), List.of(b), 1000);
        policy.recordCompletion(List.of(a), List.of(b), 500);
        assertEquals(1000L, policy.snapshot().players().get(a));
        assertNotNull(policy.block(List.of(a), List.of(b), 500, 1000, 1000));
        assertEquals(0, DuelCooldownPolicy.windowMillis(-1));
        assertEquals(Long.MAX_VALUE, DuelCooldownPolicy.windowMillis(Long.MAX_VALUE));
        assertTrue(policy.creditChallenge(List.of(a), List.of(b), 1000, 0));
        assertTrue(policy.creditChallenge(List.of(a), List.of(b), 1000, 0));
    }
    @Test void snapshotsAreImmutableAndRestartKeepsBothLimits() {
        var policy = policy();
        policy.recordCompletion(List.of(a), List.of(b), 1000);
        policy.creditChallenge(List.of(a), List.of(b), 1000, 1000);
        var snapshot = policy.snapshot();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.players().clear());
        var restarted = new DuelCooldownPolicy(snapshot);
        assertNotNull(restarted.block(List.of(b), List.of(a), 1001, 300_000, 86_400_000));
        assertFalse(restarted.creditChallenge(List.of(b), List.of(a), 1001, 1000));
    }
}
