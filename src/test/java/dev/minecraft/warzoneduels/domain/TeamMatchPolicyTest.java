package dev.minecraft.warzoneduels.domain;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamMatchPolicyTest {
    @Test
    void membershipDeterminesFriendlyFire() {
        ActiveDuel duel = partyDuel();
        MatchParticipant first = duel.teamOne().participants().get(0);
        MatchParticipant teammate = duel.teamOne().participants().get(1);
        MatchParticipant opponent = duel.teamTwo().participants().get(0);

        assertTrue(TeamMatchPolicy.isFriendlyFire(duel, first.playerId(), teammate.playerId()));
        assertFalse(TeamMatchPolicy.isFriendlyFire(duel, first.playerId(), opponent.playerId()));
        assertFalse(TeamMatchPolicy.isFriendlyFire(duel, UUID.randomUUID(), teammate.playerId()));
    }

    @Test
    void firstEliminationDoesNotEndTeamMatch() {
        ActiveDuel duel = partyDuel();
        MatchParticipant firstOpponent = duel.teamTwo().participants().get(0);
        Set<UUID> eliminated = Set.of(firstOpponent.playerId());

        assertTrue(TeamMatchPolicy.winningTeam(duel, eliminated).isEmpty());
        assertEquals(1, TeamMatchPolicy.survivingParticipants(duel.teamTwo(), eliminated).size());
    }

    @Test
    void eliminatingWholeTeamReturnsOpposingTeam() {
        ActiveDuel duel = partyDuel();
        Set<UUID> eliminated = new HashSet<>(duel.teamTwo().participantIds());

        assertEquals(duel.teamOne(), TeamMatchPolicy.winningTeam(duel, eliminated).orElseThrow());
        eliminated.addAll(duel.teamOne().participantIds());
        assertTrue(TeamMatchPolicy.winningTeam(duel, eliminated).isEmpty());
    }

    @Test
    void drawRequiresEverySurvivingParticipant() {
        ActiveDuel duel = partyDuel();
        Set<UUID> eliminated = Set.of(duel.teamTwo().participants().get(1).playerId());
        duel.teamOne().participants().forEach(participant -> participant.setDrawRequested(true));
        duel.teamTwo().participants().get(0).setDrawRequested(true);

        assertTrue(TeamMatchPolicy.allSurvivorsRequestedDraw(duel, eliminated));
        duel.teamOne().participants().get(0).setDrawRequested(false);
        assertFalse(TeamMatchPolicy.allSurvivorsRequestedDraw(duel, eliminated));
    }

    private ActiveDuel partyDuel() {
        MatchTeam first = new MatchTeam(UUID.randomUUID(), List.of(participant("One"), participant("Two")));
        MatchTeam second = new MatchTeam(UUID.randomUUID(), List.of(participant("Three"), participant("Four")));
        return new ActiveDuel(DuelMatchType.PARTY, first, second, new DuelSettings(), 1L);
    }

    private MatchParticipant participant(String name) {
        return new MatchParticipant(UUID.randomUUID(), name);
    }
}
