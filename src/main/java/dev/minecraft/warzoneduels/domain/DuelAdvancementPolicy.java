package dev.minecraft.warzoneduels.domain;

import java.util.UUID;

public final class DuelAdvancementPolicy {
    private static final double TWO_HEARTS_HEALTH = 4.0D;

    private DuelAdvancementPolicy() {
    }

    public static boolean isCustomRulesChallengerWin(
        DuelMatchType matchType,
        UUID challengerId,
        UUID winnerId,
        DuelSettings settings,
        DuelEndReason reason
    ) {
        return matchType == DuelMatchType.NORMAL
            && reason == DuelEndReason.KILL
            && challengerId != null
            && challengerId.equals(winnerId)
            && settings != null
            && settings.hasCustomAdvancementRules();
    }

    public static boolean isRestrictedMobilityWin(DuelSettings settings, DuelEndReason reason) {
        return reason == DuelEndReason.KILL
            && settings != null
            && settings.isRestrictedMobilityRuleset();
    }

    public static boolean isLowHealthWin(DuelMatchType matchType, DuelEndReason reason, double health) {
        return matchType == DuelMatchType.NORMAL
            && reason == DuelEndReason.KILL
            && health >= 0D
            && health < TWO_HEARTS_HEALTH;
    }
}