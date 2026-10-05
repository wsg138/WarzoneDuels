package dev.minecraft.warzoneduels.app;

import dev.minecraft.warzoneduels.domain.DuelParty;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuelPartyServiceTest {
    private static final long INVITATION_LIFETIME_MILLIS = 30_000L;

    @Test
    void indexesEachPlayerIntoAtMostOneParty() {
        DuelPartyService service = service();
        UUID leaderId = UUID.randomUUID();
        DuelParty party = service.createParty(leaderId, "Leader");

        assertEquals(party, service.partyOf(leaderId).orElseThrow());
        assertThrows(IllegalStateException.class, () -> service.createParty(leaderId, "Leader"));
    }

    @Test
    void onlyLeaderCanInviteAndExpiredInvitationsCannotBeAccepted() {
        DuelPartyService service = service();
        UUID leaderId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        DuelParty party = service.createParty(leaderId, "Leader");
        service.invite(leaderId, memberId, "Member", 1_000L);
        service.acceptInvite(memberId, party.id(), 1_001L);

        assertThrows(IllegalStateException.class, () -> service.invite(memberId, targetId, "Target", 1_002L));
        service.invite(leaderId, targetId, "Target", 2_000L);
        assertTrue(service.invitationFor(targetId, party.id(), 2_001L).isPresent());
        assertThrows(IllegalStateException.class,
            () -> service.acceptInvite(targetId, party.id(), 2_000L + INVITATION_LIFETIME_MILLIS));
        assertTrue(service.partyOf(targetId).isEmpty());
        assertTrue(service.invitationFor(targetId, party.id(), Long.MAX_VALUE).isEmpty());
    }

    @Test
    void acceptingOneInvitationClearsOtherPendingInvitations() {
        DuelPartyService service = service();
        UUID firstLeaderId = UUID.randomUUID();
        UUID secondLeaderId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        DuelParty first = service.createParty(firstLeaderId, "First");
        DuelParty second = service.createParty(secondLeaderId, "Second");
        service.invite(firstLeaderId, targetId, "Target", 1_000L);
        service.invite(secondLeaderId, targetId, "Target", 1_000L);

        service.acceptInvite(targetId, second.id(), 1_001L);

        assertEquals(second, service.partyOf(targetId).orElseThrow());
        assertTrue(service.invitationFor(targetId, first.id(), 1_002L).isEmpty());
        assertThrows(IllegalStateException.class, () -> service.acceptInvite(targetId, first.id(), 1_002L));
    }

    @Test
    void leadershipKickLeaveAndDisbandKeepMembershipIndexConsistent() {
        DuelPartyService service = service();
        UUID leaderId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        UUID thirdId = UUID.randomUUID();
        DuelParty party = service.createParty(leaderId, "Leader");
        join(service, party, leaderId, secondId, "Second");
        join(service, party, leaderId, thirdId, "Third");

        service.transferLeadership(leaderId, secondId);
        service.kickMember(secondId, thirdId);
        assertTrue(service.partyOf(thirdId).isEmpty());
        service.leaveParty(leaderId);
        assertTrue(service.partyOf(leaderId).isEmpty());
        service.disbandParty(secondId);

        assertTrue(service.partyOf(secondId).isEmpty());
        assertFalse(service.parties().containsKey(party.id()));
    }

    @Test
    void rosterLockRejectsEveryMembershipMutation() {
        DuelPartyService service = service();
        UUID leaderId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        DuelParty party = service.createParty(leaderId, "Leader");
        join(service, party, leaderId, memberId, "Member");
        party.lockRoster(UUID.randomUUID());

        assertThrows(IllegalStateException.class, () -> service.invite(leaderId, targetId, "Target", 1_000L));
        assertThrows(IllegalStateException.class, () -> service.kickMember(leaderId, memberId));
        assertThrows(IllegalStateException.class, () -> service.leaveParty(memberId));
        assertThrows(IllegalStateException.class, () -> service.transferLeadership(leaderId, memberId));
        assertThrows(IllegalStateException.class, () -> service.disbandParty(leaderId));
    }

    private DuelPartyService service() {
        return new DuelPartyService(INVITATION_LIFETIME_MILLIS, UUID::randomUUID);
    }

    private void join(
        DuelPartyService service,
        DuelParty party,
        UUID leaderId,
        UUID playerId,
        String playerName
    ) {
        service.invite(leaderId, playerId, playerName, 1_000L);
        service.acceptInvite(playerId, party.id(), 1_001L);
    }
}
