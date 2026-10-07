package dev.minecraft.warzoneduels.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TeamOutcomePolicyTest {
    @Test
    void returnsEveryWinnerAndLoserFromTheWinningParticipant() {
        MatchParticipant alpha = participant("Alpha");
        MatchParticipant bravo = participant("Bravo");
        MatchParticipant charlie = participant("Charlie");
        MatchParticipant delta = participant("Delta");
        ActiveDuel duel = new ActiveDuel(
            DuelMatchType.PARTY,
            new MatchTeam(UUID.randomUUID(), List.of(alpha, bravo)),
            new MatchTeam(UUID.randomUUID(), List.of(charlie, delta)),
            new DuelSettings(),
            1L
        );

        TeamOutcomePolicy.Outcome outcome = TeamOutcomePolicy.outcome(duel, bravo.playerId());

        assertEquals(List.of(alpha, bravo), outcome.winners());
        assertEquals(List.of(charlie, delta), outcome.losers());
    }

    @Test
    void rejectsPartyWagersUntilAStakeSplitPolicyExists() {
        DuelSettings settings = new DuelSettings();
        settings.setWager(25.0D);
        ActiveDuel duel = new ActiveDuel(
            DuelMatchType.PARTY,
            new MatchTeam(UUID.randomUUID(), List.of(participant("Alpha"), participant("Bravo"))),
            new MatchTeam(UUID.randomUUID(), List.of(participant("Charlie"), participant("Delta"))),
            settings,
            1L
        );

        assertThrows(IllegalArgumentException.class, () -> TeamOutcomePolicy.validateWager(duel));
    }

    private static MatchParticipant participant(String name) {
        return new MatchParticipant(UUID.randomUUID(), name);
    }
}
