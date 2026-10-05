package dev.minecraft.warzoneduels.adapter.bukkit.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancementEvidencePersistenceContractTest {
    @Test
    void statsStorePersistsEveryAdvancementEvidenceCounter() throws IOException {
        String source = Files.readString(Path.of(
            "src/main/java/dev/minecraft/warzoneduels/adapter/bukkit/persistence/PlayerStatsStore.java"));

        assertTrue(source.contains("advancements.challenges-sent"));
        assertTrue(source.contains("advancements.spoils-claims"));
        assertTrue(source.contains("advancements.mutual-draws"));
        assertTrue(source.contains("advancements.custom-rules-wins"));
        assertTrue(source.contains("advancements.restricted-mobility-wins"));
        assertTrue(source.contains("advancements.low-health-wins"));
    }
}
