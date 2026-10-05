package dev.minecraft.warzoneduels.domain.analytics;

import dev.minecraft.warzoneduels.domain.DuelEndReason;
import dev.minecraft.warzoneduels.domain.DuelMatchType;

import java.util.List;
import java.util.UUID;

public record DuelRecord(
    String reference,
    long startedAtEpochMs,
    long endedAtEpochMs,
    long durationMillis,
    UUID playerOneId,
    String playerOneName,
    UUID playerTwoId,
    String playerTwoName,
    UUID winnerId,
    String winnerName,
    UUID loserId,
    String loserName,
    String mapId,
    String mapName,
    String ruleset,
    String itemRules,
    DuelEndReason endReason,
    boolean countedAsMatch,
    int spectatorCount,
    double wager,
    DuelMatchType matchType,
    int teamSize,
    List<DuelRecordParticipant> participants
) {
    public DuelRecord {
        matchType = matchType == null ? DuelMatchType.NORMAL : matchType;
        teamSize = Math.max(1, teamSize);
        participants = participants == null ? List.of() : List.copyOf(participants);
    }
}
