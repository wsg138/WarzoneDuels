package dev.minecraft.warzoneduels.app;

import org.enthusia.tags.api.TagVisibilityService;
import org.enthusia.teleport.api.CancelReason;
import org.enthusia.teleport.api.TeleportApi;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WatcherExternalIntegrationContractTest {
    @Test
    void ownedOptionalServicesExposeTheRequiredWatcherSafetyContracts() throws Exception {
        Method cancellation = TeleportApi.class.getMethod("cancelAllRequestsInvolving", UUID.class, CancelReason.class);
        assertEquals(int.class, cancellation.getReturnType());
        assertNotNull(CancelReason.valueOf("DUEL_SPECTATE"));
        assertNotNull(TagVisibilityService.class.getMethod("suppress", UUID.class, Object.class));
        assertNotNull(TagVisibilityService.class.getMethod("unsuppress", UUID.class, Object.class));
        assertNotNull(TagVisibilityService.class.getMethod("isSuppressed", UUID.class));
    }
}
