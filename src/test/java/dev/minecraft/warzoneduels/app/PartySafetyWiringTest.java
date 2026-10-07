package dev.minecraft.warzoneduels.app;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PartySafetyWiringTest {
    @Test void secondarySpawnsAreOptionalInShippedDefaults() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.load(Path.of("src/main/resources/config.yml").toFile());
        for (String key : new String[]{"team1-spawn2", "team1-spawn3", "team2-spawn2", "team2-spawn3"}) {
            assertFalse(yaml.contains("arena." + key));
        }
    }

    @Test void readinessRequiresSpawnValidationAndOfflineCancellationExplainsWhy() throws Exception {
        String arena = Files.readString(Path.of("src/main/java/dev/minecraft/warzoneduels/domain/ArenaDefinition.java"));
        assertTrue(arena.contains("world() != null && hasValidTeamSpawns()"));
        String service = Files.readString(Path.of("src/main/java/dev/minecraft/warzoneduels/app/DuelService.java"));
        String party = service.substring(service.indexOf("private void sendPartyRequest("), service.indexOf("private List<Player> onlinePartyParticipants("));
        assertTrue(party.contains("if (participants == null) {"));
        assertTrue(party.contains("sendMessage(requester, MSG_TARGET_OFFLINE)"));
    }
}
