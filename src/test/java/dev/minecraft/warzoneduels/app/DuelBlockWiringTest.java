package dev.minecraft.warzoneduels.app;

import dev.minecraft.warzoneduels.permission.PermissionPolicy;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class DuelBlockWiringTest {
    private static String body(String source, String boundary) {
        String body = source.substring(source.indexOf(boundary));
        return body.substring(0, body.indexOf("\n    }") + 6);
    }

    @Test void blocksGuardBuilderRequestPartyChallengeAndInvitation() throws Exception {
        String service = Files.readString(Path.of("src/main/java/dev/minecraft/warzoneduels/app/DuelService.java")).replace("\r\n", "\n");
        for (String boundary : new String[]{"private boolean rejectBuilderStart(", "private boolean rejectRequestPlayers(",
                "private boolean rejectPartyRoster("}) {
            assertTrue(body(service, boundary).contains("rejectDuelBlock"), boundary + " must enforce duel blocks");
        }
        String command = Files.readString(Path.of("src/main/java/dev/minecraft/warzoneduels/adapter/bukkit/command/DuelCommand.java")).replace("\r\n", "\n");
        String invite = body(command, "private void inviteToParty(");
        assertTrue(invite.indexOf("allowsPartyInvite") < invite.indexOf("partyService.invite("), "Invitations are checked before they are created");
        String plugin = Files.readString(Path.of("src/main/java/dev/minecraft/warzoneduels/WarzoneDuelsPlugin.java"));
        assertTrue(plugin.contains("DuelBlockApi.class") && plugin.contains("duel-blocks.yml"));
    }

    @Test void blockCommandsHaveTheirOwnPermission() throws Exception {
        assertEquals(PermissionPolicy.BLOCK, PermissionPolicy.permissionForSubcommand("block"));
        assertEquals(PermissionPolicy.BLOCK, PermissionPolicy.permissionForSubcommand("unblock"));
        assertEquals(PermissionPolicy.BLOCK, PermissionPolicy.permissionForSubcommand("blocked"));
        var plugin = new YamlConfiguration();
        plugin.load(Path.of("src/main/resources/plugin.yml").toFile());
        assertTrue(plugin.getBoolean("permissions.warzoneduels.command.children." + PermissionPolicy.BLOCK));
        assertNotNull(plugin.get("permissions." + PermissionPolicy.BLOCK));
    }

    @Test void acceptanceReloadAndSuggestionsRespectBlocks() throws Exception {
        String service = Files.readString(Path.of("src/main/java/dev/minecraft/warzoneduels/app/DuelService.java")).replace("\r\n", "\n");
        assertTrue(body(service, "private boolean rejectAcceptedRequest(").contains("rejectDuelBlock"), "Accepting a request re-checks blocks");
        assertTrue(body(service, "public void reloadConfig(").contains("blockService"), "Reload retries unreadable block storage");
        String command = Files.readString(Path.of("src/main/java/dev/minecraft/warzoneduels/adapter/bukkit/command/DuelCommand.java")).replace("\r\n", "\n");
        String accept = body(command, "private void acceptPartyInvite(");
        assertTrue(accept.contains("allowsPartyJoin") && accept.indexOf("allowsPartyJoin") < accept.indexOf("partyService.acceptInvite("),
            "Accepting a party invitation re-checks blocks before joining");
        assertTrue(body(command, "private void addOnlinePlayerCompletions(").contains("canSee"), "Suggestions hide players the sender cannot see");
    }
}
