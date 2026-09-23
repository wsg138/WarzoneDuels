package dev.minecraft.warzoneduels;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class FullFeatureCoverageContractTest {
    @Test
    void establishedFeatureFamiliesRetainConcreteRegressionEvidence() {
        Path root = repositoryRoot();
        coverage().forEach((feature, evidence) -> evidence.forEach(path -> assertTrue(
                Files.isRegularFile(root.resolve(path)),
                () -> feature + " lost required regression evidence: " + path
        )));
    }

    private static Map<String, List<String>> coverage() {
        Map<String, List<String>> coverage = new LinkedHashMap<>();
        coverage.put("duel rule configuration and formatting", List.of(
                "src/test/java/dev/minecraft/warzoneduels/domain/DuelSettingsTest.java"
        ));
        coverage.put("player duel statistics and streak accounting", List.of(
                "src/test/java/dev/minecraft/warzoneduels/domain/stats/PlayerDuelStatsTest.java"
        ));
        coverage.put("arena footprint bounds and packed membership", List.of(
                "src/test/java/dev/minecraft/warzoneduels/domain/terrain/ArenaFootprintTest.java"
        ));
        coverage.put("spoils metadata null filtering and list isolation", List.of(
                "src/test/java/dev/minecraft/warzoneduels/domain/spoils/SpoilsEntryTest.java"
        ));
        coverage.put("typed teleport allowances", List.of(
                "src/test/java/dev/minecraft/warzoneduels/domain/TypedTeleportAllowanceTest.java"
        ));
        coverage.put("spectator persistence and inventory restoration", List.of(
                "src/test/java/dev/minecraft/warzoneduels/adapter/bukkit/persistence/SpectatorSessionStoreTest.java",
                "src/test/java/dev/minecraft/warzoneduels/app/SpectatorInventoryRestorerTest.java"
        ));
        coverage.put("permission namespace and parent policy", List.of(
                "src/test/java/dev/minecraft/warzoneduels/permission/PermissionNamespaceTest.java",
                "src/test/java/dev/minecraft/warzoneduels/permission/PermissionParentsTest.java",
                "src/test/java/dev/minecraft/warzoneduels/permission/PermissionPolicyTest.java"
        ));
        coverage.put("optional integration binary isolation", List.of(
                "src/test/java/dev/minecraft/warzoneduels/integration/OptionalIntegrationBinaryIsolationTest.java",
                "src/test/java/dev/minecraft/warzoneduels/adapter/plan/PlanApiBinaryCompatibilityTest.java"
        ));
        return Map.copyOf(coverage);
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isRegularFile(current.resolve("pom.xml"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.isRegularFile(parent.resolve("pom.xml"))) {
            return parent;
        }
        throw new IllegalStateException("Could not locate WarzoneDuels repository root from " + current);
    }
}
