package dev.minecraft.warzoneduels.adapter.plan;

import com.djrapitops.plan.capability.CapabilityService;
import com.djrapitops.plan.delivery.web.ResolverService;
import com.djrapitops.plan.extension.DataExtension;
import com.djrapitops.plan.extension.ExtensionService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanApiBinaryCompatibilityTest {
    @Test
    void publishedPlanServicesUseTheInterfaceApi() throws Exception {
        assertTrue(CapabilityService.class.isInterface());
        assertTrue(ExtensionService.class.isInterface());
        assertTrue(ResolverService.class.isInterface());
        assertTrue(Modifier.isStatic(CapabilityService.class.getMethod("getInstance").getModifiers()));
        assertEquals(Optional.class, ExtensionService.class.getMethod("register", DataExtension.class).getReturnType());
    }
}
