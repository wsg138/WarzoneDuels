package dev.minecraft.warzoneduels.domain;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DuelAdvancementPolicyTest {
    @Test void challengerCustomRulesRequiresNormalKillAndChallengerVictory() {
        UUID challenger = UUID.randomUUID();
        DuelSettings settings = new DuelSettings();
        assertFalse(DuelAdvancementPolicy.isCustomRulesChallengerWin(DuelMatchType.NORMAL, challenger, challenger, settings, DuelEndReason.KILL));
        settings.setAllowMaces(false);
        assertTrue(DuelAdvancementPolicy.isCustomRulesChallengerWin(DuelMatchType.NORMAL, challenger, challenger, settings, DuelEndReason.KILL));
        assertFalse(DuelAdvancementPolicy.isCustomRulesChallengerWin(DuelMatchType.NORMAL, challenger, UUID.randomUUID(), settings, DuelEndReason.KILL));
        assertFalse(DuelAdvancementPolicy.isCustomRulesChallengerWin(DuelMatchType.PARTY, challenger, challenger, settings, DuelEndReason.KILL));
        assertFalse(DuelAdvancementPolicy.isCustomRulesChallengerWin(DuelMatchType.NORMAL, challenger, challenger, settings, DuelEndReason.DISCONNECT_TIMEOUT));
    }

    @Test void restrictedMobilityRequiresCombatWin() {
        DuelSettings settings = new DuelSettings();
        settings.setAllowEnderPearls(false);
        settings.setAllowWindCharges(false);
        assertTrue(DuelAdvancementPolicy.isRestrictedMobilityWin(settings, DuelEndReason.KILL));
        assertFalse(DuelAdvancementPolicy.isRestrictedMobilityWin(settings, DuelEndReason.DISCONNECT_TIMEOUT));
    }

    @Test void lowHealthRequiresOneVersusOneKillBelowTwoHearts() {
        assertTrue(DuelAdvancementPolicy.isLowHealthWin(DuelMatchType.NORMAL, DuelEndReason.KILL, 3.99D));
        assertFalse(DuelAdvancementPolicy.isLowHealthWin(DuelMatchType.NORMAL, DuelEndReason.KILL, 4.0D));
        assertFalse(DuelAdvancementPolicy.isLowHealthWin(DuelMatchType.PARTY, DuelEndReason.KILL, 1.0D));
        assertFalse(DuelAdvancementPolicy.isLowHealthWin(DuelMatchType.NORMAL, DuelEndReason.DISCONNECT_TIMEOUT, 1.0D));
    }
}