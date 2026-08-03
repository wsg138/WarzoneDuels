package dev.minecraft.warzoneduels.adapter.plan;

import com.djrapitops.plan.capability.CapabilityService;
import com.djrapitops.plan.delivery.web.ResolverService;
import com.djrapitops.plan.extension.ExtensionService;
import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import dev.minecraft.warzoneduels.app.DuelAnalyticsService;
import dev.minecraft.warzoneduels.app.DuelService;
import dev.minecraft.warzoneduels.app.StatsService;

public final class PlanHook {
    private final WarzoneDuelsPlugin plugin;
    private final DuelService duelService;
    private final StatsService statsService;
    private final DuelAnalyticsService analyticsService;

    public PlanHook(
        WarzoneDuelsPlugin plugin,
        DuelService duelService,
        StatsService statsService,
        DuelAnalyticsService analyticsService
    ) {
        this.plugin = plugin;
        this.duelService = duelService;
        this.statsService = statsService;
        this.analyticsService = analyticsService;
    }

    public void hookIntoPlan() {
        try {
            if (!areCapabilitiesAvailable()) {
                plugin.getLogger().warning("Plan does not expose the data and page extension APIs; integration is disabled.");
                return;
            }
            registerDataExtension();
            registerPageExtension();
            listenForPlanReloads();
        } catch (LinkageError | RuntimeException ex) {
            logCompatibilityFailure("initialize", ex);
        }
    }

    private boolean areCapabilitiesAvailable() {
        CapabilityService capabilities = CapabilityService.getInstance();
        return capabilities.hasCapability("DATA_EXTENSION_VALUES")
            && capabilities.hasCapability("PAGE_EXTENSION_RESOLVERS");
    }

    private void registerDataExtension() {
        try {
            ExtensionService.getInstance().register(new WarzoneDuelsDataExtension(statsService, analyticsService, duelService));
        } catch (IllegalStateException ex) {
            plugin.getLogger().warning("Plan is enabled but not ready for the duel data extension: " + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Plan rejected the duel data extension: " + ex.getMessage());
        } catch (LinkageError ex) {
            logCompatibilityFailure("register its data extension", ex);
        }
    }

    private void registerPageExtension() {
        try {
            ResolverService service = ResolverService.getInstance();
            if (service.getResolver("/warzone-duels").isEmpty()) {
                service.registerResolver("WarzoneDuels", "/warzone-duels", new WarzoneDuelsPlanResolver(plugin, statsService, analyticsService, duelService));
            }
        } catch (IllegalStateException ex) {
            plugin.getLogger().warning("Plan is enabled but not ready for the duel dashboard page: " + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Plan rejected the duel dashboard page: " + ex.getMessage());
        } catch (LinkageError ex) {
            logCompatibilityFailure("register its dashboard page", ex);
        }
    }

    private void listenForPlanReloads() {
        try {
            CapabilityService.getInstance().registerEnableListener(isEnabled -> {
                if (Boolean.TRUE.equals(isEnabled)) {
                    registerDataExtension();
                    registerPageExtension();
                }
            });
        } catch (LinkageError | RuntimeException ex) {
            logCompatibilityFailure("register its reload listener", ex);
        }
    }

    private void logCompatibilityFailure(String action, Throwable throwable) {
        String detail = throwable.getMessage();
        plugin.getLogger().warning(
            "WarzoneDuels could not " + action + " with the installed Plan API; the plugin will continue without Plan integration"
                + (detail == null || detail.isBlank() ? "." : ": " + detail)
        );
    }
}
