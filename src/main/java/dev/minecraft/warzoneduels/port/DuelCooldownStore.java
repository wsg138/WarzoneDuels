package dev.minecraft.warzoneduels.port;

import dev.minecraft.warzoneduels.domain.DuelCooldownPolicy;
import java.io.IOException;

public interface DuelCooldownStore {
    DuelCooldownPolicy.Snapshot load() throws IOException;
    void save(DuelCooldownPolicy.Snapshot snapshot) throws IOException;
}
