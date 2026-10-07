package dev.minecraft.warzoneduels.domain;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArenaTeamSpawnTest {
    private final World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(),
        new Class<?>[]{World.class}, (proxy, method, args) -> {
            if (method.getName().equals("getName")) return "world";
            throw new AssertionError("Unexpected world lookup: " + method.getName());
        });
    private boolean valid(ArenaDefinition arena) throws Exception {
        var method = assertDoesNotThrow(() -> ArenaDefinition.class.getMethod("hasValidTeamSpawns"));
        return (boolean) method.invoke(arena);
    }

    @Test void fallbackNearBoundaryRejectsOutOfBoundsRosterSlot() throws Exception {
        ArenaDefinition arena = new ArenaDefinition("world", location(0), location(20),
            location(1), location(10), location(30), location(40));
        assertFalse(valid(arena));
    }

    @Test void explicitSecondarySpawnOutsideBoundsIsRejected() throws Exception {
        ArenaDefinition arena = new ArenaDefinition("world", location(0), location(20),
            List.of(location(2), location(4), location(25)),
            List.of(location(10), location(12), location(14)), location(30), location(40));
        assertFalse(valid(arena));
    }

    @Test void reversedCornersAcceptAllSafeFallbackPositions() throws Exception {
        ArenaDefinition arena = new ArenaDefinition("world", location(20), location(0),
            location(4), location(14), location(30), location(40));
        assertTrue(valid(arena));
    }
    @Test
    void explicitSpawnGroupsResolveByTeamAndStableRosterSlot() {
        List<Location> first = List.of(location(1), location(2), location(3));
        List<Location> second = List.of(location(11), location(12), location(13));
        ArenaDefinition arena = new ArenaDefinition(
            "world", location(-10), location(20), first, second, location(30), location(40));

        assertEquals(1D, arena.spawn1().getX());
        assertEquals(11D, arena.spawn2().getX());
        assertEquals(3D, arena.teamSpawn(0, 2).getX());
        assertEquals(12D, arena.teamSpawn(1, 1).getX());
        assertThrows(IllegalArgumentException.class, () -> arena.teamSpawn(2, 0));
        assertThrows(IllegalArgumentException.class, () -> arena.teamSpawn(0, 3));
    }

    @Test
    void returnedLocationsCannotMutateArenaConfiguration() {
        ArenaDefinition arena = new ArenaDefinition(
            "world",
            location(-10),
            location(20),
            List.of(location(1), location(2), location(3)),
            List.of(location(11), location(12), location(13)),
            location(30),
            location(40)
        );

        Location firstRead = arena.teamSpawn(0, 0);
        firstRead.setX(999D);
        Location secondRead = arena.teamSpawn(0, 0);

        assertNotSame(firstRead, secondRead);
        assertEquals(1D, secondRead.getX());
    }

    private Location location(double x) {
        return new Location(world, x, 64D, 0D);
    }
}
