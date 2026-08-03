from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one match in {path}: found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


pom = Path("pom.xml")
replace_once(
    pom,
    "    <version>1.0-SNAPSHOT</version>\n    <packaging>jar</packaging>",
    "    <version>1.0.1</version>\n    <packaging>jar</packaging>",
)
replace_once(pom, "        <plan.api.version>5.6.2965</plan.api.version>", "        <plan.api.version>5.6-R0.1</plan.api.version>")
replace_once(pom, "        <sirblobman.core.version>2.9-20251215.173206-57</sirblobman.core.version>\n", "")
replace_once(
    pom,
    """        <repository>
            <id>papermc</id>
            <url>https://repo.papermc.io/repository/maven-public/</url>
        </repository>
        <repository>
            <id>jitpack</id>
            <url>https://jitpack.io</url>
        </repository>
        <repository>
            <id>sirblobman-public</id>
            <url>https://nexus.sirblobman.xyz/public/</url>
        </repository>
""",
    """        <repository>
            <id>papermc</id>
            <url>https://repo.papermc.io/repository/maven-public/</url>
        </repository>
        <repository>
            <id>plan-releases</id>
            <url>https://repo.playeranalytics.net/releases</url>
        </repository>
""",
)
for dependency in (
    """        <dependency>
            <groupId>com.github.MilkBowl</groupId>
            <artifactId>VaultAPI</artifactId>
            <version>1.7.1</version>
            <scope>provided</scope>
        </dependency>
""",
    """        <dependency>
            <groupId>com.github.sirblobman.api</groupId>
            <artifactId>core</artifactId>
            <version>${sirblobman.core.version}</version>
            <scope>provided</scope>
        </dependency>
""",
    """        <dependency>
            <groupId>com.github.sirblobman.combatlogx</groupId>
            <artifactId>api</artifactId>
            <version>11.6-SNAPSHOT</version>
            <scope>provided</scope>
        </dependency>
""",
):
    replace_once(pom, dependency, "")
replace_once(
    pom,
    """        <dependency>
            <groupId>com.github.plan-player-analytics</groupId>
            <artifactId>Plan</artifactId>
            <version>${plan.api.version}</version>
            <scope>provided</scope>
        </dependency>
""",
    """        <dependency>
            <groupId>com.djrapitops</groupId>
            <artifactId>plan-api</artifactId>
            <version>${plan.api.version}</version>
            <scope>provided</scope>
        </dependency>
""",
)

replace_once(Path("src/main/resources/plugin.yml"), "version: 1.0.0", "version: 1.0.1")

Path("src/main/java/dev/minecraft/warzoneduels/adapter/economy/VaultEconomyPort.java").write_text(
    r'''package dev.minecraft.warzoneduels.adapter.economy;

import dev.minecraft.warzoneduels.port.EconomyPort;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

/** Optional Vault bridge that avoids hard binary linkage to VaultAPI. */
public final class VaultEconomyPort implements EconomyPort {
    private final Object economy;
    private final boolean wagersEnabled;
    private final Logger logger;
    private final Method hasMethod;
    private final Method withdrawMethod;
    private final Method depositMethod;
    private final Method transactionSuccessMethod;
    private boolean failureLogged;

    public VaultEconomyPort(Object economy, boolean wagersEnabled, Logger logger) {
        this.wagersEnabled = wagersEnabled;
        this.logger = logger;

        Object resolvedEconomy = economy;
        Method resolvedHas = null;
        Method resolvedWithdraw = null;
        Method resolvedDeposit = null;
        Method resolvedTransactionSuccess = null;
        if (resolvedEconomy != null) {
            try {
                Class<?> type = resolvedEconomy.getClass();
                resolvedHas = type.getMethod("has", OfflinePlayer.class, double.class);
                resolvedWithdraw = type.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
                resolvedDeposit = type.getMethod("depositPlayer", OfflinePlayer.class, double.class);
                resolvedTransactionSuccess = resolvedWithdraw.getReturnType().getMethod("transactionSuccess");
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
                logFailure("Vault economy API is incompatible; wagers are disabled.", ex);
                resolvedEconomy = null;
            }
        }
        this.economy = resolvedEconomy;
        this.hasMethod = resolvedHas;
        this.withdrawMethod = resolvedWithdraw;
        this.depositMethod = resolvedDeposit;
        this.transactionSuccessMethod = resolvedTransactionSuccess;
    }

    @Override
    public boolean isEnabled() {
        return wagersEnabled
            && economy != null
            && hasMethod != null
            && withdrawMethod != null
            && depositMethod != null
            && transactionSuccessMethod != null;
    }

    @Override
    public boolean has(Player player, double amount) {
        if (!isEnabled() || player == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(hasMethod.invoke(economy, player, amount));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            logFailure("Vault balance check failed; wagers are unavailable.", ex);
            return false;
        }
    }

    @Override
    public boolean withdraw(Player player, double amount) {
        if (!isEnabled() || player == null) {
            return false;
        }
        try {
            Object response = withdrawMethod.invoke(economy, player, amount);
            return response != null && Boolean.TRUE.equals(transactionSuccessMethod.invoke(response));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            logFailure("Vault withdrawal failed; the duel wager was not accepted.", ex);
            return false;
        }
    }

    @Override
    public void deposit(Player player, double amount) {
        if (player != null) {
            deposit((OfflinePlayer) player, amount);
        }
    }

    @Override
    public void deposit(UUID playerId, double amount) {
        if (playerId != null) {
            deposit(Bukkit.getOfflinePlayer(playerId), amount);
        }
    }

    private void deposit(OfflinePlayer player, double amount) {
        if (!isEnabled() || player == null) {
            return;
        }
        try {
            depositMethod.invoke(economy, player, amount);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            logFailure("Vault deposit failed; check the economy provider before enabling wagers.", ex);
        }
    }

    private void logFailure(String message, Throwable throwable) {
        if (failureLogged || logger == null) {
            return;
        }
        failureLogged = true;
        String detail = throwable.getMessage();
        logger.warning(message + (detail == null || detail.isBlank() ? "" : " " + detail));
    }
}
''',
    encoding="utf-8",
)

Path("src/main/java/dev/minecraft/warzoneduels/adapter/bukkit/integration/CombatLogXCombatTagPort.java").write_text(
    r'''package dev.minecraft.warzoneduels.adapter.bukkit.integration;

import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import dev.minecraft.warzoneduels.app.DuelService;
import dev.minecraft.warzoneduels.port.CombatTagPort;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/** Optional CombatLogX bridge that avoids hard binary linkage to its API and BlueSlimeCore. */
public final class CombatLogXCombatTagPort implements CombatTagPort, Listener {
    private static final String PRE_TAG_EVENT = "com.github.sirblobman.combatlogx.api.event.PlayerPreTagEvent";
    private static final String UNTAG_REASON = "com.github.sirblobman.combatlogx.api.object.UntagReason";

    private final WarzoneDuelsPlugin plugin;
    private final DuelService duelService;

    private Object combatManager;
    private Object expireReason;
    private Method isInCombatMethod;
    private Method untagMethod;
    private Method eventPlayerMethod;
    private boolean registered;
    private boolean failureLogged;

    public CombatLogXCombatTagPort(WarzoneDuelsPlugin plugin, DuelService duelService) {
        this.plugin = plugin;
        this.duelService = duelService;
    }

    @Override
    public void enable() {
        Plugin other = Bukkit.getPluginManager().getPlugin("CombatLogX");
        if (other == null || !other.isEnabled()) {
            return;
        }
        try {
            ClassLoader loader = other.getClass().getClassLoader();
            Object resolvedManager = other.getClass().getMethod("getCombatManager").invoke(other);
            if (resolvedManager == null) {
                return;
            }

            Class<?> untagReasonClass = Class.forName(UNTAG_REASON, true, loader);
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object resolvedExpireReason = Enum.valueOf((Class) untagReasonClass.asSubclass(Enum.class), "EXPIRE");
            Method resolvedIsInCombat = resolvedManager.getClass().getMethod("isInCombat", Player.class);
            Method resolvedUntag = resolvedManager.getClass().getMethod("untag", Player.class, untagReasonClass);

            Class<?> rawEventClass = Class.forName(PRE_TAG_EVENT, true, loader);
            if (!Event.class.isAssignableFrom(rawEventClass)) {
                throw new IllegalStateException("PlayerPreTagEvent is not a Bukkit event");
            }
            Method resolvedEventPlayer = rawEventClass.getMethod("getPlayer");

            combatManager = resolvedManager;
            expireReason = resolvedExpireReason;
            isInCombatMethod = resolvedIsInCombat;
            untagMethod = resolvedUntag;
            eventPlayerMethod = resolvedEventPlayer;

            if (!registered) {
                @SuppressWarnings("unchecked")
                Class<? extends Event> eventClass = (Class<? extends Event>) rawEventClass;
                Bukkit.getPluginManager().registerEvent(
                    eventClass,
                    this,
                    EventPriority.HIGHEST,
                    (listener, event) -> handlePreTagEvent(event),
                    plugin,
                    true
                );
                registered = true;
            }
            plugin.getLogger().info("Hooked WarzoneDuels into CombatLogX combat tagging.");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            clearBridgeState();
            logFailure("CombatLogX API is incompatible; duel combat-tag integration is disabled.", ex);
        }
    }

    @Override
    public void disable() {
        clearBridgeState();
        if (registered) {
            HandlerList.unregisterAll(this);
            registered = false;
        }
    }

    @Override
    public boolean isInCombat(Player player) {
        if (combatManager == null || isInCombatMethod == null || player == null || !player.isOnline()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(isInCombatMethod.invoke(combatManager, player));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            logFailure("CombatLogX combat lookup failed; treating the player as not tagged.", ex);
            return false;
        }
    }

    @Override
    public void clearCombatState(Player player) {
        if (combatManager == null || untagMethod == null || expireReason == null || player == null || !player.isOnline()) {
            return;
        }
        try {
            if (Boolean.TRUE.equals(isInCombatMethod.invoke(combatManager, player))) {
                untagMethod.invoke(combatManager, player, expireReason);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            logFailure("CombatLogX untag failed; duel startup will continue without clearing the external tag.", ex);
        }
    }

    private void handlePreTagEvent(Event event) {
        if (eventPlayerMethod == null || !(event instanceof Cancellable cancellable)) {
            return;
        }
        try {
            Object playerValue = eventPlayerMethod.invoke(event);
            if (playerValue instanceof Player player && duelService.isParticipantRestricted(player.getUniqueId())) {
                cancellable.setCancelled(true);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            logFailure("CombatLogX pre-tag interception failed.", ex);
        }
    }

    private void clearBridgeState() {
        combatManager = null;
        expireReason = null;
        isInCombatMethod = null;
        untagMethod = null;
        eventPlayerMethod = null;
    }

    private void logFailure(String message, Throwable throwable) {
        if (failureLogged) {
            return;
        }
        failureLogged = true;
        String detail = throwable.getMessage();
        plugin.getLogger().warning(message + (detail == null || detail.isBlank() ? "" : " " + detail));
    }
}
''',
    encoding="utf-8",
)

plugin_path = Path("src/main/java/dev/minecraft/warzoneduels/WarzoneDuelsPlugin.java")
plugin_source = plugin_path.read_text(encoding="utf-8")
plugin_source = plugin_source.replace("import net.milkbowl.vault.economy.Economy;\n", "")
plugin_source = plugin_source.replace("import org.bukkit.plugin.RegisteredServiceProvider;\n", "import org.bukkit.plugin.Plugin;\nimport org.bukkit.plugin.RegisteredServiceProvider;\n")
plugin_source = plugin_source.replace(
    'EconomyPort economyPort = new VaultEconomyPort(setupEconomy(), getConfig().getBoolean("economy.enable-wagers", true));',
    'EconomyPort economyPort = new VaultEconomyPort(setupEconomy(), getConfig().getBoolean("economy.enable-wagers", true), getLogger());',
)
plugin_source = plugin_source.replace(
    """            } catch (NoClassDefFoundError ignored) {
                getLogger().info("Plan is not installed; duel analytics integration disabled.");
            }
""",
    """            } catch (LinkageError | RuntimeException ex) {
                getLogger().warning("Plan integration could not start; WarzoneDuels will continue without it: " + ex.getMessage());
            }
""",
)
old_setup = """    private Economy setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return null;
        }
        RegisteredServiceProvider<Economy> provider = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (provider == null) {
            return null;
        }
        return provider.getProvider();
    }
"""
new_setup = """    private Object setupEconomy() {
        Plugin vault = Bukkit.getPluginManager().getPlugin("Vault");
        if (vault == null || !vault.isEnabled()) {
            return null;
        }
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy", true, vault.getClass().getClassLoader());
            @SuppressWarnings({"rawtypes", "unchecked"})
            RegisteredServiceProvider<?> provider = Bukkit.getServicesManager().getRegistration((Class) economyClass);
            return provider == null ? null : provider.getProvider();
        } catch (ClassNotFoundException | LinkageError | RuntimeException ex) {
            getLogger().warning("Vault economy API is unavailable; duel wagers are disabled: " + ex.getMessage());
            return null;
        }
    }
"""
if old_setup not in plugin_source:
    raise SystemExit("expected Vault setup method was not found")
plugin_source = plugin_source.replace(old_setup, new_setup, 1)
plugin_path.write_text(plugin_source, encoding="utf-8")

Path("src/main/java/dev/minecraft/warzoneduels/adapter/plan/PlanHook.java").write_text(
    r'''package dev.minecraft.warzoneduels.adapter.plan;

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
''',
    encoding="utf-8",
)

resolver_path = Path("src/main/java/dev/minecraft/warzoneduels/adapter/plan/WarzoneDuelsPlanResolver.java")
resolver_source = resolver_path.read_text(encoding="utf-8")
resolver_source = resolver_source.replace("import com.djrapitops.plan.capability.CapabilityService;\n", "")
old_access = """    @Override
    public boolean canAccess(Request request) {
        WebUser user = request.getUser().orElse(new WebUser(""));
        if (CapabilityService.getInstance().hasCapability("PAGE_EXTENSION_USER_PERMISSIONS")) {
            return user.hasPermission("page.server");
        }
        return user.hasPermission("page.server");
    }
"""
new_access = """    @Override
    public boolean canAccess(Request request) {
        WebUser user = request.getUser().orElse(new WebUser(""));
        return user.hasPermission("page.server");
    }
"""
if old_access not in resolver_source:
    raise SystemExit("expected Plan resolver access method was not found")
resolver_path.write_text(resolver_source.replace(old_access, new_access, 1), encoding="utf-8")

plan_test = Path("src/test/java/dev/minecraft/warzoneduels/adapter/plan/PlanApiBinaryCompatibilityTest.java")
plan_test.parent.mkdir(parents=True, exist_ok=True)
plan_test.write_text(
    r'''package dev.minecraft.warzoneduels.adapter.plan;

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
''',
    encoding="utf-8",
)

isolation_test = Path("src/test/java/dev/minecraft/warzoneduels/integration/OptionalIntegrationBinaryIsolationTest.java")
isolation_test.parent.mkdir(parents=True, exist_ok=True)
isolation_test.write_text(
    r'''package dev.minecraft.warzoneduels.integration;

import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import dev.minecraft.warzoneduels.adapter.bukkit.integration.CombatLogXCombatTagPort;
import dev.minecraft.warzoneduels.adapter.economy.VaultEconomyPort;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OptionalIntegrationBinaryIsolationTest {
    @Test
    void optionalVaultAndCombatLogXApisAreNotHardLinked() throws IOException {
        assertNoClassConstant(WarzoneDuelsPlugin.class, "net/milkbowl/vault");
        assertNoClassConstant(VaultEconomyPort.class, "net/milkbowl/vault");
        assertNoClassConstant(CombatLogXCombatTagPort.class, "com/github/sirblobman/combatlogx");
    }

    private void assertNoClassConstant(Class<?> type, String forbiddenInternalName) throws IOException {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream input = type.getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            String classBytes = new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
            assertFalse(classBytes.contains(forbiddenInternalName), type.getName() + " hard-links " + forbiddenInternalName);
        }
    }
}
''',
    encoding="utf-8",
)
