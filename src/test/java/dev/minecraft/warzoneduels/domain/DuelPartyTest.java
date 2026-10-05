package dev.minecraft.warzoneduels.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuelPartyTest {
    @Test
    void capsRosterAtThreeAndKeepsLeaderInRoster() {
        UUID leaderId = UUID.randomUUID();
        DuelParty party = new DuelParty(UUID.randomUUID(), leaderId, "Leader");
        party.addMember(UUID.randomUUID(), "Second");
        party.addMember(UUID.randomUUID(), "Third");

        assertEquals(3, party.size());
        assertTrue(party.contains(leaderId));
        assertThrows(IllegalStateException.class, () -> party.addMember(UUID.randomUUID(), "Fourth"));
        assertThrows(IllegalStateException.class, () -> party.removeMember(leaderId));
    }

    @Test
    void challengeLockPreventsEveryRosterMutation() {
        UUID leaderId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        DuelParty party = new DuelParty(UUID.randomUUID(), leaderId, "Leader");
        party.addMember(secondId, "Second");
        party.lockRoster(challengeId);

        assertTrue(party.isRosterLocked());
        assertThrows(IllegalStateException.class, () -> party.addMember(UUID.randomUUID(), "Third"));
        assertThrows(IllegalStateException.class, () -> party.removeMember(secondId));
        assertThrows(IllegalStateException.class, () -> party.transferLeadership(secondId));
        assertThrows(IllegalArgumentException.class, () -> party.unlockRoster(UUID.randomUUID()));

        party.unlockRoster(challengeId);
        assertFalse(party.isRosterLocked());
        party.removeMember(secondId);
        assertEquals(1, party.size());
    }
}
