package dev.minecraft.warzoneduels.adapter.bukkit.persistence;

import dev.minecraft.warzoneduels.port.DuelBlockStore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** duel-blocks.yml: schema 1, blocks.&lt;owner-uuid&gt; = list of blocked UUIDs. Replaced atomically on save. */
public final class YamlDuelBlockStore implements DuelBlockStore {
    private final Path file;

    public YamlDuelBlockStore(Path file) {
        this.file = file;
    }

    @Override public Map<UUID, Set<UUID>> load() throws IOException {
        if (Files.notExists(file)) return Map.of();
        var yaml = new YamlConfiguration();
        try { yaml.load(file.toFile()); }
        catch (InvalidConfigurationException ex) { throw new IOException("Invalid duel block YAML", ex); }
        if (!Integer.valueOf(1).equals(yaml.get("schema"))) throw new IOException("Unsupported duel block schema");
        ConfigurationSection section = yaml.getConfigurationSection("blocks");
        if (section == null) throw new IOException("Missing duel block section");
        Map<UUID, Set<UUID>> blocks = new HashMap<>();
        for (String owner : section.getKeys(false)) {
            Set<UUID> targets = new HashSet<>();
            List<?> values = section.getList(owner);
            if (values == null) throw new IOException("Invalid duel block list for " + owner);
            for (Object value : values) targets.add(uuid(String.valueOf(value)));
            blocks.put(uuid(owner), targets);
        }
        return blocks;
    }

    private static UUID uuid(String value) throws IOException {
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equals(value)) throw new IllegalArgumentException("Noncanonical UUID");
            return id;
        } catch (IllegalArgumentException ex) { throw new IOException("Invalid duel block UUID", ex); }
    }

    @Override public void save(Map<UUID, Set<UUID>> blocks) throws IOException {
        var yaml = new YamlConfiguration();
        yaml.set("schema", 1);
        yaml.createSection("blocks");
        blocks.forEach((owner, targets) -> yaml.set("blocks." + owner, targets.stream().map(UUID::toString).sorted().toList()));
        Path directory = file.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "duel-blocks-", ".tmp");
        try {
            yaml.save(temporary.toFile());
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
