package dev.minecraft.warzoneduels.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class TeamOutcomePolicy {
    private TeamOutcomePolicy() {
    }

    public static Outcome outcome(ActiveDuel duel, UUID winningParticipantId) {
        Objects.requireNonNull(duel, "duel");
        MatchTeam winningTeam = duel.teamOf(winningParticipantId);
        MatchTeam losingTeam = duel.opposingTeam(winningParticipantId);
        if (winningTeam == null || losingTeam == null) {
            throw new IllegalArgumentException("The winning participant must belong to the duel.");
        }
        return new Outcome(winningTeam.participants(), losingTeam.participants());
    }

    public static void validateWager(ActiveDuel duel) {
        Objects.requireNonNull(duel, "duel");
        if (duel.matchType() == DuelMatchType.PARTY && duel.settings().getWager() > 0.0D) {
            throw new IllegalArgumentException("Party duel wagers are not supported yet.");
        }
    }

    public record Outcome(List<MatchParticipant> winners, List<MatchParticipant> losers) {
        public Outcome {
            winners = List.copyOf(winners);
            losers = List.copyOf(losers);
        }
    }
}
