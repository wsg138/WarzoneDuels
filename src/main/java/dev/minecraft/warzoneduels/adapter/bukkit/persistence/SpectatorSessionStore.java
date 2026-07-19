package dev.minecraft.warzoneduels.adapter.bukkit.persistence;

import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import dev.minecraft.warzoneduels.domain.SpectatorSession;
import dev.minecraft.warzoneduels.domain.SpectatorSessionPhase;
import dev.minecraft.warzoneduels.domain.StoredLocation;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SpectatorSessionStore {
    private final Path sessionDirectory;
    private final Logger logger;

    public SpectatorSessionStore(WarzoneDuelsPlugin plugin) {
        this(plugin.getDataFolder().toPath().resolve("spectator-sessions"), plugin.getLogger());
    }

    public SpectatorSessionStore(Path directory, Logger logger) {
        this.sessionDirectory = directory;
        this.logger = logger;
    }

    public boolean save(SpectatorSession session) {
        Path destination = pathFor(session.playerId());
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        Path backup = backupPathFor(session.playerId());
        Path backupTemporary = backup.resolveSibling(backup.getFileName() + ".tmp");
        try {
            Files.createDirectories(sessionDirectory);
            YamlConfiguration yaml = serialize(session);
            yaml.save(temporary.toFile());
            forceFile(temporary);
            if (Files.isRegularFile(destination)) {
                Files.copy(destination, backupTemporary, StandardCopyOption.REPLACE_EXISTING);
                forceFile(backupTemporary);
                moveAtomic(backupTemporary, backup);
            }
            moveAtomic(temporary, destination);
            SpectatorSession verified = loadFile(destination);
            if (!session.playerId().equals(verified.playerId()) || session.phase() != verified.phase()) {
                throw new IOException("Session verification did not match the saved UUID and phase");
            }
            return true;
        } catch (Exception ex) {
            log(Level.SEVERE, () -> "Failed to persist spectator session for " + session.playerId() + " (" + session.playerName() + ")", ex);
            return false;
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ex) {
                log(Level.WARNING, () -> "Failed to remove temporary spectator session file " + temporary, ex);
            }
            try {
                Files.deleteIfExists(backupTemporary);
            } catch (IOException ex) {
                log(Level.WARNING, () -> "Failed to remove temporary spectator session backup " + backupTemporary, ex);
            }
        }
    }

    public Optional<SpectatorSession> load(UUID playerId) {
        Path file = pathFor(playerId);
        if (Files.isRegularFile(file)) {
            try {
                return Optional.of(loadFile(file));
            } catch (Exception ex) {
                log(Level.SEVERE, () -> "Failed to load primary spectator session for " + playerId + " from " + file + "; attempting its isolated backup", ex);
            }
        }
        Path backup = backupPathFor(playerId);
        if (Files.isRegularFile(backup)) {
            try {
                SpectatorSession recovered = loadFile(backup);
                log(Level.SEVERE, () -> "Recovered spectator session " + playerId + " from its isolated backup because the primary record was missing or corrupt.", null);
                return Optional.of(recovered);
            } catch (Exception ex) {
                log(Level.SEVERE, () -> "Failed to load spectator session backup for " + playerId + " from " + backup, ex);
            }
        }
        return Optional.empty();
    }

    public Map<UUID, SpectatorSession> loadAll() {
        Map<UUID, SpectatorSession> sessions = new LinkedHashMap<>();
        if (!Files.isDirectory(sessionDirectory)) {
            return sessions;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(sessionDirectory, "*.yml")) {
            for (Path file : files) {
                try {
                    SpectatorSession session = loadFile(file);
                    sessions.put(session.playerId(), session);
                } catch (Exception ex) {
                    log(Level.SEVERE, () -> "Failed to load spectator session file " + file + "; it has been retained for manual recovery", ex);
                }
            }
        } catch (IOException ex) {
            log(Level.SEVERE, () -> "Failed to scan spectator session directory " + sessionDirectory, ex);
        }
        return sessions;
    }

    public boolean exists(UUID playerId) {
        return Files.isRegularFile(pathFor(playerId)) || Files.isRegularFile(backupPathFor(playerId));
    }

    public boolean delete(SpectatorSession session) {
        try {
            Files.deleteIfExists(pathFor(session.playerId()));
            Files.deleteIfExists(backupPathFor(session.playerId()));
            return !exists(session.playerId());
        } catch (IOException ex) {
            log(Level.SEVERE, () -> "Failed to delete restored spectator session for " + session.playerId() + " (" + session.playerName() + ")", ex);
            return false;
        }
    }

    public Path directory() {
        return sessionDirectory;
    }

    private void log(Level level, java.util.function.Supplier<String> message, Throwable cause) {
        if (!logger.isLoggable(level)) {
            return;
        }
        if (cause == null) {
            logger.log(level, message.get());
            return;
        }
        logger.log(level, message.get(), cause);
    }

    private Path pathFor(UUID playerId) {
        return sessionDirectory.resolve(playerId + ".yml");
    }

    private Path backupPathFor(UUID playerId) {
        return sessionDirectory.resolve(playerId + ".yml.bak");
    }

    private YamlConfiguration serialize(SpectatorSession session) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", 1);
        yaml.set("player-id", session.playerId().toString());
        yaml.set("player-name", session.playerName());
        yaml.set("created-at", session.createdAtEpochMs());
        yaml.set("phase", session.phase().name());
        yaml.set("original.game-mode", session.originalGameMode().name());
        writeLocation(yaml.createSection("original.location"), session.originalLocation());
        yaml.set("original.allow-flight", session.originalAllowFlight());
        yaml.set("original.flying", session.originalFlying());
        yaml.set("original.fly-speed", session.originalFlySpeed());
        yaml.set("original.walk-speed", session.originalWalkSpeed());
        yaml.set("original.collidable", session.originalCollidable());
        yaml.set("original.can-pickup-items", session.originalCanPickupItems());
        yaml.set("original.contents", session.contents());
        yaml.set("original.armor", session.armor());
        yaml.set("original.offhand", session.offhand());
        yaml.set("original.cursor", session.cursor());
        yaml.set("original.health", session.health());
        yaml.set("original.food", session.foodLevel());
        yaml.set("original.saturation", session.saturation());
        yaml.set("original.total-experience", session.totalExperience());
        yaml.set("original.level", session.level());
        yaml.set("original.experience-progress", session.experienceProgress());
        yaml.set("original.fire-ticks", session.fireTicks());
        yaml.set("original.potion-effects", session.potionEffects());
        yaml.set("original.fall-distance", session.fallDistance());
        yaml.set("original.velocity.x", session.velocity().getX());
        yaml.set("original.velocity.y", session.velocity().getY());
        yaml.set("original.velocity.z", session.velocity().getZ());
        return yaml;
    }

    private SpectatorSession loadFile(Path file) throws IOException {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        UUID playerId = parseUuid(yaml.getString("player-id"));
        SpectatorSessionPhase phase = parseEnum(SpectatorSessionPhase.class, yaml.getString("phase"));
        GameMode gameMode = parseEnum(GameMode.class, yaml.getString("original.game-mode"));
        StoredLocation location = readLocation(yaml.getConfigurationSection("original.location"));
        if (playerId == null || phase == null || gameMode == null || location == null) {
            throw new IOException("Missing or invalid required spectator session fields");
        }
        return new SpectatorSession(
            playerId,
            yaml.getString("player-name", "Unknown"),
            yaml.getLong("created-at"),
            phase,
            gameMode,
            location,
            yaml.getBoolean("original.allow-flight"),
            yaml.getBoolean("original.flying"),
            (float) yaml.getDouble("original.fly-speed", 0.1D),
            (float) yaml.getDouble("original.walk-speed", 0.2D),
            yaml.getBoolean("original.collidable", true),
            yaml.getBoolean("original.can-pickup-items", true),
            itemArray(yaml.get("original.contents")),
            itemArray(yaml.get("original.armor")),
            yaml.getItemStack("original.offhand"),
            yaml.getItemStack("original.cursor"),
            yaml.getDouble("original.health", 20D),
            yaml.getInt("original.food", 20),
            (float) yaml.getDouble("original.saturation", 5D),
            yaml.getInt("original.total-experience"),
            yaml.getInt("original.level"),
            (float) yaml.getDouble("original.experience-progress"),
            yaml.getInt("original.fire-ticks"),
            potionEffects(yaml.get("original.potion-effects")),
            (float) yaml.getDouble("original.fall-distance"),
            new Vector(yaml.getDouble("original.velocity.x"), yaml.getDouble("original.velocity.y"), yaml.getDouble("original.velocity.z"))
        );
    }

    private void writeLocation(ConfigurationSection section, StoredLocation location) {
        if (location == null) {
            return;
        }
        section.set("world-id", location.worldId() == null ? null : location.worldId().toString());
        section.set("world-name", location.worldName());
        section.set("x", location.x());
        section.set("y", location.y());
        section.set("z", location.z());
        section.set("yaw", location.yaw());
        section.set("pitch", location.pitch());
    }

    private StoredLocation readLocation(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        UUID worldId = parseUuid(section.getString("world-id"));
        String worldName = section.getString("world-name");
        if (worldId == null && (worldName == null || worldName.isBlank())) {
            return null;
        }
        return new StoredLocation(worldId, worldName, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
            (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
    }

    private ItemStack[] itemArray(Object raw) {
        if (raw instanceof ItemStack[] array) {
            return array;
        }
        if (!(raw instanceof List<?> list)) {
            return new ItemStack[0];
        }
        ItemStack[] items = new ItemStack[list.size()];
        for (int index = 0; index < list.size(); index++) {
            items[index] = list.get(index) instanceof ItemStack item ? item : null;
        }
        return items;
    }

    private List<PotionEffect> potionEffects(Object raw) {
        List<PotionEffect> effects = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object value : list) {
                if (value instanceof PotionEffect effect) {
                    effects.add(effect);
                }
            }
        }
        return effects;
    }

    private UUID parseUuid(String raw) {
        try {
            return raw == null ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private <T extends Enum<T>> T parseEnum(Class<T> type, String raw) {
        try {
            return raw == null ? null : Enum.valueOf(type, raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void forceFile(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private void moveAtomic(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
