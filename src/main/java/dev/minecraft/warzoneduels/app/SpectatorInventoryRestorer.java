package dev.minecraft.warzoneduels.app;

import dev.minecraft.warzoneduels.domain.SpectatorSession;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class SpectatorInventoryRestorer {
    private SpectatorInventoryRestorer() {
    }

    public static void replace(Player player, SpectatorSession session) {
        replace(new InventoryTarget() {
            @Override
            public void setStorage(ItemStack[] contents) {
                player.getInventory().setStorageContents(contents);
            }

            @Override
            public void setArmor(ItemStack[] armor) {
                player.getInventory().setArmorContents(armor);
            }

            @Override
            public void setOffhand(ItemStack offhand) {
                player.getInventory().setItemInOffHand(offhand);
            }

            @Override
            public void setCursor(ItemStack cursor) {
                player.setItemOnCursor(cursor);
            }
        }, session);
    }

    static void replace(InventoryTarget target, SpectatorSession session) {
        target.setStorage(session.contents());
        target.setArmor(session.armor());
        target.setOffhand(session.offhand());
        target.setCursor(session.cursor());
    }

    interface InventoryTarget {
        void setStorage(ItemStack[] contents);
        void setArmor(ItemStack[] armor);
        void setOffhand(ItemStack offhand);
        void setCursor(ItemStack cursor);
    }
}
