package dev.minecraft.warzoneduels.app;

import dev.minecraft.warzoneduels.port.DuelBlockStore;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DuelBlockServiceTest {
    private final UUID alex = UUID.randomUUID();
    private final UUID blair = UUID.randomUUID();
    private final List<String> warnings = new ArrayList<>();

    private static final class MemoryStore implements DuelBlockStore {
        Map<UUID, Set<UUID>> saved = new HashMap<>();
        boolean failLoad;
        boolean failSave;
        @Override public Map<UUID, Set<UUID>> load() throws IOException {
            if (failLoad) throw new IOException("unreadable");
            return saved;
        }
        @Override public void save(Map<UUID, Set<UUID>> blocks) throws IOException {
            if (failSave) throw new IOException("disk full");
            saved = blocks;
        }
    }

    @Test void blocksPersistBeforeTheyAreConfirmed() {
        var store = new MemoryStore();
        var service = new DuelBlockService(store, warnings::add);
        service.enable();
        assertEquals(DuelBlockService.Change.CHANGED, service.set(alex, blair, true));
        assertEquals(Set.of(blair), store.saved.get(alex));
        assertEquals(DuelBlockService.Change.UNCHANGED, service.set(alex, blair, true));
        assertFalse(service.allows(List.of(blair), List.of(alex)));
        assertTrue(service.isBlocked(alex, blair));
        assertEquals(List.of(blair), service.blockedBy(alex));

        var restarted = new DuelBlockService(store, warnings::add);
        restarted.enable();
        assertTrue(restarted.isBlocked(alex, blair), "Blocks survive a restart");
    }

    @Test void unreadableStorageRefusesNewChallengesInsteadOfForgettingBlocks() {
        var store = new MemoryStore();
        store.failLoad = true;
        var service = new DuelBlockService(store, warnings::add);
        service.enable();
        assertFalse(service.isHealthy());
        assertFalse(service.allows(List.of(alex), List.of(blair)));
        assertEquals(DuelBlockService.Change.FAILED, service.set(alex, blair, true));
        assertFalse(warnings.isEmpty());
    }

    @Test void aFailedWriteFailsOnlyThatChangeAndKeepsDuelsRunning() {
        var store = new MemoryStore();
        var service = new DuelBlockService(store, warnings::add);
        service.enable();
        store.failSave = true;
        assertEquals(DuelBlockService.Change.FAILED, service.set(alex, blair, true));
        assertFalse(service.isBlocked(alex, blair), "An unsaved block must not look saved");
        assertTrue(service.isHealthy(), "One failed save must not pause every duel");
        assertTrue(service.allows(List.of(alex), List.of(blair)));
        store.failSave = false;
        assertEquals(DuelBlockService.Change.CHANGED, service.set(alex, blair, true), "The next change saves normally");
    }

    @Test void unreadableStorageRecoversWhenEnabledAgain() {
        var store = new MemoryStore();
        store.failLoad = true;
        var service = new DuelBlockService(store, warnings::add);
        service.enable();
        assertFalse(service.isHealthy());
        store.failLoad = false;
        service.enable();
        assertTrue(service.isHealthy(), "Reload retries reading the block file");
        assertTrue(service.allows(List.of(alex), List.of(blair)));
    }

    @Test void apiRequiresTheMainThreadAndRefusesSelfBlocks() {
        var service = new DuelBlockService(new MemoryStore(), warnings::add);
        service.enable();
        var api = new DuelBlockApiService(service, () -> true);
        api.setBlocked(alex, blair, true);
        assertTrue(api.isBlocked(alex, blair));
        api.setBlocked(alex, blair, false);
        assertFalse(api.isBlocked(alex, blair));
        assertThrows(IllegalArgumentException.class, () -> api.setBlocked(alex, alex, true));
        var offThread = new DuelBlockApiService(service, () -> false);
        assertThrows(IllegalStateException.class, () -> offThread.isBlocked(alex, blair));
        assertThrows(IllegalStateException.class, () -> offThread.setBlocked(alex, blair, true));
    }

    @Test void apiReportsStorageFailures() {
        var store = new MemoryStore();
        var service = new DuelBlockService(store, warnings::add);
        service.enable();
        store.failSave = true;
        assertThrows(IllegalStateException.class, () -> new DuelBlockApiService(service, () -> true).setBlocked(alex, blair, true));
    }
}
