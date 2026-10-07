package dev.minecraft.warzoneduels.app;

import dev.minecraft.warzoneduels.domain.*;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PlaytestRegressionTest {
    // spawnFor and winnerAnnouncement use only assigned arena/roster fields.
    // Bypass server wiring, but execute the real service methods and arena API.
    private DuelService service(ActiveDuel duel) throws Exception {
        Field singleton = Unsafe.class.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        DuelService service = (DuelService) ((Unsafe) singleton.get(null)).allocateInstance(DuelService.class);
        Field active = DuelService.class.getDeclaredField("activeDuel");
        active.setAccessible(true);
        active.set(service, duel);
        Field arena = DuelService.class.getDeclaredField("arena");
        arena.setAccessible(true);
        arena.set(service, new ArenaDefinition("world", location(-20), location(40),
            List.of(location(1), location(2), location(3)),
            List.of(location(11), location(12), location(13)), location(30), location(40)));
        return service;
    }

    @Test
    void ownExplosionsHurtButTeammateExplosionsDoNot() {
        ActiveDuel duel = duel();
        UUID victim = duel.teamOne().participants().get(0).playerId();
        UUID teammate = duel.teamOne().participants().get(1).playerId();
        assertFalse(ExplosiveCombatPolicy.shouldCancelDamage(duel, victim, victim));
        assertTrue(ExplosiveCombatPolicy.shouldCancelDamage(duel, victim, teammate));
    }

    @Test
    void everyParticipantResolvesTheirOwnTeamSpawn() throws Exception {
        ActiveDuel duel = duel();
        DuelService service = service(duel);
        for (int slot = 0; slot < 3; slot++) {
            assertEquals(slot + 1D, service.spawnFor(duel.teamOne().participants().get(slot).playerId()).getX());
            assertEquals(slot + 11D, service.spawnFor(duel.teamTwo().participants().get(slot).playerId()).getX());
        }
        assertNull(service.spawnFor(UUID.randomUUID()));
    }

    @Test
    void partyWinnerAnnouncementIncludesEveryWinningMember() throws Exception {
        ActiveDuel duel = duel();
        var method = DuelService.class.getDeclaredMethod("winnerAnnouncement", ActiveDuel.class, UUID.class, String.class);
        method.setAccessible(true);
        assertEquals("Party [A + B + C]", method.invoke(service(duel), duel, duel.teamOne().participants().get(0).playerId(), "A"));
    }

    @Test
    void leaderLeavingDisbandsMembersAndPendingInvitations() {
        DuelPartyService parties = new DuelPartyService(30_000, UUID::randomUUID);
        UUID leader = UUID.randomUUID(), member = UUID.randomUUID(), invitee = UUID.randomUUID();
        DuelParty party = parties.createParty(leader, "Leader");
        parties.invite(leader, member, "Member", 0);
        parties.acceptInvite(member, party.id(), 1);
        parties.invite(leader, invitee, "Invitee", 2);
        parties.leaveParty(leader);
        assertTrue(parties.partyOf(leader).isEmpty());
        assertTrue(parties.partyOf(member).isEmpty());
        assertTrue(parties.invitationFor(invitee, party.id(), 3).isEmpty());
        assertTrue(parties.parties().isEmpty());
    }

    private ActiveDuel duel() {
        return new ActiveDuel(DuelMatchType.PARTY, team("A", "B", "C"), team("D", "E", "F"), new DuelSettings(), 1);
    }
    private MatchTeam team(String... names) {
        return new MatchTeam(UUID.randomUUID(), java.util.Arrays.stream(names)
            .map(name -> new MatchParticipant(UUID.randomUUID(), name)).toList());
    }
    private Location location(double x) { return new Location(null, x, 64, 0); }
}
