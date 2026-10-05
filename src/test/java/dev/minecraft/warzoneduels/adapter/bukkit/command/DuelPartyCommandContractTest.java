package dev.minecraft.warzoneduels.adapter.bukkit.command;

import dev.minecraft.warzoneduels.permission.PermissionPolicy;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuelPartyCommandContractTest {
    @Test
    void commandRouterExposesTheCompletePartyLifecycle() throws IOException {
        String source = Files.readString(Path.of(
            "src/main/java/dev/minecraft/warzoneduels/adapter/bukkit/command/DuelCommand.java"));

        assertTrue(source.contains("case \"party\""), "DuelCommand must route /duel party");
        for (String operation : List.of("create", "invite", "accept", "info", "leave", "kick", "transfer", "disband")) {
            assertTrue(source.contains("\"" + operation + "\""), "Missing party operation " + operation);
        }
        assertTrue(source.contains("DuelPartyService"), "DuelCommand must delegate lifecycle mutations to DuelPartyService");
    }

    @Test
    void compositionConfigurationAndPermissionMetadataAreWired() throws IOException {
        String pluginSource = Files.readString(Path.of(
            "src/main/java/dev/minecraft/warzoneduels/WarzoneDuelsPlugin.java"));
        String pluginYaml = Files.readString(Path.of("src/main/resources/plugin.yml"));
        String configYaml = Files.readString(Path.of("src/main/resources/config.yml"));

        assertTrue(pluginSource.contains("new DuelPartyService"));
        assertTrue(pluginSource.contains("new DuelCommand(activeDuelService, activeSpoilsService, activePartyService)"));
        assertEquals("warzoneduels.command.party", PermissionPolicy.permissionForSubcommand("party"));
        assertTrue(pluginYaml.contains("warzoneduels.command.party:"));
        assertTrue(configYaml.contains("party-invite-expire-seconds:"));
    }
}
