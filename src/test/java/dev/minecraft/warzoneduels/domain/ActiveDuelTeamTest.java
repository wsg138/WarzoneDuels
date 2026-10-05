package dev.minecraft.warzoneduels.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActiveDuelTeamTest {
    @Test
    void normalRejectsMultiMemberTeamsButBothTypesAllowSingletons() {
        MatchTeam first = new MatchTeam(UUID.randomUUID(), List.of(participant("A"), participant("B")));
        MatchTeam second = new MatchTeam(UUID.randomUUID(), List.of(participant("C"), participant("D")));
        assertThrows(IllegalArgumentException.class,
            () -> new ActiveDuel(DuelMatchType.NORMAL, first, second, new DuelSettings(), 1L));
        for (DuelMatchType type : DuelMatchType.values()) {
            ActiveDuel duel = new ActiveDuel(type, MatchTeam.singleton(participant("A")),
                MatchTeam.singleton(participant("B")), new DuelSettings(), 1L);
            assertEquals(type, duel.matchType());
        }
    }

    @Test
    void legacyConstructorCreatesNormalSingletonTeams() {
        MatchParticipant first = participant("First");
        MatchParticipant second = participant("Second");
        ActiveDuel duel = new ActiveDuel(first, second, new DuelSettings(), 100L);

        assertEquals(DuelMatchType.NORMAL, duel.matchType());
        assertEquals(first, duel.participantOne());
        assertEquals(second, duel.other(first.playerId()));
        assertEquals(2, duel.participants().size());
    }

    @Test
    void partyMatchFindsTeamsWithoutPretendingThereIsOneOpponent() {
        MatchParticipant first = participant("First");
        MatchParticipant teammate = participant("Teammate");
        MatchTeam firstTeam = new MatchTeam(UUID.randomUUID(), List.of(first, teammate));
        MatchTeam secondTeam = new MatchTeam(UUID.randomUUID(), List.of(participant("Third"), participant("Fourth")));
        ActiveDuel duel = new ActiveDuel(DuelMatchType.PARTY, firstTeam, secondTeam, new DuelSettings(), 100L);

        assertEquals(firstTeam, duel.teamOf(first.playerId()));
        assertEquals(secondTeam, duel.opposingTeam(first.playerId()));
        assertNull(duel.other(first.playerId()));
        assertEquals(4, duel.participants().size());
    }

    @Test
    void rejectsUnequalOrOverlappingTeams() {
        MatchParticipant shared = participant("Shared");
        MatchTeam singleton = new MatchTeam(UUID.randomUUID(), List.of(shared));
        MatchTeam pair = new MatchTeam(UUID.randomUUID(), List.of(participant("One"), participant("Two")));
        MatchTeam overlap = new MatchTeam(UUID.randomUUID(), List.of(shared));

        assertThrows(IllegalArgumentException.class,
            () -> new ActiveDuel(DuelMatchType.PARTY, singleton, pair, new DuelSettings(), 100L));
        assertThrows(IllegalArgumentException.class,
            () -> new ActiveDuel(DuelMatchType.PARTY, singleton, overlap, new DuelSettings(), 100L));
    }

    private MatchParticipant participant(String name) {
        return new MatchParticipant(UUID.randomUUID(), name);
    }
}
