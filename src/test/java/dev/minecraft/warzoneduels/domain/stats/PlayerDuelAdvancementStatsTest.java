package dev.minecraft.warzoneduels.domain.stats;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class PlayerDuelAdvancementStatsTest {
 @Test void recordsDurableAdvancementEvidenceIndependently() {
  PlayerDuelStats s=new PlayerDuelStats(UUID.randomUUID(),"Fighter");
  s.recordChallengeSent(); s.recordSpoilsClaim(); s.recordMutualDraw(); s.recordCustomRulesWin(); s.recordRestrictedMobilityWin(); s.recordLowHealthWin();
  assertEquals(1,s.challengesSent()); assertEquals(1,s.spoilsClaims()); assertEquals(1,s.mutualDraws()); assertEquals(1,s.customRulesWins()); assertEquals(1,s.restrictedMobilityWins()); assertEquals(1,s.lowHealthWins());
  assertEquals(0,s.wins()); assertEquals(0,s.matchesPlayed());
 }
}