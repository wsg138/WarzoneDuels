package dev.minecraft.warzoneduels.domain;

import dev.minecraft.warzoneduels.BlockKey;
import dev.minecraft.warzoneduels.adapter.bukkit.reset.ArenaSnapshot;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ActiveDuel {
    private final DuelMatchType matchType;
    private final MatchTeam firstTeam;
    private final MatchTeam secondTeam;
    private final DuelSettings duelSettings;
    private final long startedAtMillis;
    private final Set<BlockKey> placedBlockKeys = new HashSet<>();
    private ArenaSnapshot currentArenaSnapshot;
    private Long duelDeadlineEpochMs;
    private boolean wagerHeld;
    private double wagerPot;

    public ActiveDuel(MatchParticipant participantOne, MatchParticipant participantTwo, DuelSettings settings, long startedAtEpochMs) {
        this(DuelMatchType.NORMAL, MatchTeam.singleton(participantOne), MatchTeam.singleton(participantTwo), settings, startedAtEpochMs);
    }

    public ActiveDuel(
        DuelMatchType type,
        MatchTeam teamOne,
        MatchTeam teamTwo,
        DuelSettings settings,
        long startedAtEpochMs
    ) {
        this.matchType = java.util.Objects.requireNonNull(type, "type");
        this.firstTeam = java.util.Objects.requireNonNull(teamOne, "teamOne");
        this.secondTeam = java.util.Objects.requireNonNull(teamTwo, "teamTwo");
        if (teamOne.size() != teamTwo.size()) {
            throw new IllegalArgumentException("Active duel teams must have equal roster sizes.");
        }
        if (teamOne.size() > 1 && type != DuelMatchType.PARTY) {
            throw new IllegalArgumentException("Multi-member teams require a PARTY match.");
        }
        if (!java.util.Collections.disjoint(teamOne.participantIds(), teamTwo.participantIds())) {
            throw new IllegalArgumentException("A player cannot appear on both active duel teams.");
        }
        this.duelSettings = settings;
        this.startedAtMillis = startedAtEpochMs;
    }

    public DuelMatchType matchType() {
        return matchType;
    }

    public MatchTeam teamOne() {
        return firstTeam;
    }

    public MatchTeam teamTwo() {
        return secondTeam;
    }

    public List<MatchParticipant> participants() {
        return java.util.stream.Stream.concat(firstTeam.participants().stream(), secondTeam.participants().stream()).toList();
    }

    public MatchParticipant participantOne() {
        return firstTeam.participants().get(0);
    }

    public MatchParticipant participantTwo() {
        return secondTeam.participants().get(0);
    }

    public DuelSettings settings() {
        return duelSettings;
    }

    public long startedAtEpochMs() {
        return startedAtMillis;
    }

    public Set<BlockKey> placedBlocks() {
        return placedBlockKeys;
    }

    public ArenaSnapshot arenaSnapshot() {
        return currentArenaSnapshot;
    }

    public void setArenaSnapshot(ArenaSnapshot arenaSnapshot) {
        this.currentArenaSnapshot = arenaSnapshot;
    }

    public Long duelDeadlineEpochMs() {
        return duelDeadlineEpochMs;
    }

    public void setDuelDeadlineEpochMs(Long deadlineEpochMs) {
        this.duelDeadlineEpochMs = deadlineEpochMs;
    }

    public boolean isWagerHeld() {
        return wagerHeld;
    }

    public void setWagerHeld(boolean wagerHeld) {
        this.wagerHeld = wagerHeld;
    }

    public double getWagerPot() {
        return wagerPot;
    }

    public void setWagerPot(double wagerPot) {
        this.wagerPot = wagerPot;
    }

    public boolean contains(UUID playerId) {
        return firstTeam.contains(playerId) || secondTeam.contains(playerId);
    }

    public MatchParticipant participant(UUID playerId) {
        MatchParticipant first = firstTeam.participant(playerId);
        return first == null ? secondTeam.participant(playerId) : first;
    }

    public MatchParticipant other(UUID playerId) {
        MatchTeam opposingTeam = opposingTeam(playerId);
        if (opposingTeam != null && opposingTeam.size() == 1) {
            return opposingTeam.participants().get(0);
        }
        return null;
    }

    public MatchTeam teamOf(UUID playerId) {
        if (firstTeam.contains(playerId)) {
            return firstTeam;
        }
        if (secondTeam.contains(playerId)) {
            return secondTeam;
        }
        return null;
    }

    public MatchTeam opposingTeam(UUID playerId) {
        if (firstTeam.contains(playerId)) {
            return secondTeam;
        }
        if (secondTeam.contains(playerId)) {
            return firstTeam;
        }
        return null;
    }
}
