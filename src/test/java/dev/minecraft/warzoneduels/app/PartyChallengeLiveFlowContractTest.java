package dev.minecraft.warzoneduels.app;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyChallengeLiveFlowContractTest {
    @Test
    void compositionAndGuiCallbacksUseTheChallengeCoordinator() throws IOException {
        String plugin = Files.readString(Path.of("src/main/java/dev/minecraft/warzoneduels/WarzoneDuelsPlugin.java"));
        String service = Files.readString(Path.of("src/main/java/dev/minecraft/warzoneduels/app/DuelService.java"));

        assertTrue(plugin.contains("new DuelChallengeService"));
        assertTrue(service.contains("challengeService.createPartyChallenge"));
        assertTrue(service.contains("challengeService.accept"));
        assertTrue(service.contains("challengeService.decline"));
        assertTrue(service.contains("startPartyDuel(challenge)"));
        assertTrue(service.contains("schedulePartyChallengeExpiry(challenge)"));
    }
}
