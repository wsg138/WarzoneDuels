package dev.minecraft.warzoneduels.domain;

import java.util.Set;
import java.util.UUID;

public final class TeamSpoilsPolicy {
    private TeamSpoilsPolicy() {
    }

    public static MatchParticipant recipient(
        ActiveDuel duel,
        UUID defeatedPlayerId,
        UUID killerId,
        Set<UUID> eliminatedPlayerIds
    ) {
        if (duel == null || defeatedPlayerId == null) {
            return null;
        }
        MatchTeam opposingTeam = duel.opposingTeam(defeatedPlayerId);
        if (opposingTeam == null) {
            return null;
        }
        Set<UUID> eliminated = eliminatedPlayerIds == null ? Set.of() : eliminatedPlayerIds;
        if (killerId != null && opposingTeam.contains(killerId) && !eliminated.contains(killerId)) {
            return opposingTeam.participant(killerId);
        }
        return TeamMatchPolicy.survivingParticipants(opposingTeam, eliminated).stream().findFirst().orElse(null);
    }
}
