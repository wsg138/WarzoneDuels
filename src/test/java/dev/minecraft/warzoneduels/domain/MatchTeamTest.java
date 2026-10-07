package dev.minecraft.warzoneduels.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchTeamTest {
    @Test
    void supportsOneToThreeUniqueParticipants() {
        MatchParticipant first = participant("First");
        MatchParticipant second = participant("Second");
        MatchParticipant third = participant("Third");
        MatchTeam team = new MatchTeam(UUID.randomUUID(), List.of(first, second, third));

        assertEquals(3, team.size());
        assertTrue(team.contains(second.playerId()));
        assertEquals(third, team.participant(third.playerId()));
        assertNull(team.participant(UUID.randomUUID()));
    }

    @Test
    void rejectsEmptyOversizedAndDuplicateRosters() {
        MatchParticipant duplicate = participant("Duplicate");

        assertThrows(IllegalArgumentException.class, () -> new MatchTeam(UUID.randomUUID(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new MatchTeam(UUID.randomUUID(), List.of(
            participant("One"), participant("Two"), participant("Three"), participant("Four"))));
        assertThrows(IllegalArgumentException.class, () -> new MatchTeam(UUID.randomUUID(), List.of(duplicate, duplicate)));
    }

    private MatchParticipant participant(String name) {
        return new MatchParticipant(UUID.randomUUID(), name);
    }
}
