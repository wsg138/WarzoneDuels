package dev.minecraft.warzoneduels.domain.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class PlayerDuelStatsTest {
    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void settersClampCountersAtZeroButPreserveIdentity() {
        PlayerDuelStats stats = new PlayerDuelStats(PLAYER, "Alice");

        stats.setMatchesPlayed(-1);
        stats.setWins(-2);
        stats.setLosses(-3);
        stats.setDraws(-4);
        stats.setDisconnectForfeitLosses(-5);
        stats.setCurrentWinStreak(-6);
        stats.setBestWinStreak(-7);

        assertEquals(PLAYER, stats.playerId());
        assertEquals("Alice", stats.lastKnownName());
        assertEquals(0, stats.matchesPlayed());
        assertEquals(0, stats.wins());
        assertEquals(0, stats.losses());
        assertEquals(0, stats.draws());
        assertEquals(0, stats.disconnectForfeitLosses());
        assertEquals(0, stats.currentWinStreak());
        assertEquals(0, stats.bestWinStreak());
    }

    @Test
    void winsIncreaseMatchesAndBothCurrentAndBestStreaks() {
        PlayerDuelStats stats = new PlayerDuelStats(PLAYER, "Alice");

        stats.recordWin();
        stats.recordWin();
        stats.recordWin();

        assertEquals(3, stats.matchesPlayed());
        assertEquals(3, stats.wins());
        assertEquals(3, stats.currentWinStreak());
        assertEquals(3, stats.bestWinStreak());
    }

    @Test
    void lossResetsCurrentStreakWithoutLoweringBestStreak() {
        PlayerDuelStats stats = new PlayerDuelStats(PLAYER, "Alice");
        stats.recordWin();
        stats.recordWin();

        stats.recordLoss(false);

        assertEquals(3, stats.matchesPlayed());
        assertEquals(2, stats.wins());
        assertEquals(1, stats.losses());
        assertEquals(0, stats.currentWinStreak());
        assertEquals(2, stats.bestWinStreak());
        assertEquals(0, stats.disconnectForfeitLosses());
    }

    @Test
    void disconnectForfeitIsTrackedSeparatelyFromOrdinaryLosses() {
        PlayerDuelStats stats = new PlayerDuelStats(PLAYER, "Alice");

        stats.recordLoss(true);
        stats.recordLoss(false);

        assertEquals(2, stats.matchesPlayed());
        assertEquals(2, stats.losses());
        assertEquals(1, stats.disconnectForfeitLosses());
    }

    @Test
    void drawCountsMatchAndResetsCurrentWinStreak() {
        PlayerDuelStats stats = new PlayerDuelStats(PLAYER, "Alice");
        stats.recordWin();
        stats.recordWin();

        stats.recordDraw();

        assertEquals(3, stats.matchesPlayed());
        assertEquals(1, stats.draws());
        assertEquals(0, stats.currentWinStreak());
        assertEquals(2, stats.bestWinStreak());
    }

    @Test
    void lastKnownNameCanBeRefreshedWithoutChangingCounters() {
        PlayerDuelStats stats = new PlayerDuelStats(PLAYER, "Alice");
        stats.recordWin();

        stats.setLastKnownName("AliceTwo");

        assertEquals("AliceTwo", stats.lastKnownName());
        assertEquals(1, stats.matchesPlayed());
        assertEquals(1, stats.wins());
    }
}
