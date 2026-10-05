package dev.minecraft.warzoneduels.app;

import dev.minecraft.warzoneduels.domain.DuelParty;
import dev.minecraft.warzoneduels.domain.MatchTeam;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class DuelPartyService {
    private final long invitationLifetimeMillis;
    private final Supplier<UUID> partyIdSupplier;
    private final Map<UUID, DuelParty> partiesById = new HashMap<>();
    private final Map<UUID, UUID> partyIdByPlayer = new HashMap<>();
    private final Map<UUID, Map<UUID, DuelPartyInvitation>> invitationsByInvitee = new HashMap<>();

    public DuelPartyService(long invitationLifetime, Supplier<UUID> idSupplier) {
        if (invitationLifetime <= 0L) {
            throw new IllegalArgumentException("Invitation lifetime must be positive.");
        }
        this.invitationLifetimeMillis = invitationLifetime;
        this.partyIdSupplier = Objects.requireNonNull(idSupplier, "idSupplier");
    }

    public synchronized DuelParty createParty(UUID leaderId, String leaderName) {
        requireNotInParty(leaderId);
        UUID partyId = Objects.requireNonNull(partyIdSupplier.get(), "partyIdSupplier result");
        if (partiesById.containsKey(partyId)) {
            throw new IllegalStateException("Generated Duel Party ID is already in use.");
        }
        DuelParty party = new DuelParty(partyId, leaderId, leaderName);
        partiesById.put(partyId, party);
        partyIdByPlayer.put(leaderId, partyId);
        clearInvitationsFor(leaderId);
        return party;
    }

    public synchronized Optional<DuelParty> partyOf(UUID playerId) {
        UUID partyId = partyIdByPlayer.get(playerId);
        return Optional.ofNullable(partyId == null ? null : partiesById.get(partyId));
    }

    public synchronized Map<UUID, DuelParty> parties() {
        return Map.copyOf(partiesById);
    }

    public synchronized void invite(UUID leaderId, UUID inviteeId, String inviteeName, long nowEpochMs) {
        DuelParty party = requireLeaderParty(leaderId);
        requireUnlocked(party);
        requireNotInParty(inviteeId);
        if (leaderId.equals(inviteeId)) {
            throw new IllegalArgumentException("A Duel Party leader cannot invite themselves.");
        }
        if (party.size() >= MatchTeam.MAX_SIZE) {
            throw new IllegalStateException("The Duel Party is already full.");
        }
        DuelPartyInvitation invitation = new DuelPartyInvitation(
            party.id(),
            leaderId,
            inviteeId,
            inviteeName,
            nowEpochMs,
            Math.addExact(nowEpochMs, invitationLifetimeMillis)
        );
        invitationsByInvitee.computeIfAbsent(inviteeId, ignored -> new HashMap<>()).put(party.id(), invitation);
    }

    public synchronized Optional<DuelPartyInvitation> invitationFor(UUID inviteeId, UUID partyId, long nowEpochMs) {
        removeExpiredInvitations(inviteeId, nowEpochMs);
        Map<UUID, DuelPartyInvitation> invitations = invitationsByInvitee.get(inviteeId);
        return Optional.ofNullable(invitations == null ? null : invitations.get(partyId));
    }

    public synchronized DuelParty acceptInvite(UUID inviteeId, UUID partyId, long nowEpochMs) {
        requireNotInParty(inviteeId);
        DuelPartyInvitation invitation = invitationFor(inviteeId, partyId, nowEpochMs)
            .orElseThrow(() -> new IllegalStateException("No active invitation exists for this Duel Party."));
        DuelParty party = partiesById.get(invitation.partyId());
        if (party == null) {
            removeInvitation(inviteeId, partyId);
            throw new IllegalStateException("The inviting Duel Party no longer exists.");
        }
        requireUnlocked(party);
        party.addMember(inviteeId, invitation.inviteeName());
        partyIdByPlayer.put(inviteeId, party.id());
        clearInvitationsFor(inviteeId);
        return party;
    }

    public synchronized void declineInvite(UUID inviteeId, UUID partyId) {
        if (!removeInvitation(inviteeId, partyId)) {
            throw new IllegalStateException("No invitation exists for this Duel Party.");
        }
    }

    public synchronized void kickMember(UUID leaderId, UUID memberId) {
        DuelParty party = requireLeaderParty(leaderId);
        if (leaderId.equals(memberId)) {
            throw new IllegalArgumentException("A leader cannot kick themselves.");
        }
        requireSameParty(party, memberId);
        party.removeMember(memberId);
        partyIdByPlayer.remove(memberId);
    }

    public synchronized void transferLeadership(UUID leaderId, UUID memberId) {
        DuelParty party = requireLeaderParty(leaderId);
        requireSameParty(party, memberId);
        party.transferLeadership(memberId);
    }

    public synchronized void leaveParty(UUID playerId) {
        DuelParty party = requireParty(playerId);
        requireUnlocked(party);
        if (party.leaderId().equals(playerId)) {
            disbandParty(playerId);
            return;
        }
        party.removeMember(playerId);
        partyIdByPlayer.remove(playerId);
    }

    public synchronized void disbandParty(UUID leaderId) {
        DuelParty party = requireLeaderParty(leaderId);
        requireUnlocked(party);
        partiesById.remove(party.id());
        for (DuelParty.DuelPartyMember member : party.members()) {
            partyIdByPlayer.remove(member.playerId(), party.id());
        }
        removeInvitationsForParty(party.id());
    }

    private DuelParty requireParty(UUID playerId) {
        return partyOf(playerId).orElseThrow(() -> new IllegalStateException("Player is not in a Duel Party."));
    }

    private DuelParty requireLeaderParty(UUID playerId) {
        DuelParty party = requireParty(playerId);
        if (!party.leaderId().equals(playerId)) {
            throw new IllegalStateException("Only the Duel Party leader may perform this action.");
        }
        return party;
    }

    private void requireSameParty(DuelParty party, UUID playerId) {
        if (!party.contains(playerId)) {
            throw new IllegalArgumentException("Player is not a member of this Duel Party.");
        }
    }

    private void requireNotInParty(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (partyIdByPlayer.containsKey(playerId)) {
            throw new IllegalStateException("Player is already in a Duel Party.");
        }
    }

    private void requireUnlocked(DuelParty party) {
        if (party.isRosterLocked()) {
            throw new IllegalStateException("The Duel Party roster is locked by an active challenge.");
        }
    }

    private void removeExpiredInvitations(UUID inviteeId, long nowEpochMs) {
        Map<UUID, DuelPartyInvitation> invitations = invitationsByInvitee.get(inviteeId);
        if (invitations == null) {
            return;
        }
        invitations.values().removeIf(invitation -> invitation.isExpired(nowEpochMs));
        if (invitations.isEmpty()) {
            invitationsByInvitee.remove(inviteeId);
        }
    }

    private boolean removeInvitation(UUID inviteeId, UUID partyId) {
        Map<UUID, DuelPartyInvitation> invitations = invitationsByInvitee.get(inviteeId);
        if (invitations == null || invitations.remove(partyId) == null) {
            return false;
        }
        if (invitations.isEmpty()) {
            invitationsByInvitee.remove(inviteeId);
        }
        return true;
    }

    private void clearInvitationsFor(UUID inviteeId) {
        invitationsByInvitee.remove(inviteeId);
    }

    private void removeInvitationsForParty(UUID partyId) {
        invitationsByInvitee.values().forEach(invitations -> invitations.remove(partyId));
        invitationsByInvitee.values().removeIf(Map::isEmpty);
    }
}
