package dev.minecraft.warzoneduels.domain.analytics;

import java.util.UUID;

public record DuelRecordParticipant(UUID playerId, String playerName, int teamIndex, boolean winner) {
    public DuelRecordParticipant {
        if (playerId == null || playerName == null || playerName.isBlank()) {
            throw new IllegalArgumentException("An analytics participant requires an id and name.");
        }
        if (teamIndex != 1 && teamIndex != 2) {
            throw new IllegalArgumentException("Analytics team index must be 1 or 2.");
        }
    }
}
