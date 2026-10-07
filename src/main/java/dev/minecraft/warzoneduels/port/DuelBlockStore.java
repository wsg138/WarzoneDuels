package dev.minecraft.warzoneduels.port;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Durable personal duel blocks: owner UUID to the UUIDs that owner blocks. */
public interface DuelBlockStore {
    Map<UUID, Set<UUID>> load() throws IOException;
    void save(Map<UUID, Set<UUID>> blocks) throws IOException;
}
