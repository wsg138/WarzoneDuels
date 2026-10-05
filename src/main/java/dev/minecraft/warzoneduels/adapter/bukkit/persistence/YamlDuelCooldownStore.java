package dev.minecraft.warzoneduels.adapter.bukkit.persistence;

import dev.minecraft.warzoneduels.domain.DuelCooldownPolicy;
import dev.minecraft.warzoneduels.port.DuelCooldownStore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class YamlDuelCooldownStore implements DuelCooldownStore {
    private final Path file;
    public YamlDuelCooldownStore(Path file) { this.file = file; }

    @Override public DuelCooldownPolicy.Snapshot load() throws IOException {
        if (Files.notExists(file)) return DuelCooldownPolicy.Snapshot.empty();
        var yaml = new YamlConfiguration();
        try { yaml.load(file.toFile()); }
        catch (InvalidConfigurationException ex) { throw new IOException("Invalid cooldown YAML", ex); }
        if (!Integer.valueOf(1).equals(yaml.get("schema"))) throw new IOException("Unsupported cooldown schema");
        Map<UUID, Long> players = new HashMap<>();
        for (var entry : readTimes(yaml, "players").entrySet()) {
            try {
                UUID id = UUID.fromString(entry.getKey());
                if (!id.toString().equals(entry.getKey())) throw new IllegalArgumentException("Noncanonical UUID");
                players.put(id, entry.getValue());
            } catch (IllegalArgumentException ex) { throw new IOException("Invalid cooldown player UUID", ex); }
        }
        return new DuelCooldownPolicy.Snapshot(players, readPairs(yaml, "opponents"), readPairs(yaml, "challenges"));
    }
    private Map<String, Long> readPairs(YamlConfiguration yaml, String section) throws IOException {
        Map<String, Long> pairs = readTimes(yaml, section);
        for (String key : pairs.keySet()) {
            try {
                String[] ids = key.split("_", -1);
                if (ids.length != 2 || !DuelCooldownPolicy.pairKey(UUID.fromString(ids[0]), UUID.fromString(ids[1])).equals(key)) {
                    throw new IllegalArgumentException("Invalid pair");
                }
            } catch (IllegalArgumentException ex) { throw new IOException("Invalid cooldown opponent pair", ex); }
        }
        return pairs;
    }
    private Map<String, Long> readTimes(YamlConfiguration yaml, String name) throws IOException {
        ConfigurationSection section = yaml.getConfigurationSection(name);
        if (section == null) throw new IOException("Missing cooldown section: " + name);
        Map<String, Long> result = new HashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (!(value instanceof Long || value instanceof Integer) || ((Number) value).longValue() < 0) {
                throw new IOException("Invalid cooldown timestamp: " + name + "." + key);
            }
            result.put(key, ((Number) value).longValue());
        }
        return result;
    }
    @Override public void save(DuelCooldownPolicy.Snapshot snapshot) throws IOException {
        var yaml = new YamlConfiguration();
        yaml.set("schema", 1);
        yaml.createSection("players");
        yaml.createSection("opponents");
        yaml.createSection("challenges");
        snapshot.players().forEach((id, time) -> yaml.set("players." + id, time));
        snapshot.opponents().forEach((pair, time) -> yaml.set("opponents." + pair, time));
        snapshot.challenges().forEach((pair, time) -> yaml.set("challenges." + pair, time));
        Path directory = file.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "duel-cooldowns-", ".tmp");
        try {
            yaml.save(temporary.toFile());
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
