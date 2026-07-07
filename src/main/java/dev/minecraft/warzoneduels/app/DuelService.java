package dev.minecraft.warzoneduels.app;

import dev.minecraft.warzoneduels.BlockKey;
import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import dev.minecraft.warzoneduels.adapter.bukkit.gui.DuelGui;
import dev.minecraft.warzoneduels.adapter.bukkit.persistence.LoadoutArchiveStore;
import dev.minecraft.warzoneduels.adapter.bukkit.persistence.RuntimeStateStore;
import dev.minecraft.warzoneduels.adapter.bukkit.reset.ArenaResetService;
import dev.minecraft.warzoneduels.domain.ActiveDuel;
import dev.minecraft.warzoneduels.domain.ArenaDefinition;
import dev.minecraft.warzoneduels.domain.BuilderSession;
import dev.minecraft.warzoneduels.domain.DuelEndReason;
import dev.minecraft.warzoneduels.domain.DuelMapOption;
import dev.minecraft.warzoneduels.domain.DuelRequest;
import dev.minecraft.warzoneduels.domain.DuelRuntimeState;
import dev.minecraft.warzoneduels.domain.DuelSettings;
import dev.minecraft.warzoneduels.domain.LoadoutSnapshot;
import dev.minecraft.warzoneduels.domain.MatchParticipant;
import dev.minecraft.warzoneduels.domain.stats.PlayerDuelStats;
import dev.minecraft.warzoneduels.domain.terrain.ArenaMapOperationStatus;
import dev.minecraft.warzoneduels.port.EconomyPort;
import dev.minecraft.warzoneduels.port.PermissionPort;
import dev.minecraft.warzoneduels.port.SpawnPort;
import dev.minecraft.warzoneduels.port.CombatTagPort;
import dev.minecraft.warzoneduels.util.SpearUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.Sound;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.net.InetAddress;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@SuppressWarnings({"PMD.AvoidFieldNameMatchingMethodName", "PMD.ExcessiveParameterList", "PMD.NullAssignment"})
public final class DuelService {
    private static final Set<String> BUILT_IN_ALLOWED_DUEL_COMMANDS = Set.of(
        "draw",
        "surrender",
        "duel draw",
        "duel surrender",
        "duel cancel",
        "duel info",
        "duel settings"
    );
    private static final String MSG_PLAYER_IN_COMBAT = "messages.player-in-combat";
    private static final String MSG_TARGET_IN_COMBAT = "messages.target-in-combat";
    private static final String MSG_MUST_BE_AT_SPAWN = "messages.must-be-at-spawn";
    private static final String MSG_TARGET_OFFLINE = "messages.target-offline";
    private static final String MSG_CANNOT_AFFORD = "messages.cannot-afford";
    private static final String MSG_NO_PENDING_REQUEST = "messages.no-pending-request";
    private static final String MSG_NO_PERMISSION = "messages.no-permission";
    private static final String PERMISSION_BYPASS_BUILD = "warzoneduels.bypass.build";
    private static final String PERMISSION_BYPASS_ENTER = "warzoneduels.bypass.enter";
    private static final String PERMISSION_DUEL_SEND = "warzoneduels.duel.send";
    private static final String PERMISSION_DUEL_ACCEPT = "warzoneduels.duel.accept";
    private static final String PERMISSION_DUEL_DENY = "warzoneduels.duel.deny";
    private static final String PERMISSION_DUEL_DRAW = "warzoneduels.duel.draw";
    private static final String PERMISSION_DUEL_SPECTATE = "warzoneduels.duel.spectate";
    private static final String PERMISSION_DUEL_VAULT = "warzoneduels.duel.vault";
    private static final String PLAYER_PLACEHOLDER = "{player}";
    private static final double NO_WAGER = 0D;
    private static final long QUEUED_START_PERIOD_TICKS = 20L;

    private final WarzoneDuelsPlugin plugin;
    private final EconomyPort economyPort;
    private final SpawnPort spawnPort;
    private final RuntimeStateStore runtimeStateStore;
    private final LoadoutArchiveStore loadoutArchiveStore;
    private final ArenaResetService arenaResetService;
    private final SpoilsService spoilsService;
    private final StatsService statsService;
    private final DuelAnalyticsService duelAnalyticsService;
    private final ArenaMapService arenaMapService;
    private final ArenaTerrainService arenaTerrainService;
    private final PermissionPort permissionPort;
    private CombatTagPort combatTagPort;

    private final Map<UUID, BuilderSession> builders = new ConcurrentHashMap<>();
    private final Set<UUID> oneTimeTeleportAllowance = ConcurrentHashMap.newKeySet();
    private final Set<UUID> allowedArenaItemEntityIds = ConcurrentHashMap.newKeySet();
    private final Map<BlockKey, Long> allowedArenaItemSpawnLocations = new ConcurrentHashMap<>();
    private final Set<UUID> respawnToSpawn = ConcurrentHashMap.newKeySet();
    private final Set<UUID> recoveryTeleportIds = ConcurrentHashMap.newKeySet();
    private final Set<UUID> activeParticipantIndex = ConcurrentHashMap.newKeySet();
    private final Map<UUID, LoadoutSnapshot> disconnectSnapshots = new ConcurrentHashMap<>();
    private final Set<UUID> pendingForcedDeathIds = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> blockedItemMessageCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> arenaExitMessageCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Material> trackedExplosionSources = new ConcurrentHashMap<>();
    private final Set<UUID> victoryFireworkIds = ConcurrentHashMap.newKeySet();
    private final Map<UUID, GameMode> watchedSpectators = new ConcurrentHashMap<>();

    private ArenaDefinition arena;
    private DuelRequest pendingRequest;
    private volatile ActiveDuel activeDuel;
    private ActiveDuel preparingDuel;
    private QueuedDuelStart queuedDuelStart;

    private String prefix;
    private int requestExpireSeconds;
    private int disconnectGraceSeconds;
    private double maxWager;
    private boolean allowSameIp;
    private boolean allowWaterDrain;
    private boolean clearPlacedBlocksWhenNoBreak;
    private int startCountdownSeconds;
    private int victoryMomentSeconds;
    private boolean victoryFireworks;
    private boolean spectatorReducedDebugInfo;
    private Boolean previousReducedDebugInfo;
    private String matchmakingWorld;
    private int matchmakingMinX;
    private int matchmakingMaxX;
    private int matchmakingMinY;
    private int matchmakingMaxY;
    private int matchmakingMinZ;
    private int matchmakingMaxZ;
    private boolean blockCombatEntry = true;
    private List<String> allowedDuelCommands = List.of();
    private List<DuelMapOption> mapOptions = List.of();

    private BukkitTask requestExpiryTask;
    private BukkitTask disconnectMonitorTask;
    private BukkitTask countdownTask;
    private BukkitTask queuedStartTask;
    private BukkitTask containmentTask;
    private BukkitTask victoryTask;
    private boolean duelCountdownActive;
    private boolean duelEnding;

    public DuelService(
        WarzoneDuelsPlugin plugin,
        EconomyPort economyPort,
        SpawnPort spawnPort,
        PermissionPort permissionPort,
        RuntimeStateStore runtimeStateStore,
        LoadoutArchiveStore loadoutArchiveStore,
        ArenaResetService arenaResetService,
        SpoilsService spoilsService,
        StatsService statsService,
        DuelAnalyticsService duelAnalyticsService,
        ArenaMapService arenaMapService,
        ArenaTerrainService arenaTerrainService,
        CombatTagPort combatTagPort
    ) {
        this.plugin = plugin;
        this.economyPort = economyPort;
        this.spawnPort = spawnPort;
        this.runtimeStateStore = runtimeStateStore;
        this.loadoutArchiveStore = loadoutArchiveStore;
        this.arenaResetService = arenaResetService;
        this.spoilsService = spoilsService;
        this.statsService = statsService;
        this.duelAnalyticsService = duelAnalyticsService;
        this.arenaMapService = arenaMapService;
        this.arenaTerrainService = arenaTerrainService;
        this.permissionPort = permissionPort;
        this.combatTagPort = combatTagPort;
    }

    public void setCombatTagPort(CombatTagPort combatTagPort) {
        this.combatTagPort = combatTagPort;
    }

    public void enable() {
        loadoutArchiveStore.enable();
        reloadConfig();
        recoveryTeleportIds.clear();
        recoveryTeleportIds.addAll(runtimeStateStore.loadRecoveryTeleportIds());
        recoverActiveDuelIfNeeded();
    }

    public void disable(boolean serverStopping) {
        cancelRequestExpiryTask();
        cancelDisconnectMonitorTask();
        cancelQueuedStartTask();
        cancelCountdownTask();
        cancelContainmentTask();
        cancelVictoryTask();

        if (preparingDuel != null) {
            refundWagerIfHeld(preparingDuel);
            preparingDuel = null;
        }

        if (duelEnding && activeDuel != null) {
            teleportOnlineParticipantsToExit(activeDuel);
            clearWatchedSpectators(true);
            activeDuel = null;
            duelEnding = false;
            runtimeStateStore.clearRuntime();
            runtimeStateStore.clearReloadResumeMarker();
            rebuildParticipantIndex();
            cleanupVolatileArenaState();
            loadoutArchiveStore.shutdown();
            return;
        }

        if (serverStopping) {
            handleServerStoppingDisable();
            clearWatchedSpectators(true);
            runtimeStateStore.clearReloadResumeMarker();
            runtimeStateStore.clearRuntime();
            builders.clear();
            pendingRequest = null;
            activeDuel = null;
            queuedDuelStart = null;
            rebuildParticipantIndex();
            loadoutArchiveStore.shutdown();
            return;
        }

        clearWatchedSpectators(true);
        if (activeDuel != null) {
            runtimeStateStore.saveActiveDuelSync(activeDuel);
            runtimeStateStore.markReloadResume();
        } else {
            runtimeStateStore.clearRuntime();
            runtimeStateStore.clearReloadResumeMarker();
        }
        loadoutArchiveStore.shutdown();
    }

    public void reloadConfig() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        prefix = color(config.getString("messages.prefix", "&6[Duel]&r "));
        requestExpireSeconds = Math.max(5, config.getInt("settings.request-expire-seconds", 120));
        disconnectGraceSeconds = Math.max(5, config.getInt("settings.disconnect-grace-seconds", 30));
        maxWager = Math.max(0D, config.getDouble("settings.max-wager", 100000D));
        allowSameIp = config.getBoolean("settings.allow-same-ip-duels", false);
        blockCombatEntry = config.getBoolean("settings.block-combat-entry", true);
        allowWaterDrain = config.getBoolean("settings.allow-water-drain", true);
        clearPlacedBlocksWhenNoBreak = config.getBoolean("settings.clear-placed-blocks-when-no-break", true);
        startCountdownSeconds = Math.max(0, config.getInt("settings.start-countdown-seconds", 5));
        victoryMomentSeconds = Math.max(0, config.getInt("settings.victory-moment-seconds", 6));
        victoryFireworks = config.getBoolean("settings.victory-fireworks", true);
        spectatorReducedDebugInfo = config.getBoolean("settings.spectator-reduced-debug-info", true);
        matchmakingWorld = config.getString("matchmaking-spawn.world", config.getString("arena.world", "world"));
        matchmakingMinX = Math.min(config.getInt("matchmaking-spawn.corner1.x", -218), config.getInt("matchmaking-spawn.corner2.x", 219));
        matchmakingMaxX = Math.max(config.getInt("matchmaking-spawn.corner1.x", -218), config.getInt("matchmaking-spawn.corner2.x", 219));
        matchmakingMinY = Math.min(config.getInt("matchmaking-spawn.min-y", -64), config.getInt("matchmaking-spawn.max-y", 320));
        matchmakingMaxY = Math.max(config.getInt("matchmaking-spawn.min-y", -64), config.getInt("matchmaking-spawn.max-y", 320));
        matchmakingMinZ = Math.min(config.getInt("matchmaking-spawn.corner1.z", -404), config.getInt("matchmaking-spawn.corner2.z", 188));
        matchmakingMaxZ = Math.max(config.getInt("matchmaking-spawn.corner1.z", -404), config.getInt("matchmaking-spawn.corner2.z", 188));
        allowedDuelCommands = config.getStringList("settings.allowed-duel-commands").stream()
            .map(value -> value.toLowerCase(Locale.ROOT))
            .toList();
        arenaMapService.reload(config);
        arenaTerrainService.reload(config);
        arenaMapService.promoteSavedMaps(arenaTerrainService::hasSnapshot);
        mapOptions = arenaMapService.options();
        arena = loadArena(config);
    }

    public DuelRuntimeState runtimeState() {
        if (activeDuel != null) {
            return DuelRuntimeState.ACTIVE;
        }
        if (pendingRequest != null) {
            return DuelRuntimeState.REQUEST_PENDING;
        }
        return DuelRuntimeState.IDLE;
    }

    public ArenaDefinition arena() {
        return arena;
    }

    public boolean isArenaReady() {
        return arena != null && arena.isReady();
    }

    public BuilderSession getBuilder(UUID playerId) {
        return builders.get(playerId);
    }

    public void clearBuilder(UUID playerId) {
        builders.remove(playerId);
    }

    public double maxWager() {
        return maxWager;
    }

    public WarzoneDuelsPlugin plugin() {
        return plugin;
    }

    public List<DuelMapOption> mapOptions() {
        return mapOptions;
    }

    public boolean hasActiveDuel() {
        return activeDuel != null;
    }

    public DuelRequest pendingRequest() {
        return pendingRequest;
    }

    public boolean isInActiveDuel(UUID playerId) {
        return activeDuel != null && activeDuel.contains(playerId);
    }

    public boolean isParticipantRestricted(UUID playerId) {
        return activeParticipantIndex.contains(playerId);
    }

    public boolean isTeleportAllowed(UUID playerId) {
        return oneTimeTeleportAllowance.remove(playerId);
    }

    public void allowTeleportOnce(UUID playerId) {
        oneTimeTeleportAllowance.add(playerId);
    }

    public void startBuilder(Player sender, Player target) {
        requirePrimaryThread();
        if (!permissionPort.has(sender, PERMISSION_DUEL_SEND)) {
            sendMessage(sender, MSG_NO_PERMISSION);
            return;
        }
        if (rejectBuilderStart(sender, target)) {
            return;
        }
        builders.put(sender.getUniqueId(), new BuilderSession(target.getUniqueId(), new DuelSettings()));
    }

    private boolean rejectBuilderStart(Player sender, Player target) {
        return rejectUnavailableBuilder(sender)
            || rejectInvalidBuilderPlayers(sender, target)
            || rejectCombatTaggedBuilder(sender, target)
            || rejectBusyBuilderPlayers(sender, target);
    }

    private boolean rejectUnavailableBuilder(Player sender) {
        if (!isArenaReady()) {
            sendMessage(sender, "messages.no-arena");
            return true;
        }
        if (activeDuel != null || pendingRequest != null || queuedDuelStart != null) {
            sendMessage(sender, "messages.duel-already-running");
            return true;
        }
        return false;
    }

    private boolean rejectInvalidBuilderPlayers(Player sender, Player target) {
        if (target.getUniqueId().equals(sender.getUniqueId())) {
            sendMessage(sender, "messages.self-duel");
            return true;
        }
        if (!isInsideMatchmakingSpawn(sender.getLocation()) || !isInsideMatchmakingSpawn(target.getLocation())) {
            sendMessage(sender, MSG_MUST_BE_AT_SPAWN);
            return true;
        }
        return false;
    }

    private boolean rejectCombatTaggedBuilder(Player sender, Player target) {
        if (isCombatTagged(sender)) {
            sendMessage(sender, MSG_PLAYER_IN_COMBAT);
            return true;
        }
        if (isCombatTagged(target)) {
            sendMessage(sender, MSG_TARGET_IN_COMBAT, PLAYER_PLACEHOLDER, target.getName());
            return true;
        }
        return false;
    }

    private boolean rejectBusyBuilderPlayers(Player sender, Player target) {
        if (isParticipantRestricted(sender.getUniqueId()) || isParticipantRestricted(target.getUniqueId())) {
            sendMessage(sender, "messages.target-busy");
            return true;
        }
        return false;
    }

    public void sendRequest(Player requester) {
        requirePrimaryThread();
        BuilderSession builder = builders.get(requester.getUniqueId());
        if (builder == null) {
            sendMessage(requester, "messages.no-builder");
            return;
        }
        if (pendingRequest != null || activeDuel != null || queuedDuelStart != null) {
            sendMessage(requester, "messages.duel-already-running");
            return;
        }
        Player target = Bukkit.getPlayer(builder.targetId());
        if (target == null || !target.isOnline()) {
            builders.remove(requester.getUniqueId());
            sendMessage(requester, MSG_TARGET_OFFLINE);
            return;
        }
        if (rejectRequestPlayers(requester, target)) {
            return;
        }
        DuelSettings settings = builder.settings().copy();
        if (settings.getWager() > maxWager) {
            settings.setWager(maxWager);
        }
        if (rejectRequestWager(requester, target, settings)) {
            return;
        }
        pendingRequest = new DuelRequest(
            requester.getUniqueId(),
            target.getUniqueId(),
            requester.getName(),
            target.getName(),
            settings,
            System.currentTimeMillis()
        );
        builders.remove(requester.getUniqueId());
        sendMessage(requester, "messages.request-sent", PLAYER_PLACEHOLDER, target.getName());
        sendRequestDetails(target, pendingRequest);
        scheduleRequestExpiry();
    }

    private boolean rejectRequestPlayers(Player requester, Player target) {
        if (isCombatTagged(requester)) {
            sendMessage(requester, MSG_PLAYER_IN_COMBAT);
            return true;
        }
        if (isCombatTagged(target)) {
            sendMessage(requester, MSG_TARGET_IN_COMBAT, PLAYER_PLACEHOLDER, target.getName());
            return true;
        }
        if (!isInsideMatchmakingSpawn(requester.getLocation()) || !isInsideMatchmakingSpawn(target.getLocation())) {
            sendMessage(requester, MSG_MUST_BE_AT_SPAWN);
            return true;
        }
        if (!allowSameIp && sameIp(requester, target)) {
            sendMessage(requester, "messages.same-ip-blocked");
            return true;
        }
        return false;
    }

    private boolean rejectRequestWager(Player requester, Player target, DuelSettings settings) {
        if (settings.getWager() <= 0D) {
            return false;
        }
        if (!economyPort.isEnabled()) {
            sendMessage(requester, "messages.wager-disabled");
            return true;
        }
        if (!economyPort.has(requester, settings.getWager())) {
            sendMessage(requester, MSG_CANNOT_AFFORD, PLAYER_PLACEHOLDER, requester.getName());
            return true;
        }
        if (!economyPort.has(target, settings.getWager())) {
            sendMessage(requester, MSG_CANNOT_AFFORD, PLAYER_PLACEHOLDER, target.getName());
            return true;
        }
        return false;
    }

    private boolean isCombatTagged(Player player) {
        return combatTagPort != null && combatTagPort.isInCombat(player);
    }

    public void acceptRequest(Player target) {
        requirePrimaryThread();
        if (!permissionPort.has(target, PERMISSION_DUEL_ACCEPT)) {
            sendMessage(target, MSG_NO_PERMISSION);
            return;
        }
        if (pendingRequest == null || !pendingRequest.targetId().equals(target.getUniqueId())) {
            sendMessage(target, MSG_NO_PENDING_REQUEST);
            return;
        }
        openPendingRequestReview(target);
    }

    public void confirmAcceptRequest(Player target) {
        requirePrimaryThread();
        Player requester = acceptRequester(target);
        if (requester == null) {
            return;
        }
        DuelSettings settings = pendingRequest.settings();
        if (rejectAcceptedRequest(requester, target, settings)) {
            return;
        }
        clearPendingRequest();
        if (arenaTerrainService.isBusy()) {
            queueDuelStart(requester, target, settings);
            return;
        }
        startDuel(requester, target, settings);
    }

    private Player acceptRequester(Player target) {
        if (pendingRequest == null || !pendingRequest.targetId().equals(target.getUniqueId())) {
            sendMessage(target, MSG_NO_PENDING_REQUEST);
            return null;
        }
        Player requester = Bukkit.getPlayer(pendingRequest.requesterId());
        if (requester != null && requester.isOnline()) {
            return requester;
        }
        clearPendingRequest();
        sendMessage(target, MSG_TARGET_OFFLINE);
        return null;
    }

    private boolean rejectAcceptedRequest(Player requester, Player target, DuelSettings settings) {
        return rejectAcceptedCombatState(requester, target)
            || rejectAcceptedLocation(requester, target)
            || rejectAcceptedWager(requester, target, settings);
    }

    private boolean rejectAcceptedCombatState(Player requester, Player target) {
        if (isCombatTagged(requester)) {
            clearPendingRequest();
            sendMessage(requester, MSG_PLAYER_IN_COMBAT);
            sendMessage(target, MSG_TARGET_IN_COMBAT, PLAYER_PLACEHOLDER, requester.getName());
            return true;
        }
        if (!isCombatTagged(target)) {
            return false;
        }
        clearPendingRequest();
        sendMessage(target, MSG_PLAYER_IN_COMBAT);
        sendMessage(requester, MSG_TARGET_IN_COMBAT, PLAYER_PLACEHOLDER, target.getName());
        return true;
    }

    private boolean rejectAcceptedLocation(Player requester, Player target) {
        if (isInsideMatchmakingSpawn(requester.getLocation()) && isInsideMatchmakingSpawn(target.getLocation())) {
            return false;
        }
        clearPendingRequest();
        sendMessage(requester, MSG_MUST_BE_AT_SPAWN);
        sendMessage(target, MSG_MUST_BE_AT_SPAWN);
        return true;
    }

    private boolean rejectAcceptedWager(Player requester, Player target, DuelSettings settings) {
        if (settings.getWager() > NO_WAGER) {
            if (!economyPort.isEnabled()) {
                clearPendingRequest();
                sendMessage(target, "messages.wager-disabled");
                return true;
            }
            if (!economyPort.has(requester, settings.getWager())) {
                clearPendingRequest();
                sendMessage(target, MSG_CANNOT_AFFORD, PLAYER_PLACEHOLDER, requester.getName());
                return true;
            }
            if (!economyPort.has(target, settings.getWager())) {
                clearPendingRequest();
                sendMessage(target, MSG_CANNOT_AFFORD, PLAYER_PLACEHOLDER, target.getName());
                return true;
            }
        }
        return false;
    }

    public void openPendingRequestReview(Player target) {
        requirePrimaryThread();
        if (pendingRequest == null || !pendingRequest.targetId().equals(target.getUniqueId())) {
            sendMessage(target, MSG_NO_PENDING_REQUEST);
            return;
        }
        target.openInventory(DuelGui.buildRequestPreviewGui(pendingRequest.requesterName(), pendingRequest.settings()));
    }

    public void denyRequest(Player target) {
        requirePrimaryThread();
        if (!permissionPort.has(target, PERMISSION_DUEL_DENY)) {
            sendMessage(target, MSG_NO_PERMISSION);
            return;
        }
        if (pendingRequest == null || !pendingRequest.targetId().equals(target.getUniqueId())) {
            sendMessage(target, MSG_NO_PENDING_REQUEST);
            return;
        }
        Player requester = Bukkit.getPlayer(pendingRequest.requesterId());
        clearPendingRequest();
        if (requester != null) {
            sendMessage(requester, "messages.request-denied");
        }
    }

    public void requestDraw(Player player) {
        requirePrimaryThread();
        if (!permissionPort.has(player, PERMISSION_DUEL_DRAW)) {
            sendMessage(player, MSG_NO_PERMISSION);
            return;
        }
        if (queuedDuelStart != null && queuedDuelStart.involves(player.getUniqueId())) {
            Player requester = Bukkit.getPlayer(queuedDuelStart.requesterId());
            Player target = Bukkit.getPlayer(queuedDuelStart.targetId());
            clearQueuedDuelStart();
            if (requester != null) {
                sendMessageRaw(requester, prefix + ChatColor.YELLOW + "The queued duel was canceled.");
            }
            if (target != null && !target.getUniqueId().equals(player.getUniqueId())) {
                sendMessageRaw(target, prefix + ChatColor.YELLOW + "The queued duel was canceled.");
            }
            return;
        }
        if (activeDuel == null) {
            sendMessage(player, "messages.not-in-duel");
            return;
        }
        MatchParticipant participant = activeDuel.participant(player.getUniqueId());
        if (participant == null) {
            sendMessage(player, "messages.not-in-duel");
            return;
        }
        participant.setDrawRequested(true);
        sendToParticipants("messages.draw-requested", PLAYER_PLACEHOLDER, player.getName());
        if (activeDuel.participantOne().drawRequested() && activeDuel.participantTwo().drawRequested()) {
            concludeDuel(null, DuelEndReason.DRAW, true);
            return;
        }
        runtimeStateStore.queueActiveDuelSave(activeDuel);
    }

    public void showSettings(Player player) {
        if (activeDuel == null) {
            sendMessage(player, "messages.settings-none");
            return;
        }
        player.openInventory(DuelGui.buildActiveSettingsGui(activeDuel.settings()));
    }

    public void watchDuel(Player player) {
        requirePrimaryThread();
        if (!permissionPort.has(player, PERMISSION_DUEL_SPECTATE)) {
            sendMessageOrFallback(player, null, ChatColor.RED + "You do not have permission to spectate duels.");
            return;
        }
        if (isWatchedSpectator(player.getUniqueId())) {
            sendMessageOrFallback(player, null, ChatColor.YELLOW + "You are already watching this duel.");
            return;
        }
        if (activeDuel == null) {
            sendMessageOrFallback(player, "messages.duel-watch-unavailable", ChatColor.RED + "There is no active duel to watch.");
            return;
        }
        if (isInActiveDuel(player.getUniqueId())) {
            sendMessageOrFallback(player, "messages.duel-watch-participant", ChatColor.RED + "You are already participating in this duel.");
            return;
        }
        if (blockCombatEntry && isCombatTagged(player)) {
            sendMessage(player, "messages.arena-combat-entry-blocked");
            return;
        }
        watchedSpectators.putIfAbsent(player.getUniqueId(), player.getGameMode());
        enableSpectatorDebugProtection();
        player.setGameMode(GameMode.ADVENTURE);
        teleportSafe(player, arena.spectator());
        startContainmentMonitor();
        sendMessageOrFallback(player, "messages.duel-watch-teleported", ChatColor.GREEN + "Warped to the arena stands.");
    }

    public void reloadFromCommand(CommandSender sender) {
        requirePrimaryThread();
        if (arenaTerrainService.isBusy()) {
            sendMessageRaw(sender, prefix + ChatColor.RED + "Arena terrain is busy. Wait for the current map operation to finish first.");
            return;
        }
        reloadConfig();
        if (plugin.spoilsService() != null) {
            plugin.spoilsService().reloadConfig();
        }
        sendMessageRaw(sender, prefix + ChatColor.GREEN + "Config reloaded.");
    }

    public void saveMapSnapshot(CommandSender sender, String rawMapId) {
        requirePrimaryThread();
        if (activeDuel != null || pendingRequest != null || queuedDuelStart != null) {
            sendMessageRaw(sender, prefix + ChatColor.RED + "You cannot save arena maps while a duel is active or pending.");
            return;
        }
        DuelMapOption option = arenaMapService.find(rawMapId);
        if (option == null) {
            sendMessageRaw(sender, prefix + ChatColor.RED + "Unknown map id. Use one of: " + mapOptions.stream().map(DuelMapOption::id).toList());
            return;
        }
        sendMessageRaw(sender, prefix + ChatColor.YELLOW + "Capturing arena terrain for " + option.displayName() + "...");
        arenaTerrainService.captureSnapshot(option.id(),
            () -> {
                arenaMapService.markCurrentArenaMap(option.id());
                sendMessageRaw(sender, prefix + ChatColor.GREEN + "Saved terrain snapshot for " + option.displayName() + ".");
            },
            message -> sendMessageRaw(sender, prefix + ChatColor.RED + message)
        );
    }

    public void loadMapSnapshot(CommandSender sender, String rawMapId) {
        requirePrimaryThread();
        if (activeDuel != null || pendingRequest != null || queuedDuelStart != null) {
            sendMessageRaw(sender, prefix + ChatColor.RED + "You cannot load arena maps while a duel is active or pending.");
            return;
        }
        DuelMapOption option = arenaMapService.find(rawMapId);
        if (option == null) {
            sendMessageRaw(sender, prefix + ChatColor.RED + "Unknown map id. Use one of: " + mapOptions.stream().map(DuelMapOption::id).toList());
            return;
        }
        sendMessageRaw(sender, prefix + ChatColor.YELLOW + "Loading arena terrain for " + option.displayName() + "...");
        arenaTerrainService.loadSnapshot(option.id(),
            () -> sendMessageRaw(sender, prefix + ChatColor.GREEN + "Arena terrain restored to " + option.displayName() + "."),
            message -> sendMessageRaw(sender, prefix + ChatColor.RED + message)
        );
    }

    public void showMapStatus(CommandSender sender) {
        ArenaMapOperationStatus status = arenaTerrainService.status();
        if (!status.busy()) {
            sendMessageRaw(sender, prefix + ChatColor.YELLOW + "Arena terrain idle. Current map: " + ChatColor.WHITE + arenaMapService.currentArenaMapId());
            return;
        }
        sendMessageRaw(
            sender,
            prefix + ChatColor.YELLOW + "Arena terrain " + status.type() + " " + ChatColor.WHITE + status.mapId()
                + ChatColor.YELLOW + " (" + status.processedBlocks() + "/" + status.totalBlocks() + ")."
        );
    }

    public void restoreLatestLoadout(CommandSender sender, Player target) {
        requirePrimaryThread();
        LoadoutSnapshot snapshot = loadoutArchiveStore.loadLatestPreDuel(target.getUniqueId());
        if (snapshot == null) {
            sendMessageRaw(sender, prefix + ChatColor.RED + "No archived pre-duel loadout exists for " + target.getName() + ".");
            return;
        }
        loadoutArchiveStore.apply(target, snapshot);
        sendMessageRaw(sender, prefix + ChatColor.GREEN + "Restored archived pre-duel loadout for " + target.getName() + ".");
    }

    public void handleQuit(Player player) {
        requirePrimaryThread();
        builders.remove(player.getUniqueId());
        if (queuedDuelStart != null && queuedDuelStart.involves(player.getUniqueId())) {
            Player requester = Bukkit.getPlayer(queuedDuelStart.requesterId());
            Player target = Bukkit.getPlayer(queuedDuelStart.targetId());
            if (requester != null) {
                sendMessage(requester, MSG_TARGET_OFFLINE);
            }
            if (target != null) {
                sendMessage(target, MSG_TARGET_OFFLINE);
            }
            clearQueuedDuelStart();
        }
        if (pendingRequest != null) {
            if (pendingRequest.requesterId().equals(player.getUniqueId()) || pendingRequest.targetId().equals(player.getUniqueId())) {
                clearPendingRequest();
            }
        }
        if (activeDuel == null) {
            return;
        }
        MatchParticipant participant = activeDuel.participant(player.getUniqueId());
        if (participant == null) {
            return;
        }
        disconnectSnapshots.put(player.getUniqueId(), loadoutArchiveStore.capture(player));
        participant.setDisconnectDeadlineEpochMs(System.currentTimeMillis() + (disconnectGraceSeconds * 1000L));
        sendToParticipants("messages.disconnect-grace", PLAYER_PLACEHOLDER, participant.name(), "{seconds}", String.valueOf(disconnectGraceSeconds));
        startDisconnectMonitor();
        runtimeStateStore.queueActiveDuelSave(activeDuel);
    }

    public void handleJoin(Player player) {
        requirePrimaryThread();
        GameMode watchedMode = watchedSpectators.remove(player.getUniqueId());
        if (watchedMode != null) {
            player.setGameMode(watchedMode);
            teleportToExit(player);
            return;
        }
        if (spoilsService.prepareForcedDeathIfPending(player)) {
            pendingForcedDeathIds.add(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && !player.isDead()) {
                    player.setHealth(0.0D);
                }
            });
        }
        if (recoveryTeleportIds.contains(player.getUniqueId())) {
            teleportToExit(player);
            recoveryTeleportIds.remove(player.getUniqueId());
            runtimeStateStore.clearRecoveryTeleportId(player.getUniqueId());
        }
        if (shouldBlockArenaFootprintEntry(player, player.getLocation())) {
            handleUnauthorizedArenaEntry(player);
        }
        if (activeDuel == null) {
            return;
        }
        MatchParticipant participant = activeDuel.participant(player.getUniqueId());
        if (participant == null) {
            return;
        }
        participant.setDisconnectDeadlineEpochMs(null);
        disconnectSnapshots.remove(player.getUniqueId());
        clearExternalCombatState(player);
        teleportToAssignedSpawn(player);
        sendMessage(player, "messages.rejoined-duel");
        if (!hasDisconnectingParticipant()) {
            cancelDisconnectMonitorTask();
        } else {
            startDisconnectMonitor();
        }
        runtimeStateStore.queueActiveDuelSave(activeDuel);
    }

    public void handleRespawn(PlayerRespawnEvent event) {
        requirePrimaryThread();
        UUID playerId = event.getPlayer().getUniqueId();
        if (respawnToSpawn.remove(playerId) || recoveryTeleportIds.contains(playerId)) {
            Location location = exitLocation();
            if (location != null) {
                event.setRespawnLocation(location);
            }
        }
    }

    public void handleDeath(Player player, List<ItemStack> drops) {
        requirePrimaryThread();
        if (activeDuel == null) {
            return;
        }
        MatchParticipant dead = activeDuel.participant(player.getUniqueId());
        if (dead == null) {
            return;
        }
        MatchParticipant winner = activeDuel.other(dead.playerId());
        Player winnerPlayer = winner == null ? null : Bukkit.getPlayer(winner.playerId());
        if (winner != null) {
            spoilsService.createSpoils(winner.playerId(), winner.name(), dead.playerId(), dead.name(), drops);
        }
        respawnToSpawn.add(dead.playerId());
        concludeDuel(winnerPlayer, DuelEndReason.KILL, true);
    }

    public void handleAsyncChat(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> sendMessage(player, "messages.chat-blocked"));
    }

    public void handleArenaExitAttempt(Player player) {
        requirePrimaryThread();
        teleportToAssignedSpawn(player);
        sendArenaExitBlockedMessage(player);
    }

    public void handleWatchedSpectatorExitAttempt(Player player) {
        requirePrimaryThread();
        if (player == null || arena == null || !isWatchedSpectator(player.getUniqueId())) {
            return;
        }
        player.setVelocity(player.getVelocity().zero());
        teleportSafe(player, arena.spectator());
        sendArenaExitBlockedMessage(player);
    }

    public void handleUnauthorizedArenaEntry(Player player) {
        requirePrimaryThread();
        teleportToExit(player);
        sendArenaEntryBlockedMessage(player);
    }

    public void sendArenaEntryBlockedMessage(Player player) {
        String message = plugin.getConfig().getString("messages.arena-entry-blocked", "");
        if (message == null || message.isBlank()) {
            sendMessageRaw(player, ChatColor.RED + "Only active duel participants may enter the fighting area.");
            return;
        }
        sendMessage(player, "messages.arena-entry-blocked");
    }

    public boolean isAllowedCommandForParticipant(String rawCommand) {
        String normalized = rawCommand.toLowerCase(Locale.ROOT).replaceFirst("^/", "").trim();
        if (BUILT_IN_ALLOWED_DUEL_COMMANDS.contains(normalized)) {
            return true;
        }
        for (String allowed : allowedDuelCommands) {
            if (normalized.equals(allowed) || normalized.startsWith(allowed + " ")) {
                return true;
            }
        }
        return false;
    }

    public boolean isWatchedSpectator(UUID playerId) {
        return playerId != null && watchedSpectators.containsKey(playerId);
    }

    public boolean isWatchedSpectatorCommandBlocked(Player player) {
        return player != null
            && isWatchedSpectator(player.getUniqueId())
            && !player.hasPermission(PERMISSION_BYPASS_ENTER);
    }

    public boolean isWatchedSpectatorTeleportBlocked(Player player, Location to, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause) {
        if (player == null || !isWatchedSpectator(player.getUniqueId()) || player.hasPermission(PERMISSION_BYPASS_ENTER)) {
            return false;
        }
        if (cause == org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.SPECTATE) {
            return true;
        }
        return arena == null || to == null || !arena.contains(to);
    }

    public boolean isWatchedSpectatorLeaving(Player player, Location to) {
        return player != null
            && to != null
            && isWatchedSpectator(player.getUniqueId())
            && !player.hasPermission(PERMISSION_BYPASS_ENTER)
            && arena != null
            && !arena.contains(to);
    }

    public boolean isBlockBreakAllowed(Block block, Player player) {
        if (hasBuildBypass(player)) {
            return true;
        }
        Location location = block.getLocation();
        if (isIdleArenaBlock(location)) {
            return false;
        }
        if (activeDuel == null) {
            return true;
        }
        boolean participant = isInActiveDuel(player.getUniqueId());
        if (!arena.contains(location)) {
            return !participant;
        }
        if (!participant) {
            return false;
        }
        return canParticipantBreakArenaBlock(location);
    }

    private boolean hasBuildBypass(Player player) {
        return player != null && player.hasPermission(PERMISSION_BYPASS_BUILD);
    }

    private boolean isIdleArenaBlock(Location location) {
        return arena != null && arena.contains(location) && activeDuel == null;
    }

    private boolean canParticipantBreakArenaBlock(Location location) {
        DuelSettings settings = activeDuel.settings();
        BlockKey blockKey = BlockKey.fromLocation(location);
        if (activeDuel.placedBlocks().contains(blockKey)) {
            return true;
        }
        if (settings.getPlaceBreakMode() == DuelSettings.PlaceBreakMode.PLACE_BREAK) {
            return settings.isMapSupportsBlockBreaking() && isArenaTerrainBlock(location);
        }
        return false;
    }

    public boolean isBlockPlaceAllowed(Block block, Material itemType, Player player) {
        if (hasBuildBypass(player)) {
            return true;
        }
        Location location = block.getLocation();
        if (isIdleArenaBlock(location)) {
            return false;
        }
        if (activeDuel == null) {
            return true;
        }
        boolean participant = isInActiveDuel(player.getUniqueId());
        if (!arena.contains(location)) {
            return !participant;
        }
        if (!participant) {
            return false;
        }
        if (!arenaTerrainService.containsFootprintBlock(location)) {
            return false;
        }
        return canParticipantPlaceArenaBlock(itemType);
    }

    private boolean canParticipantPlaceArenaBlock(Material itemType) {
        DuelSettings settings = activeDuel.settings();
        if (settings.getPlaceBreakMode() == DuelSettings.PlaceBreakMode.NONE) {
            return false;
        }
        if (settings.getPlaceBreakMode() == DuelSettings.PlaceBreakMode.PLACE_BREAK) {
            return true;
        }
        if (settings.getPlaceOnlyMode() == DuelSettings.PlaceOnlyMode.ALL_BLOCKS) {
            return true;
        }
        return isCobwebUtility(itemType);
    }

    public void trackPlacedBlock(Block block) {
        requirePrimaryThread();
        if (activeDuel != null) {
            activeDuel.placedBlocks().add(BlockKey.fromLocation(block.getLocation()));
        }
    }

    public boolean canUseExplosive(Material material, Player actor) {
        if (!isRestrictedExplosiveMaterial(material)) {
            return true;
        }
        if (activeDuel == null) {
            return true;
        }
        if (!isInActiveDuel(actor.getUniqueId())) {
            return false;
        }
        return activeSettingsAllowRestrictedExplosive(material);
    }

    public boolean isExplosiveMaterialAllowed(Material material) {
        if (!isRestrictedExplosiveMaterial(material)) {
            return true;
        }
        if (activeDuel == null) {
            return true;
        }
        return activeSettingsAllowRestrictedExplosive(material);
    }

    private boolean activeSettingsAllowRestrictedExplosive(Material material) {
        DuelSettings settings = activeDuel.settings();
        if (isCrystalOrAnchor(material)) {
            return isPlaceBreakOrPlaceOnly(settings) && settings.isAllowCrystalsAnchors();
        }
        if (settings.getPlaceBreakMode() != DuelSettings.PlaceBreakMode.PLACE_BREAK) {
            return false;
        }
        if (material == Material.TNT_MINECART) {
            return settings.isAllowExplosiveMinecarts();
        }
        if (material == Material.TNT) {
            return settings.isAllowOtherExplosives();
        }
        return true;
    }

    private boolean isCrystalOrAnchor(Material material) {
        return material == Material.END_CRYSTAL || material == Material.RESPAWN_ANCHOR;
    }

    private boolean isPlaceBreakOrPlaceOnly(DuelSettings settings) {
        return settings.getPlaceBreakMode() == DuelSettings.PlaceBreakMode.PLACE_BREAK
            || settings.getPlaceBreakMode() == DuelSettings.PlaceBreakMode.PLACE_ONLY;
    }

    public boolean shouldExplosionsDamageBlocks() {
        if (activeDuel == null) {
            return true;
        }
        return activeDuel.settings().getPlaceBreakMode() == DuelSettings.PlaceBreakMode.PLACE_BREAK
            && activeDuel.settings().isMapSupportsBlockBreaking();
    }

    public boolean isArenaTerrainBlock(Location location) {
        return arenaTerrainService.containsFootprintBlock(location);
    }

    public boolean isInsideArenaShell(Location location) {
        return arena != null && arena.contains(location);
    }

    public boolean shouldBlockArenaLiquidFlow(Location from, Location to) {
        if (arena == null || (from == null && to == null)) {
            return false;
        }
        boolean fromInside = from != null && arena.contains(from);
        boolean toInside = to != null && arena.contains(to);
        if (!fromInside && !toInside) {
            return false;
        }
        if (activeDuel == null) {
            return true;
        }
        if (!fromInside || !toInside) {
            return true;
        }
        return !arenaTerrainService.isNearFootprint(to, 3);
    }

    public boolean shouldBlockArenaEnvironmentalBlockChange(Location location) {
        return arena != null && location != null && arena.contains(location);
    }

    public boolean shouldProtectArenaShellBlock(Location location, Player player) {
        if (arena == null || location == null || !arena.contains(location)) {
            return false;
        }
        if (player != null && player.hasPermission("warzoneduels.bypass.build")) {
            return false;
        }
        if (activeDuel == null) {
            return true;
        }
        if (player == null || !isInActiveDuel(player.getUniqueId())) {
            return true;
        }
        return !arenaTerrainService.containsFootprintBlock(location);
    }

    public boolean shouldBlockArenaShellEntry(Player player, Location from, Location to) {
        if (!blockCombatEntry || player == null || combatTagPort == null || arena == null || to == null) {
            return false;
        }
        if (isInActiveDuel(player.getUniqueId()) || player.hasPermission(PERMISSION_BYPASS_ENTER)) {
            return false;
        }
        if (!arena.contains(to) || (from != null && arena.contains(from))) {
            return false;
        }
        return combatTagPort.isInCombat(player);
    }

    public boolean shouldBlockArenaFootprintEntry(Player player, Location to) {
        if (player == null || arena == null || to == null) {
            return false;
        }
        if (isInActiveDuel(player.getUniqueId()) || player.hasPermission(PERMISSION_BYPASS_ENTER)) {
            return false;
        }
        return arenaTerrainService.isOnOrInsideFootprintBlock(to);
    }

    public boolean isNearArenaTerrain(Location location, int radius) {
        return arenaTerrainService.isNearFootprint(location, radius);
    }

    public boolean isAllowedDuelTeleportDestination(Location location) {
        return arena != null
            && location != null
            && arena.contains(location)
            && arenaTerrainService.isWithinFootprintColumn(location, 6);
    }

    public Location chorusFallbackDestination(Player player) {
        Location preferred = player == null ? null : player.getLocation();
        Location fallback = arenaTerrainService.findPlayableLocation(preferred);
        if (fallback != null) {
            return fallback;
        }
        return player == null ? null : spawnFor(player.getUniqueId());
    }

    public boolean shouldSuppressArenaBlockDrops(Location location) {
        return activeDuel != null
            && arena != null
            && arena.contains(location)
            && !activeDuel.placedBlocks().contains(BlockKey.fromLocation(location));
    }

    public boolean shouldBlockArenaShellPvp(Player victim, Player attacker) {
        if (victim == null || attacker == null || arena == null || !arena.contains(victim.getLocation())) {
            return false;
        }
        if (activeDuel == null) {
            return true;
        }
        return !isInActiveDuel(victim.getUniqueId()) || !isInActiveDuel(attacker.getUniqueId());
    }

    public boolean shouldCancelArenaSpectatorDamage(Player player) {
        return player != null
            && arena != null
            && arena.contains(player.getLocation())
            && !isInActiveDuel(player.getUniqueId());
    }

    public void allowArenaItemPickup(UUID itemEntityId) {
        if (itemEntityId != null) {
            allowedArenaItemEntityIds.add(itemEntityId);
        }
    }

    public void allowArenaItemSpawnAt(Location location) {
        if (location != null && activeDuel != null && arena != null && arena.contains(location)) {
            allowedArenaItemSpawnLocations.put(BlockKey.fromLocation(location), System.currentTimeMillis() + 2000L);
        }
    }

    public boolean consumeAllowedArenaItemSpawn(Location location) {
        if (location == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        allowedArenaItemSpawnLocations.entrySet().removeIf(entry -> entry.getValue() < now);
        BlockKey key = BlockKey.fromLocation(location);
        Long exact = allowedArenaItemSpawnLocations.remove(key);
        if (exact != null && exact >= now) {
            return true;
        }
        for (int x = location.getBlockX() - 1; x <= location.getBlockX() + 1; x++) {
            for (int y = location.getBlockY() - 1; y <= location.getBlockY() + 1; y++) {
                for (int z = location.getBlockZ() - 1; z <= location.getBlockZ() + 1; z++) {
                    Location nearby = new Location(location.getWorld(), x, y, z);
                    Long expiresAt = allowedArenaItemSpawnLocations.remove(BlockKey.fromLocation(nearby));
                    if (expiresAt != null && expiresAt >= now) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean isAllowedArenaItemEntity(UUID itemEntityId) {
        return itemEntityId != null && allowedArenaItemEntityIds.contains(itemEntityId);
    }

    public void forgetArenaItemEntity(UUID itemEntityId) {
        if (itemEntityId != null) {
            allowedArenaItemEntityIds.remove(itemEntityId);
        }
    }

    public void trackExplosionSource(UUID entityId, Material sourceMaterial) {
        if (entityId != null && sourceMaterial != null) {
            trackedExplosionSources.put(entityId, sourceMaterial);
        }
    }

    public Material explosionSourceMaterial(UUID entityId) {
        if (entityId == null) {
            return null;
        }
        return trackedExplosionSources.get(entityId);
    }

    public void clearExplosionSource(UUID entityId) {
        if (entityId != null) {
            trackedExplosionSources.remove(entityId);
        }
    }

    public boolean isVictoryFirework(UUID entityId) {
        return entityId != null && victoryFireworkIds.contains(entityId);
    }

    public boolean shouldCancelDamage(Player victim, Player attacker) {
        if (activeDuel == null) {
            return false;
        }
        if (duelCountdownActive && isInActiveDuel(victim.getUniqueId())) {
            return true;
        }
        boolean victimParticipant = isInActiveDuel(victim.getUniqueId());
        if (!victimParticipant) {
            return false;
        }
        if (attacker == null) {
            return false;
        }
        return !isInActiveDuel(attacker.getUniqueId());
    }

    public boolean isDuelCountdownActive() {
        return duelCountdownActive;
    }

    public boolean canUseCombatItem(Material material, Player actor) {
        if (activeDuel == null || !isInActiveDuel(actor.getUniqueId())) {
            return true;
        }
        if (duelCountdownActive) {
            return false;
        }
        return material != Material.BRUSH
            && isCombatItemEnabledForSettings(material, activeDuel.settings())
            && isCombatItemOffCooldown(material, actor);
    }

    public boolean isCombatItemEnabled(Material material, Player actor) {
        if (activeDuel == null || !isInActiveDuel(actor.getUniqueId())) {
            return true;
        }
        return isCombatItemEnabledForSettings(material, activeDuel.settings());
    }

    private boolean isCombatItemEnabledForSettings(Material material, DuelSettings settings) {
        if (SpearUtil.isSpear(material)) {
            return settings.isAllowSpears();
        }
        return switch (material) {
            case ENDER_PEARL -> settings.isAllowEnderPearls();
            case WIND_CHARGE -> settings.isAllowWindCharges();
            case CHORUS_FRUIT -> settings.isAllowChorusFruit();
            case MACE -> settings.isAllowMaces();
            case ELYTRA -> settings.isAllowElytras();
            default -> true;
        };
    }

    private boolean isCombatItemOffCooldown(Material material, Player actor) {
        return switch (material) {
            case ENDER_PEARL -> !actor.hasCooldown(Material.ENDER_PEARL);
            case WIND_CHARGE -> !actor.hasCooldown(Material.WIND_CHARGE);
            default -> true;
        };
    }

    public boolean canUseEnderChest(Player actor) {
        if (activeDuel == null || actor == null || !isInActiveDuel(actor.getUniqueId())) {
            return true;
        }
        return !duelCountdownActive && activeDuel.settings().isAllowEnderChests();
    }

    public int combatCooldownSeconds(Material material, Player actor) {
        if (activeDuel == null || !isInActiveDuel(actor.getUniqueId())) {
            return 0;
        }
        DuelSettings settings = activeDuel.settings();
        if (material == Material.ENDER_PEARL) {
            return settings.getEnderPearlCooldownSeconds();
        }
        if (material == Material.WIND_CHARGE) {
            return settings.getWindChargeCooldownSeconds();
        }
        return 0;
    }

    public void applyCombatCooldown(Material material, Player actor) {
        int seconds = combatCooldownSeconds(material, actor);
        if (seconds > 0) {
            actor.setCooldown(material, seconds * 20);
        }
    }

    public void applyCombatCooldownDeferred(Material material, Player actor) {
        Bukkit.getScheduler().runTask(plugin, () -> applyCombatCooldown(material, actor));
    }

    public Location spawnFor(UUID playerId) {
        if (activeDuel == null || arena == null) {
            return null;
        }
        if (activeDuel.participantOne().playerId().equals(playerId)) {
            return arena.spawn1();
        }
        if (activeDuel.participantTwo().playerId().equals(playerId)) {
            return arena.spawn2();
        }
        return null;
    }

    public void updateArenaLocation(String key, Location location) {
        requirePrimaryThread();
        FileConfiguration config = plugin.getConfig();
        config.set("arena.world", location.getWorld() == null ? "world" : location.getWorld().getName());
        String value = location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
        switch (key) {
            case "setpos1" -> config.set("arena.pos1", value);
            case "setpos2" -> config.set("arena.pos2", value);
            case "setspawn1" -> config.set("arena.spawn1", value);
            case "setspawn2" -> config.set("arena.spawn2", value);
            case "setspectator", "setspectatorlocation" -> config.set("arena.spectator", value);
            case "setexit" -> config.set("arena.exit", value);
            default -> {
                return;
            }
        }
        plugin.saveConfig();
        reloadConfig();
    }

    public Collection<String> commandSuggestions() {
        return List.of(
            "accept", "deny", "review", "watch", "spectate", "stands", "draw", "surrender", "cancel", "vault", "stats", "info", "settings",
            "reload", "restoreloadout", "mapsave", "mapload", "mapstatus",
            "setpos1", "setpos2", "setspawn1", "setspawn2", "setspectator", "setspectatorlocation", "setexit"
        );
    }

    public PlayerDuelStats stats(UUID playerId, String playerName) {
        return statsService.stats(playerId, playerName);
    }

    public void applyMapChoice(DuelSettings settings, DuelMapOption option) {
        arenaMapService.applySelection(settings, option);
    }

    public void sanitizeBuilderSettings(DuelSettings settings) {
        arenaMapService.sanitizeSettings(settings);
    }

    public boolean shouldShowExplosivesMenu(DuelSettings settings) {
        return settings.shouldShowExplosivesConfiguration();
    }

    private void recoverActiveDuelIfNeeded() {
        RuntimeStateStore.PersistedRuntime persistedRuntime = runtimeStateStore.loadActiveDuel();
        runtimeStateStore.clearReloadResumeMarker();
        if (persistedRuntime.activeDuel() == null) {
            runtimeStateStore.clearRuntime();
            return;
        }
        if (!persistedRuntime.resumeAllowed()) {
            refundPersistedWagerIfHeld(persistedRuntime.activeDuel());
            Set<UUID> playerIds = Set.of(
                persistedRuntime.activeDuel().participantOne().playerId(),
                persistedRuntime.activeDuel().participantTwo().playerId()
            );
            recoveryTeleportIds.addAll(playerIds);
            runtimeStateStore.saveRecoveryTeleportIds(recoveryTeleportIds);
            runtimeStateStore.clearRuntime();
            plugin.getLogger().warning("Found stale duel runtime data without a reload marker. Match was canceled for safety.");
            return;
        }
        activeDuel = persistedRuntime.activeDuel();
        arenaMapService.prepareArenaForMatch(arena, activeDuel.settings());
        rebuildParticipantIndex();
        for (UUID playerId : List.of(activeDuel.participantOne().playerId(), activeDuel.participantTwo().playerId())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                clearExternalCombatState(player);
                teleportToAssignedSpawn(player);
                sendMessage(player, "messages.duel-resumed");
            }
        }
        startDisconnectMonitor();
        startContainmentMonitor();
    }

    private void handleServerStoppingDisable() {
        clearQueuedDuelStart();
        if (activeDuel == null) {
            return;
        }
        ActiveDuel finishedDuel = activeDuel;
        Set<UUID> participantIds = Set.of(activeDuel.participantOne().playerId(), activeDuel.participantTwo().playerId());
        recoveryTeleportIds.addAll(participantIds);
        runtimeStateStore.saveRecoveryTeleportIds(recoveryTeleportIds);
        refundWagerIfHeld();
        duelAnalyticsService.recordDuel(finishedDuel, null, DuelEndReason.SERVER_RESTART);
        for (UUID playerId : participantIds) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                teleportToExit(player);
            }
        }
        activeDuel = null;
        rebuildParticipantIndex();
        cleanupVolatileArenaState();
    }

    private void startDuel(Player requester, Player target, DuelSettings settings) {
        if (arenaTerrainService.isBusy()) {
            queueDuelStart(requester, target, settings);
            return;
        }
        if (!arenaTerrainService.isReady()) {
            sendMessageRaw(requester, prefix + ChatColor.RED + "Arena terrain footprint is not loaded.");
            sendMessageRaw(target, prefix + ChatColor.RED + "Arena terrain footprint is not loaded.");
            return;
        }
        DuelSettings preparedSettings = settings.copy();
        arenaMapService.sanitizeSettings(preparedSettings);
        DuelMapOption selectedMap = arenaMapService.resolve(preparedSettings.getMapId());
        if (!arenaTerrainService.hasSnapshot(selectedMap.id())) {
            sendMessageRaw(requester, prefix + ChatColor.RED + "The selected arena map is not saved yet: " + selectedMap.displayName() + ".");
            sendMessageRaw(target, prefix + ChatColor.RED + "The selected arena map is not saved yet: " + selectedMap.displayName() + ".");
            return;
        }

        MatchParticipant one = new MatchParticipant(requester.getUniqueId(), requester.getName());
        MatchParticipant two = new MatchParticipant(target.getUniqueId(), target.getName());
        ActiveDuel stagedDuel = new ActiveDuel(one, two, preparedSettings, System.currentTimeMillis());
        loadoutArchiveStore.saveLatestPreDuel(requester, loadoutArchiveStore.capture(requester));
        loadoutArchiveStore.saveLatestPreDuel(target, loadoutArchiveStore.capture(target));

        if (preparedSettings.getWager() > NO_WAGER && !holdWager(stagedDuel, requester, target, preparedSettings.getWager())) {
            sendMessage(requester, MSG_CANNOT_AFFORD, PLAYER_PLACEHOLDER, target.getName());
            sendMessage(target, MSG_CANNOT_AFFORD, PLAYER_PLACEHOLDER, requester.getName());
            return;
        }

        preparingDuel = stagedDuel;
        sendMessageRaw(requester, ChatColor.YELLOW + "Preparing arena terrain...");
        sendMessageRaw(target, ChatColor.YELLOW + "Preparing arena terrain...");
        arenaTerrainService.loadSnapshot(selectedMap.id(), () -> {
            if (preparingDuel != stagedDuel) {
                refundWagerIfHeld(stagedDuel);
                return;
            }
            preparingDuel = null;
            activeDuel = stagedDuel;
            duelEnding = false;
            queuedDuelStart = null;
            arenaMapService.prepareArenaForMatch(arena, activeDuel.settings());
            clearExternalCombatState(requester);
            clearExternalCombatState(target);
            prepareCombatant(requester);
            prepareCombatant(target);
            teleportToAssignedSpawn(requester);
            teleportToAssignedSpawn(target);
            rebuildParticipantIndex();
            startContainmentMonitor();
            sendMessage(requester, "messages.duel-risk-warning");
            sendMessage(target, "messages.duel-risk-warning");
            sendMessageRaw(requester, ChatColor.RED + "Disconnecting gives you " + disconnectGraceSeconds + " seconds to rejoin before you lose.");
            sendMessageRaw(target, ChatColor.RED + "Disconnecting gives you " + disconnectGraceSeconds + " seconds to rejoin before you lose.");
            String wagerText = preparedSettings.getWager() > NO_WAGER ? " for $" + formatAmount(preparedSettings.getWager()) : "";
            broadcast("messages.duel-start", "{p1}", requester.getName(), "{p2}", target.getName(), "{wager}", wagerText);
            broadcastWatchPrompt(requester.getUniqueId(), target.getUniqueId());
            runtimeStateStore.queueActiveDuelSave(activeDuel, 1L);
            startCountdown(requester, target);
        }, message -> {
            if (preparingDuel == stagedDuel) {
                preparingDuel = null;
            }
            refundWagerIfHeld(stagedDuel);
            sendMessageRaw(requester, ChatColor.RED + message);
            sendMessageRaw(target, ChatColor.RED + message);
            arenaTerrainService.ensureDefaultSnapshotLoaded(logMessage -> plugin.getLogger().warning(logMessage));
        });
    }

    private void concludeDuel(Player winner, DuelEndReason reason, boolean broadcastOutcome) {
        requirePrimaryThread();
        if (activeDuel == null || duelEnding) {
            return;
        }
        ActiveDuel finishedDuel = activeDuel;
        UUID winnerId = winner == null ? null : winner.getUniqueId();
        duelEnding = true;
        cancelCountdownTask();
        cancelDisconnectMonitorTask();

        if (winner != null) {
            payoutWager(winner);
            if (broadcastOutcome) {
                String wagerText = finishedDuel.settings().getWager() > NO_WAGER ? " for $" + formatAmount(finishedDuel.settings().getWager()) : "";
                broadcast("messages.duel-end", "{winner}", winner.getName(), "{wager}", wagerText);
            }
        } else {
            refundWagerIfHeld();
            if (broadcastOutcome) {
                broadcast("messages.duel-draw");
            }
        }

        duelAnalyticsService.recordDuel(finishedDuel, winnerId, reason);
        statsService.recordMatchResult(finishedDuel, winnerId, reason);
        runtimeStateStore.clearRuntime();

        if (winner != null && reason == DuelEndReason.KILL && victoryMomentSeconds > 0) {
            healAfterDuel(winner);
            startVictoryMoment(finishedDuel, winner);
            return;
        }

        finishConcludedDuel(finishedDuel, winner);
    }

    private void finishConcludedDuel(ActiveDuel finishedDuel, Player winner) {
        UUID participantOne = finishedDuel.participantOne().playerId();
        UUID participantTwo = finishedDuel.participantTwo().playerId();

        if (winner != null) {
            healAfterDuel(winner);
            teleportToExit(winner);
        }
        Player first = Bukkit.getPlayer(participantOne);
        Player second = Bukkit.getPlayer(participantTwo);
        if (first != null && first.isOnline() && !respawnToSpawn.contains(participantOne)) {
            teleportToExit(first);
        }
        if (second != null && second.isOnline() && !respawnToSpawn.contains(participantTwo)) {
            teleportToExit(second);
        }

        activeDuel = null;
        duelEnding = false;
        cancelVictoryTask();
        cancelContainmentTask();
        rebuildParticipantIndex();
        clearWatchedSpectators(true);
        cleanupArenaAfterMatch(finishedDuel, true);
    }

    private void teleportOnlineParticipantsToExit(ActiveDuel duel) {
        if (duel == null) {
            return;
        }
        for (UUID playerId : List.of(duel.participantOne().playerId(), duel.participantTwo().playerId())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline() && !player.isDead()) {
                teleportToExit(player);
            }
        }
    }

    private void clearWatchedSpectators(boolean teleportOut) {
        if (watchedSpectators.isEmpty()) {
            restoreSpectatorDebugProtection();
            return;
        }
        Map<UUID, GameMode> previousModes = Map.copyOf(watchedSpectators);
        for (Map.Entry<UUID, GameMode> entry : previousModes.entrySet()) {
            watchedSpectators.remove(entry.getKey());
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }
            GameMode previousMode = entry.getValue() == null ? GameMode.SURVIVAL : entry.getValue();
            player.setGameMode(previousMode);
            if (teleportOut && !player.isDead()) {
                teleportToExit(player);
            }
        }
        restoreSpectatorDebugProtection();
    }

    private void enableSpectatorDebugProtection() {
        if (!spectatorReducedDebugInfo || arena == null || arena.spectator().getWorld() == null) {
            return;
        }
        World world = arena.spectator().getWorld();
        if (previousReducedDebugInfo == null) {
            previousReducedDebugInfo = world.getGameRuleValue(GameRule.REDUCED_DEBUG_INFO);
        }
        world.setGameRule(GameRule.REDUCED_DEBUG_INFO, true);
    }

    private void restoreSpectatorDebugProtection() {
        if (previousReducedDebugInfo == null || arena == null || arena.spectator().getWorld() == null) {
            previousReducedDebugInfo = null;
            return;
        }
        arena.spectator().getWorld().setGameRule(GameRule.REDUCED_DEBUG_INFO, previousReducedDebugInfo);
        previousReducedDebugInfo = null;
    }

    private void startVictoryMoment(ActiveDuel finishedDuel, Player winner) {
        sendMessage(winner, "messages.victory-moment");
        winner.sendTitle(color("&6Victory"), color("&7You will return to spawn shortly."), 5, 50, 10);
        winner.playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
        int totalTicks = victoryMomentSeconds * 20;
        final int[] elapsedTicks = {0};
        cancelVictoryTask();
        victoryTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeDuel != finishedDuel) {
                cancelVictoryTask();
                return;
            }
            if (victoryFireworks && elapsedTicks[0] % 20 == 0) {
                launchVictoryFireworks(winner.getLocation());
            }
            elapsedTicks[0] += 10;
            if (elapsedTicks[0] >= totalTicks) {
                finishConcludedDuel(finishedDuel, winner);
            }
        }, 0L, 10L);
    }

    private void launchVictoryFireworks(Location center) {
        if (center == null || center.getWorld() == null || arena == null) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 3; i++) {
            Location location = center.clone().add(random.nextDouble(-8D, 8D), random.nextDouble(2D, 5D), random.nextDouble(-8D, 8D));
            Firework firework = center.getWorld().spawn(location, Firework.class);
            victoryFireworkIds.add(firework.getUniqueId());
            FireworkMeta meta = firework.getFireworkMeta();
            meta.setPower(1);
            meta.addEffect(FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL_LARGE)
                .withColor(Color.ORANGE, Color.YELLOW)
                .withFade(Color.WHITE)
                .trail(true)
                .flicker(true)
                .build());
            firework.setFireworkMeta(meta);
        }
    }

    private void cleanupArenaAfterMatch(ActiveDuel duel, boolean restoreDefaultTerrain) {
        if (duel == null || arena == null) {
            return;
        }
        if (duel.arenaSnapshot() != null) {
            arenaResetService.restore(arena, duel.arenaSnapshot());
        }
        if (clearPlacedBlocksWhenNoBreak || !duel.placedBlocks().isEmpty()) {
            arenaResetService.clearTrackedPlacedBlocks(arena, duel.placedBlocks());
        }
        if (allowWaterDrain) {
            arenaResetService.clearFluids(arena, arenaTerrainService.footprint());
        }
        arenaResetService.clearNonPlayerEntities(arena);
        if (restoreDefaultTerrain) {
            String defaultMapId = arenaMapService.defaultMapId();
            if (arenaTerrainService.hasSnapshot(defaultMapId)) {
                arenaTerrainService.loadSnapshot(defaultMapId, () -> {
                }, message -> plugin.getLogger().warning(message));
            } else {
                arenaMapService.restoreDefaultArena(arena);
                plugin.getLogger().warning("Default arena terrain snapshot '" + defaultMapId + "' does not exist yet.");
            }
        }
        disconnectSnapshots.clear();
        cleanupVolatileArenaState();
    }

    private void cleanupVolatileArenaState() {
        allowedArenaItemEntityIds.clear();
        allowedArenaItemSpawnLocations.clear();
        trackedExplosionSources.clear();
        blockedItemMessageCooldowns.clear();
        arenaExitMessageCooldowns.clear();
        victoryFireworkIds.clear();
        pendingForcedDeathIds.clear();
        duelCountdownActive = false;
    }

    private void prepareCombatant(Player player) {
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20F);
        player.setFireTicks(0);
        player.setArrowsInBody(0);
        player.setFreezeTicks(0);
        for (var effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.setFallDistance(0F);
        player.setGameMode(GameMode.SURVIVAL);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
    }

    private void healAfterDuel(Player player) {
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20F);
        player.setFireTicks(0);
        player.setArrowsInBody(0);
        player.setFreezeTicks(0);
    }

    private void scheduleRequestExpiry() {
        cancelRequestExpiryTask();
        requestExpiryTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingRequest == null) {
                return;
            }
            Player requester = Bukkit.getPlayer(pendingRequest.requesterId());
            String targetName = pendingRequest.targetName();
            clearPendingRequest();
            if (requester != null) {
                sendMessage(requester, "messages.request-expired", PLAYER_PLACEHOLDER, targetName);
            }
        }, requestExpireSeconds * 20L);
    }

    private void startDisconnectMonitor() {
        if (activeDuel == null) {
            return;
        }
        if (!hasDisconnectingParticipant()) {
            cancelDisconnectMonitorTask();
            return;
        }
        if (disconnectMonitorTask != null) {
            return;
        }
        disconnectMonitorTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeDuel == null) {
                cancelDisconnectMonitorTask();
                return;
            }
            MatchParticipant expired = findExpiredDisconnectParticipant();
            if (expired == null) {
                if (!hasDisconnectingParticipant()) {
                    cancelDisconnectMonitorTask();
                }
                return;
            }
            MatchParticipant winner = activeDuel.other(expired.playerId());
            Player winnerPlayer = winner == null ? null : Bukkit.getPlayer(winner.playerId());
            recoveryTeleportIds.add(expired.playerId());
            runtimeStateStore.saveRecoveryTeleportIds(recoveryTeleportIds);
            LoadoutSnapshot snapshot = disconnectSnapshots.get(expired.playerId());
            if (winner != null && snapshot != null) {
                spoilsService.createSpoilsFromSnapshot(winner.playerId(), winner.name(), expired.playerId(), expired.name(), snapshot);
            }
            spoilsService.markForcedDeathOnJoin(expired.playerId());
            if (winnerPlayer != null) {
                sendToParticipants("messages.disconnect-loss", PLAYER_PLACEHOLDER, expired.name());
            }
            concludeDuel(winnerPlayer, DuelEndReason.DISCONNECT_TIMEOUT, true);
        }, 20L, 20L);
    }

    private MatchParticipant findExpiredDisconnectParticipant() {
        if (activeDuel == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        for (MatchParticipant participant : List.of(activeDuel.participantOne(), activeDuel.participantTwo())) {
            Long deadline = participant.disconnectDeadlineEpochMs();
            if (deadline != null && now >= deadline) {
                return participant;
            }
        }
        return null;
    }

    private boolean hasDisconnectingParticipant() {
        if (activeDuel == null) {
            return false;
        }
        return activeDuel.participantOne().disconnectDeadlineEpochMs() != null
            || activeDuel.participantTwo().disconnectDeadlineEpochMs() != null;
    }

    private void cancelRequestExpiryTask() {
        if (requestExpiryTask != null) {
            requestExpiryTask.cancel();
            requestExpiryTask = null;
        }
    }

    private void cancelDisconnectMonitorTask() {
        if (disconnectMonitorTask != null) {
            disconnectMonitorTask.cancel();
            disconnectMonitorTask = null;
        }
    }

    private void cancelCountdownTask() {
        duelCountdownActive = false;
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    private void startContainmentMonitor() {
        if ((activeDuel == null && watchedSpectators.isEmpty()) || containmentTask != null) {
            return;
        }
        containmentTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeDuel == null) {
                clearWatchedSpectators(true);
                cancelContainmentTask();
                return;
            }
            enforceParticipantContainment(activeDuel.participantOne().playerId());
            enforceParticipantContainment(activeDuel.participantTwo().playerId());
            enforceWatchedSpectatorContainment();
        }, 5L, 5L);
    }

    private void enforceParticipantContainment(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }
        if (!isParticipantInsideAllowedArena(player.getLocation())) {
            handleArenaExitAttempt(player);
        }
    }

    private void enforceWatchedSpectatorContainment() {
        if (watchedSpectators.isEmpty() || arena == null) {
            return;
        }
        for (UUID playerId : List.copyOf(watchedSpectators.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                watchedSpectators.remove(playerId);
                continue;
            }
            if (player.hasPermission(PERMISSION_BYPASS_ENTER)) {
                continue;
            }
            if (player.getGameMode() != GameMode.SPECTATOR) {
                player.setGameMode(GameMode.SPECTATOR);
            }
            if (!arena.contains(player.getLocation())) {
                handleWatchedSpectatorExitAttempt(player);
            }
        }
        if (watchedSpectators.isEmpty()) {
            restoreSpectatorDebugProtection();
        }
    }

    private boolean isParticipantInsideAllowedArena(Location location) {
        return arena != null
            && location != null
            && arena.contains(location)
            && arenaTerrainService.isWithinFootprintColumn(location, 6);
    }

    private void cancelContainmentTask() {
        if (containmentTask != null) {
            containmentTask.cancel();
            containmentTask = null;
        }
    }

    private void cancelVictoryTask() {
        if (victoryTask != null) {
            victoryTask.cancel();
            victoryTask = null;
        }
    }

    private void clearPendingRequest() {
        pendingRequest = null;
        cancelRequestExpiryTask();
    }

    private boolean holdWager(ActiveDuel duel, Player one, Player two, double amount) {
        if (!economyPort.withdraw(one, amount)) {
            return false;
        }
        if (!economyPort.withdraw(two, amount)) {
            economyPort.deposit(one, amount);
            return false;
        }
        duel.setWagerHeld(true);
        duel.setWagerPot(amount * 2D);
        return true;
    }

    private void payoutWager(Player winner) {
        if (activeDuel == null || !activeDuel.isWagerHeld()) {
            return;
        }
        if (activeDuel.getWagerPot() > NO_WAGER) {
            economyPort.deposit(winner, activeDuel.getWagerPot());
        }
        activeDuel.setWagerHeld(false);
        activeDuel.setWagerPot(0D);
    }

    private void refundWagerIfHeld() {
        if (activeDuel == null || !activeDuel.isWagerHeld()) {
            return;
        }
        double each = activeDuel.settings().getWager();
        economyPort.deposit(activeDuel.participantOne().playerId(), each);
        economyPort.deposit(activeDuel.participantTwo().playerId(), each);
        activeDuel.setWagerHeld(false);
        activeDuel.setWagerPot(0D);
    }

    private void refundPersistedWagerIfHeld(ActiveDuel duel) {
        if (duel == null || !duel.isWagerHeld()) {
            return;
        }
        double each = duel.settings().getWager();
        economyPort.deposit(duel.participantOne().playerId(), each);
        economyPort.deposit(duel.participantTwo().playerId(), each);
        duel.setWagerHeld(false);
        duel.setWagerPot(0D);
    }

    private void refundWagerIfHeld(ActiveDuel duel) {
        refundPersistedWagerIfHeld(duel);
    }

    private void rebuildParticipantIndex() {
        activeParticipantIndex.clear();
        if (activeDuel != null) {
            activeParticipantIndex.add(activeDuel.participantOne().playerId());
            activeParticipantIndex.add(activeDuel.participantTwo().playerId());
        }
    }

    private ArenaDefinition loadArena(FileConfiguration config) {
        String worldName = config.getString("arena.world", "world");
        Location pos1 = parseLocation(worldName, config.getString("arena.pos1", "0,64,0"));
        Location pos2 = parseLocation(worldName, config.getString("arena.pos2", "10,70,10"));
        Location spawn1 = exactSpawn(parseLocation(worldName, config.getString("arena.spawn1", "2,65,2")), 180.0F);
        Location spawn2 = exactSpawn(parseLocation(worldName, config.getString("arena.spawn2", "8,65,8")), 0.0F);
        Location spectator = parseLocation(worldName, config.getString("arena.spectator", "5,75,5"));
        Location exit = parseLocation(worldName, config.getString("arena.exit", "0,80,0"));
        return new ArenaDefinition(worldName, pos1, pos2, spawn1, spawn2, spectator, exit);
    }

    private Location exactSpawn(Location base, float yaw) {
        if (base == null || base.getWorld() == null) {
            return base;
        }
        return new Location(base.getWorld(), base.getX(), base.getY(), base.getZ(), yaw, 0.0F);
    }

    private Location parseLocation(String worldName, String raw) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }
        if (world == null) {
            return new Location(null, 0, 0, 0);
        }
        String[] parts = raw.split(",");
        if (parts.length < 3) {
            return new Location(world, 0, 0, 0);
        }
        try {
            return new Location(
                world,
                Double.parseDouble(parts[0].trim()),
                Double.parseDouble(parts[1].trim()),
                Double.parseDouble(parts[2].trim())
            );
        } catch (NumberFormatException ignored) {
            return new Location(world, 0, 0, 0);
        }
    }

    private void sendRequestDetails(Player target, DuelRequest request) {
        sendMessage(target, "messages.request-received", PLAYER_PLACEHOLDER, request.requesterName());
        target.sendMessage(
            Component.text("[Review Request] ", NamedTextColor.GOLD)
                .clickEvent(ClickEvent.runCommand("/duel review"))
                .append(Component.text("[Open Accept Menu] ", NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/duel accept")))
                .append(Component.text("[Deny]", NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/duel deny")))
        );
        sendMessageRaw(target, ChatColor.YELLOW + "Use /duel accept to review the settings and accept or deny the request.");
    }

    public void sendMessage(Player player, String path, String... replacements) {
        if (player == null) {
            return;
        }
        String message = plugin.getConfig().getString(path, "");
        if (message == null || message.isEmpty()) {
            return;
        }
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        sendMessageRaw(player, color(message));
    }

    private void sendMessageOrFallback(Player player, String path, String fallback) {
        if (player == null) {
            return;
        }
        String message = plugin.getConfig().getString(path, "");
        if (message == null || message.isBlank()) {
            sendMessageRaw(player, fallback);
            return;
        }
        sendMessageRaw(player, color(message));
    }

    public void sendMessageRaw(CommandSender sender, String message) {
        sender.sendMessage(message);
    }

    public void sendMessageRaw(Player player, String message) {
        player.sendMessage(prefix + message);
    }

    public void sendBlockedCombatItemMessage(Player player) {
        if (player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long lastSent = blockedItemMessageCooldowns.get(player.getUniqueId());
        if (lastSent != null && now - lastSent < 750L) {
            return;
        }
        blockedItemMessageCooldowns.put(player.getUniqueId(), now);
        sendMessage(player, "messages.combat-item-blocked");
    }

    private void sendArenaExitBlockedMessage(Player player) {
        if (player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long lastSent = arenaExitMessageCooldowns.get(player.getUniqueId());
        if (lastSent != null && now - lastSent < 1500L) {
            return;
        }
        arenaExitMessageCooldowns.put(player.getUniqueId(), now);
        sendMessage(player, "messages.arena-exit-blocked");
    }

    private void sendToParticipants(String path, String... replacements) {
        if (activeDuel == null) {
            return;
        }
        Player one = Bukkit.getPlayer(activeDuel.participantOne().playerId());
        Player two = Bukkit.getPlayer(activeDuel.participantTwo().playerId());
        if (one != null) {
            sendMessage(one, path, replacements);
        }
        if (two != null) {
            sendMessage(two, path, replacements);
        }
    }

    private void broadcast(String path, String... replacements) {
        String message = plugin.getConfig().getString(path, "");
        if (message == null || message.isEmpty()) {
            return;
        }
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        Bukkit.broadcastMessage(prefix + color(message));
    }

    private void broadcastWatchPrompt(UUID participantOne, UUID participantTwo) {
        String message = plugin.getConfig().getString("messages.duel-watch-broadcast", "");
        if (message == null || message.isEmpty()) {
            return;
        }
        String hover = plugin.getConfig().getString("messages.duel-watch-hover", "&7Click to warp to the arena stands.");
        Component component = Component.text("[Duel] ", NamedTextColor.GOLD)
            .append(Component.text(ChatColor.stripColor(color(message)), NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.runCommand("/duel watch"))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text(ChatColor.stripColor(color(hover)), NamedTextColor.GRAY))));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getUniqueId().equals(participantOne) || player.getUniqueId().equals(participantTwo)) {
                continue;
            }
            player.sendMessage(component);
        }
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }

    public void teleportSafe(Player player, Location location) {
        if (player == null || location == null || location.getWorld() == null) {
            return;
        }
        allowTeleportOnce(player.getUniqueId());
        player.teleport(location);
    }

    private void teleportToAssignedSpawn(Player player) {
        Location spawn = spawnFor(player.getUniqueId());
        if (spawn != null) {
            teleportSafe(player, spawn);
        }
    }

    private void teleportToExit(Player player) {
        teleportSafe(player, exitLocation());
    }

    private Location exitLocation() {
        return spawnPort.resolveSpawnFallback(arena == null ? null : arena.exit());
    }

    private boolean sameIp(Player a, Player b) {
        if (a.getAddress() == null || b.getAddress() == null) {
            return false;
        }
        InetAddress aAddress = a.getAddress().getAddress();
        InetAddress bAddress = b.getAddress().getAddress();
        if (aAddress == null || bAddress == null) {
            return false;
        }
        return aAddress.getHostAddress().equalsIgnoreCase(bAddress.getHostAddress());
    }

    private boolean isCobwebUtility(Material material) {
        return material == Material.COBWEB
            || material == Material.WATER_BUCKET
            || material.name().endsWith("_BUTTON")
            || material.name().endsWith("_PRESSURE_PLATE");
    }

    private boolean isRestrictedExplosiveMaterial(Material material) {
        return material == Material.END_CRYSTAL
            || material == Material.RESPAWN_ANCHOR
            || material == Material.TNT_MINECART
            || material == Material.TNT;
    }

    private void sendTerrainBusyMessage(CommandSender sender) {
        ArenaMapOperationStatus status = arenaTerrainService.status();
        if (status.busy()) {
            sendMessageRaw(
                sender,
                prefix + ChatColor.RED + "Arena terrain is busy with " + status.type() + " " + status.mapId()
                    + " (" + status.processedBlocks() + "/" + status.totalBlocks() + ")."
            );
            return;
        }
        sendMessageRaw(sender, prefix + ChatColor.RED + "Arena terrain is busy.");
    }

    private void queueDuelStart(Player requester, Player target, DuelSettings settings) {
        queuedDuelStart = new QueuedDuelStart(
            requester.getUniqueId(),
            target.getUniqueId(),
            requester.getName(),
            target.getName(),
            settings.copy()
        );
        sendMessageRaw(requester, prefix + ChatColor.YELLOW + "Arena is busy. Your duel is queued and will start when the arena is ready.");
        sendMessageRaw(target, prefix + ChatColor.YELLOW + "Arena is busy. Your duel is queued and will start when the arena is ready.");
        ensureQueuedStartTask();
    }

    private void ensureQueuedStartTask() {
        if (queuedStartTask != null) {
            return;
        }
        queuedStartTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::processQueuedDuelStart,
            QUEUED_START_PERIOD_TICKS,
            QUEUED_START_PERIOD_TICKS
        );
    }

    private void processQueuedDuelStart() {
        if (queuedDuelStart == null) {
            cancelQueuedStartTask();
            return;
        }
        if (activeDuel != null || pendingRequest != null || arenaTerrainService.isBusy()) {
            return;
        }
        Player requester = Bukkit.getPlayer(queuedDuelStart.requesterId());
        Player target = Bukkit.getPlayer(queuedDuelStart.targetId());
        if (rejectQueuedPlayersOnline(requester, target)) {
            return;
        }
        if (rejectQueuedStartState(requester, target)) {
            return;
        }
        DuelSettings settings = queuedDuelStart.settings().copy();
        clearQueuedDuelStart();
        startDuel(requester, target, settings);
    }

    private boolean rejectQueuedPlayersOnline(Player requester, Player target) {
        if (requester != null && requester.isOnline() && target != null && target.isOnline()) {
            return false;
        }
        if (requester != null) {
            sendMessage(requester, MSG_TARGET_OFFLINE);
        }
        if (target != null) {
            sendMessage(target, MSG_TARGET_OFFLINE);
        }
        clearQueuedDuelStart();
        return true;
    }

    private boolean rejectQueuedStartState(Player requester, Player target) {
        return rejectQueuedLocation(requester, target) || rejectQueuedCombat(requester, target);
    }

    private boolean rejectQueuedLocation(Player requester, Player target) {
        if (isInsideMatchmakingSpawn(requester.getLocation()) && isInsideMatchmakingSpawn(target.getLocation())) {
            return false;
        }
        sendMessage(requester, MSG_MUST_BE_AT_SPAWN);
        sendMessage(target, MSG_MUST_BE_AT_SPAWN);
        clearQueuedDuelStart();
        return true;
    }

    private boolean rejectQueuedCombat(Player requester, Player target) {
        if (isCombatTagged(requester)) {
            sendMessage(requester, MSG_PLAYER_IN_COMBAT);
            sendMessage(target, MSG_TARGET_IN_COMBAT, PLAYER_PLACEHOLDER, requester.getName());
            clearQueuedDuelStart();
            return true;
        }
        if (!isCombatTagged(target)) {
            return false;
        }
        sendMessage(target, MSG_PLAYER_IN_COMBAT);
        sendMessage(requester, MSG_TARGET_IN_COMBAT, PLAYER_PLACEHOLDER, target.getName());
        clearQueuedDuelStart();
        return true;
    }

    private void clearQueuedDuelStart() {
        queuedDuelStart = null;
        cancelQueuedStartTask();
    }

    private void cancelQueuedStartTask() {
        if (queuedStartTask != null) {
            queuedStartTask.cancel();
            queuedStartTask = null;
        }
    }

    private boolean isInsideMatchmakingSpawn(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (!location.getWorld().getName().equalsIgnoreCase(matchmakingWorld)) {
            return false;
        }
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= matchmakingMinX && x <= matchmakingMaxX
            && y >= matchmakingMinY && y <= matchmakingMaxY
            && z >= matchmakingMinZ && z <= matchmakingMaxZ;
    }

    private void clearExternalCombatState(Player player) {
        if (combatTagPort != null) {
            combatTagPort.clearCombatState(player);
        }
    }

    private String formatAmount(double amount) {
        if (amount % 1D == 0D) {
            return String.valueOf((long) amount);
        }
        return String.format(Locale.US, "%.2f", amount);
    }

    private void startCountdown(Player requester, Player target) {
        cancelCountdownTask();
        if (startCountdownSeconds <= 0) {
            duelCountdownActive = false;
            return;
        }
        duelCountdownActive = true;
        applyCountdownLock(requester);
        applyCountdownLock(target);
        final int[] secondsLeft = {startCountdownSeconds};
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeDuel == null) {
                cancelCountdownTask();
                return;
            }
            if (secondsLeft[0] <= 0) {
                duelCountdownActive = false;
                clearCountdownLock(requester);
                clearCountdownLock(target);
                playCountdownSound(requester, true);
                playCountdownSound(target, true);
                showCountdownTitle(requester, "&aFight!", "&7You are released.");
                showCountdownTitle(target, "&aFight!", "&7You are released.");
                sendToParticipants("messages.duel-released");
                cancelCountdownTask();
                return;
            }
            playCountdownSound(requester, false);
            playCountdownSound(target, false);
            showCountdownTitle(requester, "&6" + secondsLeft[0], "&7Prepare to fight");
            showCountdownTitle(target, "&6" + secondsLeft[0], "&7Prepare to fight");
            sendToParticipants("messages.duel-countdown", "{seconds}", String.valueOf(secondsLeft[0]));
            secondsLeft[0]--;
        }, 0L, 20L);
    }

    private void applyCountdownLock(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (startCountdownSeconds + 2) * 20, 10, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, (startCountdownSeconds + 2) * 20, 250, false, false, false));
    }

    private void clearCountdownLock(Player player) {
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
    }

    private void showCountdownTitle(Player player, String title, String subtitle) {
        player.sendTitle(color(title), color(subtitle), 0, 15, 5);
    }

    private void playCountdownSound(Player player, boolean release) {
        player.playSound(
            player.getLocation(),
            release ? Sound.ENTITY_PLAYER_LEVELUP : Sound.BLOCK_NOTE_BLOCK_PLING,
            1.0F,
            release ? 1.0F : 1.5F
        );
    }

    private void requirePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("DuelService state mutations must run on the primary thread.");
        }
    }

    private record QueuedDuelStart(
        UUID requesterId,
        UUID targetId,
        String requesterName,
        String targetName,
        DuelSettings settings
    ) {
        private boolean involves(UUID playerId) {
            return requesterId.equals(playerId) || targetId.equals(playerId);
        }
    }

    public boolean consumePendingForcedDeath(UUID playerId) {
        return pendingForcedDeathIds.remove(playerId);
    }
}
