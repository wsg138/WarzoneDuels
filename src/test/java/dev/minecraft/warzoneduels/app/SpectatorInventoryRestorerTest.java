package dev.minecraft.warzoneduels.app;

import dev.minecraft.warzoneduels.domain.SpectatorSession;
import dev.minecraft.warzoneduels.domain.SpectatorSessionPhase;
import dev.minecraft.warzoneduels.domain.StoredLocation;
import org.bukkit.GameMode;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpectatorInventoryRestorerTest {
    @Test
    void repeatedRestorationReplacesRatherThanAppendsInventory() {
        RecordingTarget target = new RecordingTarget();
        SpectatorSession session = session();

        SpectatorInventoryRestorer.replace(target, session);
        SpectatorInventoryRestorer.replace(target, session);

        assertEquals(0, target.storage.length);
        assertEquals(4, target.armor.length);
        assertEquals(2, target.replacementCount);
    }

    private SpectatorSession session() {
        return new SpectatorSession(
            UUID.randomUUID(), "P2wn", 1L, SpectatorSessionPhase.RESTORING, GameMode.SURVIVAL,
            new StoredLocation(UUID.randomUUID(), "world", 0D, 64D, 0D, 0F, 0F), false, false,
            0.1F, 0.2F, true, true, new ItemStack[0], new ItemStack[4], null, null,
            20D, 20, 5F, 0, 0, 0F, 0, List.of(), 0F, new Vector()
        );
    }

    private static final class RecordingTarget implements SpectatorInventoryRestorer.InventoryTarget {
        private ItemStack[] storage = new ItemStack[0];
        private ItemStack[] armor = new ItemStack[0];
        private int replacementCount;

        @Override
        public void setStorage(ItemStack[] contents) {
            storage = contents;
            replacementCount++;
        }

        @Override
        public void setArmor(ItemStack[] armorContents) {
            armor = armorContents;
        }

        @Override
        public void setOffhand(ItemStack offhand) {
        }

        @Override
        public void setCursor(ItemStack cursor) {
        }
    }
}
