package dev.minecraft.warzoneduels.domain;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class TeamMatchPolicy {
    private TeamMatchPolicy() {
    }

    public static boolean isFriendlyFire(ActiveDuel duel, UUID attackerId, UUID victimId) {
        if (duel == null || attackerId == null || victimId == null) {
            return false;
        }
        MatchTeam attackerTeam = duel.teamOf(attackerId);
        return attackerTeam != null && attackerTeam.contains(victimId);
    }

    public static List<MatchParticipant> survivingParticipants(MatchTeam team, Set<UUID> eliminatedPlayerIds) {
        if (team == null) {
            return List.of();
        }
        Set<UUID> eliminated = eliminatedPlayerIds == null ? Set.of() : eliminatedPlayerIds;
        return team.participants().stream()
            .filter(participant -> !eliminated.contains(participant.playerId()))
            .toList();
    }

    public static Optional<MatchTeam> winningTeam(ActiveDuel duel, Set<UUID> eliminatedPlayerIds) {
        TeamEliminationOutcome outcome = eliminationOutcome(duel, eliminatedPlayerIds);
        return switch (outcome) {
            case TEAM_ONE_WINS -> Optional.of(duel.teamOne());
            case TEAM_TWO_WINS -> Optional.of(duel.teamTwo());
            case CONTINUE, DRAW -> Optional.empty();
        };
    }

    public static TeamEliminationOutcome eliminationOutcome(ActiveDuel duel, Set<UUID> eliminatedPlayerIds) {
        if (duel == null) {
            return TeamEliminationOutcome.CONTINUE;
        }
        boolean firstEliminated = survivingParticipants(duel.teamOne(), eliminatedPlayerIds).isEmpty();
        boolean secondEliminated = survivingParticipants(duel.teamTwo(), eliminatedPlayerIds).isEmpty();
        if (firstEliminated && secondEliminated) {
            return TeamEliminationOutcome.DRAW;
        }
        if (secondEliminated) {
            return TeamEliminationOutcome.TEAM_ONE_WINS;
        }
        if (firstEliminated) {
            return TeamEliminationOutcome.TEAM_TWO_WINS;
        }
        return TeamEliminationOutcome.CONTINUE;
    }

    public static boolean allSurvivorsRequestedDraw(ActiveDuel duel, Set<UUID> eliminatedPlayerIds) {
        if (duel == null) {
            return false;
        }
        List<MatchParticipant> survivors = duel.participants().stream()
            .filter(participant -> eliminatedPlayerIds == null || !eliminatedPlayerIds.contains(participant.playerId()))
            .toList();
        return !survivors.isEmpty() && survivors.stream().allMatch(MatchParticipant::drawRequested);
    }
}
