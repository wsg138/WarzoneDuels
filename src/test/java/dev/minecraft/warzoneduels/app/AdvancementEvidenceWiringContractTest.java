package dev.minecraft.warzoneduels.app;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancementEvidenceWiringContractTest {
    @Test
    void gameplayBoundariesRecordAdvancementEvidence() throws IOException {
        String duelService = Files.readString(Path.of(
            "src/main/java/dev/minecraft/warzoneduels/app/DuelService.java"));
        String statsService = Files.readString(Path.of(
            "src/main/java/dev/minecraft/warzoneduels/app/StatsService.java"));
        String spoilsService = Files.readString(Path.of(
            "src/main/java/dev/minecraft/warzoneduels/app/SpoilsService.java"));

        assertTrue(duelService.contains("recordChallengeSent"));
        assertTrue(spoilsService.contains("recordSpoilsClaim"));
        assertTrue(statsService.contains("recordMutualDraw"));
        assertTrue(statsService.contains("recordCustomRulesWin"));
        assertTrue(statsService.contains("recordRestrictedMobilityWin"));
        assertTrue(statsService.contains("recordLowHealthWin"));
        assertTrue(statsService.contains("DuelAdvancementPolicy"));
    }
}
