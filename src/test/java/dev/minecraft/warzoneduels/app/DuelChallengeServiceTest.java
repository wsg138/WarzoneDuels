package dev.minecraft.warzoneduels.app;

import dev.minecraft.warzoneduels.domain.DuelChallenge;
import dev.minecraft.warzoneduels.domain.DuelChallengeStatus;
import dev.minecraft.warzoneduels.domain.DuelParty;
import dev.minecraft.warzoneduels.domain.DuelSettings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuelChallengeServiceTest {
    @Test
    void expiredChallengeCanBeReplacedWithoutAnIntermediateLookup() {
        Fixture fixture = fixture(2, 2);
        DuelChallenge old = fixture.challenges.createPartyChallenge(
            fixture.first.leaderId(), fixture.second.leaderId(), new DuelSettings(), 1_000L);
        DuelChallenge replacement = fixture.challenges.createPartyChallenge(
            fixture.first.leaderId(), fixture.second.leaderId(), new DuelSettings(), 31_000L);
        assertFalse(old.id().equals(replacement.id()));
        assertEquals(1, fixture.challenges.activeChallengeCount());
        assertEquals(replacement.id(), fixture.first.rosterLockId());
        assertEquals(replacement.id(), fixture.second.rosterLockId());
        for (UUID playerId : participantIds(fixture.first, fixture.second)) {
            assertEquals(replacement, fixture.challenges.challengeForParticipant(playerId, 31_001L).orElseThrow());
        }
    }

    @Test
    void partyChallengeLocksBothRostersAndIndexesEveryParticipant() {
        Fixture fixture = fixture(2, 2);
        DuelChallenge challenge = fixture.challenges.createPartyChallenge(
            fixture.first.leaderId(), fixture.second.leaderId(), new DuelSettings(), 1_000L);

        assertTrue(fixture.first.isRosterLocked());
        assertTrue(fixture.second.isRosterLocked());
        for (UUID playerId : participantIds(fixture.first, fixture.second)) {
            assertEquals(challenge, fixture.challenges.challengeForParticipant(playerId, 1_001L).orElseThrow());
        }
    }

    @Test
    void challengeBecomesReadyOnlyAfterAllParticipantsAccept() {
        Fixture fixture = fixture(2, 2);
        DuelChallenge challenge = fixture.challenges.createPartyChallenge(
            fixture.first.leaderId(), fixture.second.leaderId(), new DuelSettings(), 1_000L);
        List<UUID> participants = participantIds(fixture.first, fixture.second);

        for (int index = 0; index < participants.size() - 1; index++) {
            assertEquals(DuelChallengeStatus.PENDING, fixture.challenges.accept(participants.get(index), 1_001L));
        }
        assertEquals(DuelChallengeStatus.READY,
            fixture.challenges.accept(participants.get(participants.size() - 1), 1_001L));
        assertEquals(challenge, fixture.challenges.readyChallengeFor(participants.get(0), 1_002L).orElseThrow());
        assertTrue(fixture.first.isRosterLocked());
    }

    @Test
    void declineExpiryAndCompletionReleaseBothRostersAndIndexes() {
        Fixture declined = fixture(1, 1);
        declined.challenges.createPartyChallenge(
            declined.first.leaderId(), declined.second.leaderId(), new DuelSettings(), 1_000L);
        declined.challenges.decline(declined.second.leaderId(), 1_001L);
        assertReleased(declined);

        Fixture expired = fixture(1, 1);
        expired.challenges.createPartyChallenge(
            expired.first.leaderId(), expired.second.leaderId(), new DuelSettings(), 1_000L);
        assertTrue(expired.challenges.challengeForParticipant(expired.first.leaderId(), 31_000L).isEmpty());
        assertReleased(expired);

        Fixture completed = fixture(1, 1);
        DuelChallenge challenge = completed.challenges.createPartyChallenge(
            completed.first.leaderId(), completed.second.leaderId(), new DuelSettings(), 1_000L);
        completed.challenges.accept(completed.first.leaderId(), 1_001L);
        completed.challenges.accept(completed.second.leaderId(), 1_001L);
        completed.challenges.complete(challenge.id());
        assertReleased(completed);
    }

    @Test
    void unequalOrAlreadyBusyRostersAreRejectedWithoutPartialLocks() {
        Fixture unequal = fixture(1, 2);
        assertThrows(IllegalArgumentException.class, () -> unequal.challenges.createPartyChallenge(
            unequal.first.leaderId(), unequal.second.leaderId(), new DuelSettings(), 1_000L));
        assertFalse(unequal.first.isRosterLocked());
        assertFalse(unequal.second.isRosterLocked());

        Fixture busy = fixture(1, 1);
        busy.challenges.createPartyChallenge(
            busy.first.leaderId(), busy.second.leaderId(), new DuelSettings(), 1_000L);
        assertThrows(IllegalStateException.class, () -> busy.challenges.createPartyChallenge(
            busy.first.leaderId(), busy.second.leaderId(), new DuelSettings(), 1_001L));
    }

    private Fixture fixture(int firstSize, int secondSize) {
        DuelPartyService parties = new DuelPartyService(30_000L, UUID::randomUUID);
        DuelParty first = createParty(parties, "First", firstSize);
        DuelParty second = createParty(parties, "Second", secondSize);
        return new Fixture(parties, new DuelChallengeService(parties, 30_000L, UUID::randomUUID), first, second);
    }

    private DuelParty createParty(DuelPartyService parties, String leaderName, int size) {
        UUID leaderId = UUID.randomUUID();
        DuelParty party = parties.createParty(leaderId, leaderName);
        for (int index = 1; index < size; index++) {
            UUID memberId = UUID.randomUUID();
            parties.invite(leaderId, memberId, leaderName + index, 1L);
            parties.acceptInvite(memberId, party.id(), 2L);
        }
        return party;
    }

    private List<UUID> participantIds(DuelParty... parties) {
        List<UUID> result = new ArrayList<>();
        for (DuelParty party : parties) {
            party.members().stream().map(DuelParty.DuelPartyMember::playerId).forEach(result::add);
        }
        return result;
    }

    private void assertReleased(Fixture fixture) {
        assertFalse(fixture.first.isRosterLocked());
        assertFalse(fixture.second.isRosterLocked());
        for (UUID playerId : participantIds(fixture.first, fixture.second)) {
            assertTrue(fixture.challenges.challengeForParticipant(playerId, Long.MAX_VALUE).isEmpty());
        }
    }

    private record Fixture(
        DuelPartyService parties,
        DuelChallengeService challenges,
        DuelParty first,
        DuelParty second
    ) {
    }
}
