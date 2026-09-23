package dev.minecraft.warzoneduels.domain.terrain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ArenaFootprintTest {
    @Test
    void boundsAreInclusiveOnEveryAxis() {
        ArenaFootprint footprint = footprint(Set.of(pack(-2, 60, 4), pack(5, 80, 9)));

        assertTrue(footprint.withinBounds(-2, 60, 4));
        assertTrue(footprint.withinBounds(5, 80, 9));
        assertFalse(footprint.withinBounds(-3, 60, 4));
        assertFalse(footprint.withinBounds(5, 81, 9));
        assertFalse(footprint.withinBounds(5, 80, 10));
    }

    @Test
    void containsRequiresBothBoundingBoxAndPackedMembership() {
        ArenaFootprint footprint = footprint(Set.of(pack(-2, 60, 4), pack(1, 70, 7)));

        assertTrue(footprint.contains(-2, 60, 4));
        assertTrue(footprint.contains(1, 70, 7));
        assertFalse(footprint.contains(0, 70, 7));
        assertFalse(footprint.contains(99, 70, 7));
    }

    @Test
    void packingSupportsNegativeCoordinatesTheSameWayAsProduction() {
        ArenaFootprint footprint = new ArenaFootprint(
                "world",
                -100,
                -64,
                -100,
                100,
                319,
                100,
                List.of(),
                Set.of(pack(-25, -20, -30))
        );

        assertTrue(footprint.contains(-25, -20, -30));
        assertFalse(footprint.contains(-25, -19, -30));
    }

    @Test
    void emptyRecognizesNullOrEmptyOrderedBlockLists() {
        ArenaFootprint nullBlocks = new ArenaFootprint("world", 0, 0, 0, 0, 0, 0, null, Set.of());
        ArenaFootprint emptyBlocks = new ArenaFootprint("world", 0, 0, 0, 0, 0, 0, List.of(), Set.of());
        ArenaFootprint populated = new ArenaFootprint(
                "world", 0, 0, 0, 0, 0, 0,
                List.of(new FootprintBlock(0, 0, 0)),
                Set.of(pack(0, 0, 0))
        );

        assertTrue(nullBlocks.isEmpty());
        assertTrue(emptyBlocks.isEmpty());
        assertFalse(populated.isEmpty());
    }

    private static ArenaFootprint footprint(Set<Long> keys) {
        return new ArenaFootprint("world", -2, 60, 4, 5, 80, 9, List.of(), keys);
    }

    private static long pack(int x, int y, int z) {
        return (((long) (x & 0x3FFFFFF)) << 38)
                | (((long) (z & 0x3FFFFFF)) << 12)
                | (y & 0xFFF);
    }
}
