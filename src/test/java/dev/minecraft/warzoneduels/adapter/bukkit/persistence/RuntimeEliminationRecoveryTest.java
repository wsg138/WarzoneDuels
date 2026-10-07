package dev.minecraft.warzoneduels.adapter.bukkit.persistence;

import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import dev.minecraft.warzoneduels.domain.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeEliminationRecoveryTest {
    @TempDir Path directory;

    @Test
    void eliminatedRosterIdsSurviveRealYamlRoundTripAndLegacyFilesRemainValid() throws Exception {
        RuntimeStateStore store = store();
        var first = List.of(member("A"), member("B"));
        var second = List.of(member("C"), member("D"));
        ActiveDuel duel = new ActiveDuel(DuelMatchType.PARTY,
            new MatchTeam(UUID.randomUUID(), first), new MatchTeam(UUID.randomUUID(), second),
            new DuelSettings(), 123);
        UUID eliminated = first.get(1).playerId(), outsider = UUID.randomUUID();
        var save = assertDoesNotThrow(() -> RuntimeStateStore.class.getMethod(
            "saveActiveDuelSync", ActiveDuel.class, Set.class));
        save.invoke(store, duel, Set.of(eliminated, outsider));
        store.markReloadResume();
        Path runtimeFile = directory.resolve("runtime-state.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(runtimeFile.toFile());
        assertEquals(List.of(eliminated.toString()), yaml.getStringList("eliminated-participants"));
        yaml.set("eliminated-participants", List.of(eliminated.toString(), outsider.toString(), "malformed"));
        yaml.save(runtimeFile.toFile());
        RuntimeStateStore.PersistedRuntime resumed = store.loadActiveDuel();
        assertTrue(resumed.resumeAllowed());
        assertEquals(4, resumed.activeDuel().participants().size());
        var accessor = assertDoesNotThrow(() -> resumed.getClass().getMethod("eliminatedParticipantIds"));
        assertEquals(Set.of(eliminated), accessor.invoke(resumed));
        assertTrue(TeamMatchPolicy.survivingParticipants(resumed.activeDuel().teamOne(), Set.of(eliminated))
            .stream().noneMatch(p -> p.playerId().equals(eliminated)));
        yaml.set("eliminated-participants", null);
        yaml.save(runtimeFile.toFile());
        assertEquals(Set.of(), accessor.invoke(store.loadActiveDuel()));
        store.saveActiveDuelSync(duel);
        assertEquals(Set.of(), accessor.invoke(store.loadActiveDuel()));
    }

    @Test
    void recoveryRestoresEliminationsBeforeIndexingAndSkipsTheirTeleport() throws Exception {
        String source = Files.readString(Path.of("src/main/java/dev/minecraft/warzoneduels/app/DuelService.java"));
        String recovery = source.substring(source.indexOf("private void recoverActiveDuelIfNeeded()"),
            source.indexOf("private void handleServerStoppingDisable()"));
        int restore = recovery.indexOf("eliminatedParticipantIds.addAll(persistedRuntime.eliminatedParticipantIds())");
        assertTrue(restore >= 0 && restore < recovery.indexOf("rebuildParticipantIndex()"));
        assertTrue(recovery.contains("if (eliminatedParticipantIds.contains(playerId))"));
        assertFalse(source.contains("queueActiveDuelSave(activeDuel);"));
        assertFalse(source.contains("saveActiveDuelSync(activeDuel);"));
    }

    private MatchParticipant member(String name) {
        return new MatchParticipant(UUID.randomUUID(), name);
    }

    private RuntimeStateStore store() throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        WarzoneDuelsPlugin plugin = (WarzoneDuelsPlugin) unsafe.allocateInstance(WarzoneDuelsPlugin.class);
        set(plugin, "dataFolder", directory.toFile());
        set(plugin, "logger", Logger.getAnonymousLogger());
        return new RuntimeStateStore(plugin);
    }

    private void set(JavaPlugin plugin, String name, Object value) throws Exception {
        Field field = JavaPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }
}
