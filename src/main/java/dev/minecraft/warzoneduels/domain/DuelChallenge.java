package dev.minecraft.warzoneduels.domain;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class DuelChallenge {
    private final UUID challengeId;
    private final MatchTeam challengerTeam;
    private final MatchTeam opponentTeam;
    private final DuelSettings duelSettings;
    private final long createdAtMillis;
    private final long expiresAtMillis;
    private final Set<UUID> requiredParticipantIds;
    private final Set<UUID> acceptedParticipantIds = new HashSet<>();
    private DuelChallengeStatus currentStatus = DuelChallengeStatus.PENDING;

    public DuelChallenge(
        UUID id,
        MatchTeam challengers,
        MatchTeam opponents,
        DuelSettings settings,
        long createdAtEpochMs,
        long expiresAtEpochMs
    ) {
        this.challengeId = Objects.requireNonNull(id, "id");
        this.challengerTeam = snapshot(Objects.requireNonNull(challengers, "challengers"));
        this.opponentTeam = snapshot(Objects.requireNonNull(opponents, "opponents"));
        this.duelSettings = Objects.requireNonNull(settings, "settings").copy();
        if (challengerTeam.size() != opponentTeam.size()) {
            throw new IllegalArgumentException("Duel Party challenges require equally sized teams.");
        }
        Set<UUID> participants = new HashSet<>(challengerTeam.participantIds());
        if (!java.util.Collections.disjoint(participants, opponentTeam.participantIds())) {
            throw new IllegalArgumentException("A player cannot participate on both sides of a challenge.");
        }
        participants.addAll(opponentTeam.participantIds());
        this.requiredParticipantIds = Set.copyOf(participants);
        if (expiresAtEpochMs <= createdAtEpochMs) {
            throw new IllegalArgumentException("Challenge expiration must be after its creation time.");
        }
        this.createdAtMillis = createdAtEpochMs;
        this.expiresAtMillis = expiresAtEpochMs;
    }

    public UUID id() {
        return challengeId;
    }

    public MatchTeam challengerTeam() {
        return challengerTeam;
    }

    public MatchTeam opponentTeam() {
        return opponentTeam;
    }

    public DuelSettings settings() {
        return duelSettings.copy();
    }

    public long createdAtEpochMs() {
        return createdAtMillis;
    }

    public long expiresAtEpochMs() {
        return expiresAtMillis;
    }

    public DuelChallengeStatus status(long nowEpochMs) {
        expireIfNeeded(nowEpochMs);
        return currentStatus;
    }

    public DuelChallengeStatus accept(UUID playerId, long nowEpochMs) {
        expireIfNeeded(nowEpochMs);
        requirePending();
        if (!requiredParticipantIds.contains(playerId)) {
            throw new IllegalArgumentException("Only a snapshotted participant may accept this challenge.");
        }
        acceptedParticipantIds.add(playerId);
        if (acceptedParticipantIds.equals(requiredParticipantIds)) {
            currentStatus = DuelChallengeStatus.READY;
        }
        return currentStatus;
    }

    public DuelChallengeStatus decline(UUID playerId, long nowEpochMs) {
        expireIfNeeded(nowEpochMs);
        requirePending();
        if (!requiredParticipantIds.contains(playerId)) {
            throw new IllegalArgumentException("Only a snapshotted participant may decline this challenge.");
        }
        currentStatus = DuelChallengeStatus.DECLINED;
        return currentStatus;
    }

    public int acceptedCount() {
        return acceptedParticipantIds.size();
    }

    public int requiredAcceptanceCount() {
        return requiredParticipantIds.size();
    }

    public boolean hasAccepted(UUID playerId) {
        return acceptedParticipantIds.contains(playerId);
    }

    public Set<UUID> waitingForParticipantIds() {
        Set<UUID> waiting = new HashSet<>(requiredParticipantIds);
        waiting.removeAll(acceptedParticipantIds);
        return Set.copyOf(waiting);
    }

    private void expireIfNeeded(long nowEpochMs) {
        if (currentStatus == DuelChallengeStatus.PENDING && nowEpochMs >= expiresAtMillis) {
            currentStatus = DuelChallengeStatus.EXPIRED;
        }
    }

    private void requirePending() {
        if (currentStatus != DuelChallengeStatus.PENDING) {
            throw new IllegalStateException("Challenge is no longer pending: " + currentStatus);
        }
    }

    private MatchTeam snapshot(MatchTeam team) {
        return new MatchTeam(
            team.id(),
            team.participants().stream()
                .map(participant -> new MatchParticipant(participant.playerId(), participant.name()))
                .toList()
        );
    }
}
