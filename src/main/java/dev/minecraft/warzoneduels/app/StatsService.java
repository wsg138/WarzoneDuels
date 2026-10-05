package dev.minecraft.warzoneduels.app;

import dev.minecraft.warzoneduels.adapter.bukkit.persistence.PlayerStatsStore;
import dev.minecraft.warzoneduels.domain.ActiveDuel;
import dev.minecraft.warzoneduels.domain.DuelAdvancementPolicy;
import dev.minecraft.warzoneduels.domain.DuelEndReason;
import dev.minecraft.warzoneduels.domain.DuelMatchType;
import dev.minecraft.warzoneduels.domain.MatchParticipant;
import dev.minecraft.warzoneduels.domain.TeamOutcomePolicy;
import dev.minecraft.warzoneduels.domain.stats.PlayerDuelStats;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class StatsService {
    private final PlayerStatsStore store;
    private final Consumer<Map<UUID, PlayerDuelStats>> saveSink;
    private final Map<UUID, PlayerDuelStats> statsByPlayerId = new ConcurrentHashMap<>();

    public StatsService(PlayerStatsStore store) {
        this.store = store;
        this.saveSink = store::saveAsync;
    }

    StatsService(Consumer<Map<UUID, PlayerDuelStats>> saveSink) {
        this.store = null;
        this.saveSink = java.util.Objects.requireNonNull(saveSink);
    }

    public void enable() {
        statsByPlayerId.clear();
        statsByPlayerId.putAll(store.load());
    }

    public void disable() {
        store.shutdown();
        store.save(statsByPlayerId);
    }

    public void recordMatchResult(ActiveDuel duel, UUID winnerId, DuelEndReason reason) {
        recordMatchResult(duel, winnerId, reason, true);
    }

    public void recordMatchResult(ActiveDuel duel, UUID winnerId, DuelEndReason reason, boolean advancementEvidenceAllowed) {
        if (duel == null) {
            return;
        }
        if (reason == DuelEndReason.DRAW) {
            for (MatchParticipant participant : duel.participants()) {
                stats(participant.playerId(), participant.name()).recordDraw();
            }
            save();
            return;
        }
        if (winnerId == null || !duel.contains(winnerId)) {
            return;
        }

        TeamOutcomePolicy.Outcome outcome = TeamOutcomePolicy.outcome(duel, winnerId);
        boolean restrictedMobility = advancementEvidenceAllowed && DuelAdvancementPolicy.isRestrictedMobilityWin(duel.settings(), reason);
        for (MatchParticipant winner : outcome.winners()) {
            PlayerDuelStats winnerStats = stats(winner.playerId(), winner.name());
            winnerStats.recordWin();
            if (restrictedMobility) {
                winnerStats.recordRestrictedMobilityWin();
            }
        }
        for (MatchParticipant loser : outcome.losers()) {
            stats(loser.playerId(), loser.name()).recordLoss(reason == DuelEndReason.DISCONNECT_TIMEOUT);
        }

        MatchParticipant challenger = duel.participantOne();
        if (advancementEvidenceAllowed && DuelAdvancementPolicy.isCustomRulesChallengerWin(
            duel.matchType(), challenger.playerId(), winnerId, duel.settings(), reason
        )) {
            stats(challenger.playerId(), challenger.name()).recordCustomRulesWin();
        }

        if (advancementEvidenceAllowed && duel.matchType() == DuelMatchType.NORMAL && reason == DuelEndReason.KILL) {
            Player onlineWinner = Bukkit.getPlayer(winnerId);
            if (onlineWinner != null
                && DuelAdvancementPolicy.isLowHealthWin(duel.matchType(), reason, onlineWinner.getHealth())) {
                stats(winnerId, onlineWinner.getName()).recordLowHealthWin();
            }
        }
        save();
    }

    public void recordChallengeSent(UUID playerId, String name) {
        stats(playerId, name).recordChallengeSent();
        save();
    }

    public void recordSpoilsClaim(UUID playerId, String name) {
        stats(playerId, name).recordSpoilsClaim();
        save();
    }

    public void recordMutualDraw(UUID playerId, String name) {
        stats(playerId, name).recordMutualDraw();
        save();
    }

    public PlayerDuelStats findByNameOrOffline(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        PlayerDuelStats existingStats = findExistingByName(name);
        if (existingStats != null) {
            return existingStats;
        }
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(name);
        if (offlinePlayer != null && offlinePlayer.getUniqueId() != null) {
            return stats(offlinePlayer.getUniqueId(), offlinePlayer.getName() == null ? name : offlinePlayer.getName());
        }
        return null;
    }

    public PlayerDuelStats findExistingByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String needle = name.toLowerCase(Locale.ROOT);
        PlayerDuelStats exactMatch = findByLastKnownName(needle, false);
        return exactMatch == null ? findByLastKnownName(needle, true) : exactMatch;
    }

    private PlayerDuelStats findByLastKnownName(String needle, boolean prefixMatch) {
        for (PlayerDuelStats stats : statsByPlayerId.values()) {
            String lastKnownName = stats.lastKnownName();
            if (lastKnownName == null) {
                continue;
            }
            String normalizedName = lastKnownName.toLowerCase(Locale.ROOT);
            if (!prefixMatch && normalizedName.equals(needle)) {
                return stats;
            }
            if (prefixMatch && normalizedName.startsWith(needle)) {
                return stats;
            }
        }
        return null;
    }

    public PlayerDuelStats stats(UUID playerId, String lastKnownName) {
        PlayerDuelStats playerStats = statsByPlayerId.computeIfAbsent(playerId, id -> new PlayerDuelStats(id, lastKnownName));
        if (lastKnownName != null && !lastKnownName.isBlank()) {
            playerStats.setLastKnownName(lastKnownName);
        }
        return playerStats;
    }

    public PlayerDuelStats findById(UUID playerId) {
        return statsByPlayerId.get(playerId);
    }

    public Collection<PlayerDuelStats> all() {
        return statsByPlayerId.values();
    }

    public List<PlayerDuelStats> topByWins(int page, int pageSize) {
        int safePage = Math.max(0, page);
        int safePageSize = Math.max(1, pageSize);
        return statsByPlayerId.values().stream()
            .filter(stats -> stats.matchesPlayed() > 0)
            .sorted(Comparator
                .comparingInt(PlayerDuelStats::wins).reversed()
                .thenComparing(Comparator.comparingInt(PlayerDuelStats::bestWinStreak).reversed())
                .thenComparing(PlayerDuelStats::lastKnownName, String.CASE_INSENSITIVE_ORDER))
            .skip((long) safePage * safePageSize)
            .limit(safePageSize)
            .toList();
    }

    private void save() {
        saveSink.accept(statsByPlayerId);
    }
}
