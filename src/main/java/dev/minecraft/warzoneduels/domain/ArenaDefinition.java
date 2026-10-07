package dev.minecraft.warzoneduels.domain;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

public final class ArenaDefinition {
    private final String arenaWorldName;
    private final Location firstCorner;
    private final Location secondCorner;
    private final List<Location> firstTeamSpawns;
    private final List<Location> secondTeamSpawns;
    private final Location spectatorLocation;
    private final Location exitLocation;

    public ArenaDefinition(
        String worldName,
        Location pos1,
        Location pos2,
        Location spawn1,
        Location spawn2,
        Location spectator,
        Location exit
    ) {
        this(
            worldName,
            pos1,
            pos2,
            fallbackSpawnGroup(spawn1),
            fallbackSpawnGroup(spawn2),
            spectator,
            exit
        );
    }

    public ArenaDefinition(
        String worldName,
        Location pos1,
        Location pos2,
        List<Location> teamOneSpawns,
        List<Location> teamTwoSpawns,
        Location spectator,
        Location exit
    ) {
        this.arenaWorldName = worldName;
        this.firstCorner = pos1.clone();
        this.secondCorner = pos2.clone();
        this.firstTeamSpawns = copySpawnGroup(teamOneSpawns);
        this.secondTeamSpawns = copySpawnGroup(teamTwoSpawns);
        this.spectatorLocation = spectator.clone();
        this.exitLocation = exit.clone();
    }

    public String worldName() {
        return arenaWorldName;
    }

    public World world() {
        return Bukkit.getWorld(arenaWorldName);
    }

    public Location pos1() {
        return firstCorner.clone();
    }

    public Location pos2() {
        return secondCorner.clone();
    }

    public Location spawn1() {
        return teamSpawn(0, 0);
    }

    public Location spawn2() {
        return teamSpawn(1, 0);
    }

    public Location teamSpawn(int teamIndex, int rosterSlot) {
        if (teamIndex < 0 || teamIndex > 1) {
            throw new IllegalArgumentException("Team index must be 0 or 1.");
        }
        if (rosterSlot < 0 || rosterSlot >= MatchTeam.MAX_SIZE) {
            throw new IllegalArgumentException("Roster slot must be between 0 and " + (MatchTeam.MAX_SIZE - 1) + ".");
        }
        List<Location> spawns = teamIndex == 0 ? firstTeamSpawns : secondTeamSpawns;
        return spawns.get(rosterSlot).clone();
    }

    public Location spectator() {
        return spectatorLocation.clone();
    }

    public Location exit() {
        return exitLocation.clone();
    }

    public boolean isReady() {
        return world() != null && hasValidTeamSpawns();
    }

    public boolean hasValidTeamSpawns() {
        return java.util.stream.Stream.concat(firstTeamSpawns.stream(), secondTeamSpawns.stream())
            .allMatch(this::contains);
    }

    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (!location.getWorld().getName().equalsIgnoreCase(arenaWorldName)) {
            return false;
        }
        return withinBounds(location);
    }

    private boolean withinBounds(Location location) {
        int minX = Math.min(firstCorner.getBlockX(), secondCorner.getBlockX());
        int maxX = Math.max(firstCorner.getBlockX(), secondCorner.getBlockX());
        int minY = Math.min(firstCorner.getBlockY(), secondCorner.getBlockY());
        int maxY = Math.max(firstCorner.getBlockY(), secondCorner.getBlockY());
        int minZ = Math.min(firstCorner.getBlockZ(), secondCorner.getBlockZ());
        int maxZ = Math.max(firstCorner.getBlockZ(), secondCorner.getBlockZ());
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    private static List<Location> copySpawnGroup(List<Location> spawns) {
        if (spawns == null || spawns.size() != MatchTeam.MAX_SIZE || spawns.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Each arena team requires exactly " + MatchTeam.MAX_SIZE + " spawn locations.");
        }
        return spawns.stream().map(Location::clone).toList();
    }

    private static List<Location> fallbackSpawnGroup(Location primary) {
        if (primary == null) {
            throw new IllegalArgumentException("Primary spawn cannot be null.");
        }
        return List.of(
            primary.clone(),
            primary.clone().add(2D, 0D, 0D),
            primary.clone().add(-2D, 0D, 0D)
        );
    }
}
