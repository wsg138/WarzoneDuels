package dev.minecraft.warzoneduels.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class MatchTeam {
    public static final int MAX_SIZE = 3;

    private final UUID teamId;
    private final List<MatchParticipant> participants;

    public MatchTeam(UUID id, List<MatchParticipant> members) {
        this.teamId = Objects.requireNonNull(id, "id");
        Objects.requireNonNull(members, "members");
        if (members.isEmpty() || members.size() > MAX_SIZE) {
            throw new IllegalArgumentException("A match team must contain between 1 and " + MAX_SIZE + " participants.");
        }

        Set<UUID> playerIds = new HashSet<>();
        for (MatchParticipant participant : members) {
            Objects.requireNonNull(participant, "participant");
            if (!playerIds.add(participant.playerId())) {
                throw new IllegalArgumentException("A player cannot appear twice on the same match team.");
            }
        }
        this.participants = List.copyOf(members);
    }

    public static MatchTeam singleton(MatchParticipant participant) {
        Objects.requireNonNull(participant, "participant");
        return new MatchTeam(participant.playerId(), List.of(participant));
    }

    public UUID id() {
        return teamId;
    }

    public List<MatchParticipant> participants() {
        return participants;
    }

    public Set<UUID> participantIds() {
        return participants.stream().map(MatchParticipant::playerId).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public int size() {
        return participants.size();
    }

    public boolean contains(UUID playerId) {
        return participant(playerId) != null;
    }

    public MatchParticipant participant(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return participants.stream()
            .filter(participant -> participant.playerId().equals(playerId))
            .findFirst()
            .orElse(null);
    }
}
