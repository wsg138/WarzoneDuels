package dev.minecraft.warzoneduels.domain;

public final class DuelDurationPolicy {
    private static final long MILLIS_PER_SECOND = 1_000L;
    private static final long MILLIS_PER_TICK = 50L;

    private DuelDurationPolicy() {
    }

    public static Long deadlineEpochMs(long releasedAtEpochMs, int durationSeconds) {
        if (durationSeconds <= 0) {
            return null;
        }
        return Math.addExact(releasedAtEpochMs, Math.multiplyExact((long) durationSeconds, MILLIS_PER_SECOND));
    }

    public static long remainingTicks(long deadlineEpochMs, long nowEpochMs) {
        long remainingMillis = Math.max(0L, deadlineEpochMs - nowEpochMs);
        return remainingMillis == 0L ? 0L : (remainingMillis + MILLIS_PER_TICK - 1L) / MILLIS_PER_TICK;
    }
}
