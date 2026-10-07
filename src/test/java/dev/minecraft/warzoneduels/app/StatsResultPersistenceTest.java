package dev.minecraft.warzoneduels.app;

import dev.minecraft.warzoneduels.domain.*;
import dev.minecraft.warzoneduels.domain.stats.PlayerDuelStats;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class StatsResultPersistenceTest {
    private StatsService service() throws Exception {
        var constructor = assertDoesNotThrow(() -> StatsService.class.getDeclaredConstructor(Consumer.class));
        constructor.setAccessible(true);
        Consumer<Map<UUID, PlayerDuelStats>> save = ignored -> { };
        return constructor.newInstance(save);
    }

    private ActiveDuel duel() {
        var first = new MatchParticipant(UUID.randomUUID(), "First");
        var second = new MatchParticipant(UUID.randomUUID(), "Second");
        var settings = new DuelSettings();
        settings.setAllowEnderPearls(false);
        settings.setAllowWindCharges(false);
        return new ActiveDuel(DuelMatchType.PARTY, MatchTeam.singleton(first), MatchTeam.singleton(second), settings, 1L);
    }

    private void record(StatsService service, ActiveDuel duel, UUID winner, DuelEndReason reason, boolean durable) throws Exception {
        var method = assertDoesNotThrow(() -> StatsService.class.getMethod("recordMatchResult",
            ActiveDuel.class, UUID.class, DuelEndReason.class, boolean.class));
        method.invoke(service, duel, winner, reason, durable);
    }

    @Test void failedCooldownWriteStillRecordsWinAndLossWithoutSpecializedEvidence() throws Exception {
        var service = service();
        var duel = duel();
        record(service, duel, duel.participantOne().playerId(), DuelEndReason.KILL, false);
        assertEquals(1, service.findById(duel.participantOne().playerId()).wins());
        assertEquals(1, service.findById(duel.participantTwo().playerId()).losses());
        assertEquals(0, service.findById(duel.participantOne().playerId()).restrictedMobilityWins());
    }

    @Test void failedCooldownWriteStillRecordsDraws() throws Exception {
        var service = service();
        var duel = duel();
        record(service, duel, null, DuelEndReason.DRAW, false);
        assertEquals(1, service.findById(duel.participantOne().playerId()).draws());
        assertEquals(1, service.findById(duel.participantTwo().playerId()).draws());
    }

    @Test void durableCooldownWriteAllowsSpecializedEvidence() throws Exception {
        var service = service();
        var duel = duel();
        record(service, duel, duel.participantOne().playerId(), DuelEndReason.KILL, true);
        assertEquals(1, service.findById(duel.participantOne().playerId()).wins());
        assertEquals(1, service.findById(duel.participantOne().playerId()).restrictedMobilityWins());
    }

    @Test void terminalWiringAlwaysRecordsOrdinaryNormalResults() throws Exception {
        String source = Files.readString(Path.of("src/main/java/dev/minecraft/warzoneduels/app/DuelService.java"));
        assertTrue(source.contains("if (normalResult) {"));
        assertTrue(source.contains("statsService.recordMatchResult(finishedDuel, winnerId, reason, durableCooldown)"));
    }
}
