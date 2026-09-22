package dev.minecraft.warzoneduels.domain.spoils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

final class SpoilsEntryTest {
    private static final UUID ENTRY = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID OWNER = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final UUID SOURCE = UUID.fromString("99999999-8888-7777-6666-555555555555");

    @Test
    void constructorFiltersNullsAndClonesInputItems() {
        ItemStack diamonds = new ItemStack(Material.DIAMOND, 2);
        SpoilsEntry entry = entry(Arrays.asList(diamonds, null));

        diamonds.setAmount(40);

        assertEquals(1, entry.itemCount());
        assertFalse(entry.isEmpty());
        assertEquals(2, entry.items().getFirst().getAmount());
        assertNotSame(diamonds, entry.items().getFirst());
    }

    @Test
    void publicItemsReturnsFreshDefensiveCopiesOnEveryRead() {
        SpoilsEntry entry = entry(List.of(new ItemStack(Material.GOLDEN_APPLE, 3)));

        List<ItemStack> first = entry.items();
        first.getFirst().setAmount(1);
        first.clear();

        List<ItemStack> second = entry.items();
        assertEquals(1, second.size());
        assertEquals(3, second.getFirst().getAmount());
    }

    @Test
    void mutableItemsIsTheIntentionalInternalMutationSurface() {
        SpoilsEntry entry = entry(List.of(new ItemStack(Material.TOTEM_OF_UNDYING, 1)));

        entry.mutableItems().clear();

        assertTrue(entry.isEmpty());
        assertEquals(0, entry.itemCount());
    }

    @Test
    void metadataIsPreservedExactly() {
        SpoilsEntry entry = new SpoilsEntry(
                ENTRY,
                OWNER,
                "Winner",
                SOURCE,
                "Loser",
                1_000L,
                61_000L,
                List.of()
        );

        assertEquals(ENTRY, entry.entryId());
        assertEquals(OWNER, entry.ownerId());
        assertEquals("Winner", entry.ownerName());
        assertEquals(SOURCE, entry.sourcePlayerId());
        assertEquals("Loser", entry.sourcePlayerName());
        assertEquals(1_000L, entry.createdAtEpochMs());
        assertEquals(61_000L, entry.expiresAtEpochMs());
        assertTrue(entry.isEmpty());
    }

    private static SpoilsEntry entry(List<ItemStack> items) {
        return new SpoilsEntry(ENTRY, OWNER, "Winner", SOURCE, "Loser", 1_000L, 61_000L, items);
    }
}
