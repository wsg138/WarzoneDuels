package dev.minecraft.warzoneduels.domain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplosiveTeamCombatPolicyTest {
    @Test
    void partyExplosionRequiresAnOpposingAttributedParticipant() {
        Fixture fixture = fixture(DuelMatchType.PARTY);

        assertTrue(ExplosiveCombatPolicy.shouldCancelDamage(
            fixture.duel, fixture.first.playerId(), fixture.teammate.playerId()
        ));
        assertTrue(ExplosiveCombatPolicy.shouldCancelDamage(
            fixture.duel, fixture.first.playerId(), null
        ));
        assertFalse(ExplosiveCombatPolicy.shouldCancelDamage(
            fixture.duel, fixture.first.playerId(), fixture.opponent.playerId()
        ));
    }

    @Test
    void legacyOneVersusOneKeepsUnattributedExplosionDamage() {
        Fixture fixture = fixture(DuelMatchType.NORMAL);

        assertFalse(ExplosiveCombatPolicy.shouldCancelDamage(
            fixture.duel, fixture.first.playerId(), null
        ));
    }

    @Test
    void eliminatingBothTeamsInOneBatchIsADraw() {
        Fixture fixture = fixture(DuelMatchType.PARTY);

        TeamEliminationOutcome outcome = TeamMatchPolicy.eliminationOutcome(
            fixture.duel,
            Set.of(
                fixture.first.playerId(), fixture.teammate.playerId(),
                fixture.opponent.playerId(), fixture.secondOpponent.playerId()
            )
        );

        assertEquals(TeamEliminationOutcome.DRAW, outcome);
    }

    @Test
    void liveAdapterTracksExplosiveOwnersAndBatchesDeaths() throws IOException {
        String listener = Files.readString(Path.of(
            "src/main/java/dev/minecraft/warzoneduels/adapter/bukkit/listener/DuelListener.java"
        ));
        String service = Files.readString(Path.of(
            "src/main/java/dev/minecraft/warzoneduels/app/DuelService.java"
        ));

        assertTrue(listener.contains("event.getDamageSource().getCausingEntity()"));
        assertTrue(listener.contains("duelService.trackBlockExplosionSource("));
        assertTrue(listener.contains("duelService.recordAttributedDamage("));
        assertTrue(listener.contains("duelService.shouldCancelExplosiveDamage("));
        assertTrue(service.contains("pendingDeaths.put("));
        assertTrue(service.contains("runTask(plugin, this::resolvePendingDeaths)"));
        assertTrue(service.contains("TeamEliminationOutcome.DRAW"));
        assertTrue(service.contains("restoreLoadoutAfterRespawn"));
    }

    private static Fixture fixture(DuelMatchType matchType) {
        MatchParticipant first = participant("First");
        MatchParticipant teammate = participant("Teammate");
        MatchParticipant opponent = participant("Opponent");
        MatchParticipant secondOpponent = participant("SecondOpponent");
        MatchTeam firstTeam = matchType == DuelMatchType.NORMAL
            ? MatchTeam.singleton(first)
            : new MatchTeam(UUID.randomUUID(), List.of(first, teammate));
        MatchTeam secondTeam = matchType == DuelMatchType.NORMAL
            ? MatchTeam.singleton(opponent)
            : new MatchTeam(UUID.randomUUID(), List.of(opponent, secondOpponent));
        return new Fixture(
            new ActiveDuel(matchType, firstTeam, secondTeam, new DuelSettings(), 1L),
            first, teammate, opponent, secondOpponent
        );
    }

    private static MatchParticipant participant(String name) {
        return new MatchParticipant(UUID.randomUUID(), name);
    }

    private record Fixture(
        ActiveDuel duel,
        MatchParticipant first,
        MatchParticipant teammate,
        MatchParticipant opponent,
        MatchParticipant secondOpponent
    ) {
    }
}
