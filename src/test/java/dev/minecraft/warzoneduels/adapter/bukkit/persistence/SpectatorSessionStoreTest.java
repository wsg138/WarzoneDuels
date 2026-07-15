package dev.minecraft.warzoneduels.adapter.bukkit.persistence;

import dev.minecraft.warzoneduels.domain.SpectatorSession;
import dev.minecraft.warzoneduels.domain.SpectatorSessionPhase;
import dev.minecraft.warzoneduels.domain.StoredLocation;
import org.bukkit.GameMode;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpectatorSessionStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void serializesSessionAndAtomicallyReplacesPhase() throws java.io.IOException {
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        SpectatorSessionStore store = new SpectatorSessionStore(temporaryDirectory.resolve("sessions"), logger);
        SpectatorSession prepared = session(SpectatorSessionPhase.PREPARED);

        assertTrue(store.save(prepared));
        Path destination = store.directory().resolve(prepared.playerId() + ".yml");
        assertTrue(Files.isRegularFile(destination));
        assertFalse(Files.exists(destination.resolveSibling(destination.getFileName() + ".tmp")));

        SpectatorSession loaded = store.load(prepared.playerId()).orElseThrow();
        assertEquals(SpectatorSessionPhase.PREPARED, loaded.phase());
        assertEquals(GameMode.SURVIVAL, loaded.originalGameMode());
        assertEquals(0, loaded.contents().length);
        assertEquals(0.25D, loaded.velocity().getX());

        SpectatorSession active = prepared.withPhase(SpectatorSessionPhase.ACTIVE);
        assertTrue(store.save(active));
        assertEquals(SpectatorSessionPhase.ACTIVE, store.load(active.playerId()).orElseThrow().phase());
        assertEquals(1, store.loadAll().size());

        assertTrue(Files.deleteIfExists(destination));
        assertEquals(SpectatorSessionPhase.PREPARED, store.load(active.playerId()).orElseThrow().phase());
    }

    @Test
    void phaseTransitionsRetainOriginalSnapshot() {
        SpectatorSession prepared = session(SpectatorSessionPhase.PREPARED);
        SpectatorSession restoring = prepared.withPhase(SpectatorSessionPhase.ACTIVE).withPhase(SpectatorSessionPhase.RESTORING);
        assertEquals(SpectatorSessionPhase.RESTORING, restoring.phase());
        assertEquals(0, restoring.contents().length);
        assertEquals(prepared.originalLocation(), restoring.originalLocation());
    }

    private SpectatorSession session(SpectatorSessionPhase phase) {
        UUID playerId = UUID.randomUUID();
        return new SpectatorSession(
            playerId, "P2wn", 1234L, phase, GameMode.SURVIVAL,
            new StoredLocation(UUID.randomUUID(), "world", 1D, 65D, 2D, 90F, 0F),
            false, false, 0.1F, 0.2F, true, true,
            new ItemStack[0], new ItemStack[4], null, null,
            20D, 20, 5F, 10, 2, 0.5F, 0, List.of(), 0F, new Vector(0.25D, 0D, -0.25D)
        );
    }
}
