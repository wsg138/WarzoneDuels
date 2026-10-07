package dev.minecraft.warzoneduels.app;

import dev.minecraft.warzoneduels.domain.DuelBlockList;
import dev.minecraft.warzoneduels.port.DuelBlockStore;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Personal duel blocks are saved before they are confirmed. An unreadable block file refuses new duels until it is
 * read again (enable on reload); a failed save only fails that change and keeps the previous list in effect.
 */
public final class DuelBlockService {
    public enum Change { CHANGED, UNCHANGED, FAILED }

    private final DuelBlockStore store;
    private final Consumer<String> warning;
    private DuelBlockList blocks = new DuelBlockList(Map.of());
    private boolean healthy;

    public DuelBlockService(DuelBlockStore store, Consumer<String> warning) {
        this.store = store;
        this.warning = warning;
    }

    public void enable() {
        healthy = false;
        try {
            blocks = new DuelBlockList(store.load());
            healthy = true;
        } catch (IOException ex) {
            fail(ex);
        }
    }

    public boolean isHealthy() {
        return healthy;
    }

    /** False when either side blocks the other, or when blocks cannot be trusted. */
    public boolean allows(Collection<UUID> first, Collection<UUID> second) {
        return healthy && !blocks.anyBlocked(first, second);
    }

    public boolean isBlocked(UUID owner, UUID target) {
        return blocks.blocks(owner, target);
    }

    public Change set(UUID owner, UUID target, boolean blocked) {
        if (!healthy) {
            return Change.FAILED;
        }
        if (!blocks.set(owner, target, blocked)) {
            return Change.UNCHANGED;
        }
        try {
            store.save(blocks.snapshot());
            return Change.CHANGED;
        } catch (IOException ex) {
            blocks.set(owner, target, !blocked);
            warning.accept("Could not save a duel block change; the previous block list stays in effect: " + ex.getMessage());
            return Change.FAILED;
        }
    }

    public List<UUID> blockedBy(UUID owner) {
        return blocks.blockedBy(owner);
    }

    private void fail(IOException ex) {
        healthy = false;
        warning.accept("Duel block storage unavailable; new challenges and party invitations are refused: " + ex.getMessage());
    }
}
