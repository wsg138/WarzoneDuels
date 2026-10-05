package dev.minecraft.warzoneduels.adapter.bukkit.persistence;

import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import dev.minecraft.warzoneduels.domain.stats.PlayerDuelStats;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public final class PlayerStatsStore {
    private final WarzoneDuelsPlugin plugin;
    private final File file;
    private final ExecutorService writer;
    private final Object saveLock = new Object();

    public PlayerStatsStore(WarzoneDuelsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
        this.writer = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "WarzoneDuels-StatsStore");
            thread.setDaemon(true);
            return thread;
        });
    }

    public Map<UUID, PlayerDuelStats> load() {
        Map<UUID, PlayerDuelStats> stats = new HashMap<>();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("players");
        if (section == null) {
            return stats;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection playerSection = section.getConfigurationSection(key);
            if (playerSection == null) {
                continue;
            }
            UUID playerId;
            try {
                playerId = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            String lastKnownName = playerSection.getString("last-known-name", "Unknown");
            PlayerDuelStats playerStats = new PlayerDuelStats(playerId, lastKnownName);
            playerStats.setMatchesPlayed(playerSection.getInt("matches-played", 0));
            playerStats.setWins(playerSection.getInt("wins", 0));
            playerStats.setLosses(playerSection.getInt("losses", 0));
            playerStats.setDraws(playerSection.getInt("draws", 0));
            playerStats.setDisconnectForfeitLosses(playerSection.getInt("disconnect-forfeit-losses", 0));
            playerStats.setCurrentWinStreak(playerSection.getInt("current-win-streak", 0));
            playerStats.setBestWinStreak(playerSection.getInt("best-win-streak", 0));
            playerStats.setChallengesSent(playerSection.getInt("advancements.challenges-sent", 0));
            playerStats.setSpoilsClaims(playerSection.getInt("advancements.spoils-claims", 0));
            playerStats.setMutualDraws(playerSection.getInt("advancements.mutual-draws", 0));
            playerStats.setCustomRulesWins(playerSection.getInt("advancements.custom-rules-wins", 0));
            playerStats.setRestrictedMobilityWins(playerSection.getInt("advancements.restricted-mobility-wins", 0));
            playerStats.setLowHealthWins(playerSection.getInt("advancements.low-health-wins", 0));
            stats.put(playerId, playerStats);
        }
        return stats;
    }

    public void save(Map<UUID, PlayerDuelStats> stats) {
        saveSnapshot(snapshot(stats.values()));
    }

    public void saveAsync(Map<UUID, PlayerDuelStats> stats) {
        List<StatsSnapshot> snapshot = snapshot(stats.values());
        try {
            writer.execute(() -> saveSnapshot(snapshot));
        } catch (RejectedExecutionException ex) {
            plugin.getLogger().warning("Duel stats save skipped because the writer is shutting down.");
        }
    }

    public void shutdown() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) {
                writer.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        }
    }

    private List<StatsSnapshot> snapshot(Collection<PlayerDuelStats> stats) {
        return stats.stream()
            .map(playerStats -> new StatsSnapshot(
                playerStats.playerId(),
                playerStats.lastKnownName(),
                playerStats.matchesPlayed(),
                playerStats.wins(),
                playerStats.losses(),
                playerStats.draws(),
                playerStats.disconnectForfeitLosses(),
                playerStats.currentWinStreak(),
                playerStats.bestWinStreak(),
                playerStats.challengesSent(),
                playerStats.spoilsClaims(),
                playerStats.mutualDraws(),
                playerStats.customRulesWins(),
                playerStats.restrictedMobilityWins(),
                playerStats.lowHealthWins()
            ))
            .toList();
    }

    private void saveSnapshot(List<StatsSnapshot> stats) {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection section = yaml.createSection("players");
        for (StatsSnapshot playerStats : stats) {
            ConfigurationSection playerSection = section.createSection(playerStats.playerId().toString());
            playerSection.set("last-known-name", playerStats.lastKnownName());
            playerSection.set("matches-played", playerStats.matchesPlayed());
            playerSection.set("wins", playerStats.wins());
            playerSection.set("losses", playerStats.losses());
            playerSection.set("draws", playerStats.draws());
            playerSection.set("disconnect-forfeit-losses", playerStats.disconnectForfeitLosses());
            playerSection.set("current-win-streak", playerStats.currentWinStreak());
            playerSection.set("best-win-streak", playerStats.bestWinStreak());
            playerSection.set("advancements.challenges-sent", playerStats.challengesSent());
            playerSection.set("advancements.spoils-claims", playerStats.spoilsClaims());
            playerSection.set("advancements.mutual-draws", playerStats.mutualDraws());
            playerSection.set("advancements.custom-rules-wins", playerStats.customRulesWins());
            playerSection.set("advancements.restricted-mobility-wins", playerStats.restrictedMobilityWins());
            playerSection.set("advancements.low-health-wins", playerStats.lowHealthWins());
        }
        saveAtomic(yaml);
    }

    private void saveAtomic(YamlConfiguration yaml) {
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
                plugin.getLogger().warning("Failed to save duel stats: " + ex.getMessage());
            }
        }
    }

    private record StatsSnapshot(
        UUID playerId,
        String lastKnownName,
        int matchesPlayed,
        int wins,
        int losses,
        int draws,
        int disconnectForfeitLosses,
        int currentWinStreak,
        int bestWinStreak,
        int challengesSent,
        int spoilsClaims,
        int mutualDraws,
        int customRulesWins,
        int restrictedMobilityWins,
        int lowHealthWins
    ) {
    }
}
