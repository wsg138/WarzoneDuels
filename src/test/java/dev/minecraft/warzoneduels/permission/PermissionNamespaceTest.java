package dev.minecraft.warzoneduels.permission;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class PermissionNamespaceTest {
    @Test
    void applicationCodeDoesNotCheckDeprecatedPermissionNames() throws IOException {
        List<String> deprecated = List.of(
            "warzoneduels.user", "warzoneduels.challenge", "warzoneduels.accept", "warzoneduels.deny",
            "warzoneduels.review", "warzoneduels.draw", "warzoneduels.vault", "warzoneduels.stats.self",
            "warzoneduels.stats.others", "warzoneduels.info", "warzoneduels.bypass."
        );
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String source = Files.readString(path);
                for (String permission : deprecated) {
                    assertFalse(source.contains(permission), () -> path + " checks deprecated permission " + permission);
                }
            }
        }
    }
}
