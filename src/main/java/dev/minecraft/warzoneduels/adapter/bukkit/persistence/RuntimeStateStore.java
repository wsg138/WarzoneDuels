package dev.minecraft.warzoneduels.adapter.bukkit.persistence;

import dev.minecraft.warzoneduels.BlockKey;
import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import dev.minecraft.warzoneduels.adapter.bukkit.reset.ArenaSnapshot;
import dev.minecraft.warzoneduels.adapter.bukkit.reset.SavedBlockState;
import dev.minecraft.warzoneduels.domain.ActiveDuel;
import dev.minecraft.warzoneduels.domain.DuelSettings;
import dev.minecraft.warzoneduels.domain.DuelMatchType;
import dev.minecraft.warzoneduels.domain.MatchTeam;
import dev.minecraft.warzoneduels.domain.MatchParticipant;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class RuntimeStateStore {
    private static final String RESUME_MARKER = "resume.marker";
    private static final String ACTIVE_STATE_FILE = "runtime-state.yml";
    private static final String RECOVERY_FILE = "restart-recovery.yml";
    private static final long ACTIVE_SAVE_DEBOUNCE_TICKS = 40L;

    private final WarzoneDuelsPlugin plugin;
    private final File resumeMarkerFile;
    private final File runtimeFile;
    private final File recoveryFile;
    private final Object lock = new Object();
    private final Object saveLock = new Object();

    private SerializedActiveDuel pendingActiveDuel;
    private BukkitTask queuedActiveSaveTask;
    private long runtimeRevision;

    public RuntimeStateStore(WarzoneDuelsPlugin plugin) {
        this.plugin = plugin;
        this.resumeMarkerFile = new File(plugin.getDataFolder(), RESUME_MARKER);
        this.runtimeFile = new File(plugin.getDataFolder(), ACTIVE_STATE_FILE);
        this.recoveryFile = new File(plugin.getDataFolder(), RECOVERY_FILE);
    }

    public void queueActiveDuelSave(ActiveDuel duel) {
        queueActiveDuelSave(duel, ACTIVE_SAVE_DEBOUNCE_TICKS);
    }

    public void queueActiveDuelSave(ActiveDuel duel, long delayTicks) {
        queueActiveDuelSave(duel, Set.of(), delayTicks);
    }

    public void queueActiveDuelSave(ActiveDuel duel, Set<UUID> eliminatedIds) {
        queueActiveDuelSave(duel, eliminatedIds, ACTIVE_SAVE_DEBOUNCE_TICKS);
    }

    public void queueActiveDuelSave(ActiveDuel duel, Set<UUID> eliminatedIds, long delayTicks) {
        SerializedActiveDuel snapshot = serializeActiveDuel(duel, eliminatedIds);
        synchronized (lock) {
            runtimeRevision++;
            pendingActiveDuel = snapshot;
            if (queuedActiveSaveTask != null) {
                return;
            }
            long expectedRevision = runtimeRevision;
            queuedActiveSaveTask = Bukkit.getScheduler().runTaskLaterAsynchronously(
                plugin,
                () -> flushQueuedActiveDuelSave(expectedRevision),
                Math.max(1L, delayTicks)
            );
        }
    }

    public void saveActiveDuelSync(ActiveDuel duel) {
        saveActiveDuelSync(duel, Set.of());
    }

    public void saveActiveDuelSync(ActiveDuel duel, Set<UUID> eliminatedIds) {
        synchronized (lock) {
            runtimeRevision++;
            pendingActiveDuel = null;
            if (queuedActiveSaveTask != null) {
                queuedActiveSaveTask.cancel();
                queuedActiveSaveTask = null;
            }
        }
        writeActiveDuel(serializeActiveDuel(duel, eliminatedIds));
    }

    public PersistedRuntime loadActiveDuel() {
        if (!runtimeFile.exists()) {
            return new PersistedRuntime(null, false);
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(runtimeFile);
            if (!yaml.getBoolean("active")) {
                return new PersistedRuntime(null, false);
            }
            DuelSettings settings = readSettings(yaml.getConfigurationSection("settings"));
            ActiveDuel duel = readActiveDuel(yaml, settings);
            if (duel == null) {
                return new PersistedRuntime(null, false);
            }
            duel.setWagerHeld(yaml.getBoolean("wager-held"));
            duel.setWagerPot(yaml.getDouble("wager-pot"));
            for (String value : yaml.getStringList("placed-blocks")) {
                BlockKey key = deserializeBlockKey(value);
                if (key != null) {
                    duel.placedBlocks().add(key);
                }
            }
            duel.setArenaSnapshot(readArenaSnapshot(yaml.getConfigurationSection("arena-snapshot")));
            Set<UUID> eliminatedIds = yaml.getStringList("eliminated-participants").stream()
                .map(this::safeUuid)
                .filter(id -> id != null && duel.contains(id))
                .collect(Collectors.toUnmodifiableSet());
            return new PersistedRuntime(duel, resumeMarkerFile.exists(), eliminatedIds);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Ignoring corrupt runtime duel state: " + ex.getMessage());
            return new PersistedRuntime(null, false);
        }
    }

    public void markReloadResume() {
        try {
            if (!resumeMarkerFile.getParentFile().exists()) {
                resumeMarkerFile.getParentFile().mkdirs();
            }
            if (!resumeMarkerFile.exists()) {
                resumeMarkerFile.createNewFile();
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to mark reload resume: " + ex.getMessage());
        }
    }

    public void clearReloadResumeMarker() {
        if (resumeMarkerFile.exists() && !resumeMarkerFile.delete()) {
            plugin.getLogger().warning("Failed to clear reload resume marker.");
        }
    }

    public void clearRuntime() {
        synchronized (lock) {
            runtimeRevision++;
            pendingActiveDuel = null;
            if (queuedActiveSaveTask != null) {
                queuedActiveSaveTask.cancel();
                queuedActiveSaveTask = null;
            }
        }
        if (runtimeFile.exists() && !runtimeFile.delete()) {
            plugin.getLogger().warning("Failed to clear runtime state file.");
        }
    }

    public void saveRecoveryTeleportIds(Set<UUID> playerIds) {
        YamlConfiguration yaml = recoveryFile.exists()
            ? YamlConfiguration.loadConfiguration(recoveryFile)
            : new YamlConfiguration();
        yaml.set("players", playerIds.stream().map(UUID::toString).toList());
        save(yaml, recoveryFile);
    }

    public Set<UUID> loadRecoveryTeleportIds() {
        if (!recoveryFile.exists()) {
            return Set.of();
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(recoveryFile);
        return yaml.getStringList("players").stream()
            .map(this::safeUuid)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());
    }

    public void clearRecoveryTeleportId(UUID playerId) {
        if (!recoveryFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(recoveryFile);
        Set<String> remaining = new java.util.HashSet<>(yaml.getStringList("players"));
        remaining.remove(playerId.toString());
        if (remaining.isEmpty() && yaml.getStringList("loadout-restores").isEmpty()) {
            if (!recoveryFile.delete()) {
                plugin.getLogger().warning("Failed to clear recovery teleport file.");
            }
            return;
        }
        yaml.set("players", remaining.stream().toList());
        save(yaml, recoveryFile);
    }

    public void savePendingLoadoutRestoreIds(Set<UUID> playerIds) {
        YamlConfiguration yaml = recoveryFile.exists()
            ? YamlConfiguration.loadConfiguration(recoveryFile)
            : new YamlConfiguration();
        yaml.set("loadout-restores", playerIds.stream().map(UUID::toString).toList());
        save(yaml, recoveryFile);
    }

    public Set<UUID> loadPendingLoadoutRestoreIds() {
        if (!recoveryFile.exists()) {
            return Set.of();
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(recoveryFile);
        return yaml.getStringList("loadout-restores").stream()
            .map(this::safeUuid)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());
    }

    public void clearPendingLoadoutRestoreId(UUID playerId) {
        if (!recoveryFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(recoveryFile);
        Set<String> remaining = new java.util.HashSet<>(yaml.getStringList("loadout-restores"));
        remaining.remove(playerId.toString());
        if (remaining.isEmpty() && yaml.getStringList("players").isEmpty()) {
            if (!recoveryFile.delete()) {
                plugin.getLogger().warning("Failed to clear recovery file.");
            }
            return;
        }
        yaml.set("loadout-restores", remaining.stream().toList());
        save(yaml, recoveryFile);
    }

    public void clearRecoveryFile() {
        if (recoveryFile.exists() && !recoveryFile.delete()) {
            plugin.getLogger().warning("Failed to clear recovery teleport file.");
        }
    }

    private void flushQueuedActiveDuelSave(long expectedRevision) {
        SerializedActiveDuel snapshot;
        synchronized (lock) {
            queuedActiveSaveTask = null;
            if (expectedRevision != runtimeRevision) {
                if (pendingActiveDuel != null) {
                    long nextRevision = runtimeRevision;
                    queuedActiveSaveTask = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> flushQueuedActiveDuelSave(nextRevision), ACTIVE_SAVE_DEBOUNCE_TICKS);
                }
                return;
            }
            snapshot = pendingActiveDuel;
            pendingActiveDuel = null;
        }
        writeActiveDuel(snapshot);
    }

    private SerializedActiveDuel serializeActiveDuel(ActiveDuel duel, Set<UUID> eliminatedIds) {
        if (duel == null) {
            return null;
        }
        return new SerializedActiveDuel(
            duel.matchType().name(),
            duel.teamOne().id(),
            duel.teamOne().participants().stream().map(this::serializeParticipant).toList(),
            duel.teamTwo().id(),
            duel.teamTwo().participants().stream().map(this::serializeParticipant).toList(),
            serializeSettings(duel.settings()),
            duel.startedAtEpochMs(),
            duel.duelDeadlineEpochMs(),
            duel.isWagerHeld(),
            duel.getWagerPot(),
            duel.placedBlocks().stream().map(this::serializeBlockKey).toList(),
            serializeArenaSnapshot(duel.arenaSnapshot()),
            eliminatedIds.stream().filter(duel::contains).map(UUID::toString).sorted().toList()
        );
    }

    private SerializedParticipant serializeParticipant(MatchParticipant participant) {
        return new SerializedParticipant(
            participant.playerId(),
            participant.name(),
            participant.drawRequested(),
            participant.disconnectDeadlineEpochMs()
        );
    }

    private SerializedSettings serializeSettings(DuelSettings settings) {
        return new SerializedSettings(
            settings.getPlaceBreakMode().name(),
            settings.getPlaceOnlyMode().name(),
            settings.getMapId(),
            settings.getMapDisplayName(),
            settings.getMapDescription(),
            settings.isMapSupportsPlaceOnly(),
            settings.isMapSupportsBlockBreaking(),
            settings.isMapSupportsProtectedExplosives(),
            settings.getMapSchematicFile(),
            settings.isMapPasteAir(),
            settings.isAllowCrystalsAnchors(),
            settings.isAllowExplosiveMinecarts(),
            settings.isAllowOtherExplosives(),
            settings.isAllowEnderPearls(),
            settings.getEnderPearlCooldownSeconds(),
            settings.isAllowWindCharges(),
            settings.getWindChargeCooldownSeconds(),
            settings.isAllowMaces(),
            settings.isAllowChorusFruit(),
            settings.isAllowSpears(),
            settings.isAllowElytras(),
            settings.isAllowEnderChests(),
            settings.getWager()
        );
    }

    private Map<String, SerializedBlockState> serializeArenaSnapshot(ArenaSnapshot snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return Map.of();
        }
        Map<String, SerializedBlockState> serialized = new LinkedHashMap<>();
        int index = 0;
        for (Map.Entry<BlockKey, SavedBlockState> entry : snapshot.blocks().entrySet()) {
            serialized.put("block-" + index++, new SerializedBlockState(
                serializeBlockKey(entry.getKey()),
                entry.getValue().materialKey(),
                entry.getValue().blockData()
            ));
        }
        return serialized;
    }

    private void writeActiveDuel(SerializedActiveDuel duel) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("active", duel != null);
        if (duel != null) {
            yaml.set("schema-version", 2);
            yaml.set("match-type", duel.matchType());
            writeTeam(yaml.createSection("team-one"), duel.teamOneId(), duel.teamOneParticipants());
            writeTeam(yaml.createSection("team-two"), duel.teamTwoId(), duel.teamTwoParticipants());
            writeParticipant(yaml.createSection("participant-one"), duel.teamOneParticipants().get(0));
            writeParticipant(yaml.createSection("participant-two"), duel.teamTwoParticipants().get(0));
            writeSettings(yaml.createSection("settings"), duel.settings());
            yaml.set("started-at", duel.startedAtEpochMs());
            yaml.set("duel-deadline-epoch-ms", duel.duelDeadlineEpochMs());
            yaml.set("wager-held", duel.wagerHeld());
            yaml.set("wager-pot", duel.wagerPot());
            yaml.set("placed-blocks", duel.placedBlocks());
            yaml.set("eliminated-participants", duel.eliminatedParticipantIds());
            writeArenaSnapshot(yaml.createSection("arena-snapshot"), duel.arenaSnapshot());
        }
        save(yaml, runtimeFile);
    }

    private void writeParticipant(ConfigurationSection section, SerializedParticipant participant) {
        section.set("id", participant.playerId().toString());
        section.set("name", participant.name());
        section.set("draw-requested", participant.drawRequested());
        section.set("disconnect-deadline", participant.disconnectDeadlineEpochMs());
    }

    private void writeTeam(ConfigurationSection section, UUID teamId, java.util.List<SerializedParticipant> participants) {
        section.set("id", teamId.toString());
        ConfigurationSection participantSection = section.createSection("participants");
        for (int index = 0; index < participants.size(); index++) {
            writeParticipant(participantSection.createSection("member-" + index), participants.get(index));
        }
    }

    private void writeSettings(ConfigurationSection section, SerializedSettings settings) {
        section.set("place-break-mode", settings.placeBreakMode());
        section.set("place-only-mode", settings.placeOnlyMode());
        section.set("map-id", settings.mapId());
        section.set("map-display-name", settings.mapDisplayName());
        section.set("map-description", settings.mapDescription());
        section.set("map-supports-place-only", settings.mapSupportsPlaceOnly());
        section.set("map-supports-block-breaking", settings.mapSupportsBlockBreaking());
        section.set("map-supports-protected-explosives", settings.mapSupportsProtectedExplosives());
        section.set("map-schematic-file", settings.mapSchematicFile());
        section.set("map-paste-air", settings.mapPasteAir());
        section.set("allow-crystals-anchors", settings.allowCrystalsAnchors());
        section.set("allow-explosive-minecarts", settings.allowExplosiveMinecarts());
        section.set("allow-other-explosives", settings.allowOtherExplosives());
        section.set("allow-ender-pearls", settings.allowEnderPearls());
        section.set("ender-pearl-cooldown-seconds", settings.enderPearlCooldownSeconds());
        section.set("allow-wind-charges", settings.allowWindCharges());
        section.set("wind-charge-cooldown-seconds", settings.windChargeCooldownSeconds());
        section.set("allow-maces", settings.allowMaces());
        section.set("allow-chorus-fruit", settings.allowChorusFruit());
        section.set("allow-spears", settings.allowSpears());
        section.set("allow-elytras", settings.allowElytras());
        section.set("allow-ender-chests", settings.allowEnderChests());
        section.set("wager", settings.wager());
    }

    private MatchParticipant readParticipant(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        UUID playerId = safeUuid(section.getString("id"));
        if (playerId == null) {
            return null;
        }
        MatchParticipant participant = new MatchParticipant(playerId, section.getString("name", "Unknown"));
        participant.setDrawRequested(section.getBoolean("draw-requested"));
        if (section.contains("disconnect-deadline")) {
            participant.setDisconnectDeadlineEpochMs(section.getLong("disconnect-deadline"));
        }
        return participant;
    }

    private ActiveDuel readActiveDuel(YamlConfiguration yaml, DuelSettings settings) {
        if (yaml.getInt("schema-version", 1) >= 2) {
            MatchTeam teamOne = readTeam(yaml.getConfigurationSection("team-one"));
            MatchTeam teamTwo = readTeam(yaml.getConfigurationSection("team-two"));
            if (teamOne == null || teamTwo == null) {
                return null;
            }
            DuelMatchType matchType = safeEnum(
                DuelMatchType.class,
                yaml.getString("match-type"),
                teamOne.size() == 1 ? DuelMatchType.NORMAL : DuelMatchType.PARTY
            );
            ActiveDuel duel = new ActiveDuel(matchType, teamOne, teamTwo, settings, yaml.getLong("started-at"));
            if (yaml.contains("duel-deadline-epoch-ms")) {
                duel.setDuelDeadlineEpochMs(yaml.getLong("duel-deadline-epoch-ms"));
            }
            return duel;
        }
        MatchParticipant one = readParticipant(yaml.getConfigurationSection("participant-one"));
        MatchParticipant two = readParticipant(yaml.getConfigurationSection("participant-two"));
        return one == null || two == null ? null : new ActiveDuel(one, two, settings, yaml.getLong("started-at"));
    }

    private MatchTeam readTeam(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        UUID teamId = safeUuid(section.getString("id"));
        ConfigurationSection participantSection = section.getConfigurationSection("participants");
        if (teamId == null || participantSection == null) {
            return null;
        }
        java.util.List<String> keys = participantSection.getKeys(false).stream().sorted().toList();
        java.util.List<MatchParticipant> participants = new java.util.ArrayList<>();
        for (String key : keys) {
            MatchParticipant participant = readParticipant(participantSection.getConfigurationSection(key));
            if (participant == null) {
                return null;
            }
            participants.add(participant);
        }
        try {
            return new MatchTeam(teamId, participants);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private DuelSettings readSettings(ConfigurationSection section) {
        DuelSettings settings = new DuelSettings();
        if (section == null) {
            return settings;
        }
        settings.setPlaceBreakMode(safeEnum(DuelSettings.PlaceBreakMode.class, section.getString("place-break-mode"), DuelSettings.PlaceBreakMode.NONE));
        settings.setPlaceOnlyMode(safeEnum(DuelSettings.PlaceOnlyMode.class, section.getString("place-only-mode"), DuelSettings.PlaceOnlyMode.COBWEB_UTILS));
        settings.setMapId(section.getString("map-id", settings.getMapId()));
        settings.setMapDisplayName(section.getString("map-display-name", settings.getMapDisplayName()));
        settings.setMapDescription(section.getString("map-description", settings.getMapDescription()));
        settings.setMapSupportsPlaceOnly(section.getBoolean("map-supports-place-only", settings.isMapSupportsPlaceOnly()));
        settings.setMapSupportsBlockBreaking(section.getBoolean("map-supports-block-breaking", settings.isMapSupportsBlockBreaking()));
        settings.setMapSupportsProtectedExplosives(section.getBoolean("map-supports-protected-explosives", settings.isMapSupportsProtectedExplosives()));
        settings.setMapSchematicFile(section.getString("map-schematic-file", settings.getMapSchematicFile()));
        settings.setMapPasteAir(section.getBoolean("map-paste-air", settings.isMapPasteAir()));
        settings.setAllowCrystalsAnchors(section.getBoolean("allow-crystals-anchors"));
        settings.setAllowExplosiveMinecarts(section.getBoolean("allow-explosive-minecarts"));
        settings.setAllowOtherExplosives(section.getBoolean("allow-other-explosives"));
        settings.setAllowEnderPearls(section.getBoolean("allow-ender-pearls", settings.isAllowEnderPearls()));
        settings.setEnderPearlCooldownSeconds(section.getInt("ender-pearl-cooldown-seconds", settings.getEnderPearlCooldownSeconds()));
        settings.setAllowWindCharges(section.getBoolean("allow-wind-charges", settings.isAllowWindCharges()));
        settings.setWindChargeCooldownSeconds(section.getInt("wind-charge-cooldown-seconds", settings.getWindChargeCooldownSeconds()));
        settings.setAllowMaces(section.getBoolean("allow-maces", settings.isAllowMaces()));
        settings.setAllowChorusFruit(section.getBoolean("allow-chorus-fruit", settings.isAllowChorusFruit()));
        settings.setAllowSpears(section.getBoolean("allow-spears", settings.isAllowSpears()));
        settings.setAllowElytras(section.getBoolean("allow-elytras", settings.isAllowElytras()));
        settings.setAllowEnderChests(section.getBoolean("allow-ender-chests", settings.isAllowEnderChests()));
        settings.setWager(section.getDouble("wager"));
        return settings;
    }

    private void writeArenaSnapshot(ConfigurationSection section, Map<String, SerializedBlockState> snapshot) {
        for (Map.Entry<String, SerializedBlockState> entry : snapshot.entrySet()) {
            ConfigurationSection child = section.createSection(entry.getKey());
            child.set("key", entry.getValue().blockKey());
            child.set("material", entry.getValue().materialKey());
            child.set("data", entry.getValue().blockData());
        }
    }

    private ArenaSnapshot readArenaSnapshot(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        Map<BlockKey, SavedBlockState> snapshot = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(key);
            if (child == null) {
                continue;
            }
            BlockKey blockKey = deserializeBlockKey(child.getString("key"));
            if (blockKey == null) {
                continue;
            }
            snapshot.put(blockKey, new SavedBlockState(child.getString("material", "AIR"), child.getString("data", "")));
        }
        return new ArenaSnapshot(snapshot);
    }

    private String serializeBlockKey(BlockKey key) {
        return key.getWorldId() + ":" + key.getX() + ":" + key.getY() + ":" + key.getZ();
    }

    private BlockKey deserializeBlockKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split(":");
        if (parts.length != 4) {
            return null;
        }
        try {
            UUID worldId = safeUuid(parts[0]);
            if (worldId == null) {
                return null;
            }
            return new BlockKey(
                worldId,
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3])
            );
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private UUID safeUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private <T extends Enum<T>> T safeEnum(Class<T> type, String raw, T fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private void save(YamlConfiguration yaml, File file) {
        synchronized (saveLock) {
            try {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("Failed to create " + parent.getAbsolutePath());
                }
                File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
                yaml.save(temporary);
                try {
                    Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ex) {
                plugin.getLogger().warning("Failed to save " + file.getName() + ": " + ex.getMessage());
            }
        }
    }

    public record PersistedRuntime(ActiveDuel activeDuel, boolean resumeAllowed, Set<UUID> eliminatedParticipantIds) {
        public PersistedRuntime {
            eliminatedParticipantIds = Set.copyOf(eliminatedParticipantIds);
        }

        public PersistedRuntime(ActiveDuel activeDuel, boolean resumeAllowed) {
            this(activeDuel, resumeAllowed, Set.of());
        }
    }

    private record SerializedActiveDuel(
        String matchType,
        UUID teamOneId,
        java.util.List<SerializedParticipant> teamOneParticipants,
        UUID teamTwoId,
        java.util.List<SerializedParticipant> teamTwoParticipants,
        SerializedSettings settings,
        long startedAtEpochMs,
        Long duelDeadlineEpochMs,
        boolean wagerHeld,
        double wagerPot,
        java.util.List<String> placedBlocks,
        Map<String, SerializedBlockState> arenaSnapshot,
        java.util.List<String> eliminatedParticipantIds
    ) {
    }

    private record SerializedParticipant(
        UUID playerId,
        String name,
        boolean drawRequested,
        Long disconnectDeadlineEpochMs
    ) {
    }

    private record SerializedSettings(
        String placeBreakMode,
        String placeOnlyMode,
        String mapId,
        String mapDisplayName,
        String mapDescription,
        boolean mapSupportsPlaceOnly,
        boolean mapSupportsBlockBreaking,
        boolean mapSupportsProtectedExplosives,
        String mapSchematicFile,
        boolean mapPasteAir,
        boolean allowCrystalsAnchors,
        boolean allowExplosiveMinecarts,
        boolean allowOtherExplosives,
        boolean allowEnderPearls,
        int enderPearlCooldownSeconds,
        boolean allowWindCharges,
        int windChargeCooldownSeconds,
        boolean allowMaces,
        boolean allowChorusFruit,
        boolean allowSpears,
        boolean allowElytras,
        boolean allowEnderChests,
        double wager
    ) {
    }

    private record SerializedBlockState(
        String blockKey,
        String materialKey,
        String blockData
    ) {
    }
}
