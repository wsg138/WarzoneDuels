package dev.minecraft.warzoneduels.app;

import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import dev.minecraft.warzoneduels.adapter.bukkit.command.DuelCommand;
import dev.minecraft.warzoneduels.domain.*;
import dev.minecraft.warzoneduels.permission.PermissionPolicy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DuelModeControlsTest {
    @Test
    void opposingNonLeaderAddressesAreCheckedWithoutBlockingTeammatesOrOptOut() throws Exception {
        DuelService service = service(new YamlConfiguration(), directory);
        var check = assertDoesNotThrow(() -> DuelService.class.getDeclaredMethod(
            "hasOpposingSameIp", List.class, List.class));
        check.setAccessible(true);
        var first = List.of(addressPlayer("192.0.2.1"), addressPlayer("192.0.2.2"));
        var opposing = List.of(addressPlayer("192.0.2.3"), addressPlayer("192.0.2.2"));
        set(service, DuelService.class, "allowSameIp", false);
        assertEquals(true, check.invoke(service, first, opposing));
        assertEquals(false, check.invoke(service,
            List.of(addressPlayer("192.0.2.1"), addressPlayer("192.0.2.1")),
            List.of(addressPlayer("192.0.2.3"), addressPlayer("192.0.2.4"))));
        assertEquals(false, check.invoke(service, List.of(addressPlayer(null)), opposing));
        set(service, DuelService.class, "allowSameIp", true);
        assertEquals(false, check.invoke(service, first, opposing));
    }

    @Test
    void partyAdmissionWiresTheSameIpCheckAfterResolvingCompleteRosters() throws Exception {
        String source = Files.readString(Path.of("src/main/java/dev/minecraft/warzoneduels/app/DuelService.java"));
        String guard = source.substring(source.indexOf("private boolean rejectPartyRoster("),
            source.indexOf("private boolean rejectRequestPlayers("));
        assertTrue(guard.contains("hasOpposingSameIp("));
        assertTrue(guard.contains("messages.same-ip-blocked"));
    }

    private Player addressPlayer(String address) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
            (proxy, method, args) -> method.getName().equals("getAddress") && address != null
                ? new java.net.InetSocketAddress(address, 25565) : null);
    }

    @TempDir Path directory;
    private final List<String> messages = new ArrayList<>();
    private final Command duelCommand = new Command("duel") {
        @Override public boolean execute(CommandSender sender, String label, String[] args) { return false; }
    };

    @Test
    void modesDefaultEnabledAndPersistIndependentlyWithoutChangingActiveMatch() throws Exception {
        DuelService service = service(new YamlConfiguration(), directory);
        ActiveDuel active = new ActiveDuel(DuelMatchType.PARTY, team(2), team(2), new DuelSettings(), 1);
        set(service, DuelService.class, "activeDuel", active);
        assertTrue(service.isDuelModeEnabled(2));
        assertTrue(service.isDuelModeEnabled(3));
        service.setDuelModeEnabled(2, false);
        assertFalse(service.isDuelModeEnabled(2));
        assertTrue(service.isDuelModeEnabled(3));
        assertTrue(service.isDuelModeEnabled(1));
        var field = DuelService.class.getDeclaredField("activeDuel");
        field.setAccessible(true);
        assertSame(active, field.get(service));
        DuelService restarted = service(YamlConfiguration.loadConfiguration(directory.resolve("config.yml").toFile()), directory);
        assertFalse(restarted.isDuelModeEnabled(2));
        restarted.setDuelModeEnabled(3, false);
        restarted.setDuelModeEnabled(2, true);
        assertTrue(restarted.isDuelModeEnabled(2));
        assertFalse(restarted.isDuelModeEnabled(3));
        assertTrue(restarted.isDuelModeEnabled(1));
    }

    @Test
    void consoleCommandsEnforcePermissionValidateArgumentsAndComplete() throws Exception {
        DuelService service = service(new YamlConfiguration(), directory);
        DuelCommand command = new DuelCommand(service, null, null);
        CommandSender denied = sender(false);
        command.onCommand(denied, duelCommand, "duel", new String[]{"mode", "2v2", "disable"});
        assertTrue(service.isDuelModeEnabled(2));
        assertTrue(messages.getLast().contains("permission"));
        assertTrue(command.onTabComplete(denied, duelCommand, "duel", new String[]{"mode", ""}).isEmpty());
        CommandSender admin = sender(true);
        command.onCommand(admin, duelCommand, "duel", new String[]{"mode", "2v2", "disable"});
        assertFalse(service.isDuelModeEnabled(2));
        command.onCommand(admin, duelCommand, "duel", new String[]{"mode", "3v3", "enable"});
        assertTrue(service.isDuelModeEnabled(3));
        command.onCommand(admin, duelCommand, "duel", new String[]{"mode", "2v2", "status"});
        assertTrue(messages.getLast().contains("disabled"));
        command.onCommand(admin, duelCommand, "duel", new String[]{"mode", "1v1", "disable"});
        assertTrue(messages.getLast().contains("Usage:"));
        command.onCommand(admin, duelCommand, "duel", new String[]{"mode", "3v3", "nope"});
        assertTrue(messages.getLast().contains("Usage:"));
        assertTrue(service.isDuelModeEnabled(3));
        assertEquals(List.of("2v2", "3v3"), command.onTabComplete(admin, duelCommand, "duel", new String[]{"mode", ""}));
        assertEquals(List.of("disable"), command.onTabComplete(admin, duelCommand, "duel", new String[]{"mode", "2v2", "dis"}));
        assertEquals(List.of("mode"), command.onTabComplete(admin, duelCommand, "duel", new String[]{"mo"}));
        assertEquals(PermissionPolicy.ADMIN_MODES, PermissionPolicy.permissionForSubcommand("mode"));
    }

    @Test
    void disabledModesStopBeforeAnyArenaPreparation() throws Exception {
        DuelService service = service(new YamlConfiguration(), directory);
        var start = DuelService.class.getDeclaredMethod("startDuel", MatchTeam.class, MatchTeam.class, List.class, DuelSettings.class);
        start.setAccessible(true);
        for (int size : List.of(2, 3)) {
            service.setDuelModeEnabled(size, false);
            assertEquals(false, start.invoke(service, team(size), team(size), List.of(), new DuelSettings()));
        }
    }

    @Test
    void disabledChallengeCannotBeSentOrAcceptedAndAcceptanceReleasesBothRosters() throws Exception {
        for (int size : List.of(2, 3)) {
            DuelService service = service(new YamlConfiguration(), directory);
            DuelPartyService parties = new DuelPartyService(60_000, UUID::randomUUID);
            DuelParty first = party(parties, size), second = party(parties, size);
            DuelChallengeService challenges = new DuelChallengeService(parties, 60_000, UUID::randomUUID);
            set(service, DuelService.class, "challengeService", challenges);
            Player leader = player(first.leaderId());
            DuelChallenge challenge = challenges.createPartyChallenge(first.leaderId(), second.leaderId(),
                new DuelSettings(), System.currentTimeMillis());
            service.setDuelModeEnabled(size, false);
            var confirm = DuelService.class.getDeclaredMethod("confirmPartyChallenge", Player.class, DuelChallenge.class);
            confirm.setAccessible(true);
            confirm.invoke(service, leader, challenge);
            assertFalse(first.isRosterLocked());
            assertFalse(second.isRosterLocked());
            assertEquals(0, challenges.activeChallengeCount());
            assertTrue(messages.getLast().contains("disabled"));
            var send = DuelService.class.getDeclaredMethod("sendPartyRequest", Player.class, Player.class,
                DuelSettings.class, Optional.class, Optional.class);
            send.setAccessible(true);
            send.invoke(service, leader, player(second.leaderId()), new DuelSettings(), Optional.of(first), Optional.of(second));
            assertEquals(0, challenges.activeChallengeCount());
            assertFalse(first.isRosterLocked());
            assertFalse(second.isRosterLocked());
            assertTrue(messages.getLast().contains("disabled"));
        }
    }

    @Test
    void failedSaveRetainsPriorModeAndRejectsUnsupportedSizes() throws Exception {
        Path blocked = directory.resolve("not-a-directory");
        Files.writeString(blocked, "occupied");
        DuelService service = service(new YamlConfiguration(), blocked);
        assertThrows(java.io.IOException.class, () -> service.setDuelModeEnabled(2, false));
        assertTrue(service.isDuelModeEnabled(2));
        assertThrows(IllegalArgumentException.class, () -> service.setDuelModeEnabled(1, false));
        assertTrue(service.isDuelModeEnabled(1));
    }

    private DuelParty party(DuelPartyService parties, int size) {
        DuelParty party = parties.createParty(UUID.randomUUID(), "Leader");
        for (int slot = 1; slot < size; slot++) {
            UUID member = UUID.randomUUID();
            parties.invite(party.leaderId(), member, "Member" + slot, 1);
            parties.acceptInvite(member, party.id(), 2);
        }
        return party;
    }

    private MatchTeam team(int size) {
        return new MatchTeam(UUID.randomUUID(), java.util.stream.IntStream.range(0, size)
            .mapToObj(slot -> new MatchParticipant(UUID.randomUUID(), "Player" + slot)).toList());
    }

    private CommandSender sender(boolean permitted) {
        return (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(), new Class<?>[]{CommandSender.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "hasPermission" -> permitted;
                case "sendMessage" -> { messages.add(String.valueOf(args[0])); yield null; }
                default -> null;
            });
    }

    private Player player(UUID id) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "sendMessage" -> { messages.add(String.valueOf(args[0])); yield null; }
                default -> null;
            });
    }

    private DuelService service(YamlConfiguration config, Path dataFolder) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Unsafe unsafe = (Unsafe) field.get(null);
        WarzoneDuelsPlugin plugin = (WarzoneDuelsPlugin) unsafe.allocateInstance(WarzoneDuelsPlugin.class);
        set(plugin, JavaPlugin.class, "newConfig", config);
        set(plugin, JavaPlugin.class, "dataFolder", dataFolder.toFile());
        DuelService service = (DuelService) unsafe.allocateInstance(DuelService.class);
        set(service, DuelService.class, "plugin", plugin);
        set(service, DuelService.class, "prefix", "[Duel] ");
        return service;
    }

    private void set(Object target, Class<?> owner, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
