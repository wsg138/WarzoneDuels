package dev.minecraft.warzoneduels.app;

import dev.minecraft.warzoneduels.domain.DuelCooldownPolicy;
import dev.minecraft.warzoneduels.port.DuelCooldownStore;
import dev.minecraft.warzoneduels.adapter.bukkit.persistence.YamlDuelCooldownStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class DuelCooldownServiceTest {
    private static boolean recover(DuelCooldownService service) throws Exception {
        var method = assertDoesNotThrow(() -> service.getClass().getMethod("recover"),
                                       "Admins need a safe storage recovery boundary");
        return (boolean) method.invoke(service);
    }

    @Test void repairedWritePreservesUncommittedInMemoryHistory() throws Exception {
        class Store implements DuelCooldownStore {
            boolean fail;
            int reads;
            DuelCooldownPolicy.Snapshot saved = DuelCooldownPolicy.Snapshot.empty();
            public DuelCooldownPolicy.Snapshot load() { reads++; return saved; }
            public void save(DuelCooldownPolicy.Snapshot value) throws IOException {
                if (fail) throw new IOException("locked");
                saved = value;
            }
        }
        var store = new Store();
        var service = service(store);
        store.fail = true;
        assertFalse(service.recordCompletion(first, second));
        assertFalse(recover(service));
        store.fail = false;
        assertTrue(recover(service));
        assertEquals(1, store.reads, "Recovery must not discard the failed write by reloading disk");
        assertNotNull(service.block(first, second));
        assertFalse(store.saved.players().isEmpty());
    }

    @Test void repairedInitialReadRetriesLoadingBeforeUnblocking() throws Exception {
        class Store implements DuelCooldownStore {
            boolean fail = true;
            public DuelCooldownPolicy.Snapshot load() throws IOException {
                if (fail) throw new IOException("unreadable");
                return new DuelCooldownPolicy.Snapshot(java.util.Map.of(first.getFirst(), 1000L),
                                                       java.util.Map.of(), java.util.Map.of());
            }
            public void save(DuelCooldownPolicy.Snapshot value) { }
        }
        var store = new Store();
        var service = service(store);
        assertFalse(service.isHealthy());
        store.fail = false;
        assertTrue(recover(service));
        assertNotNull(service.block(first, second));
    }

    @Test void persistencePrunesOnlyExpiredEnabledHistory() {
        class Store implements DuelCooldownStore {
            DuelCooldownPolicy.Snapshot saved = new DuelCooldownPolicy.Snapshot(
                java.util.Map.of(first.getFirst(), 1000L, second.getFirst(), 5000L),
                java.util.Map.of(DuelCooldownPolicy.pairKey(first.getFirst(), second.getFirst()), 1000L),
                java.util.Map.of(DuelCooldownPolicy.pairKey(first.getFirst(), second.getFirst()), 1000L));
            public DuelCooldownPolicy.Snapshot load() { return saved; }
            public void save(DuelCooldownPolicy.Snapshot value) { saved = value; }
        }
        var store = new Store();
        var service = service(store);
        service.configure(0, 0);
        clock.set(3000);
        assertTrue(service.ensureWritable());
        assertEquals(2, store.saved.players().size());
        assertEquals(1, store.saved.opponents().size());
        service.configure(1, 1);
        assertTrue(service.ensureWritable());
        assertEquals(java.util.Map.of(second.getFirst(), 5000L), store.saved.players());
        assertTrue(store.saved.opponents().isEmpty());
        assertTrue(store.saved.challenges().isEmpty());
    }
    @TempDir Path directory;
    private final AtomicLong clock = new AtomicLong(1000);
    private final List<String> warnings = new ArrayList<>();
    private final List<UUID> first = List.of(UUID.randomUUID()), second = List.of(UUID.randomUUID());
    private DuelCooldownService service(DuelCooldownStore store) {
        var service = new DuelCooldownService(store, clock::get, warnings::add);
        service.enable();
        return service;
    }

    @Test void diskRestartAndReloadRetainCooldownAndChallengeEvidence() {
        var store = new YamlDuelCooldownStore(directory.resolve("duel-cooldowns.yml"));
        var service = service(store);
        assertTrue(service.isHealthy());
        assertTrue(service.creditChallenge(first, second));
        assertTrue(service.recordCompletion(first, second));
        var restarted = service(store);
        assertNotNull(restarted.block(second, first));
        assertFalse(restarted.creditChallenge(second, first));
        restarted.configure(0, 0);
        assertNull(restarted.block(second, first));
        restarted.configure(300, 86400);
        assertNotNull(restarted.block(second, first));
        clock.set(86_401_000);
        assertNull(restarted.block(second, first));
        assertTrue(restarted.creditChallenge(second, first));
        assertTrue(warnings.isEmpty());
    }
    @Test void malformedYamlIsRetainedAndFailsClosed() throws Exception {
        Path file = directory.resolve("duel-cooldowns.yml");
        String invalid = "schema: 1\nplayers: [broken";
        Files.writeString(file, invalid);
        var service = service(new YamlDuelCooldownStore(file));
        assertFalse(service.isHealthy());
        assertFalse(service.creditChallenge(first, second));
        assertFalse(service.recordCompletion(first, second));
        assertEquals(invalid, Files.readString(file));
        assertEquals(1, warnings.size());
    }
    @Test void invalidRecordsAndUnsupportedSchemaCannotSilentlyResetHistory() throws Exception {
        Path file = directory.resolve("duel-cooldowns.yml");
        var store = new YamlDuelCooldownStore(file);
        for (String invalid : List.of("", "schema: 2\nplayers: {}\nopponents: {}\nchallenges: {}",
            "schema: 1\nplayers: {invalid: 100}\nopponents: {}\nchallenges: {}",
            "schema: 1\nplayers: {}\nopponents: {invalid: 100}\nchallenges: {}",
            "schema: 1\nplayers: {}\nopponents: {}\nchallenges: {invalid: -1}",
            "schema: 1\nplayers: {}\nopponents: {}")) {
            Files.writeString(file, invalid);
            assertThrows(IOException.class, store::load);
            assertEquals(invalid, Files.readString(file));
        }
    }
    @Test void failedWriteWithholdsEvidenceAndBlocksFurtherMatches() {
        class FailingStore implements DuelCooldownStore {
            boolean fail;
            @Override public DuelCooldownPolicy.Snapshot load() { return DuelCooldownPolicy.Snapshot.empty(); }
            @Override public void save(DuelCooldownPolicy.Snapshot snapshot) throws IOException {
                if (fail) throw new IOException("test write failure");
            }
        }
        var store = new FailingStore();
        var service = service(store);
        store.fail = true;
        assertFalse(service.recordCompletion(first, second));
        assertFalse(service.isHealthy());
        assertFalse(service.creditChallenge(first, second));
        assertFalse(service.recordCompletion(second, first));
        assertEquals(1, warnings.size());
    }
    @Test void initialWriteFailureAndUnreadablePathBlockAdmission() throws Exception {
        Path parent = directory.resolve("blocked");
        Files.writeString(parent, "occupied");
        var service = service(new YamlDuelCooldownStore(parent.resolve("duel-cooldowns.yml")));
        assertFalse(service.isHealthy());
        assertFalse(service.recordCompletion(first, second));
        assertFalse(service.creditChallenge(first, second));
    }
    @Test void challengePersistenceFailureDoesNotEmitCredit() {
        var store = new DuelCooldownStore() {
            int writes;
            @Override public DuelCooldownPolicy.Snapshot load() { return DuelCooldownPolicy.Snapshot.empty(); }
            @Override public void save(DuelCooldownPolicy.Snapshot snapshot) throws IOException {
                if (++writes > 1) throw new IOException("credit write failed");
            }
        };
        var service = service(store);
        assertFalse(service.creditChallenge(first, second));
        assertFalse(service.isHealthy());
    }
    @Test void writeFailureBeforeArenaPreparationRejectsAdmission() {
        var store = new DuelCooldownStore() {
            int writes;
            @Override public DuelCooldownPolicy.Snapshot load() { return DuelCooldownPolicy.Snapshot.empty(); }
            @Override public void save(DuelCooldownPolicy.Snapshot snapshot) throws IOException {
                if (++writes > 1) throw new IOException("admission write failed");
            }
        };
        var service = service(store);
        assertFalse(service.ensureWritable());
        assertFalse(service.isHealthy());
    }
}
