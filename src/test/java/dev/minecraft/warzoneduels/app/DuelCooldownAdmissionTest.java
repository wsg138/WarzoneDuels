package dev.minecraft.warzoneduels.app;

import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import dev.minecraft.warzoneduels.domain.*;
import dev.minecraft.warzoneduels.adapter.bukkit.persistence.YamlDuelCooldownStore;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sun.misc.Unsafe;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises actual matchmaking methods; blocked duels must never reach the absent arena adapter. */
class DuelCooldownAdmissionTest {
    @TempDir Path directory;
    private final List<String> messages = new ArrayList<>();
    private final AtomicLong now = new AtomicLong(1000);

    @Test void requestAcceptanceAndQueuedStartRejectEitherParticipantWithRemainingWait() throws Exception {
        var cooldown = cooldown();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), newOpponent = UUID.randomUUID();
        cooldown.recordCompletion(List.of(a), List.of(b));
        var service = service(cooldown);
        Player requester = player(newOpponent, "NewPlayer"), target = player(b, "RecentLoser");
        for (String method : List.of("rejectRequestPlayers", "rejectAcceptedRequest")) {
            var types = method.equals("rejectRequestPlayers") ? new Class<?>[]{Player.class, Player.class}
                : new Class<?>[]{Player.class, Player.class, DuelSettings.class};
            var guard = DuelService.class.getDeclaredMethod(method, types);
            guard.setAccessible(true);
            Object result = types.length == 2 ? guard.invoke(service, requester, target)
                : guard.invoke(service, requester, target, new DuelSettings());
            assertEquals(true, result);
            assertTrue(messages.getLast().contains("RecentLoser"));
            assertTrue(messages.getLast().contains("300s"));
        }
        assertFalse(start(service, team(newOpponent), team(b), List.of(requester, target)));
        assertTrue(messages.getLast().contains("300s"));
    }
    @Test void changedLeaderAndPartyCompositionStillBlockNonLeaderPairs() throws Exception {
        var cooldown = cooldown();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        cooldown.recordCompletion(List.of(a), List.of(b));
        now.set(301_000);
        var service = service(cooldown);
        MatchTeam first = team(UUID.randomUUID(), b), second = team(UUID.randomUUID(), a);
        var challenge = new DuelChallenge(UUID.randomUUID(), first, second, new DuelSettings(), 1, Long.MAX_VALUE);
        var guard = DuelService.class.getDeclaredMethod("rejectPartyRoster", DuelChallenge.class, Player.class);
        guard.setAccessible(true);
        assertEquals(true, guard.invoke(service, challenge, player(first.participants().getFirst().playerId(), "Leader")));
        assertTrue(messages.getLast().contains("facing each other"));
        assertFalse(start(service, first, second, List.of(player(a, "A"), player(b, "B"))));
    }
    @Test void persistenceFailureBlocksStartEvenWhenBothLimitsDisabled() throws Exception {
        Path blocked = directory.resolve("unusable");
        java.nio.file.Files.writeString(blocked, "occupied");
        var cooldown = new DuelCooldownService(new YamlDuelCooldownStore(blocked.resolve("history.yml")), now::get, messages::add);
        cooldown.enable();
        cooldown.configure(0, 0);
        var service = service(cooldown);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertFalse(start(service, team(a), team(b), List.of(player(a, "A"))));
        assertTrue(messages.getLast().contains("administrator"));
    }
    @Test void cooldownAtPartyAcceptanceCancelsChallengeAndUnlocksBothRosters() throws Exception {
        var cooldown = cooldown();
        var parties = new DuelPartyService(60_000, UUID::randomUUID);
        var first = parties.createParty(UUID.randomUUID(), "First");
        var second = parties.createParty(UUID.randomUUID(), "Second");
        var challenges = new DuelChallengeService(parties, 60_000, UUID::randomUUID);
        var challenge = challenges.createPartyChallenge(first.leaderId(), second.leaderId(), new DuelSettings(), System.currentTimeMillis());
        cooldown.recordCompletion(List.of(first.leaderId()), List.of(second.leaderId()));
        var service = service(cooldown);
        set(service, DuelService.class, "challengeService", challenges);
        var confirm = DuelService.class.getDeclaredMethod("confirmPartyChallenge", Player.class, DuelChallenge.class);
        confirm.setAccessible(true);
        confirm.invoke(service, player(first.leaderId(), "First"), challenge);
        assertFalse(first.isRosterLocked());
        assertFalse(second.isRosterLocked());
        assertEquals(0, challenges.activeChallengeCount());
    }
    @Test void absentProtectionCannotBypassAdmission() throws Exception {
        var service = service(null);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertFalse(start(service, team(a), team(b), List.of(player(a, "A"))));
        assertTrue(messages.getLast().contains("administrator"));
    }
    private DuelCooldownService cooldown() {
        var cooldown = new DuelCooldownService(new YamlDuelCooldownStore(directory.resolve("history.yml")), now::get, messages::add);
        cooldown.enable();
        return cooldown;
    }
    private boolean start(DuelService service, MatchTeam first, MatchTeam second, List<Player> players) throws Exception {
        var method = DuelService.class.getDeclaredMethod("startDuel", MatchTeam.class, MatchTeam.class, List.class, DuelSettings.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, first, second, players, new DuelSettings());
    }
    private MatchTeam team(UUID... ids) {
        return new MatchTeam(UUID.randomUUID(), java.util.Arrays.stream(ids).map(id -> new MatchParticipant(id, id.toString())).toList());
    }
    private Player player(UUID id, String name) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getName" -> name;
                case "sendMessage" -> { messages.add(String.valueOf(args[0])); yield null; }
                default -> null;
            });
    }
    private DuelService service(DuelCooldownService cooldown) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Unsafe unsafe = (Unsafe) field.get(null);
        var plugin = (WarzoneDuelsPlugin) unsafe.allocateInstance(WarzoneDuelsPlugin.class);
        set(plugin, JavaPlugin.class, "newConfig", new YamlConfiguration());
        var service = (DuelService) unsafe.allocateInstance(DuelService.class);
        set(service, DuelService.class, "plugin", plugin);
        set(service, DuelService.class, "prefix", "[Duel] ");
        set(service, DuelService.class, "cooldownService", cooldown);
        return service;
    }
    private void set(Object target, Class<?> owner, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
