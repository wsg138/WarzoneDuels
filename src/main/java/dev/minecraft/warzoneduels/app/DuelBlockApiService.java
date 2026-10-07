package dev.minecraft.warzoneduels.app;

import dev.minecraft.warzoneduels.api.DuelBlockApi;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Exposes {@link DuelBlockService} to other plugins with the same rules as /duel block. */
public final class DuelBlockApiService implements DuelBlockApi {
    private final DuelBlockService blocks;
    private final BooleanSupplier mainThread;

    public DuelBlockApiService(DuelBlockService blocks, BooleanSupplier mainThread) {
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
    }

    @Override
    public boolean isBlocked(UUID owner, UUID target) {
        requireMainThread();
        return blocks.isBlocked(Objects.requireNonNull(owner), Objects.requireNonNull(target));
    }

    @Override
    public void setBlocked(UUID owner, UUID target, boolean blocked) {
        requireMainThread();
        if (blocks.set(owner, target, blocked) == DuelBlockService.Change.FAILED) {
            throw new IllegalStateException("Duel blocks could not be saved");
        }
    }

    private void requireMainThread() {
        if (!mainThread.getAsBoolean()) {
            throw new IllegalStateException("DuelBlockApi must be called on the main thread");
        }
    }
}
