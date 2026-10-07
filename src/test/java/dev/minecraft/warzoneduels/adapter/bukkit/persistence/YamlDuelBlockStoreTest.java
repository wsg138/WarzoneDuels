package dev.minecraft.warzoneduels.adapter.bukkit.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class YamlDuelBlockStoreTest {
    @TempDir Path directory;

    @Test void roundTripsBlocksAndStartsEmpty() throws IOException {
        var file = directory.resolve("duel-blocks.yml");
        var store = new YamlDuelBlockStore(file);
        assertTrue(store.load().isEmpty());
        UUID owner = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        store.save(Map.of(owner, Set.of(first, second)));
        assertEquals(Map.of(owner, Set.of(first, second)), new YamlDuelBlockStore(file).load());
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count(), "No temporary files are left behind");
        }
    }

    @Test void malformedFilesFailInsteadOfLoadingEmpty() throws IOException {
        var file = directory.resolve("duel-blocks.yml");
        Files.writeString(file, "schema: 1\nblocks:\n  not-a-uuid:\n  - also-bad\n");
        assertThrows(IOException.class, () -> new YamlDuelBlockStore(file).load());
        Files.writeString(file, "schema: 2\nblocks: {}\n");
        assertThrows(IOException.class, () -> new YamlDuelBlockStore(file).load());
        Files.writeString(file, ": : not yaml");
        assertThrows(IOException.class, () -> new YamlDuelBlockStore(file).load());
    }
}
