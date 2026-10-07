package dev.minecraft.warzoneduels.app;

import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import dev.minecraft.warzoneduels.adapter.bukkit.persistence.DuelAnalyticsStore;
import dev.minecraft.warzoneduels.domain.ActiveDuel;
import dev.minecraft.warzoneduels.domain.DuelEndReason;
import dev.minecraft.warzoneduels.domain.DuelSettings;
import dev.minecraft.warzoneduels.domain.MatchParticipant;
import dev.minecraft.warzoneduels.domain.analytics.DuelRecord;
import dev.minecraft.warzoneduels.domain.analytics.DuelRecordParticipant;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

@SuppressWarnings({"PMD.DoNotUseThreads", "PMD.UseConcurrentHashMap"})
public final class DuelAnalyticsService {
    private static final String NO_DATA_LABEL = "None";
    private static final int TOP_LABEL_LIMIT = 1;
    private static final int RECENT_OPPONENT_SCAN_LIMIT = 250;
    private static final int PLAYER_LABEL_SCAN_LIMIT = 500;
    private static final int CLEANUP_MINUTES_TO_TICKS = 60 * 20;
    private static final int WRITER_SHUTDOWN_SECONDS = 5;

    private final WarzoneDuelsPlugin plugin;
    private final DuelAnalyticsStore store;
    private final ExecutorService writer;
    private BukkitTask cleanupTask;

    public DuelAnalyticsService(WarzoneDuelsPlugin plugin, DuelAnalyticsStore store) {
        this.plugin = plugin;
        this.store = store;
        this.writer = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "WarzoneDuels-DuelAnalytics");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void enable() {
        store.enable();
        long retentionDays = Math.max(1L, plugin.getConfig().getLong("analytics.retention-days", 365L));
        long cleanupMinutes = Math.max(5L, plugin.getConfig().getLong("analytics.cleanup-interval-minutes", 30L));
        long cleanupTicks = cleanupMinutes * CLEANUP_MINUTES_TO_TICKS;
        cleanupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long cutoff = System.currentTimeMillis() - Duration.ofDays(retentionDays).toMillis();
            store.cleanupOlderThan(cutoff);
        }, cleanupTicks, cleanupTicks);
    }

    public void disable() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        writer.shutdown();
        try {
            if (!writer.awaitTermination(WRITER_SHUTDOWN_SECONDS, TimeUnit.SECONDS)) {
                writer.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        } finally {
            store.disable();
        }
    }

    public void recordDuel(ActiveDuel duel, UUID winnerId, DuelEndReason reason) {
        if (duel == null) {
            return;
        }
        DuelRecord record = buildRecord(duel, winnerId, reason);
        try {
            writer.execute(() -> store.insert(record));
        } catch (RejectedExecutionException ex) {
            plugin.getLogger().warning("Skipped duel analytics record because the writer is shutting down.");
        }
    }

    public long countTotalDuels() {
        return store.countAll();
    }

    public long countDuelsSince(Duration duration) {
        return store.countSince(cutoff(duration));
    }

    public long countDuelsForPlayerSince(UUID playerId, Duration duration) {
        return store.countForPlayerSince(playerId, cutoff(duration));
    }

    public long countCancelledDuelsSince(Duration duration) {
        return store.countCancelledSince(cutoff(duration));
    }

    public List<DuelRecord> recentDuels(int limit) {
        return store.findRecent(limit);
    }

    public List<DuelRecord> recentDuelsForPlayer(UUID playerId, int limit) {
        return store.findRecentForPlayer(playerId, limit);
    }

    public List<Map.Entry<String, Long>> topMapsSince(Duration duration, int limit) {
        return store.topMapsSince(cutoff(duration), limit);
    }

    public List<Map.Entry<String, Long>> topRulesSince(Duration duration, int limit) {
        return store.topRulesSince(cutoff(duration), limit);
    }

    public List<Map.Entry<String, Long>> topEndReasonsSince(Duration duration, int limit) {
        return store.topEndReasonsSince(cutoff(duration), limit);
    }

    public String mostUsedMapSince(Duration duration) {
        List<Map.Entry<String, Long>> entries = topMapsSince(duration, TOP_LABEL_LIMIT);
        return entries.isEmpty() ? NO_DATA_LABEL : safeLabel(entries.get(0).getKey());
    }

    public String mostUsedRulesSince(Duration duration) {
        List<Map.Entry<String, Long>> entries = topRulesSince(duration, TOP_LABEL_LIMIT);
        return entries.isEmpty() ? NO_DATA_LABEL : safeLabel(entries.get(0).getKey());
    }

    public String mostPlayedMapForPlayer(UUID playerId) {
        return topLabelForPlayer(playerId, DuelRecord::mapName, TOP_LABEL_LIMIT, NO_DATA_LABEL);
    }

    public String mostPlayedRulesForPlayer(UUID playerId) {
        return topLabelForPlayer(playerId, DuelRecord::ruleset, TOP_LABEL_LIMIT, NO_DATA_LABEL);
    }

    public List<Map.Entry<String, Long>> recentOpponents(UUID playerId, int limit) {
        List<DuelRecord> records = recentDuelsForPlayer(playerId, RECENT_OPPONENT_SCAN_LIMIT);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (DuelRecord record : records) {
            String opponent = opponentName(record, playerId);
            if (opponent == null || opponent.isBlank()) {
                continue;
            }
            counts.merge(opponent, 1L, Long::sum);
        }
        return counts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER)))
            .limit(Math.max(1, limit))
            .toList();
    }

    private DuelRecord buildRecord(ActiveDuel duel, UUID winnerId, DuelEndReason reason) {
        MatchParticipant participantOne = duel.participantOne();
        MatchParticipant participantTwo = duel.participantTwo();
        DuelSettings settings = duel.settings();
        long endedAt = System.currentTimeMillis();
        long duration = Math.max(0L, endedAt - duel.startedAtEpochMs());
        DuelOutcome outcome = outcomeFor(duel, winnerId);
        List<DuelRecordParticipant> participants = new ArrayList<>();
        for (MatchParticipant participant : duel.teamOne().participants()) {
            participants.add(new DuelRecordParticipant(participant.playerId(), participant.name(), 1,
                winnerId != null && duel.teamOne().contains(winnerId)));
        }
        for (MatchParticipant participant : duel.teamTwo().participants()) {
            participants.add(new DuelRecordParticipant(participant.playerId(), participant.name(), 2,
                winnerId != null && duel.teamTwo().contains(winnerId)));
        }

        String reference = Long.toString(duel.startedAtEpochMs(), 36).toUpperCase(Locale.ROOT);
        return new DuelRecord(
            reference,
            duel.startedAtEpochMs(),
            endedAt,
            duration,
            participantOne.playerId(),
            participantOne.name(),
            participantTwo.playerId(),
            participantTwo.name(),
            winnerId,
            outcome.winnerName(),
            outcome.loserId(),
            outcome.loserName(),
            settings.getMapId(),
            settings.getMapDisplayName(),
            settings.formatBlockRules(),
            settings.formatExtendedItemRules(),
            reason,
            countsAsMatch(reason),
            0,
            settings.getWager(),
            duel.matchType(),
            duel.teamOne().size(),
            participants
        );
    }

    private DuelOutcome outcomeFor(ActiveDuel duel, UUID winnerId) {
        if (winnerId == null) {
            return DuelOutcome.empty();
        }
        MatchParticipant winner = duel.participant(winnerId);
        MatchParticipant loser = duel.opposingTeam(winnerId) == null
            ? null
            : duel.opposingTeam(winnerId).participants().get(0);
        return new DuelOutcome(
            winner == null ? null : winner.name(),
            loser == null ? null : loser.playerId(),
            loser == null ? null : loser.name()
        );
    }

    private boolean countsAsMatch(DuelEndReason reason) {
        return switch (reason) {
            case KILL, DRAW, DISCONNECT_TIMEOUT -> true;
            case SERVER_RESTART, PLUGIN_DISABLE, ADMIN_ABORT -> false;
            default -> false;
        };
    }

    private long cutoff(Duration duration) {
        return System.currentTimeMillis() - Math.max(1L, duration.toMillis());
    }

    private String opponentName(DuelRecord record, UUID playerId) {
        if (!record.participants().isEmpty()) {
            DuelRecordParticipant participant = record.participants().stream()
                .filter(candidate -> candidate.playerId().equals(playerId))
                .findFirst()
                .orElse(null);
            if (participant == null) {
                return null;
            }
            return record.participants().stream()
                .filter(candidate -> candidate.teamIndex() != participant.teamIndex())
                .map(DuelRecordParticipant::playerName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .findFirst()
                .orElse(null);
        }
        if (record.playerOneId().equals(playerId)) {
            return record.playerTwoName();
        }
        if (record.playerTwoId().equals(playerId)) {
            return record.playerOneName();
        }
        return null;
    }

    private String safeLabel(String value) {
        return value == null || value.isBlank() ? NO_DATA_LABEL : value;
    }

    private String topLabelForPlayer(UUID playerId, java.util.function.Function<DuelRecord, String> extractor, int limit, String fallback) {
        List<DuelRecord> records = recentDuelsForPlayer(playerId, PLAYER_LABEL_SCAN_LIMIT);
        if (records.isEmpty()) {
            return fallback;
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (DuelRecord record : records) {
            String key = extractor.apply(record);
            if (key == null || key.isBlank()) {
                continue;
            }
            counts.merge(key, 1L, Long::sum);
        }
        return counts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER)))
            .limit(limit)
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(fallback);
    }

    private record DuelOutcome(String winnerName, UUID loserId, String loserName) {
        private static DuelOutcome empty() {
            return new DuelOutcome(null, null, null);
        }
    }
}
