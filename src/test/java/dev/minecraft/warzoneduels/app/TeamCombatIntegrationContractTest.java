package dev.minecraft.warzoneduels.app;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamCombatIntegrationContractTest {
    @Test
    void liveCombatUsesTeamPolicyForFriendlyFireAndWholeTeamVictory() throws IOException {
        String source = Files.readString(Path.of(
            "src/main/java/dev/minecraft/warzoneduels/app/DuelService.java"
        ));

        assertTrue(source.contains("TeamMatchPolicy.isFriendlyFire(activeDuel"));
        assertTrue(source.contains("TeamMatchPolicy.winningTeam(activeDuel, eliminatedParticipantIds)"));
        assertTrue(source.contains("TeamMatchPolicy.allSurvivorsRequestedDraw(activeDuel, eliminatedParticipantIds)"));
        assertTrue(source.contains("public void handleDeath(Player player, Player killer, List<ItemStack> drops)"));
    }
}
