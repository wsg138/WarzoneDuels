package dev.minecraft.warzoneduels.domain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** UUID-based history: party identity, leadership and challenger direction are irrelevant. */
public final class DuelCooldownPolicy {
    public record Snapshot(Map<UUID, Long> players, Map<String, Long> opponents, Map<String, Long> challenges) {
        public Snapshot {
            players = Map.copyOf(players);
            opponents = Map.copyOf(opponents);
            challenges = Map.copyOf(challenges);
        }
        public static Snapshot empty() { return new Snapshot(Map.of(), Map.of(), Map.of()); }
    }
    public record Block(UUID player, UUID opponent, long remainingMillis) {
        public long remainingSeconds() { return remainingMillis / 1000 + (remainingMillis % 1000 == 0 ? 0 : 1); }
    }
    private final Map<UUID, Long> players;
    private final Map<String, Long> opponents;
    private final Map<String, Long> challenges;

    public DuelCooldownPolicy(Snapshot snapshot) {
        players = new HashMap<>(snapshot.players());
        opponents = new HashMap<>(snapshot.opponents());
        challenges = new HashMap<>(snapshot.challenges());
    }

    public Block block(List<UUID> first, List<UUID> second, long now, long playerWindow, long pairWindow) {
        Block longest = null;
        for (List<UUID> team : List.of(first, second)) {
            for (UUID player : team) {
                long remaining = remaining(players.get(player), now, playerWindow);
                if (remaining > 0 && (longest == null || remaining > longest.remainingMillis())) {
                    longest = new Block(player, null, remaining);
                }
            }
        }
        for (UUID player : first) {
            for (UUID opponent : second) {
                long remaining = remaining(opponents.get(pairKey(player, opponent)), now, pairWindow);
                if (remaining > 0 && (longest == null || remaining > longest.remainingMillis())) {
                    longest = new Block(player, opponent, remaining);
                }
            }
        }
        return longest;
    }

    public void recordCompletion(List<UUID> first, List<UUID> second, long now) {
        for (List<UUID> team : List.of(first, second)) {
            for (UUID player : team) players.merge(player, now, Math::max);
        }
        for (UUID player : first) {
            for (UUID opponent : second) opponents.merge(pairKey(player, opponent), now, Math::max);
        }
    }

    public boolean creditChallenge(List<UUID> first, List<UUID> second, long now, long window) {
        if (window <= 0) return true;
        for (UUID player : first) {
            for (UUID opponent : second) {
                if (remaining(challenges.get(pairKey(player, opponent)), now, window) > 0) return false;
            }
        }
        for (UUID player : first) {
            for (UUID opponent : second) challenges.merge(pairKey(player, opponent), now, Math::max);
        }
        return true;
    }

    public Snapshot snapshot() { return new Snapshot(players, opponents, challenges); }

    /** Disabled windows retain history; future timestamps remain protected. */
    public void prune(long now, long playerWindow, long pairWindow) {
        if (playerWindow > 0) players.values().removeIf(last -> remaining(last, now, playerWindow) == 0);
        if (pairWindow > 0) {
            opponents.values().removeIf(last -> remaining(last, now, pairWindow) == 0);
            challenges.values().removeIf(last -> remaining(last, now, pairWindow) == 0);
        }
    }

    public static String pairKey(UUID first, UUID second) {
        if (first.equals(second)) throw new IllegalArgumentException("Opponents must differ");
        String a = first.toString(), b = second.toString();
        return a.compareTo(b) < 0 ? a + "_" + b : b + "_" + a;
    }

    public static long windowMillis(long seconds) {
        return seconds <= 0 ? 0 : seconds > Long.MAX_VALUE / 1000 ? Long.MAX_VALUE : seconds * 1000;
    }

    private static long remaining(Long last, long now, long window) {
        if (last == null || window <= 0) return 0;
        // A backwards wall-clock adjustment must not release a still-protected player.
        long elapsed = now >= last ? now - last : 0;
        return elapsed >= window ? 0 : window - elapsed;
    }
}
