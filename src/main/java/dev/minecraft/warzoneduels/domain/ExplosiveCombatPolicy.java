package dev.minecraft.warzoneduels.domain;

import java.util.UUID;

public final class ExplosiveCombatPolicy {
    private ExplosiveCombatPolicy() {
    }

    public static boolean shouldCancelDamage(ActiveDuel duel, UUID victimId, UUID attributedAttackerId) {
        if (duel == null || victimId == null || !duel.contains(victimId)) {
            return false;
        }
        if (attributedAttackerId == null) {
            return duel.matchType() == DuelMatchType.PARTY;
        }
        if (!duel.contains(attributedAttackerId)) {
            return true;
        }
        if (attributedAttackerId.equals(victimId)) {
            return false;
        }
        return TeamMatchPolicy.isFriendlyFire(duel, attributedAttackerId, victimId);
    }
}
