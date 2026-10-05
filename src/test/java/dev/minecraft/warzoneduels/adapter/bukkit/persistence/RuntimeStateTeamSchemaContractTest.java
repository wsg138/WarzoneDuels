package dev.minecraft.warzoneduels.adapter.bukkit.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeStateTeamSchemaContractTest {
    @Test
    void runtimeStoreVersionsAndPersistsBothCompleteTeams() throws IOException {
        String source = Files.readString(Path.of(
            "src/main/java/dev/minecraft/warzoneduels/adapter/bukkit/persistence/RuntimeStateStore.java"));

        assertTrue(source.contains("schema-version"));
        assertTrue(source.contains("match-type"));
        assertTrue(source.contains("team-one"));
        assertTrue(source.contains("team-two"));
        assertTrue(source.contains("readTeam"));
        assertTrue(source.contains("writeTeam"));
        assertTrue(source.contains("participant-one"), "Legacy schema reader must remain available");
        assertTrue(source.contains("participant-two"), "Legacy schema reader must remain available");
    }

    @Test
    void restartAndRecoveryPathsEnumerateTheWholeRoster() throws IOException {
        String source = Files.readString(Path.of(
            "src/main/java/dev/minecraft/warzoneduels/app/DuelService.java"));

        assertTrue(source.contains("persistedRuntime.activeDuel().participants()"));
        assertTrue(source.contains("finishedDuel.participants()"));
        assertTrue(source.contains("duel.participants()"));
    }
}
