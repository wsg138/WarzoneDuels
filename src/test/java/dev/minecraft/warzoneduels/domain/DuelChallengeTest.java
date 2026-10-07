package dev.minecraft.warzoneduels.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuelChallengeTest {
    @Test
    void requiresEqualTeamSizes() {
        assertThrows(IllegalArgumentException.class, () -> challenge(team(1), team(2), 1_000L, 2_000L));
    }

    @Test
    void becomesReadyOnlyAfterEverySnapshottedParticipantAccepts() {
        MatchTeam challengers = team(2);
        MatchTeam opponents = team(2);
        DuelChallenge challenge = challenge(challengers, opponents, 1_000L, 2_000L);

        for (MatchParticipant participant : challengers.participants()) {
            assertEquals(DuelChallengeStatus.PENDING, challenge.accept(participant.playerId(), 1_500L));
        }
        assertEquals(DuelChallengeStatus.PENDING, challenge.accept(opponents.participants().get(0).playerId(), 1_500L));
        assertEquals(DuelChallengeStatus.READY, challenge.accept(opponents.participants().get(1).playerId(), 1_500L));
        assertEquals(4, challenge.acceptedCount());
        assertTrue(challenge.waitingForParticipantIds().isEmpty());
    }

    @Test
    void expiresWithoutConvertingIntoAReadyChallenge() {
        DuelChallenge challenge = challenge(team(1), team(1), 1_000L, 2_000L);

        assertEquals(DuelChallengeStatus.EXPIRED, challenge.status(2_000L));
        assertThrows(IllegalStateException.class,
            () -> challenge.accept(challenge.challengerTeam().participants().get(0).playerId(), 2_001L));
    }

    @Test
    void snapshotsRostersAndSettings() {
        DuelParty party = new DuelParty(UUID.randomUUID(), UUID.randomUUID(), "Leader");
        MatchTeam initialTeam = party.snapshotTeam();
        DuelSettings settings = new DuelSettings();
        settings.setWager(25D);
        DuelChallenge challenge = challenge(initialTeam, team(1), settings, 1_000L, 2_000L);

        settings.setWager(50D);
        party.addMember(UUID.randomUUID(), "Late Joiner");

        assertEquals(1, challenge.challengerTeam().size());
        assertEquals(25D, challenge.settings().getWager());
        DuelSettings exposedCopy = challenge.settings();
        exposedCopy.setWager(75D);
        assertEquals(25D, challenge.settings().getWager());
        assertFalse(challenge.hasAccepted(UUID.randomUUID()));
    }

    @Test
    void rejectsPlayersAppearingOnBothTeams() {
        MatchParticipant shared = participant("Shared");
        MatchTeam first = new MatchTeam(UUID.randomUUID(), List.of(shared));
        MatchTeam second = new MatchTeam(UUID.randomUUID(), List.of(shared));

        assertThrows(IllegalArgumentException.class, () -> challenge(first, second, 1_000L, 2_000L));
    }

    private DuelChallenge challenge(MatchTeam first, MatchTeam second, long created, long expires) {
        return challenge(first, second, new DuelSettings(), created, expires);
    }

    private DuelChallenge challenge(
        MatchTeam first,
        MatchTeam second,
        DuelSettings settings,
        long created,
        long expires
    ) {
        return new DuelChallenge(UUID.randomUUID(), first, second, settings, created, expires);
    }

    private MatchTeam team(int size) {
        java.util.ArrayList<MatchParticipant> participants = new java.util.ArrayList<>();
        for (int index = 0; index < size; index++) {
            participants.add(participant("Player" + index));
        }
        return new MatchTeam(UUID.randomUUID(), participants);
    }

    private MatchParticipant participant(String name) {
        return new MatchParticipant(UUID.randomUUID(), name);
    }
}
