package dev.minecraft.warzoneduels.app;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class DuelCooldownWiringTest {
    @Test void defaultsProtectMatchesAndRepeatOpponents() throws Exception {
        var config = new YamlConfiguration();
        config.load(Path.of("src/main/resources/config.yml").toFile());
        assertEquals(300, config.getLong("settings.duel-cooldown-seconds"));
        assertEquals(86400, config.getLong("settings.repeat-opponent-cooldown-seconds"));
    }

    @Test void guardsCoverRequestAcceptancePartyAndFinalStartBeforeTerrain() throws Exception {
        String source = Files.readString(Path.of("src/main/java/dev/minecraft/warzoneduels/app/DuelService.java")).replace("\r\n", "\n");
        for (String boundary : new String[]{"private boolean rejectRequestPlayers(", "private boolean rejectPartyRoster(",
                "private boolean rejectAcceptedRequest(", "private boolean startDuel(MatchTeam"}) {
            String body = source.substring(source.indexOf(boundary));
            body = body.substring(0, body.indexOf("\n    }") + 6);
            assertTrue(body.contains("rejectCooldown"), boundary + " must enforce cooldowns");
        }
        String result = source.substring(source.indexOf("private void concludeDuel(\n        UUID"));
        assertTrue(result.indexOf("cooldownService.recordCompletion") < result.indexOf("statsService.recordMatchResult"));
        assertTrue(source.contains("cooldownService.creditChallenge"));
    }
}
