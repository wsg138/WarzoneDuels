package dev.minecraft.warzoneduels.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TeamSpoilsPolicyTest {
    @Test
    void prefersAValidOpposingKiller() {
        Fixture fixture = fixture();

        MatchParticipant recipient = TeamSpoilsPolicy.recipient(
            fixture.duel, fixture.defeated.playerId(), fixture.secondOpponent.playerId(), Set.of()
        );

        assertEquals(fixture.secondOpponent, recipient);
    }

    @Test
    void ignoresTeammateAndSelectsFirstSurvivingOpponentDeterministically() {
        Fixture fixture = fixture();

        MatchParticipant recipient = TeamSpoilsPolicy.recipient(
            fixture.duel,
            fixture.defeated.playerId(),
            fixture.teammate.playerId(),
            Set.of(fixture.firstOpponent.playerId())
        );

        assertEquals(fixture.secondOpponent, recipient);
    }

    private static Fixture fixture() {
        MatchParticipant defeated = participant("Defeated");
        MatchParticipant teammate = participant("Teammate");
        MatchParticipant firstOpponent = participant("OpponentOne");
        MatchParticipant secondOpponent = participant("OpponentTwo");
        ActiveDuel duel = new ActiveDuel(
            DuelMatchType.PARTY,
            new MatchTeam(UUID.randomUUID(), List.of(defeated, teammate)),
            new MatchTeam(UUID.randomUUID(), List.of(firstOpponent, secondOpponent)),
            new DuelSettings(),
            1L
        );
        return new Fixture(duel, defeated, teammate, firstOpponent, secondOpponent);
    }

    private static MatchParticipant participant(String name) {
        return new MatchParticipant(UUID.randomUUID(), name);
    }

    private record Fixture(
        ActiveDuel duel,
        MatchParticipant defeated,
        MatchParticipant teammate,
        MatchParticipant firstOpponent,
        MatchParticipant secondOpponent
    ) {
    }
}
