package dev.minecraft.warzoneduels.domain.stats;

import java.util.UUID;

public final class PlayerDuelStats {
    private final UUID playerUuid;
    private String storedLastKnownName;
    private int matchesPlayedCount;
    private int winCount;
    private int lossCount;
    private int drawCount;
    private int disconnectForfeitLossCount;
    private int currentWinStreakCount;
    private int bestWinStreakCount;
    private int challengesSentCount;
    private int spoilsClaimsCount;
    private int mutualDrawsCount;
    private int customRulesWinsCount;
    private int restrictedMobilityWinsCount;
    private int lowHealthWinsCount;

    public PlayerDuelStats(UUID playerId, String lastKnownName) {
        this.playerUuid = playerId;
        this.storedLastKnownName = lastKnownName;
    }

    public UUID playerId() {
        return playerUuid;
    }

    public String lastKnownName() {
        return storedLastKnownName;
    }

    public void setLastKnownName(String lastKnownName) {
        this.storedLastKnownName = lastKnownName;
    }

    public int matchesPlayed() {
        return matchesPlayedCount;
    }

    public void setMatchesPlayed(int matchesPlayed) {
        this.matchesPlayedCount = Math.max(0, matchesPlayed);
    }

    public int wins() {
        return winCount;
    }

    public void setWins(int wins) {
        this.winCount = Math.max(0, wins);
    }

    public int losses() {
        return lossCount;
    }

    public void setLosses(int losses) {
        this.lossCount = Math.max(0, losses);
    }

    public int draws() {
        return drawCount;
    }

    public void setDraws(int draws) {
        this.drawCount = Math.max(0, draws);
    }

    public int disconnectForfeitLosses() {
        return disconnectForfeitLossCount;
    }

    public void setDisconnectForfeitLosses(int disconnectForfeitLosses) {
        this.disconnectForfeitLossCount = Math.max(0, disconnectForfeitLosses);
    }

    public int currentWinStreak() {
        return currentWinStreakCount;
    }

    public void setCurrentWinStreak(int currentWinStreak) {
        this.currentWinStreakCount = Math.max(0, currentWinStreak);
    }

    public int bestWinStreak() {
        return bestWinStreakCount;
    }

    public void setBestWinStreak(int bestWinStreak) {
        this.bestWinStreakCount = Math.max(0, bestWinStreak);
    }

    public int challengesSent() { return challengesSentCount; }
    public void setChallengesSent(int value) { challengesSentCount = Math.max(0, value); }
    public void recordChallengeSent() { challengesSentCount++; }

    public int spoilsClaims() { return spoilsClaimsCount; }
    public void setSpoilsClaims(int value) { spoilsClaimsCount = Math.max(0, value); }
    public void recordSpoilsClaim() { spoilsClaimsCount++; }

    public int mutualDraws() { return mutualDrawsCount; }
    public void setMutualDraws(int value) { mutualDrawsCount = Math.max(0, value); }
    public void recordMutualDraw() { mutualDrawsCount++; }

    public int customRulesWins() { return customRulesWinsCount; }
    public void setCustomRulesWins(int value) { customRulesWinsCount = Math.max(0, value); }
    public void recordCustomRulesWin() { customRulesWinsCount++; }

    public int restrictedMobilityWins() { return restrictedMobilityWinsCount; }
    public void setRestrictedMobilityWins(int value) { restrictedMobilityWinsCount = Math.max(0, value); }
    public void recordRestrictedMobilityWin() { restrictedMobilityWinsCount++; }

    public int lowHealthWins() { return lowHealthWinsCount; }
    public void setLowHealthWins(int value) { lowHealthWinsCount = Math.max(0, value); }
    public void recordLowHealthWin() { lowHealthWinsCount++; }

    public void recordWin() {
        matchesPlayedCount++;
        winCount++;
        currentWinStreakCount++;
        if (currentWinStreakCount > bestWinStreakCount) {
            bestWinStreakCount = currentWinStreakCount;
        }
    }

    public void recordLoss(boolean disconnectForfeit) {
        matchesPlayedCount++;
        lossCount++;
        currentWinStreakCount = 0;
        if (disconnectForfeit) {
            disconnectForfeitLossCount++;
        }
    }

    public void recordDraw() {
        matchesPlayedCount++;
        drawCount++;
        currentWinStreakCount = 0;
    }
}
