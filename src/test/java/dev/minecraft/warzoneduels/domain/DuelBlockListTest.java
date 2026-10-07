package dev.minecraft.warzoneduels.domain;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DuelBlockListTest {
    private final UUID alex = UUID.randomUUID();
    private final UUID blair = UUID.randomUUID();
    private final UUID casey = UUID.randomUUID();
    private final UUID drew = UUID.randomUUID();

    @Test void blocksAreOneWayButAnyDirectionRefusesAPair() {
        var list = new DuelBlockList(Map.of());
        assertTrue(list.set(alex, blair, true));
        assertFalse(list.set(alex, blair, true), "Setting the same state again changes nothing");
        assertTrue(list.blocks(alex, blair));
        assertFalse(list.blocks(blair, alex));
        assertTrue(list.anyBlocked(List.of(blair), List.of(alex)), "The blocked player cannot challenge the blocker");
        assertTrue(list.anyBlocked(List.of(alex), List.of(blair)), "The blocker cannot challenge the blocked player either");
        assertTrue(list.set(alex, blair, false));
        assertFalse(list.anyBlocked(List.of(alex), List.of(blair)));
    }

    @Test void anyCrossTeamPairRefusesPartyRosters() {
        var list = new DuelBlockList(Map.of(drew, Set.of(alex)));
        assertTrue(list.anyBlocked(List.of(alex, blair), List.of(casey, drew)));
        assertFalse(list.anyBlocked(List.of(alex, drew), List.of(blair, casey)), "Teammates blocking each other is not a cross-team pair");
    }

    @Test void selfBlocksAreRefusedAndSnapshotsAreDefensive() {
        var list = new DuelBlockList(Map.of(alex, Set.of(casey, blair)));
        assertThrows(IllegalArgumentException.class, () -> list.set(alex, alex, true));
        assertEquals(List.of(blair, casey).stream().sorted().toList(), list.blockedBy(alex));
        var snapshot = list.snapshot();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.get(alex).add(drew));
        list.set(alex, blair, false);
        list.set(alex, casey, false);
        assertFalse(list.snapshot().containsKey(alex), "Owners with no blocks are dropped");
        assertTrue(new DuelBlockList(Map.of(alex, Set.of(alex))).blockedBy(alex).isEmpty(), "Loaded self-blocks are ignored");
    }
}
