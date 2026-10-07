package dev.minecraft.warzoneduels.app;

import dev.minecraft.warzoneduels.BlockKey;
import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import dev.minecraft.warzoneduels.adapter.bukkit.gui.DuelGui;
import dev.minecraft.warzoneduels.adapter.bukkit.persistence.LoadoutArchiveStore;
import dev.minecraft.warzoneduels.adapter.bukkit.persistence.RuntimeStateStore;
import dev.minecraft.warzoneduels.adapter.bukkit.persistence.SpectatorSessionStore;
import dev.minecraft.warzoneduels.adapter.bukkit.reset.ArenaResetService;
import dev.minecraft.warzoneduels.domain.ActiveDuel;
import dev.minecraft.warzoneduels.domain.ArenaDefinition;
import dev.minecraft.warzoneduels.domain.BuilderSession;
import dev.minecraft.warzoneduels.domain.DuelEndReason;
import dev.minecraft.warzoneduels.domain.DuelDurationPolicy;
import dev.minecraft.warzoneduels.domain.DuelChallenge;
import dev.minecraft.warzoneduels.domain.DuelChallengeStatus;
import dev.minecraft.warzoneduels.domain.DuelMapOption;
import dev.minecraft.warzoneduels.domain.DuelMatchType;
import dev.minecraft.warzoneduels.domain.DuelParty;
import dev.minecraft.warzoneduels.domain.DuelRequest;
import dev.minecraft.warzoneduels.domain.DuelRuntimeState;
import dev.minecraft.warzoneduels.domain.DuelSettings;
import dev.minecraft.warzoneduels.domain.ExplosiveCombatPolicy;
import dev.minecraft.warzoneduels.domain.LoadoutSnapshot;
import dev.minecraft.warzoneduels.domain.MatchParticipant;
import dev.minecraft.warzoneduels.domain.MatchTeam;
import dev.minecraft.warzoneduels.domain.TeamOutcomePolicy;
import dev.minecraft.warzoneduels.domain.TeamEliminationOutcome;
import dev.minecraft.warzoneduels.domain.TeamMatchPolicy;
import dev.minecraft.warzoneduels.domain.TeamSpoilsPolicy;
import dev.minecraft.warzoneduels.domain.TeleportAllowanceReason;
import dev.minecraft.warzoneduels.domain.TypedTeleportAllowance;
import dev.minecraft.warzoneduels.domain.stats.PlayerDuelStats;
import dev.minecraft.warzoneduels.domain.terrain.ArenaMapOperationStatus;
import dev.minecraft.warzoneduels.port.EconomyPort;
import dev.minecraft.warzoneduels.port.SpawnPort;
import dev.minecraft.warzoneduels.port.CombatTagPort;
import dev.minecraft.warzoneduels.permission.PermissionPolicy;
import dev.minecraft.warzoneduels.util.SpearUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
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
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
    private final DuelPartyService partyService;
    private final DuelChallengeService challengeService;
    private final DuelCooldownService cooldownService;
    private CombatTagPort combatTagPort;

    private final Map<UUID, BuilderSession> builders = new ConcurrentHashMap<>();
    private final Map<UUID, TypedTeleportAllowance> teleportAllowances = new ConcurrentHashMap<>();
    private final Set<UUID> allowedArenaItemEntityIds = ConcurrentHashMap.newKeySet();
    private final Map<BlockKey, Long> allowedArenaItemSpawnLocations = new ConcurrentHashMap<>();
    private final Set<UUID> respawnToSpawn = ConcurrentHashMap.newKeySet();
    private final Set<UUID> eliminatedParticipantIds = ConcurrentHashMap.newKeySet();
    private final Set<UUID> recoveryTeleportIds = ConcurrentHashMap.newKeySet();
    private final Set<UUID> activeParticipantIndex = ConcurrentHashMap.newKeySet();
    private final Map<UUID, LoadoutSnapshot> disconnectSnapshots = new ConcurrentHashMap<>();
    private final Set<UUID> pendingForcedDeathIds = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> blockedItemMessageCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> arenaExitMessageCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Material> trackedExplosionSources = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> trackedExplosionOwners = new ConcurrentHashMap<>();
    private final Map<BlockKey, UUID> trackedBlockExplosionOwners = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastAttributedDamagers = new ConcurrentHashMap<>();
    private final Map<UUID, PendingDeath> pendingDeaths = new LinkedHashMap<>();
    private final Set<UUID> restoreLoadoutAfterRespawn = ConcurrentHashMap.newKeySet();
    private final Set<UUID> victoryFireworkIds = ConcurrentHashMap.newKeySet();
    private final SpectatorManager spectatorManager;

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
    private int duelTimeLimitSeconds;
    private int victoryMomentSeconds;
    private boolean victoryFireworks;
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
    private BukkitTask duelTimeLimitTask;
    private BukkitTask deathResolutionTask;
    private BukkitTask queuedStartTask;
    private BukkitTask containmentTask;
    private BukkitTask victoryTask;
    private boolean duelCountdownActive;
    private boolean duelEnding;
    private DuelBlockService blockService;

    public DuelService(
        WarzoneDuelsPlugin plugin,
        EconomyPort economyPort,
        SpawnPort spawnPort,
        RuntimeStateStore runtimeStateStore,
        LoadoutArchiveStore loadoutArchiveStore,
        SpectatorSessionStore spectatorSessionStore,
        ArenaResetService arenaResetService,
        SpoilsService spoilsService,
        StatsService statsService,
        DuelAnalyticsService duelAnalyticsService,
        ArenaMapService arenaMapService,
        ArenaTerrainService arenaTerrainService,
        CombatTagPort combatTagPort,
        DuelPartyService partyService,
        DuelChallengeService challengeService,
        DuelCooldownService cooldownService
    ) {
        this.plugin = plugin;
        this.economyPort = economyPort;
        this.spawnPort = spawnPort;
        this.runtimeStateStore = runtimeStateStore;
        this.loadoutArchiveStore = loadoutArchiveStore;
        this.spectatorManager = new SpectatorManager(
            plugin,
            spectatorSessionStore,
            this::activeParticipantIds,
            () -> arena,
            this::exitLocation
        );
        this.arenaResetService = arenaResetService;
        this.spoilsService = spoilsService;
        this.statsService = statsService;
        this.duelAnalyticsService = duelAnalyticsService;
        this.arenaMapService = arenaMapService;
        this.arenaTerrainService = arenaTerrainService;
        this.partyService = partyService;
        this.challengeService = challengeService;
        this.cooldownService = java.util.Objects.requireNonNull(cooldownService, "cooldownService");
        this.combatTagPort = combatTagPort;
    }

    /** Personal duel blocks (REQ-041); without a block service no blocks apply. */
    public void setBlockService(DuelBlockService blockService) {
        this.blockService = blockService;
    }

    /**
     * Refuses a duel or party pairing when any player on one side blocks any player on the other, or when blocks
     * cannot be read. The message never says which player set the block.
     */
    private boolean rejectDuelBlock(java.util.Collection<UUID> first, java.util.Collection<UUID> second, Player notify) {
        if (blockService == null || blockService.allows(first, second)) {
            return false;
        }
        sendMessageRaw(notify, prefix + ChatColor.RED + (blockService.isHealthy()
            ? "You can't duel or party with that player."
            : "Duel blocks are unavailable right now, so new duels and party invites are paused."));
        return true;
    }

    public DuelBlockService blockService() {
        return blockService;
    }

    /** A player may join only if no member of the party and the joining player block each other. */
    public boolean allowsPartyJoin(DuelParty party, Player joining) {
        List<UUID> members = party.members().stream().map(DuelParty.DuelPartyMember::playerId).toList();
        return !rejectDuelBlock(members, List.of(joining.getUniqueId()), joining);
    }

    /** Party invitations are refused when the invitee and any member of the inviting party block each other. */
    public boolean allowsPartyInvite(Player leader, Player invitee) {
        List<UUID> members = partyService.partyOf(leader.getUniqueId())
            .map(party -> party.members().stream().map(DuelParty.DuelPartyMember::playerId).toList())
            .orElse(List.of(leader.getUniqueId()));
        return !rejectDuelBlock(members, List.of(invitee.getUniqueId()), leader);
    }

    public void setCombatTagPort(CombatTagPort combatTagPort) {
        this.combatTagPort = combatTagPort;
    }

    public void enable() {
        loadoutArchiveStore.enable();
        reloadConfig();
        cooldownService.enable();
        recoveryTeleportIds.clear();
        recoveryTeleportIds.addAll(runtimeStateStore.loadRecoveryTeleportIds());
        restoreLoadoutAfterRespawn.clear();
        restoreLoadoutAfterRespawn.addAll(runtimeStateStore.loadPendingLoadoutRestoreIds());
        recoverActiveDuelIfNeeded();
        spectatorManager.enable();
    }

    public void disable(boolean serverStopping) {
        flushPendingDeathsBeforeTransition();
        spectatorManager.disable(serverStopping ? "server-shutdown" : "plugin-disable");
        cancelRequestExpiryTask();
        cancelDisconnectMonitorTask();
        cancelQueuedStartTask();
        cancelCountdownTask();
        cancelDuelTimeLimitTask();
        cancelDeathResolutionTask();
        cancelContainmentTask();
        cancelVictoryTask();

        if (preparingDuel != null) {
            refundWagerIfHeld(preparingDuel);
            preparingDuel = null;
        }

        if (duelEnding && activeDuel != null) {
            ActiveDuel endingDuel = activeDuel;
            teleportOnlineParticipantsToExit(endingDuel);
            endingDuel.participants().forEach(participant -> clearRespawnMarkerIfLiving(participant.playerId()));
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

        if (activeDuel != null) {
            runtimeStateStore.saveActiveDuelSync(activeDuel, eliminatedParticipantIds);
            runtimeStateStore.markReloadResume();
        } else {
            runtimeStateStore.clearRuntime();
            runtimeStateStore.clearReloadResumeMarker();
        }
        loadoutArchiveStore.shutdown();
    }

    public void reloadConfig() {
        plugin.reloadConfig();
        if (blockService != null && !blockService.isHealthy()) {
            blockService.enable();
        }
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
        duelTimeLimitSeconds = Math.max(0, config.getInt("settings.duel-time-limit-seconds", 0));
        cooldownService.configure(config.getLong("settings.duel-cooldown-seconds", 300),
            config.getLong("settings.repeat-opponent-cooldown-seconds", 86400));
        victoryMomentSeconds = Math.max(0, config.getInt("settings.victory-moment-seconds", 6));
        victoryFireworks = config.getBoolean("settings.victory-fireworks", true);
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
        spectatorManager.reload(config);
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
        return activeDuel != null
            && activeDuel.contains(playerId)
            && !eliminatedParticipantIds.contains(playerId);
    }

    public boolean shouldCancelVictoryMomentDamage(Player player) {
        return player != null
            && duelEnding
            && activeDuel != null
            && activeDuel.contains(player.getUniqueId())
            && !eliminatedParticipantIds.contains(player.getUniqueId());
    }

    public boolean handleVictoryMomentDeath(Player player) {
        requirePrimaryThread();
        if (player == null || !duelEnding || activeDuel == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (!activeDuel.contains(playerId) || !eliminatedParticipantIds.add(playerId)) {
            return false;
        }
        respawnToSpawn.add(playerId);
        activeParticipantIndex.remove(playerId);
        return true;
    }

    public boolean isParticipantRestricted(UUID playerId) {
        return activeParticipantIndex.contains(playerId);
    }

    public boolean consumeTeleportAllowance(UUID playerId, Location destination) {
        if (spectatorManager.isActiveWatcher(playerId)) {
            return spectatorManager.consumeTeleportAllowance(playerId, destination);
        }
        if (spectatorManager.consumeTeleportAllowance(playerId, destination)) {
            return true;
        }
        TypedTeleportAllowance allowance = teleportAllowances.remove(playerId);
        return allowance != null && allowance.matches(destination, System.currentTimeMillis());
    }

    public boolean shouldBlockExternalTeleportIntoActiveDuel(Player player, Location destination) {
        if (player == null || destination == null || activeDuel == null || isInActiveDuel(player.getUniqueId())) {
            return false;
        }
        if (spectatorManager.isActiveWatcher(player.getUniqueId())) {
            return false;
        }
        return spectatorManager.isInsideBoundary(destination) || arena != null && arena.contains(destination);
    }

    public void sendExternalTeleportIntoActiveDuelBlocked(Player player) {
        spectatorManager.sendExternalTeleportIntoActiveDuelBlocked(player);
    }

    public void startBuilder(Player sender, Player target) {
        requirePrimaryThread();
        if (!requirePermission(sender, PermissionPolicy.CHALLENGE)) {
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
            || rejectDuelBlock(List.of(sender.getUniqueId()), List.of(target.getUniqueId()), sender)
            || rejectCombatTaggedBuilder(sender, target)
            || rejectBusyBuilderPlayers(sender, target);
    }

    private boolean rejectUnavailableBuilder(Player sender) {
        if (!isArenaReady()) {
            sendMessage(sender, "messages.no-arena");
            return true;
        }
        if (activeDuel != null || pendingRequest != null || challengeService.activeChallengeCount() > 0
            || queuedDuelStart != null) {
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
        if (!requirePermission(requester, PermissionPolicy.CHALLENGE)) {
            return;
        }
        BuilderSession builder = builders.get(requester.getUniqueId());
        if (builder == null) {
            sendMessage(requester, "messages.no-builder");
            return;
        }
        if (pendingRequest != null || challengeService.activeChallengeCount() > 0
            || activeDuel != null || queuedDuelStart != null) {
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
        Optional<DuelParty> requesterParty = partyService.partyOf(requester.getUniqueId());
        Optional<DuelParty> targetParty = partyService.partyOf(target.getUniqueId());
        if (requesterParty.isPresent() || targetParty.isPresent()) {
            sendPartyRequest(requester, target, settings, requesterParty, targetParty);
            return;
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
        if (cooldownService.creditChallenge(List.of(requester.getUniqueId()), List.of(target.getUniqueId()))) {
            statsService.recordChallengeSent(requester.getUniqueId(), requester.getName());
        }
        builders.remove(requester.getUniqueId());
        sendMessage(requester, "messages.request-sent", PLAYER_PLACEHOLDER, target.getName());
        sendRequestDetails(target, pendingRequest);
        scheduleRequestExpiry();
    }

    private void sendPartyRequest(
        Player requester,
        Player target,
        DuelSettings settings,
        Optional<DuelParty> requesterParty,
        Optional<DuelParty> targetParty
    ) {
        if (requesterParty.isEmpty() || targetParty.isEmpty()) {
            sendMessageRaw(requester, prefix + ChatColor.RED + "Both players must lead a Duel Party for a party challenge.");
            return;
        }
        if (!requesterParty.get().leaderId().equals(requester.getUniqueId())
            || !targetParty.get().leaderId().equals(target.getUniqueId())) {
            sendMessageRaw(requester, prefix + ChatColor.RED + "Only both Duel Party leaders can create this challenge.");
            return;
        }
        if (!isDuelModeEnabled(requesterParty.get().size())) {
            sendMessageRaw(requester, ChatColor.RED + disabledModeMessage(requesterParty.get().size()));
            return;
        }
        if (settings.getWager() > NO_WAGER) {
            sendMessageRaw(requester, prefix + ChatColor.RED + "Party duel wagers are not supported yet.");
            return;
        }
        DuelChallenge challenge;
        try {
            challenge = challengeService.createPartyChallenge(
                requester.getUniqueId(), target.getUniqueId(), settings, System.currentTimeMillis()
            );
        } catch (IllegalArgumentException | IllegalStateException ex) {
            sendMessageRaw(requester, prefix + ChatColor.RED + ex.getMessage());
            return;
        }
        List<Player> participants = onlinePartyParticipants(challenge);
        if (participants == null) {
            sendMessage(requester, MSG_TARGET_OFFLINE);
            challengeService.cancel(requester.getUniqueId(), System.currentTimeMillis());
            return;
        }
        if (rejectPartyRoster(challenge, requester)) {
            challengeService.cancel(requester.getUniqueId(), System.currentTimeMillis());
            return;
        }
        if (cooldownService.creditChallenge(rosterIds(challenge.challengerTeam()), rosterIds(challenge.opponentTeam()))) {
            statsService.recordChallengeSent(requester.getUniqueId(), requester.getName());
        }
        builders.remove(requester.getUniqueId());
        String contract = prefix + ChatColor.GOLD + teamLabel(challenge.challengerTeam()) + ChatColor.YELLOW + " vs "
            + ChatColor.GOLD + teamLabel(challenge.opponentTeam()) + ChatColor.YELLOW + ": use /duel accept to review and confirm.";
        sendRaw(participants, contract);
        schedulePartyChallengeExpiry(challenge);
    }

    private List<Player> onlinePartyParticipants(DuelChallenge challenge) {
        List<Player> participants = java.util.stream.Stream.concat(
                challenge.challengerTeam().participants().stream(),
                challenge.opponentTeam().participants().stream()
            )
            .map(participant -> Bukkit.getPlayer(participant.playerId()))
            .toList();
        return participants.stream().anyMatch(player -> player == null || !player.isOnline()) ? null : participants;
    }

    private boolean rejectPartyRoster(DuelChallenge challenge, Player requester) {
        if (rejectCooldown(challenge.challengerTeam(), challenge.opponentTeam(), List.of(requester))) {
            return true;
        }
        if (rejectDuelBlock(rosterIds(challenge.challengerTeam()), rosterIds(challenge.opponentTeam()), requester)) {
            return true;
        }
        List<Player> participants = onlinePartyParticipants(challenge);
        if (participants == null) {
            sendMessage(requester, MSG_TARGET_OFFLINE);
            return true;
        }
        int firstTeamSize = challenge.challengerTeam().participants().size();
        if (hasOpposingSameIp(participants.subList(0, firstTeamSize),
            participants.subList(firstTeamSize, participants.size()))) {
            sendMessage(requester, "messages.same-ip-blocked");
            return true;
        }
        for (Player participant : participants) {
            if (isCombatTagged(participant)) {
                sendMessageRaw(requester, prefix + ChatColor.RED + participant.getName() + " is currently in combat.");
                return true;
            }
            if (!isInsideMatchmakingSpawn(participant.getLocation())) {
                sendMessageRaw(requester, prefix + ChatColor.RED + participant.getName() + " must be inside the matchmaking spawn.");
                return true;
            }
            if (isParticipantRestricted(participant.getUniqueId())) {
                sendMessageRaw(requester, prefix + ChatColor.RED + participant.getName() + " is already busy.");
                return true;
            }
        }
        return false;
    }

    private boolean rejectRequestPlayers(Player requester, Player target) {
        if (rejectCooldown(requester, target)) {
            return true;
        }
        if (rejectDuelBlock(List.of(requester.getUniqueId()), List.of(target.getUniqueId()), requester)) {
            return true;
        }
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
        if (!requirePermission(target, PermissionPolicy.ACCEPT)) {
            return;
        }
        if (challengeService.challengeForParticipant(target.getUniqueId(), System.currentTimeMillis()).isPresent()) {
            openPendingRequestReview(target);
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
        if (!requirePermission(target, PermissionPolicy.ACCEPT)) {
            return;
        }
        Optional<DuelChallenge> partyChallenge = challengeService.challengeForParticipant(
            target.getUniqueId(), System.currentTimeMillis()
        );
        if (partyChallenge.isPresent()) {
            confirmPartyChallenge(target, partyChallenge.get());
            return;
        }
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

    private void confirmPartyChallenge(Player participant, DuelChallenge challenge) {
        int teamSize = challenge.challengerTeam().size();
        if (!isDuelModeEnabled(teamSize)) {
            challengeService.challengeForParticipant(participant.getUniqueId(), System.currentTimeMillis())
                .ifPresent(current -> challengeService.complete(current.id()));
            cancelRequestExpiryTask();
            sendMessageRaw(participant, ChatColor.RED + disabledModeMessage(teamSize)
                + " The challenge was cancelled and both rosters unlocked.");
            return;
        }
        if (rejectCooldown(challenge.challengerTeam(), challenge.opponentTeam(), List.of(participant))) {
            challengeService.cancel(participant.getUniqueId(), System.currentTimeMillis());
            cancelRequestExpiryTask();
            return;
        }
        DuelChallengeStatus status;
        try {
            status = challengeService.accept(participant.getUniqueId(), System.currentTimeMillis());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            sendMessageRaw(participant, prefix + ChatColor.RED + ex.getMessage());
            return;
        }
        List<Player> participants = onlinePartyParticipants(challenge);
        if (participants != null) {
            sendRaw(participants, prefix + ChatColor.GREEN + participant.getName() + " accepted the party duel ("
                + challenge.acceptedCount() + "/" + challenge.requiredAcceptanceCount() + ").");
        }
        if (status != DuelChallengeStatus.READY) {
            return;
        }
        if (rejectPartyRoster(challenge, participant) || !startPartyDuel(challenge)) {
            challengeService.cancel(participant.getUniqueId(), System.currentTimeMillis());
            if (participants != null) {
                sendRaw(participants, prefix + ChatColor.RED + "The party duel could not start; the roster lock was released.");
            }
            return;
        }
        challengeService.complete(challenge.id());
        cancelRequestExpiryTask();
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
        if (rejectCooldown(requester, target, List.of(requester, target))) {
            clearPendingRequest();
            return true;
        }
        // A block added after the request was sent still stops the duel.
        if (rejectDuelBlock(List.of(requester.getUniqueId()), List.of(target.getUniqueId()), target)) {
            clearPendingRequest();
            return true;
        }
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
        Optional<DuelChallenge> partyChallenge = challengeService.challengeForParticipant(
            target.getUniqueId(), System.currentTimeMillis()
        );
        if (partyChallenge.isPresent()) {
            DuelChallenge challenge = partyChallenge.get();
            target.openInventory(DuelGui.buildRequestPreviewGui(
                teamLabel(challenge.challengerTeam()), challenge.settings()
            ));
            return;
        }
        if (pendingRequest == null || !pendingRequest.targetId().equals(target.getUniqueId())) {
            sendMessage(target, MSG_NO_PENDING_REQUEST);
            return;
        }
        target.openInventory(DuelGui.buildRequestPreviewGui(pendingRequest.requesterName(), pendingRequest.settings()));
    }

    public void denyRequest(Player target) {
        requirePrimaryThread();
        if (!requirePermission(target, PermissionPolicy.DENY)) {
            return;
        }
        Optional<DuelChallenge> partyChallenge = challengeService.challengeForParticipant(
            target.getUniqueId(), System.currentTimeMillis()
        );
        if (partyChallenge.isPresent()) {
            DuelChallenge challenge = partyChallenge.get();
            List<Player> participants = onlinePartyParticipants(challenge);
            challengeService.decline(target.getUniqueId(), System.currentTimeMillis());
            cancelRequestExpiryTask();
            if (participants != null) {
                sendRaw(participants, prefix + ChatColor.YELLOW + target.getName() + " declined the party duel.");
            }
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
        if (!requirePermission(player, PermissionPolicy.DRAW)) {
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
        if (activeDuel == null || duelEnding) {
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
        if (TeamMatchPolicy.allSurvivorsRequestedDraw(activeDuel, eliminatedParticipantIds)) {
            concludeDuel((Player) null, DuelEndReason.DRAW, true);
            return;
        }
        runtimeStateStore.queueActiveDuelSave(activeDuel, eliminatedParticipantIds);
    }

    public void showSettings(Player player) {
        if (!requirePermission(player, PermissionPolicy.INFO)) {
            return;
        }
        if (activeDuel == null) {
            sendMessage(player, "messages.settings-none");
            return;
        }
        player.openInventory(DuelGui.buildActiveSettingsGui(activeDuel.settings()));
    }

    public void watchDuel(Player player) {
        requirePrimaryThread();
        if (!player.hasPermission(PermissionPolicy.SPECTATE_USE)) {
            sendMessage(player, "messages.no-spectate-permission");
            return;
        }
        if (spectatorManager.isActiveWatcher(player.getUniqueId())) {
            spectatorManager.restore(player, "watch-toggle", true);
            return;
        }
        if (activeDuel == null) {
            sendMessageOrFallback(player, "messages.duel-watch-unavailable", ChatColor.RED + "There is no active duel to watch.");
            return;
        }
        if (activeDuel.contains(player.getUniqueId())) {
            sendMessageOrFallback(player, "messages.duel-watch-participant", ChatColor.RED + "You are already participating in this duel.");
            return;
        }
        if (blockCombatEntry && isCombatTagged(player) && !player.hasPermission(PermissionPolicy.BYPASS_COMBAT_ENTRY)) {
            sendMessage(player, "messages.arena-combat-entry-blocked");
            return;
        }
        if (builders.containsKey(player.getUniqueId())
            || pendingRequest != null && (pendingRequest.requesterId().equals(player.getUniqueId()) || pendingRequest.targetId().equals(player.getUniqueId()))
            || queuedDuelStart != null && queuedDuelStart.involves(player.getUniqueId())) {
            sendMessageOrFallback(player, "messages.duel-watch-incompatible", ChatColor.RED + "Finish or cancel your current duel setup before watching.");
            return;
        }
        spectatorManager.enter(player);
    }

    public boolean leaveWatchMode(Player player, String reason, boolean notify) {
        return spectatorManager.restore(player, reason, notify);
    }

    public void reloadFromCommand(CommandSender sender) {
        requirePrimaryThread();
        if (!requirePermission(sender, PermissionPolicy.ADMIN_RELOAD)) {
            return;
        }
        if (arenaTerrainService.isBusy()) {
            sendMessageRaw(sender, prefix + ChatColor.RED + "Arena terrain is busy. Wait for the current map operation to finish first.");
            return;
        }
        reloadConfig();
        if (plugin.spoilsService() != null) {
            plugin.spoilsService().reloadConfig();
        }
        if (!cooldownService.recover()) {
            sendMessageRaw(sender, prefix + ChatColor.RED + "Cooldown storage remains unavailable; new duels remain blocked.");
        }
        sendMessageRaw(sender, prefix + ChatColor.GREEN + "Config reloaded.");
    }

    public void saveMapSnapshot(CommandSender sender, String rawMapId) {
        requirePrimaryThread();
        if (!requirePermission(sender, PermissionPolicy.ADMIN_MAP_SAVE)) {
            return;
        }
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
        if (!requirePermission(sender, PermissionPolicy.ADMIN_MAP_LOAD)) {
            return;
        }
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
        if (!requirePermission(sender, PermissionPolicy.ADMIN_MAP_STATUS)) {
            return;
        }
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
        if (!requirePermission(sender, PermissionPolicy.ADMIN_RESTORE_LOADOUT)) {
            return;
        }
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
        if (spectatorManager.isActiveWatcher(player.getUniqueId()) || spectatorManager.hasRecoverableSession(player.getUniqueId())) {
            spectatorManager.restore(player, "player-quit", false);
        }
        builders.remove(player.getUniqueId());
        Optional<DuelChallenge> partyChallenge = challengeService.challengeForParticipant(
            player.getUniqueId(), System.currentTimeMillis()
        );
        if (partyChallenge.isPresent()) {
            DuelChallenge challenge = partyChallenge.get();
            List<Player> participants = onlinePartyParticipants(challenge);
            challengeService.cancel(player.getUniqueId(), System.currentTimeMillis());
            cancelRequestExpiryTask();
            if (participants != null) {
                sendRaw(participants, prefix + ChatColor.YELLOW + "The party duel was canceled because a participant left.");
            }
        }
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
        partyService.partyOf(player.getUniqueId()).ifPresent(party -> {
            if (party.leaderId().equals(player.getUniqueId()) && !party.isRosterLocked()) {
                partyService.leaveParty(player.getUniqueId());
                for (DuelParty.DuelPartyMember member : party.members()) {
                    Player online = Bukkit.getPlayer(member.playerId());
                    if (online != null) {
                        sendMessageRaw(online, ChatColor.YELLOW + "The Duel Party was disbanded because its leader disconnected.");
                    }
                }
            }
        });
        if (pendingRequest != null) {
            if (pendingRequest.requesterId().equals(player.getUniqueId()) || pendingRequest.targetId().equals(player.getUniqueId())) {
                clearPendingRequest();
            }
        }
        if (activeDuel == null) {
            return;
        }
        MatchParticipant participant = activeDuel.participant(player.getUniqueId());
        if (participant == null || duelEnding) {
            return;
        }
        disconnectSnapshots.put(player.getUniqueId(), loadoutArchiveStore.capture(player));
        participant.setDisconnectDeadlineEpochMs(System.currentTimeMillis() + (disconnectGraceSeconds * 1000L));
        sendToParticipants("messages.disconnect-grace", PLAYER_PLACEHOLDER, participant.name(), "{seconds}", String.valueOf(disconnectGraceSeconds));
        startDisconnectMonitor();
        runtimeStateStore.queueActiveDuelSave(activeDuel, eliminatedParticipantIds);
    }

    public void handleJoin(Player player) {
        requirePrimaryThread();
        if (!spectatorManager.recoverOnJoin(player)) {
            return;
        }
        spectatorManager.handleViewerJoin(player);
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
        if (restoreLoadoutAfterRespawn.contains(player.getUniqueId()) && !player.isDead()) {
            Bukkit.getScheduler().runTask(plugin, () -> restoreArchivedLoadout(player));
        }
        if (shouldBlockArenaFootprintEntry(player, player.getLocation())) {
            handleUnauthorizedArenaEntry(player);
        }
        if (activeDuel == null) {
            clearCompletedRespawnMarker(player);
            return;
        }
        MatchParticipant participant = activeDuel.participant(player.getUniqueId());
        if (participant == null) {
            return;
        }
        if (eliminatedParticipantIds.contains(player.getUniqueId())) {
            teleportToExit(player);
            return;
        }
        if (duelEnding) {
            if (eliminatedParticipantIds.contains(player.getUniqueId())) {
                teleportToExit(player);
            }
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
        runtimeStateStore.queueActiveDuelSave(activeDuel, eliminatedParticipantIds);
    }

    public void handleRespawn(PlayerRespawnEvent event) {
        requirePrimaryThread();
        UUID playerId = event.getPlayer().getUniqueId();
        if (spectatorManager.hasRecoverableSession(playerId)) {
            Location location = exitLocation();
            if (location != null) {
                event.setRespawnLocation(location);
            }
            Bukkit.getScheduler().runTask(plugin, () -> spectatorManager.restore(event.getPlayer(), "unexpected-watcher-death", false));
            return;
        }
        if (respawnToSpawn.remove(playerId) || recoveryTeleportIds.contains(playerId)) {
            Location location = exitLocation();
            if (location != null) {
                event.setRespawnLocation(location);
            }
        }
        if (restoreLoadoutAfterRespawn.contains(playerId)) {
            Bukkit.getScheduler().runTask(plugin, () -> restoreArchivedLoadout(event.getPlayer()));
        }
    }

    public void handleDeath(Player player, Player killer, List<ItemStack> drops) {
        requirePrimaryThread();
        if (activeDuel == null || duelEnding) {
            return;
        }
        MatchParticipant dead = activeDuel.participant(player.getUniqueId());
        if (dead == null) {
            return;
        }
        if (!eliminatedParticipantIds.add(dead.playerId())) {
            return;
        }
        UUID killerId = attributedKillerId(dead.playerId(), killer);
        pendingDeaths.put(dead.playerId(), new PendingDeath(dead, killerId, List.copyOf(drops)));
        respawnToSpawn.add(dead.playerId());
        activeParticipantIndex.remove(dead.playerId());
        runtimeStateStore.queueActiveDuelSave(activeDuel, eliminatedParticipantIds);
        if (deathResolutionTask == null) {
            deathResolutionTask = Bukkit.getScheduler().runTask(plugin, this::resolvePendingDeaths);
        }
    }

    private UUID attributedKillerId(UUID defeatedPlayerId, Player bukkitKiller) {
        UUID bukkitKillerId = bukkitKiller == null ? null : bukkitKiller.getUniqueId();
        MatchTeam opposingTeam = activeDuel == null ? null : activeDuel.opposingTeam(defeatedPlayerId);
        if (opposingTeam != null && opposingTeam.contains(bukkitKillerId)) {
            return bukkitKillerId;
        }
        UUID attributedId = lastAttributedDamagers.remove(defeatedPlayerId);
        return opposingTeam != null && opposingTeam.contains(attributedId) ? attributedId : null;
    }

    private void flushPendingDeathsBeforeTransition() {
        if (!pendingDeaths.isEmpty() && activeDuel != null && !duelEnding) {
            cancelDeathResolutionTask();
            resolvePendingDeaths();
        }
    }

    private void resolvePendingDeaths() {
        requirePrimaryThread();
        deathResolutionTask = null;
        if (activeDuel == null || duelEnding || pendingDeaths.isEmpty()) {
            pendingDeaths.clear();
            return;
        }
        TeamEliminationOutcome outcome = TeamMatchPolicy.eliminationOutcome(activeDuel, eliminatedParticipantIds);
        if (outcome == TeamEliminationOutcome.DRAW) {
            pendingDeaths.keySet().forEach(restoreLoadoutAfterRespawn::add);
            runtimeStateStore.savePendingLoadoutRestoreIds(restoreLoadoutAfterRespawn);
            for (UUID playerId : pendingDeaths.keySet()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline() && !player.isDead()) {
                    restoreArchivedLoadout(player);
                }
            }
            pendingDeaths.clear();
            concludeDuel((Player) null, DuelEndReason.DRAW, true);
            return;
        }
        for (PendingDeath pendingDeath : pendingDeaths.values()) {
            createSpoils(pendingDeath);
        }
        List<MatchParticipant> resolvedDeaths = pendingDeaths.values().stream().map(PendingDeath::participant).toList();
        pendingDeaths.clear();
        if (outcome == TeamEliminationOutcome.CONTINUE) {
            for (MatchParticipant participant : resolvedDeaths) {
                sendToParticipants("messages.player-eliminated", PLAYER_PLACEHOLDER, participant.name());
            }
            return;
        }
        MatchTeam winningTeam = outcome == TeamEliminationOutcome.TEAM_ONE_WINS
            ? activeDuel.teamOne()
            : activeDuel.teamTwo();
        concludeDuel(winningTeam, DuelEndReason.KILL, true);
    }

    private void createSpoils(PendingDeath pendingDeath) {
        MatchParticipant dead = pendingDeath.participant();
        MatchParticipant spoilsRecipient = selectSpoilsRecipient(dead.playerId(), pendingDeath.killerId());
        if (spoilsRecipient != null) {
            spoilsService.createSpoils(
                spoilsRecipient.playerId(), spoilsRecipient.name(), dead.playerId(), dead.name(), pendingDeath.drops()
            );
        }
    }

    private MatchParticipant selectSpoilsRecipient(UUID defeatedPlayerId, UUID killerId) {
        return TeamSpoilsPolicy.recipient(
            activeDuel,
            defeatedPlayerId,
            killerId,
            eliminatedParticipantIds
        );
    }

    private void restoreArchivedLoadout(Player player) {
        LoadoutSnapshot snapshot = loadoutArchiveStore.loadLatestPreDuel(player.getUniqueId());
        if (snapshot != null && player.isOnline()) {
            loadoutArchiveStore.apply(player, snapshot);
            restoreLoadoutAfterRespawn.remove(player.getUniqueId());
            runtimeStateStore.clearPendingLoadoutRestoreId(player.getUniqueId());
        }
    }

    private Player winnerRepresentative(MatchTeam winningTeam) {
        return TeamMatchPolicy.survivingParticipants(winningTeam, eliminatedParticipantIds).stream()
            .map(participant -> Bukkit.getPlayer(participant.playerId()))
            .filter(java.util.Objects::nonNull)
            .filter(Player::isOnline)
            .findFirst()
            .orElse(null);
    }

    private void concludeDuel(MatchTeam winningTeam, DuelEndReason reason, boolean broadcastOutcome) {
        MatchParticipant representative = TeamMatchPolicy.survivingParticipants(winningTeam, eliminatedParticipantIds).stream()
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("A winning team requires a surviving participant."));
        Player onlineRepresentative = winnerRepresentative(winningTeam);
        concludeDuel(representative.playerId(), representative.name(), onlineRepresentative, reason, broadcastOutcome);
    }

    public void handleAsyncChat(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> sendMessage(player, "messages.chat-blocked"));
    }

    public void handleArenaExitAttempt(Player player) {
        requirePrimaryThread();
        teleportToAssignedSpawn(player);
        sendArenaExitBlockedMessage(player);
    }

    public void handleKick(Player player) {
        requirePrimaryThread();
        if (spectatorManager.isActiveWatcher(player.getUniqueId()) || spectatorManager.hasRecoverableSession(player.getUniqueId())) {
            spectatorManager.restore(player, "player-kick", false);
        }
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

    public boolean isActiveWatcher(UUID playerId) {
        return spectatorManager.isActiveWatcher(playerId);
    }

    public boolean shouldBlockWatcherAction(Player player) {
        return spectatorManager.shouldBlockAction(player);
    }

    public boolean isAllowedCommandForWatcher(String rawCommand) {
        return spectatorManager.isAllowedCommand(rawCommand);
    }

    public boolean shouldBlockWatcherTeleport(Player player) {
        return spectatorManager.shouldBlockTeleport(player);
    }

    public void enforceWatcherState(Player player) {
        spectatorManager.enforce(player);
    }

    public void sendWatcherActionBlocked(Player player) {
        spectatorManager.sendActionBlocked(player);
    }

    public void sendWatcherTeleportBlocked(Player player) {
        spectatorManager.sendTeleportBlocked(player);
    }

    public boolean recoverWatcher(Player administrator, Player target) {
        if (!requirePermission(administrator, PermissionPolicy.ADMIN_RECOVER_WATCHER)) {
            return false;
        }
        return spectatorManager.recoverByAdmin(administrator, target);
    }

    public boolean hasRecoverableWatcherSession(UUID playerId) {
        return spectatorManager.hasRecoverableSession(playerId);
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
        return player != null && player.hasPermission(PermissionPolicy.BYPASS_BUILD);
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
        if (player != null && player.hasPermission(PermissionPolicy.BYPASS_BUILD)) {
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
        if (isInActiveDuel(player.getUniqueId()) || player.hasPermission(PermissionPolicy.BYPASS_COMBAT_ENTRY)) {
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
        if (isInActiveDuel(player.getUniqueId()) || player.hasPermission(PermissionPolicy.BYPASS_ARENA_ENTRY)) {
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

    public void trackExplosionSource(UUID entityId, Material sourceMaterial, UUID ownerId) {
        if (entityId != null && sourceMaterial != null) {
            trackedExplosionSources.put(entityId, sourceMaterial);
        }
        if (entityId != null && ownerId != null && isInActiveDuel(ownerId)) {
            trackedExplosionOwners.put(entityId, ownerId);
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
            trackedExplosionOwners.remove(entityId);
        }
    }

    public void clearExplosionSourceNextTick(UUID entityId) {
        if (entityId != null) {
            Bukkit.getScheduler().runTask(plugin, () -> clearExplosionSource(entityId));
        }
    }

    public UUID explosionOwner(UUID entityId) {
        return entityId == null ? null : trackedExplosionOwners.get(entityId);
    }

    public void trackBlockExplosionSource(Block block, UUID ownerId) {
        if (block != null && ownerId != null && isInActiveDuel(ownerId)) {
            trackedBlockExplosionOwners.put(BlockKey.fromLocation(block.getLocation()), ownerId);
        }
    }

    public UUID blockExplosionOwner(Location location) {
        return location == null ? null : trackedBlockExplosionOwners.get(BlockKey.fromLocation(location));
    }

    public void clearBlockExplosionSourceNextTick(Location location) {
        if (location == null) {
            return;
        }
        BlockKey key = BlockKey.fromLocation(location);
        Bukkit.getScheduler().runTask(plugin, () -> trackedBlockExplosionOwners.remove(key));
    }

    public boolean shouldCancelExplosiveDamage(Player victim, UUID attributedAttackerId) {
        return ExplosiveCombatPolicy.shouldCancelDamage(activeDuel, victim.getUniqueId(), attributedAttackerId);
    }

    public void recordAttributedDamage(Player victim, UUID attributedAttackerId) {
        if (activeDuel == null || victim == null || attributedAttackerId == null) {
            return;
        }
        MatchTeam opposingTeam = activeDuel.opposingTeam(victim.getUniqueId());
        if (opposingTeam != null && opposingTeam.contains(attributedAttackerId)) {
            lastAttributedDamagers.put(victim.getUniqueId(), attributedAttackerId);
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
        return !isInActiveDuel(attacker.getUniqueId())
            || TeamMatchPolicy.isFriendlyFire(activeDuel, attacker.getUniqueId(), victim.getUniqueId());
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
        int firstTeamIndex = participantIndex(activeDuel.teamOne(), playerId);
        if (firstTeamIndex >= 0) {
            return arena.teamSpawn(0, firstTeamIndex);
        }
        int secondTeamIndex = participantIndex(activeDuel.teamTwo(), playerId);
        if (secondTeamIndex >= 0) {
            return arena.teamSpawn(1, secondTeamIndex);
        }
        return null;
    }

    private int participantIndex(MatchTeam team, UUID playerId) {
        for (int index = 0; index < team.participants().size(); index++) {
            if (team.participants().get(index).playerId().equals(playerId)) {
                return index;
            }
        }
        return -1;
    }

    public void updateArenaLocation(Player actor, String key, Location location) {
        requirePrimaryThread();
        String permission = switch (key) {
            case "setpos1", "setpos2" -> PermissionPolicy.ADMIN_ARENA_SET_POS;
            case "setspawn1", "setspawn2" -> PermissionPolicy.ADMIN_ARENA_SET_SPAWN;
            case "setspectator" -> PermissionPolicy.ADMIN_ARENA_SET_SPECTATOR;
            case "setexit" -> PermissionPolicy.ADMIN_ARENA_SET_EXIT;
            default -> "";
        };
        if (permission.isBlank() || !requirePermission(actor, permission)) {
            return;
        }
        FileConfiguration config = plugin.getConfig();
        config.set("arena.world", location.getWorld() == null ? "world" : location.getWorld().getName());
        String value = location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
        switch (key) {
            case "setpos1" -> config.set("arena.pos1", value);
            case "setpos2" -> config.set("arena.pos2", value);
            case "setspawn1" -> config.set("arena.spawn1", value);
            case "setspawn2" -> config.set("arena.spawn2", value);
            case "setspectator" -> config.set("arena.spectator", value);
            case "setexit" -> config.set("arena.exit", value);
            default -> {
                return;
            }
        }
        plugin.saveConfig();
        reloadConfig();
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
            Set<UUID> playerIds = persistedRuntime.activeDuel().participants().stream()
                .map(MatchParticipant::playerId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
            recoveryTeleportIds.addAll(playerIds);
            runtimeStateStore.saveRecoveryTeleportIds(recoveryTeleportIds);
            runtimeStateStore.clearRuntime();
            plugin.getLogger().warning("Found stale duel runtime data without a reload marker. Match was canceled for safety.");
            return;
        }
        activeDuel = persistedRuntime.activeDuel();
        eliminatedParticipantIds.clear();
        eliminatedParticipantIds.addAll(persistedRuntime.eliminatedParticipantIds());
        arenaMapService.prepareArenaForMatch(arena, activeDuel.settings());
        rebuildParticipantIndex();
        for (MatchParticipant participant : activeDuel.participants()) {
            UUID playerId = participant.playerId();
            if (eliminatedParticipantIds.contains(playerId)) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                clearExternalCombatState(player);
                teleportToAssignedSpawn(player);
                sendMessage(player, "messages.duel-resumed");
            }
        }
        startDisconnectMonitor();
        startContainmentMonitor();
        startDuelTimeLimit();
    }

    private void handleServerStoppingDisable() {
        clearQueuedDuelStart();
        if (activeDuel == null) {
            return;
        }
        ActiveDuel finishedDuel = activeDuel;
        Set<UUID> participantIds = finishedDuel.participants().stream()
            .map(MatchParticipant::playerId)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
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
        MatchTeam firstTeam = MatchTeam.singleton(new MatchParticipant(requester.getUniqueId(), requester.getName()));
        MatchTeam secondTeam = MatchTeam.singleton(new MatchParticipant(target.getUniqueId(), target.getName()));
        startDuel(firstTeam, secondTeam, List.of(requester, target), settings);
    }

    public boolean isDuelModeEnabled(int teamSize) {
        return switch (teamSize) {
            case 1 -> true;
            case 2, 3 -> plugin.getConfig().getBoolean(modeConfigPath(teamSize), true);
            default -> false;
        };
    }

    public void setDuelModeEnabled(int teamSize, boolean enabled) throws java.io.IOException {
        String path = modeConfigPath(teamSize);
        FileConfiguration config = plugin.getConfig();
        Object previous = config.get(path);
        config.set(path, enabled);
        try {
            config.save(new java.io.File(plugin.getDataFolder(), "config.yml"));
        } catch (java.io.IOException ex) {
            config.set(path, previous);
            throw ex;
        }
    }

    private String modeConfigPath(int teamSize) {
        if (teamSize != 2 && teamSize != 3) {
            throw new IllegalArgumentException("Only 2v2 and 3v3 can be toggled.");
        }
        return "settings.duel-modes." + teamSize + "v" + teamSize + "-enabled";
    }

    private String disabledModeMessage(int teamSize) {
        return teamSize + "v" + teamSize + " duels are disabled by an administrator.";
    }

    public boolean startPartyDuel(DuelChallenge challenge) {
        requirePrimaryThread();
        if (challenge == null || challenge.status(System.currentTimeMillis()) != DuelChallengeStatus.READY
            || !isDuelModeEnabled(challenge.challengerTeam().size())
            || activeDuel != null || preparingDuel != null || queuedDuelStart != null || arenaTerrainService.isBusy()) {
            return false;
        }
        List<Player> participants = java.util.stream.Stream.concat(
                challenge.challengerTeam().participants().stream(),
                challenge.opponentTeam().participants().stream()
            )
            .map(participant -> Bukkit.getPlayer(participant.playerId()))
            .toList();
        if (participants.stream().anyMatch(player -> player == null || !player.isOnline())) {
            return false;
        }
        return startDuel(challenge.challengerTeam(), challenge.opponentTeam(), participants, challenge.settings());
    }

    private boolean startDuel(MatchTeam firstTeam, MatchTeam secondTeam, List<Player> participants, DuelSettings settings) {
        if (!isDuelModeEnabled(firstTeam.size())) {
            sendRaw(participants, prefix + ChatColor.RED + disabledModeMessage(firstTeam.size()));
            return false;
        }
        if (rejectCooldown(firstTeam, secondTeam, participants)) {
            return false;
        }
        if (!cooldownService.ensureWritable()) {
            rejectCooldown(firstTeam, secondTeam, participants);
            return false;
        }
        if (!arenaTerrainService.isReady()) {
            sendRaw(participants, prefix + ChatColor.RED + "Arena terrain footprint is not loaded.");
            return false;
        }
        DuelSettings preparedSettings = settings.copy();
        arenaMapService.sanitizeSettings(preparedSettings);
        DuelMapOption selectedMap = arenaMapService.resolve(preparedSettings.getMapId());
        if (!arenaTerrainService.hasSnapshot(selectedMap.id())) {
            sendRaw(participants, prefix + ChatColor.RED + "The selected arena map is not saved yet: " + selectedMap.displayName() + ".");
            return false;
        }

        DuelMatchType matchType = firstTeam.size() == 1 ? DuelMatchType.NORMAL : DuelMatchType.PARTY;
        ActiveDuel stagedDuel = new ActiveDuel(matchType, firstTeam, secondTeam, preparedSettings, System.currentTimeMillis());
        try {
            TeamOutcomePolicy.validateWager(stagedDuel);
        } catch (IllegalArgumentException ex) {
            sendRaw(participants, prefix + ChatColor.RED + ex.getMessage());
            return false;
        }
        for (Player participant : participants) {
            loadoutArchiveStore.saveLatestPreDuel(participant, loadoutArchiveStore.capture(participant));
        }

        if (preparedSettings.getWager() > NO_WAGER
            && !holdWager(stagedDuel, participants.get(0), participants.get(1), preparedSettings.getWager())) {
            sendRaw(participants, prefix + ChatColor.RED + "A participant cannot afford this wager.");
            return false;
        }

        preparingDuel = stagedDuel;
        sendRaw(participants, ChatColor.YELLOW + "Preparing arena terrain...");
        arenaTerrainService.loadSnapshot(selectedMap.id(), () -> {
            if (preparingDuel != stagedDuel) {
                refundWagerIfHeld(stagedDuel);
                return;
            }
            if (rejectCooldown(firstTeam, secondTeam, participants)) {
                preparingDuel = null;
                refundWagerIfHeld(stagedDuel);
                return;
            }
            preparingDuel = null;
            activeDuel = stagedDuel;
            duelEnding = false;
            queuedDuelStart = null;
            rebuildParticipantIndex();
            duelCountdownActive = true;
            arenaMapService.prepareArenaForMatch(arena, activeDuel.settings());
            for (Player participant : participants) {
                clearExternalCombatState(participant);
                prepareCombatant(participant);
                teleportToAssignedSpawn(participant);
            }
            rebuildParticipantIndex();
            startContainmentMonitor();
            for (Player participant : participants) {
                sendMessage(participant, "messages.duel-risk-warning");
                sendMessageRaw(participant, ChatColor.RED + "Disconnecting gives you " + disconnectGraceSeconds + " seconds to rejoin before elimination.");
            }
            String wagerText = preparedSettings.getWager() > NO_WAGER ? " for $" + formatAmount(preparedSettings.getWager()) : "";
            broadcast("messages.duel-start", "{p1}", teamLabel(firstTeam), "{p2}", teamLabel(secondTeam), "{wager}", wagerText);
            broadcastWatchPrompt(activeDuel.participants().stream().map(MatchParticipant::playerId).collect(java.util.stream.Collectors.toSet()));
            runtimeStateStore.queueActiveDuelSave(activeDuel, eliminatedParticipantIds, 1L);
            startCountdown(participants);
        }, message -> {
            if (preparingDuel == stagedDuel) {
                preparingDuel = null;
            }
            refundWagerIfHeld(stagedDuel);
            sendRaw(participants, ChatColor.RED + message);
            arenaTerrainService.ensureDefaultSnapshotLoaded(logMessage -> plugin.getLogger().warning(logMessage));
        });
        return true;
    }

    private void concludeDuel(Player winner, DuelEndReason reason, boolean broadcastOutcome) {
        concludeDuel(
            winner == null ? null : winner.getUniqueId(),
            winner == null ? null : winner.getName(),
            winner,
            reason,
            broadcastOutcome
        );
    }

    private void concludeDuel(
        UUID winnerId,
        String winnerName,
        Player onlineWinner,
        DuelEndReason reason,
        boolean broadcastOutcome
    ) {
        requirePrimaryThread();
        if (activeDuel == null || duelEnding) {
            return;
        }
        flushPendingDeathsBeforeTransition();
        if (activeDuel == null || duelEnding) {
            return;
        }
        ActiveDuel finishedDuel = activeDuel;
        duelEnding = true;
        cancelCountdownTask();
        cancelDuelTimeLimitTask();
        cancelDisconnectMonitorTask();

        if (winnerId != null) {
            if (onlineWinner != null) {
                payoutWager(onlineWinner);
            }
            if (broadcastOutcome) {
                String wagerText = finishedDuel.settings().getWager() > NO_WAGER ? " for $" + formatAmount(finishedDuel.settings().getWager()) : "";
                broadcast("messages.duel-end", "{winner}", winnerAnnouncement(finishedDuel, winnerId, winnerName), "{wager}", wagerText);
            }
        } else {
            refundWagerIfHeld();
            if (broadcastOutcome) {
                broadcast("messages.duel-draw");
            }
        }

        duelAnalyticsService.recordDuel(finishedDuel, winnerId, reason);
        boolean normalResult = reason == DuelEndReason.KILL || reason == DuelEndReason.DRAW
            || reason == DuelEndReason.DISCONNECT_TIMEOUT;
        if (normalResult) {
            boolean durableCooldown = cooldownService.recordCompletion(rosterIds(finishedDuel.teamOne()), rosterIds(finishedDuel.teamTwo()));
            if (durableCooldown && reason == DuelEndReason.DRAW && TeamMatchPolicy.allSurvivorsRequestedDraw(finishedDuel, eliminatedParticipantIds)) {
                for (MatchParticipant participant : finishedDuel.participants()) {
                    if (!eliminatedParticipantIds.contains(participant.playerId())) {
                        statsService.recordMutualDraw(participant.playerId(), participant.name());
                    }
                }
            }
            statsService.recordMatchResult(finishedDuel, winnerId, reason, durableCooldown);
        }
        runtimeStateStore.clearRuntime();

        if (winnerId != null && finishedDuel.matchType() == DuelMatchType.PARTY) {
            MatchTeam winningTeam = finishedDuel.teamOf(winnerId);
            if (winningTeam != null) {
                for (MatchParticipant participant : TeamMatchPolicy.survivingParticipants(winningTeam, eliminatedParticipantIds)) {
                    Player onlineParticipant = Bukkit.getPlayer(participant.playerId());
                    if (onlineParticipant != null && onlineParticipant.isOnline() && !onlineParticipant.isDead()) {
                        healAfterDuel(onlineParticipant);
                    }
                }
            }
        }

        if (onlineWinner != null && finishedDuel.matchType() == DuelMatchType.NORMAL
            && reason == DuelEndReason.KILL && victoryMomentSeconds > 0) {
            healAfterDuel(onlineWinner);
            startVictoryMoment(finishedDuel, onlineWinner);
            return;
        }

        finishConcludedDuel(finishedDuel, onlineWinner);
    }

    private void finishConcludedDuel(ActiveDuel finishedDuel, Player winner) {
        if (winner != null) {
            healAfterDuel(winner);
        }
        finishedDuel.participants().forEach(participant -> finishParticipantExit(participant.playerId()));

        activeDuel = null;
        duelEnding = false;
        cancelVictoryTask();
        cancelContainmentTask();
        rebuildParticipantIndex();
        spectatorManager.restoreAllOnline("duel-ended");
        cleanupArenaAfterMatch(finishedDuel, true);
    }

    private List<UUID> rosterIds(MatchTeam team) {
        return team.participants().stream().map(MatchParticipant::playerId).toList();
    }

    private boolean rejectCooldown(Player requester, Player target) {
        return rejectCooldown(requester, target, List.of(requester));
    }

    private boolean rejectCooldown(Player requester, Player target, List<Player> recipients) {
        return rejectCooldown(MatchTeam.singleton(new MatchParticipant(requester.getUniqueId(), requester.getName())),
            MatchTeam.singleton(new MatchParticipant(target.getUniqueId(), target.getName())), recipients);
    }

    private boolean rejectCooldown(MatchTeam first, MatchTeam second, List<Player> recipients) {
        if (cooldownService == null || !cooldownService.isHealthy()) {
            sendRaw(recipients, prefix + color(plugin.getConfig().getString("messages.cooldown-unavailable",
                "&cDuels are unavailable while cooldown history needs administrator attention.")));
            return true;
        }
        var blocked = cooldownService.block(rosterIds(first), rosterIds(second));
        if (blocked == null) return false;
        Map<UUID, String> names = new java.util.HashMap<>();
        first.participants().forEach(participant -> names.put(participant.playerId(), participant.name()));
        second.participants().forEach(participant -> names.put(participant.playerId(), participant.name()));
        String path = blocked.opponent() == null ? "messages.duel-cooldown" : "messages.repeat-opponent-cooldown";
        String fallback = blocked.opponent() == null ? "&c{player} must wait {seconds}s before another duel."
            : "&c{player} and {opponent} must wait {seconds}s before facing each other again.";
        String message = plugin.getConfig().getString(path, fallback)
            .replace("{player}", names.get(blocked.player()))
            .replace("{opponent}", blocked.opponent() == null ? "" : names.get(blocked.opponent()))
            .replace("{seconds}", Long.toString(blocked.remainingSeconds()));
        sendRaw(recipients, prefix + color(message));
        return true;
    }

    private void finishParticipantExit(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }
        teleportToExit(player);
        respawnToSpawn.remove(playerId);
    }

    private void clearCompletedRespawnMarker(Player player) {
        if (player == null || player.isDead() || !respawnToSpawn.remove(player.getUniqueId())) {
            return;
        }
        teleportToExit(player);
    }

    private void clearRespawnMarkerIfLiving(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline() && !player.isDead()) {
            respawnToSpawn.remove(playerId);
        }
    }

    private void teleportOnlineParticipantsToExit(ActiveDuel duel) {
        if (duel == null) {
            return;
        }
        for (MatchParticipant participant : duel.participants()) {
            Player player = Bukkit.getPlayer(participant.playerId());
            if (player != null && player.isOnline() && !player.isDead()) {
                teleportToExit(player);
            }
        }
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
        teleportAllowances.clear();
        allowedArenaItemEntityIds.clear();
        allowedArenaItemSpawnLocations.clear();
        trackedExplosionSources.clear();
        trackedExplosionOwners.clear();
        trackedBlockExplosionOwners.clear();
        lastAttributedDamagers.clear();
        pendingDeaths.clear();
        blockedItemMessageCooldowns.clear();
        arenaExitMessageCooldowns.clear();
        victoryFireworkIds.clear();
        pendingForcedDeathIds.clear();
        eliminatedParticipantIds.clear();
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

    private void schedulePartyChallengeExpiry(DuelChallenge challenge) {
        cancelRequestExpiryTask();
        long remainingMillis = Math.max(50L, challenge.expiresAtEpochMs() - System.currentTimeMillis());
        long delayTicks = Math.max(1L, (remainingMillis + 49L) / 50L);
        List<UUID> participantIds = java.util.stream.Stream.concat(
                challenge.challengerTeam().participants().stream(),
                challenge.opponentTeam().participants().stream()
            )
            .map(MatchParticipant::playerId)
            .toList();
        requestExpiryTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Optional<DuelChallenge> current = challengeService.challengeForParticipant(
                participantIds.get(0), System.currentTimeMillis()
            );
            if (current.isPresent()) {
                challengeService.cancel(participantIds.get(0), System.currentTimeMillis());
            }
            for (UUID participantId : participantIds) {
                Player participant = Bukkit.getPlayer(participantId);
                if (participant != null && participant.isOnline()) {
                    sendMessageRaw(participant, prefix + ChatColor.YELLOW + "The party duel challenge expired.");
                }
            }
            requestExpiryTask = null;
        }, delayTicks);
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
            recoveryTeleportIds.add(expired.playerId());
            runtimeStateStore.saveRecoveryTeleportIds(recoveryTeleportIds);
            LoadoutSnapshot snapshot = disconnectSnapshots.get(expired.playerId());
            MatchParticipant spoilsRecipient = selectSpoilsRecipient(expired.playerId(), null);
            if (spoilsRecipient != null && snapshot != null) {
                spoilsService.createSpoilsFromSnapshot(
                    spoilsRecipient.playerId(), spoilsRecipient.name(), expired.playerId(), expired.name(), snapshot
                );
            }
            spoilsService.markForcedDeathOnJoin(expired.playerId());
            eliminatedParticipantIds.add(expired.playerId());
            activeParticipantIndex.remove(expired.playerId());
            sendToParticipants("messages.disconnect-loss", PLAYER_PLACEHOLDER, expired.name());
            Optional<MatchTeam> winningTeam = TeamMatchPolicy.winningTeam(activeDuel, eliminatedParticipantIds);
            if (winningTeam.isEmpty()) {
                runtimeStateStore.queueActiveDuelSave(activeDuel, eliminatedParticipantIds);
                return;
            }
            concludeDuel(winningTeam.get(), DuelEndReason.DISCONNECT_TIMEOUT, true);
        }, 20L, 20L);
    }

    private MatchParticipant findExpiredDisconnectParticipant() {
        if (activeDuel == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        for (MatchParticipant participant : activeDuel.participants()) {
            if (eliminatedParticipantIds.contains(participant.playerId())) {
                continue;
            }
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
        return activeDuel.participants().stream()
            .anyMatch(participant -> participant.disconnectDeadlineEpochMs() != null);
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
        if (activeDuel == null || containmentTask != null) {
            return;
        }
        containmentTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeDuel == null) {
                cancelContainmentTask();
                return;
            }
            activeDuel.participants().forEach(participant -> enforceParticipantContainment(participant.playerId()));
        }, 5L, 5L);
    }

    private void enforceParticipantContainment(UUID playerId) {
        if (!isInActiveDuel(playerId)) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }
        if (!isParticipantInsideAllowedArena(player.getLocation())) {
            handleArenaExitAttempt(player);
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
        activeDuel.participants().forEach(participant -> economyPort.deposit(participant.playerId(), each));
        activeDuel.setWagerHeld(false);
        activeDuel.setWagerPot(0D);
    }

    private void refundPersistedWagerIfHeld(ActiveDuel duel) {
        if (duel == null || !duel.isWagerHeld()) {
            return;
        }
        double each = duel.settings().getWager();
        duel.participants().forEach(participant -> economyPort.deposit(participant.playerId(), each));
        duel.setWagerHeld(false);
        duel.setWagerPot(0D);
    }

    private void refundWagerIfHeld(ActiveDuel duel) {
        refundPersistedWagerIfHeld(duel);
    }

    private void rebuildParticipantIndex() {
        activeParticipantIndex.clear();
        if (activeDuel == null) {
            return;
        }
        for (MatchParticipant participant : activeDuel.participants()) {
            UUID playerId = participant.playerId();
            if (!eliminatedParticipantIds.contains(playerId)) {
                activeParticipantIndex.add(playerId);
            }
        }
    }

    private ArenaDefinition loadArena(FileConfiguration config) {
        String worldName = config.getString("arena.world", "world");
        Location pos1 = parseLocation(worldName, config.getString("arena.pos1", "0,64,0"));
        Location pos2 = parseLocation(worldName, config.getString("arena.pos2", "10,70,10"));
        Location spawn1 = exactSpawn(parseLocation(worldName, config.getString("arena.spawn1", "2,65,2")), 180.0F);
        Location spawn2 = exactSpawn(parseLocation(worldName, config.getString("arena.spawn2", "8,65,8")), 0.0F);
        List<Location> teamOneSpawns = List.of(
            spawn1,
            exactSpawn(parseLocation(worldName, config.getString("arena.team1-spawn2", offsetLocation(spawn1, 2D))), 180.0F),
            exactSpawn(parseLocation(worldName, config.getString("arena.team1-spawn3", offsetLocation(spawn1, -2D))), 180.0F)
        );
        List<Location> teamTwoSpawns = List.of(
            spawn2,
            exactSpawn(parseLocation(worldName, config.getString("arena.team2-spawn2", offsetLocation(spawn2, 2D))), 0.0F),
            exactSpawn(parseLocation(worldName, config.getString("arena.team2-spawn3", offsetLocation(spawn2, -2D))), 0.0F)
        );
        Location spectator = parseLocation(worldName, config.getString("arena.spectator", "5,75,5"));
        Location exit = parseLocation(worldName, config.getString("arena.exit", "0,80,0"));
        return new ArenaDefinition(worldName, pos1, pos2, teamOneSpawns, teamTwoSpawns, spectator, exit);
    }

    private void cancelDuelTimeLimitTask() {
        if (duelTimeLimitTask != null) {
            duelTimeLimitTask.cancel();
            duelTimeLimitTask = null;
        }
    }

    private void cancelDeathResolutionTask() {
        if (deathResolutionTask != null) {
            deathResolutionTask.cancel();
            deathResolutionTask = null;
        }
    }

    private String offsetLocation(Location base, double xOffset) {
        return (base.getX() + xOffset) + "," + base.getY() + "," + base.getZ();
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

    private boolean requirePermission(CommandSender sender, String permission) {
        if (sender != null && sender.hasPermission(permission)) {
            return true;
        }
        if (sender instanceof Player player) {
            sendMessage(player, "messages.no-permission");
        } else if (sender != null) {
            sendMessageRaw(sender, prefix + color(plugin.getConfig().getString("messages.no-permission", "&cYou do not have permission.")));
        }
        return false;
    }

    private Set<UUID> activeParticipantIds() {
        if (activeDuel == null) {
            return Set.of();
        }
        return activeDuel.participants().stream()
            .map(MatchParticipant::playerId)
            .filter(playerId -> !eliminatedParticipantIds.contains(playerId))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
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
        for (MatchParticipant participant : activeDuel.participants()) {
            Player player = Bukkit.getPlayer(participant.playerId());
            if (player != null) {
                sendMessage(player, path, replacements);
            }
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

    private void broadcastWatchPrompt(Set<UUID> participantIds) {
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
            if (participantIds.contains(player.getUniqueId())) {
                continue;
            }
            if (!player.hasPermission(PermissionPolicy.SPECTATE_USE)) {
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
        TypedTeleportAllowance allowance = TypedTeleportAllowance.forDestination(
            TeleportAllowanceReason.DUEL_MOVEMENT,
            location,
            System.currentTimeMillis() + 2000L
        );
        teleportAllowances.put(player.getUniqueId(), allowance);
        if (!player.teleport(location)) {
            teleportAllowances.remove(player.getUniqueId(), allowance);
        }
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

    private boolean hasOpposingSameIp(List<Player> firstTeam, List<Player> secondTeam) {
        return !allowSameIp && firstTeam.stream().anyMatch(first ->
            secondTeam.stream().anyMatch(second -> sameIp(first, second)));
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

    private void startCountdown(List<Player> participants) {
        cancelCountdownTask();
        if (startCountdownSeconds <= 0) {
            duelCountdownActive = false;
            startDuelTimeLimit();
            return;
        }
        duelCountdownActive = true;
        participants.forEach(this::applyCountdownLock);
        final int[] secondsLeft = {startCountdownSeconds};
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeDuel == null) {
                cancelCountdownTask();
                return;
            }
            if (secondsLeft[0] <= 0) {
                duelCountdownActive = false;
                for (Player participant : participants) {
                    clearCountdownLock(participant);
                    playCountdownSound(participant, true);
                    showCountdownTitle(participant, "&aFight!", "&7You are released.");
                }
                sendToParticipants("messages.duel-released");
                startDuelTimeLimit();
                cancelCountdownTask();
                return;
            }
            for (Player participant : participants) {
                playCountdownSound(participant, false);
                showCountdownTitle(participant, "&6" + secondsLeft[0], "&7Prepare to fight");
            }
            sendToParticipants("messages.duel-countdown", "{seconds}", String.valueOf(secondsLeft[0]));
            secondsLeft[0]--;
        }, 0L, 20L);
    }

    private void startDuelTimeLimit() {
        cancelDuelTimeLimitTask();
        if (activeDuel == null) {
            return;
        }
        if (duelTimeLimitSeconds <= 0) {
            activeDuel.setDuelDeadlineEpochMs(null);
            runtimeStateStore.queueActiveDuelSave(activeDuel, eliminatedParticipantIds);
            return;
        }
        long now = System.currentTimeMillis();
        Long deadline = activeDuel.duelDeadlineEpochMs();
        if (deadline == null) {
            deadline = DuelDurationPolicy.deadlineEpochMs(now, duelTimeLimitSeconds);
            activeDuel.setDuelDeadlineEpochMs(deadline);
            runtimeStateStore.queueActiveDuelSave(activeDuel, eliminatedParticipantIds);
        }
        long delayTicks = Math.max(1L, DuelDurationPolicy.remainingTicks(deadline, now));
        duelTimeLimitTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            duelTimeLimitTask = null;
            if (activeDuel != null && !duelEnding) {
                concludeDuel((Player) null, DuelEndReason.DRAW, true);
            }
        }, delayTicks);
    }

    private void sendRaw(List<Player> participants, String message) {
        for (Player participant : participants) {
            if (participant != null) {
                sendMessageRaw(participant, message);
            }
        }
    }

    private String teamLabel(MatchTeam team) {
        return team.participants().stream().map(MatchParticipant::name).collect(java.util.stream.Collectors.joining(" + "));
    }

    private String winnerAnnouncement(ActiveDuel duel, UUID winnerId, String winnerName) {
        MatchTeam team = duel.teamOf(winnerId);
        return duel.matchType() == DuelMatchType.PARTY && team != null
            ? "Party [" + teamLabel(team) + "]" : winnerName;
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

    private record PendingDeath(MatchParticipant participant, UUID killerId, List<ItemStack> drops) {
    }

    public boolean consumePendingForcedDeath(UUID playerId) {
        return pendingForcedDeathIds.remove(playerId);
    }
}
