package dev.minecraft.warzoneduels.app;

import java.util.Objects;
import java.util.UUID;

public record DuelPartyInvitation(
    UUID partyId,
    UUID invitedBy,
    UUID inviteeId,
    String inviteeName,
    long createdAtEpochMs,
    long expiresAtEpochMs
) {
    public DuelPartyInvitation {
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(invitedBy, "invitedBy");
        Objects.requireNonNull(inviteeId, "inviteeId");
        if (inviteeName == null || inviteeName.isBlank()) {
            throw new IllegalArgumentException("Invitee name cannot be blank.");
        }
        if (expiresAtEpochMs <= createdAtEpochMs) {
            throw new IllegalArgumentException("Invitation expiration must be after its creation time.");
        }
    }

    public boolean isExpired(long nowEpochMs) {
        return nowEpochMs >= expiresAtEpochMs;
    }
}
