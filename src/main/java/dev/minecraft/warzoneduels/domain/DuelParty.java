package dev.minecraft.warzoneduels.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class DuelParty {
    private final UUID partyId;
    private final Map<UUID, String> members = new LinkedHashMap<>();
    private UUID leaderId;
    private UUID rosterLockId;

    public DuelParty(UUID id, UUID initialLeaderId, String initialLeaderName) {
        this.partyId = Objects.requireNonNull(id, "id");
        this.leaderId = Objects.requireNonNull(initialLeaderId, "initialLeaderId");
        members.put(initialLeaderId, requireName(initialLeaderName));
    }

    public UUID id() {
        return partyId;
    }

    public UUID leaderId() {
        return leaderId;
    }

    public List<DuelPartyMember> members() {
        return members.entrySet().stream()
            .map(entry -> new DuelPartyMember(entry.getKey(), entry.getValue()))
            .toList();
    }

    public int size() {
        return members.size();
    }

    public boolean contains(UUID playerId) {
        return members.containsKey(playerId);
    }

    public boolean isRosterLocked() {
        return rosterLockId != null;
    }

    public UUID rosterLockId() {
        return rosterLockId;
    }

    public void addMember(UUID playerId, String playerName) {
        requireUnlocked();
        Objects.requireNonNull(playerId, "playerId");
        if (members.containsKey(playerId)) {
            throw new IllegalArgumentException("Player is already in this Duel Party.");
        }
        if (members.size() >= MatchTeam.MAX_SIZE) {
            throw new IllegalStateException("A Duel Party cannot contain more than " + MatchTeam.MAX_SIZE + " players.");
        }
        members.put(playerId, requireName(playerName));
    }

    public void removeMember(UUID playerId) {
        requireUnlocked();
        if (leaderId.equals(playerId)) {
            throw new IllegalStateException("Transfer leadership or disband the Duel Party before removing its leader.");
        }
        if (members.remove(playerId) == null) {
            throw new IllegalArgumentException("Player is not in this Duel Party.");
        }
    }

    public void transferLeadership(UUID playerId) {
        requireUnlocked();
        if (!members.containsKey(playerId)) {
            throw new IllegalArgumentException("The new leader must be a Duel Party member.");
        }
        leaderId = playerId;
    }

    public void lockRoster(UUID challengeId) {
        Objects.requireNonNull(challengeId, "challengeId");
        if (rosterLockId != null && !rosterLockId.equals(challengeId)) {
            throw new IllegalStateException("The Duel Party roster is already locked by another challenge.");
        }
        rosterLockId = challengeId;
    }

    public void unlockRoster(UUID challengeId) {
        Objects.requireNonNull(challengeId, "challengeId");
        if (rosterLockId == null) {
            return;
        }
        if (!rosterLockId.equals(challengeId)) {
            throw new IllegalArgumentException("Only the challenge that locked this roster may unlock it.");
        }
        rosterLockId = null;
    }

    public MatchTeam snapshotTeam() {
        List<MatchParticipant> participants = members.entrySet().stream()
            .map(entry -> new MatchParticipant(entry.getKey(), entry.getValue()))
            .toList();
        return new MatchTeam(partyId, participants);
    }

    private void requireUnlocked() {
        if (isRosterLocked()) {
            throw new IllegalStateException("The Duel Party roster is locked while its challenge is pending.");
        }
    }

    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Player name cannot be blank.");
        }
        return name;
    }

    public record DuelPartyMember(UUID playerId, String name) {
        public DuelPartyMember {
            Objects.requireNonNull(playerId, "playerId");
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Player name cannot be blank.");
            }
        }
    }
}
