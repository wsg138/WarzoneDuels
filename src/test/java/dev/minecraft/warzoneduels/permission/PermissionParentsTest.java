package dev.minecraft.warzoneduels.permission;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionParentsTest {
    @Test
    @SuppressWarnings("unchecked")
    void primaryParentsGrantExpectedChildrenWithTestingDefaults() {
        Map<String, Object> permissions = permissions();
        Map<String, Object> command = permission(permissions, "warzoneduels.command");
        Map<String, Object> spectate = permission(permissions, "warzoneduels.spectate");
        Map<String, Object> admin = permission(permissions, "warzoneduels.admin");
        Map<String, Object> arena = permission(permissions, "warzoneduels.admin.arena");
        Map<String, Object> map = permission(permissions, "warzoneduels.admin.map");
        Map<String, Object> bypass = permission(permissions, "warzoneduels.admin.bypass");

        assertFalse((Boolean) command.get("default"));
        assertFalse((Boolean) spectate.get("default"));
        assertEquals("op", admin.get("default"));
        assertTrue(children(command).get("warzoneduels.command.challenge"));
        assertTrue(children(command).get("warzoneduels.command.stats.others"));
        assertTrue(children(spectate).get("warzoneduels.spectate.use"));
        assertTrue(children(spectate).get("warzoneduels.spectate.leave"));
        assertTrue(children(admin).get("warzoneduels.command"));
        assertTrue(children(admin).get("warzoneduels.spectate"));
        assertTrue(children(admin).get("warzoneduels.admin.arena"));
        assertTrue(children(arena).get("warzoneduels.admin.arena.setspectator"));
        assertTrue(children(map).get("warzoneduels.admin.map.status"));
        assertTrue(children(bypass).get("warzoneduels.admin.bypass.combat"));
    }

    @Test
    void legacyAliasesGrantOnlyTheirReplacementPermissions() {
        Map<String, Object> permissions = permissions();
        assertTrue(children(permission(permissions, "warzoneduels.user")).get("warzoneduels.command"));
        assertTrue(children(permission(permissions, "warzoneduels.user")).get("warzoneduels.spectate"));
        Map<String, String> aliases = Map.ofEntries(
            Map.entry("warzoneduels.challenge", "warzoneduels.command.challenge"),
            Map.entry("warzoneduels.accept", "warzoneduels.command.accept"),
            Map.entry("warzoneduels.deny", "warzoneduels.command.deny"),
            Map.entry("warzoneduels.review", "warzoneduels.command.review"),
            Map.entry("warzoneduels.draw", "warzoneduels.command.draw"),
            Map.entry("warzoneduels.vault", "warzoneduels.command.vault"),
            Map.entry("warzoneduels.stats.self", "warzoneduels.command.stats"),
            Map.entry("warzoneduels.stats.others", "warzoneduels.command.stats.others"),
            Map.entry("warzoneduels.info", "warzoneduels.command.info"),
            Map.entry("warzoneduels.bypass.build", "warzoneduels.admin.bypass.build"),
            Map.entry("warzoneduels.bypass.arena-entry", "warzoneduels.admin.bypass.arena"),
            Map.entry("warzoneduels.bypass.combat-entry", "warzoneduels.admin.bypass.combat")
        );
        aliases.forEach((legacy, replacement) -> {
            Map<String, Object> alias = permission(permissions, legacy);
            assertFalse((Boolean) alias.get("default"));
            assertTrue(children(alias).get(replacement));
        });
        assertTrue(children(permission(permissions, "warzoneduels.bypass.enter")).get("warzoneduels.admin.bypass.arena"));
        assertTrue(children(permission(permissions, "warzoneduels.bypass.enter")).get("warzoneduels.admin.bypass.combat"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void noPermissionDeclaresItselfAsAChild() {
        for (Map.Entry<String, Object> entry : permissions().entrySet()) {
            Map<String, Boolean> children = children((Map<String, Object>) entry.getValue());
            assertFalse(children.containsKey(entry.getKey()), () -> entry.getKey() + " recursively grants itself");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> permissions() {
        var resource = getClass().getClassLoader().getResourceAsStream("plugin.yml");
        assertNotNull(resource);
        Map<String, Object> root = new Yaml().load(resource);
        return (Map<String, Object>) root.get("permissions");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> permission(Map<String, Object> permissions, String name) {
        Map<String, Object> permission = (Map<String, Object>) permissions.get(name);
        assertNotNull(permission, name);
        return permission;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Boolean> children(Map<String, Object> permission) {
        Object value = permission.get("children");
        return value == null ? Map.of() : (Map<String, Boolean>) value;
    }
}
