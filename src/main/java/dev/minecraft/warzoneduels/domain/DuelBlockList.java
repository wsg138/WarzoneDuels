package dev.minecraft.warzoneduels.domain;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Personal duel blocks: each owner's set of players they will not duel or party with. Not thread-safe. */
public final class DuelBlockList {
    private final Map<UUID, Set<UUID>> blocks = new HashMap<>();

    public DuelBlockList(Map<UUID, Set<UUID>> loaded) {
        Objects.requireNonNull(loaded, "loaded").forEach((owner, targets) -> targets.stream()
            .filter(target -> target != null && !target.equals(owner))
            .forEach(target -> blocks.computeIfAbsent(owner, ignored -> new HashSet<>()).add(target)));
    }

    public boolean blocks(UUID owner, UUID target) {
        Set<UUID> targets = blocks.get(owner);
        return targets != null && targets.contains(target);
    }

    /** True when any player on one side blocks, or is blocked by, any player on the other side. */
    public boolean anyBlocked(Collection<UUID> first, Collection<UUID> second) {
        for (UUID a : first) {
            for (UUID b : second) {
                if (blocks(a, b) || blocks(b, a)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Returns whether the stored state changed. */
    public boolean set(UUID owner, UUID target, boolean blocked) {
        if (Objects.requireNonNull(owner, "owner").equals(Objects.requireNonNull(target, "target"))) {
            throw new IllegalArgumentException("You cannot block yourself.");
        }
        if (blocked) {
            return blocks.computeIfAbsent(owner, ignored -> new HashSet<>()).add(target);
        }
        Set<UUID> targets = blocks.get(owner);
        boolean changed = targets != null && targets.remove(target);
        if (targets != null && targets.isEmpty()) {
            blocks.remove(owner);
        }
        return changed;
    }

    public List<UUID> blockedBy(UUID owner) {
        return blocks.getOrDefault(owner, Set.of()).stream().sorted().toList();
    }

    public Map<UUID, Set<UUID>> snapshot() {
        Map<UUID, Set<UUID>> copy = new HashMap<>();
        blocks.forEach((owner, targets) -> copy.put(owner, Set.copyOf(targets)));
        return Map.copyOf(copy);
    }
}
