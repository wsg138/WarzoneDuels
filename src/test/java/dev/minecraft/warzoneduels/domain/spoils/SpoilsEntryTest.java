package dev.minecraft.warzoneduels.domain.spoils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

final class SpoilsEntryTest {
    private static final UUID ENTRY = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID OWNER = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final UUID SOURCE = UUID.fromString("99999999-8888-7777-6666-555555555555");

    @Test
    void constructorFiltersNullEntriesWithoutRequiringPaperBootstrap() {
        SpoilsEntry entry = entry(Arrays.asList(null, null));

        assertTrue(entry.isEmpty());
        assertEquals(0, entry.itemCount());
    }

    @Test
    void publicItemsReturnsAnIndependentList() {
        SpoilsEntry entry = entry(List.of());

        List<ItemStack> returned = entry.items();
        returned.add(null);

        assertEquals(1, returned.size());
        assertTrue(entry.isEmpty());
        assertEquals(0, entry.itemCount());
    }

    @Test
    void mutableItemsIsTheIntentionalInternalMutationSurface() {
        SpoilsEntry entry = entry(List.of());

        entry.mutableItems().add(null);

        assertFalse(entry.isEmpty());
        assertEquals(1, entry.itemCount());
        assertEquals(1, entry.items().size());
        assertNull(entry.items().getFirst());

        entry.mutableItems().clear();
        assertTrue(entry.isEmpty());
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
