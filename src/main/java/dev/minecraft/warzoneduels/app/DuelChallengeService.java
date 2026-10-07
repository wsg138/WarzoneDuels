package dev.minecraft.warzoneduels.app;

import dev.minecraft.warzoneduels.domain.DuelChallenge;
import dev.minecraft.warzoneduels.domain.DuelChallengeStatus;
import dev.minecraft.warzoneduels.domain.DuelParty;
import dev.minecraft.warzoneduels.domain.DuelSettings;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class DuelChallengeService {
    private final DuelPartyService partyService;
    private final long challengeLifetimeMillis;
    private final Supplier<UUID> challengeIdSupplier;
    private final Map<UUID, DuelChallenge> challengesById = new HashMap<>();
    private final Map<UUID, UUID> challengeIdByParticipant = new HashMap<>();

    public DuelChallengeService(DuelPartyService parties, long challengeLifetime, Supplier<UUID> idSupplier) {
        this.partyService = Objects.requireNonNull(parties, "parties");
        if (challengeLifetime <= 0L) {
            throw new IllegalArgumentException("Challenge lifetime must be positive.");
        }
        this.challengeLifetimeMillis = challengeLifetime;
        this.challengeIdSupplier = Objects.requireNonNull(idSupplier, "idSupplier");
    }

    public synchronized DuelChallenge createPartyChallenge(
        UUID challengerLeaderId,
        UUID opponentLeaderId,
        DuelSettings settings,
        long nowEpochMs
    ) {
        DuelParty challengers = requireLeaderParty(challengerLeaderId);
        DuelParty opponents = requireLeaderParty(opponentLeaderId);
        if (challengers.id().equals(opponents.id())) {
            throw new IllegalArgumentException("A Duel Party cannot challenge itself.");
        }
        if (challengers.size() != opponents.size()) {
            throw new IllegalArgumentException("Duel Parties must have exactly the same number of players.");
        }
        ensureRosterAvailable(challengers, nowEpochMs);
        ensureRosterAvailable(opponents, nowEpochMs);

        UUID challengeId = Objects.requireNonNull(challengeIdSupplier.get(), "challengeIdSupplier result");
        if (challengesById.containsKey(challengeId)) {
            throw new IllegalStateException("Generated duel challenge ID is already in use.");
        }
        DuelChallenge challenge = new DuelChallenge(
            challengeId,
            challengers.snapshotTeam(),
            opponents.snapshotTeam(),
            settings,
            nowEpochMs,
            Math.addExact(nowEpochMs, challengeLifetimeMillis)
        );

        challengers.lockRoster(challengeId);
        try {
            opponents.lockRoster(challengeId);
        } catch (RuntimeException ex) {
            challengers.unlockRoster(challengeId);
            throw ex;
        }
        challengesById.put(challengeId, challenge);
        challenge.challengerTeam().participantIds().forEach(playerId -> challengeIdByParticipant.put(playerId, challengeId));
        challenge.opponentTeam().participantIds().forEach(playerId -> challengeIdByParticipant.put(playerId, challengeId));
        return challenge;
    }

    public synchronized Optional<DuelChallenge> challengeForParticipant(UUID playerId, long nowEpochMs) {
        UUID challengeId = challengeIdByParticipant.get(playerId);
        if (challengeId == null) {
            return Optional.empty();
        }
        DuelChallenge challenge = challengesById.get(challengeId);
        if (challenge == null) {
            challengeIdByParticipant.remove(playerId);
            return Optional.empty();
        }
        if (challenge.status(nowEpochMs) == DuelChallengeStatus.EXPIRED) {
            release(challenge);
            return Optional.empty();
        }
        return Optional.of(challenge);
    }

    public synchronized Optional<DuelChallenge> readyChallengeFor(UUID playerId, long nowEpochMs) {
        return challengeForParticipant(playerId, nowEpochMs)
            .filter(challenge -> challenge.status(nowEpochMs) == DuelChallengeStatus.READY);
    }

    public synchronized DuelChallengeStatus accept(UUID playerId, long nowEpochMs) {
        DuelChallenge challenge = challengeForParticipant(playerId, nowEpochMs)
            .orElseThrow(() -> new IllegalStateException("Player has no active duel challenge."));
        return challenge.accept(playerId, nowEpochMs);
    }

    public synchronized DuelChallengeStatus decline(UUID playerId, long nowEpochMs) {
        DuelChallenge challenge = challengeForParticipant(playerId, nowEpochMs)
            .orElseThrow(() -> new IllegalStateException("Player has no active duel challenge."));
        DuelChallengeStatus status = challenge.decline(playerId, nowEpochMs);
        release(challenge);
        return status;
    }

    public synchronized void cancel(UUID playerId, long nowEpochMs) {
        DuelChallenge challenge = challengeForParticipant(playerId, nowEpochMs)
            .orElseThrow(() -> new IllegalStateException("Player has no active duel challenge."));
        release(challenge);
    }

    public synchronized void complete(UUID challengeId) {
        DuelChallenge challenge = challengesById.get(challengeId);
        if (challenge == null) {
            throw new IllegalStateException("Duel challenge is no longer active.");
        }
        release(challenge);
    }

    public synchronized int activeChallengeCount() {
        return challengesById.size();
    }

    private DuelParty requireLeaderParty(UUID leaderId) {
        DuelParty party = partyService.partyOf(leaderId)
            .orElseThrow(() -> new IllegalStateException("A Duel Party leader is required."));
        if (!party.leaderId().equals(leaderId)) {
            throw new IllegalStateException("Only a Duel Party leader may create a party challenge.");
        }
        return party;
    }

    private void ensureRosterAvailable(DuelParty party, long nowEpochMs) {
        for (DuelParty.DuelPartyMember member : party.members()) {
            if (challengeForParticipant(member.playerId(), nowEpochMs).isPresent()) {
                throw new IllegalStateException(member.name() + " is already part of another duel challenge.");
            }
        }
        if (party.isRosterLocked()) {
            throw new IllegalStateException("A Duel Party roster is already locked by another challenge.");
        }
    }

    private void release(DuelChallenge challenge) {
        challengesById.remove(challenge.id());
        challenge.challengerTeam().participantIds().forEach(playerId -> challengeIdByParticipant.remove(playerId, challenge.id()));
        challenge.opponentTeam().participantIds().forEach(playerId -> challengeIdByParticipant.remove(playerId, challenge.id()));
        unlockParty(challenge.challengerTeam().id(), challenge.id());
        unlockParty(challenge.opponentTeam().id(), challenge.id());
    }

    private void unlockParty(UUID partyId, UUID challengeId) {
        DuelParty party = partyService.parties().get(partyId);
        if (party != null && challengeId.equals(party.rosterLockId())) {
            party.unlockRoster(challengeId);
        }
    }
}
