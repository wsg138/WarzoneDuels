package dev.minecraft.warzoneduels;

import dev.minecraft.warzoneduels.adapter.bukkit.command.DuelCommand;
import dev.minecraft.warzoneduels.adapter.bukkit.command.StatsCommand;
import dev.minecraft.warzoneduels.adapter.bukkit.gui.DuelGuiListener;
import dev.minecraft.warzoneduels.adapter.bukkit.integration.CombatLogXCombatTagPort;
import dev.minecraft.warzoneduels.adapter.bukkit.integration.NoOpCombatTagPort;
import dev.minecraft.warzoneduels.adapter.bukkit.listener.DuelListener;
import dev.minecraft.warzoneduels.adapter.bukkit.persistence.ArenaFootprintStore;
import dev.minecraft.warzoneduels.adapter.bukkit.persistence.ArenaMapSnapshotStore;
import dev.minecraft.warzoneduels.adapter.bukkit.persistence.DuelAnalyticsStore;
import dev.minecraft.warzoneduels.adapter.bukkit.persistence.LoadoutArchiveStore;
import dev.minecraft.warzoneduels.adapter.bukkit.persistence.PlayerStatsStore;
import dev.minecraft.warzoneduels.adapter.bukkit.persistence.RuntimeStateStore;
import dev.minecraft.warzoneduels.adapter.bukkit.persistence.SpoilsStore;
import dev.minecraft.warzoneduels.adapter.bukkit.persistence.SpectatorSessionStore;
import dev.minecraft.warzoneduels.adapter.bukkit.reset.ArenaResetService;
import dev.minecraft.warzoneduels.adapter.bukkit.spoils.SpoilsCommand;
import dev.minecraft.warzoneduels.adapter.bukkit.spoils.SpoilsGuiListener;
import dev.minecraft.warzoneduels.adapter.bukkit.stats.PlayerHeadCache;
import dev.minecraft.warzoneduels.adapter.bukkit.stats.PlayerHeadCacheListener;
import dev.minecraft.warzoneduels.adapter.bukkit.stats.StatsGuiListener;
import dev.minecraft.warzoneduels.adapter.plan.PlanHook;
import dev.minecraft.warzoneduels.adapter.bukkit.spawn.EnthusiaSpawnPort;
import dev.minecraft.warzoneduels.adapter.economy.VaultEconomyPort;
import dev.minecraft.warzoneduels.app.ArenaMapService;
import dev.minecraft.warzoneduels.app.ArenaTerrainService;
import dev.minecraft.warzoneduels.app.DuelAnalyticsService;
import dev.minecraft.warzoneduels.app.DuelService;
import dev.minecraft.warzoneduels.app.SpoilsService;
import dev.minecraft.warzoneduels.app.StatsService;
import dev.minecraft.warzoneduels.port.EconomyPort;
import dev.minecraft.warzoneduels.port.SpawnPort;
import dev.minecraft.warzoneduels.port.CombatTagPort;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class WarzoneDuelsPlugin extends JavaPlugin {
    private DuelService activeDuelService;
    private SpoilsService activeSpoilsService;
    private ArenaTerrainService activeArenaTerrainService;
    private CombatTagPort combatTagPort;
    private StatsService statsService;
    private DuelAnalyticsService analyticsService;
    private PlayerHeadCache headCache;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        EconomyPort economyPort = new VaultEconomyPort(setupEconomy(), getConfig().getBoolean("economy.enable-wagers", true), getLogger());
        SpawnPort spawnPort = new EnthusiaSpawnPort(getLogger());
        RuntimeStateStore runtimeStateStore = new RuntimeStateStore(this);
        LoadoutArchiveStore loadoutArchiveStore = new LoadoutArchiveStore(this);
        SpoilsStore spoilsStore = new SpoilsStore(this);
        ArenaResetService arenaResetService = new ArenaResetService();
        ArenaMapService arenaMapService = new ArenaMapService();
        this.activeArenaTerrainService = new ArenaTerrainService(
            this,
            arenaMapService,
            new ArenaFootprintStore(this),
            new ArenaMapSnapshotStore(this)
        );
        activeArenaTerrainService.enable();
        this.activeSpoilsService = new SpoilsService(this, spoilsStore);
        activeSpoilsService.enable();
        this.statsService = new StatsService(new PlayerStatsStore(this));
        statsService.enable();
        this.analyticsService = new DuelAnalyticsService(this, new DuelAnalyticsStore(this));
        analyticsService.enable();
        this.headCache = new PlayerHeadCache(this);
        headCache.load();

        this.activeDuelService = new DuelService(
            this,
            economyPort,
            spawnPort,
            runtimeStateStore,
            loadoutArchiveStore,
            new SpectatorSessionStore(this),
            arenaResetService,
            activeSpoilsService,
            statsService,
            analyticsService,
            arenaMapService,
            activeArenaTerrainService,
            new NoOpCombatTagPort()
        );
        this.combatTagPort = new CombatLogXCombatTagPort(this, activeDuelService);
        activeDuelService.setCombatTagPort(combatTagPort);
        combatTagPort.enable();
        activeDuelService.enable();
        if (!activeDuelService.hasActiveDuel()) {
            activeArenaTerrainService.ensureDefaultSnapshotLoaded(message -> getLogger().info(message));
        }

        getServer().getPluginManager().registerEvents(new DuelListener(activeDuelService), this);
        getServer().getPluginManager().registerEvents(new DuelGuiListener(activeDuelService), this);
        getServer().getPluginManager().registerEvents(new SpoilsGuiListener(this, activeSpoilsService), this);
        getServer().getPluginManager().registerEvents(new StatsGuiListener(this, statsService, headCache), this);
        getServer().getPluginManager().registerEvents(new PlayerHeadCacheListener(headCache), this);

        if (getConfig().getBoolean("plan.enabled", true)) {
            try {
                new PlanHook(this, activeDuelService, statsService, analyticsService).hookIntoPlan();
            } catch (LinkageError | RuntimeException ex) {
                getLogger().warning("Plan integration could not start; WarzoneDuels will continue without it: " + ex.getMessage());
            }
        }

        PluginCommand duelCommand = getCommand("duel");
        if (duelCommand != null) {
            DuelCommand command = new DuelCommand(activeDuelService, activeSpoilsService);
            duelCommand.setExecutor(command);
            duelCommand.setTabCompleter(command);
        }
        PluginCommand surrenderCommand = getCommand("surrender");
        if (surrenderCommand != null) {
            DuelCommand command = new DuelCommand(activeDuelService, activeSpoilsService);
            surrenderCommand.setExecutor(command);
            surrenderCommand.setTabCompleter(command);
        }
        PluginCommand vaultCommand = getCommand("vault");
        if (vaultCommand != null) {
            SpoilsCommand command = new SpoilsCommand(this, activeSpoilsService);
            vaultCommand.setExecutor(command);
            vaultCommand.setTabCompleter(command);
        }
        PluginCommand statsCommand = getCommand("stats");
        if (statsCommand != null) {
            StatsCommand command = new StatsCommand(this, statsService, headCache);
            statsCommand.setExecutor(command);
            statsCommand.setTabCompleter(command);
        }
    }

    @Override
    public void onDisable() {
        if (activeDuelService != null) {
            activeDuelService.disable(isServerStopping());
        }
        if (activeSpoilsService != null) {
            activeSpoilsService.disable();
        }
        if (statsService != null) {
            statsService.disable();
        }
        if (analyticsService != null) {
            analyticsService.disable();
        }
        if (headCache != null) {
            headCache.save();
        }
        if (combatTagPort != null) {
            combatTagPort.disable();
        }
        if (activeArenaTerrainService != null) {
            activeArenaTerrainService.disable();
        }
    }

    public DuelService duelService() {
        return activeDuelService;
    }

    public SpoilsService spoilsService() {
        return activeSpoilsService;
    }

    public ArenaTerrainService arenaTerrainService() {
        return activeArenaTerrainService;
    }

    private Object setupEconomy() {
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

    private boolean isServerStopping() {
        try {
            return Bukkit.getServer().isStopping();
        } catch (NoSuchMethodError ignored) {
            return false;
        }
    }
}
