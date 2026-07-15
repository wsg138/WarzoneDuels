package dev.minecraft.warzoneduels.permission;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionPolicyTest {
    @Test
    void mapsEveryCommandAliasToItsSpecificPermission() {
        assertEquals(PermissionPolicy.CHALLENGE, PermissionPolicy.CHALLENGE);
        assertEquals(PermissionPolicy.ACCEPT, PermissionPolicy.permissionForSubcommand("accept"));
        assertEquals(PermissionPolicy.DENY, PermissionPolicy.permissionForSubcommand("deny"));
        assertEquals(PermissionPolicy.REVIEW, PermissionPolicy.permissionForSubcommand("review"));
        assertEquals(PermissionPolicy.SPECTATE, PermissionPolicy.permissionForSubcommand("watch"));
        assertEquals(PermissionPolicy.SPECTATE, PermissionPolicy.permissionForSubcommand("spectate"));
        assertEquals(PermissionPolicy.SPECTATE, PermissionPolicy.permissionForSubcommand("stands"));
        assertEquals(PermissionPolicy.SPECTATE_LEAVE, PermissionPolicy.permissionForSubcommand("leave"));
        assertEquals(PermissionPolicy.SPECTATE_LEAVE, PermissionPolicy.permissionForSubcommand("unwatch"));
        assertEquals(PermissionPolicy.DRAW, PermissionPolicy.permissionForSubcommand("draw"));
        assertEquals(PermissionPolicy.VAULT, PermissionPolicy.permissionForSubcommand("vault"));
        assertEquals(PermissionPolicy.ADMIN_RECOVER_WATCHER, PermissionPolicy.permissionForSubcommand("recoverwatcher"));
    }

    @Test
    void rootSuggestionsOnlyExposeGrantedPermissions() {
        Set<String> granted = Set.of(PermissionPolicy.CHALLENGE, PermissionPolicy.SPECTATE, PermissionPolicy.INFO);
        var suggestions = PermissionPolicy.visibleRootSuggestions(granted::contains, false);
        assertTrue(suggestions.contains("watch"));
        assertTrue(suggestions.contains("info"));
        assertFalse(suggestions.contains("accept"));
        assertFalse(suggestions.contains("reload"));
    }

    @Test
    void activeWatcherAlwaysReceivesSafetyExitCompletions() {
        var suggestions = PermissionPolicy.visibleRootSuggestions(permission -> false, true);
        assertTrue(suggestions.contains("leave"));
        assertTrue(suggestions.contains("unwatch"));
        assertTrue(suggestions.contains("watch"));
        assertFalse(suggestions.contains("spectate"));
    }
}
