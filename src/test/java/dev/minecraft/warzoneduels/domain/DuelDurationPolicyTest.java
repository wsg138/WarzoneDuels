package dev.minecraft.warzoneduels.domain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DuelDurationPolicyTest {
    @Test
    void zeroIsUnlimitedAndPositiveSecondsProduceAReleaseBasedDeadline() {
        assertNull(DuelDurationPolicy.deadlineEpochMs(1_000L, 0));
        assertEquals(91_000L, DuelDurationPolicy.deadlineEpochMs(1_000L, 90));
        assertEquals(1_800L, DuelDurationPolicy.remainingTicks(91_000L, 1_000L));
        assertEquals(1L, DuelDurationPolicy.remainingTicks(1_001L, 1_000L));
        assertEquals(0L, DuelDurationPolicy.remainingTicks(1_000L, 1_000L));
    }

    @Test
    void liveServiceConfiguresSchedulesCancelsAndPersistsTheOptionalDeadline() throws IOException {
        String service = Files.readString(Path.of(
            "src/main/java/dev/minecraft/warzoneduels/app/DuelService.java"
        ));
        String runtimeStore = Files.readString(Path.of(
            "src/main/java/dev/minecraft/warzoneduels/adapter/bukkit/persistence/RuntimeStateStore.java"
        ));
        String config = Files.readString(Path.of("src/main/resources/config.yml"));

        assertEquals(true, config.contains("duel-time-limit-seconds: 0"));
        assertEquals(true, service.contains("config.getInt(\"settings.duel-time-limit-seconds\", 0)"));
        assertEquals(true, service.contains("startDuelTimeLimit()"));
        assertEquals(true, service.contains("cancelDuelTimeLimitTask()"));
        assertEquals(true, runtimeStore.contains("duel-deadline-epoch-ms"));
    }
}
