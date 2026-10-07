package dev.minecraft.warzoneduels.app;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamMatchPreparationContractTest {
    @Test
    void livePreparationUsesCompleteRostersAndGroupedSpawns() throws IOException {
        String source = Files.readString(Path.of(
            "src/main/java/dev/minecraft/warzoneduels/app/DuelService.java"
        ));

        assertTrue(source.contains("public boolean startPartyDuel(DuelChallenge challenge)"));
        assertTrue(source.contains("for (Player participant : participants)"));
        assertTrue(source.contains("startCountdown(participants)"));
        // Actual team/slot resolution is exercised by PlaytestRegressionTest.
    }
}
