package dev.minecraft.warzoneduels.permission;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionParentsTest {
    @Test
    @SuppressWarnings("unchecked")
    void userAndAdminParentsGrantExpectedChildrenWithTestingDefaults() {
        var resource = getClass().getClassLoader().getResourceAsStream("plugin.yml");
        assertNotNull(resource);
        Map<String, Object> root = new Yaml().load(resource);
        Map<String, Object> permissions = (Map<String, Object>) root.get("permissions");
        Map<String, Object> user = (Map<String, Object>) permissions.get("warzoneduels.user");
        Map<String, Object> admin = (Map<String, Object>) permissions.get("warzoneduels.admin");
        Map<String, Object> legacy = (Map<String, Object>) permissions.get("warzoneduels.bypass.enter");
        Map<String, Boolean> userChildren = (Map<String, Boolean>) user.get("children");
        Map<String, Boolean> adminChildren = (Map<String, Boolean>) admin.get("children");
        Map<String, Boolean> legacyChildren = (Map<String, Boolean>) legacy.get("children");

        assertFalse((Boolean) user.get("default"));
        assertTrue(userChildren.get("warzoneduels.challenge"));
        assertTrue(userChildren.get("warzoneduels.spectate"));
        assertTrue(adminChildren.get("warzoneduels.user"));
        assertTrue(adminChildren.get("warzoneduels.admin.recoverwatcher"));
        assertTrue(legacyChildren.get("warzoneduels.bypass.arena-entry"));
        assertTrue(legacyChildren.get("warzoneduels.bypass.combat-entry"));
    }
}
